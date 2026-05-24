/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forward-pass total difficulty (TD) backfill for PoW chains after SNAP sync backward header
 * download.
 *
 * <p>SNAP sync's {@code ImportHeadersStep} stores headers and block-hash mappings via {@code
 * storeBlockHeaders()} but does not call {@code putTotalDifficulty()}. This leaves every downloaded
 * header without a TD entry in the DB. As a result, ETH69 Tier-1 (hash lookup) and Tier-2
 * (canonical-number lookup) both return empty and the node falls through to the Tier-3
 * marginal-rate estimate for every incoming peer, using a stale chain-head TD.
 *
 * <p>This step iterates forward from the anchor block (genesis or checkpoint, whose TD is always in
 * the DB) to the pivot block, computes cumulative TD in memory, and writes it in batches. After
 * this step, Tier-1 and Tier-2 lookups work correctly for all downloaded headers before Stage 2
 * (body download) completes.
 *
 * <p>Only runs on PoW chains ({@code MergeConfiguration.isMergeEnabled() == false}). On PoS chains,
 * {@code unsafeImportSyncBodiesAndReceipts()} in Stage 2 already stores TD for every block, making
 * this step unnecessary.
 */
public class PoWTdBackfillStep {

  private static final Logger LOG = LoggerFactory.getLogger(PoWTdBackfillStep.class);
  static final int BATCH_SIZE = 10_000;

  private final DefaultBlockchain blockchain;

  PoWTdBackfillStep(final DefaultBlockchain blockchain) {
    this.blockchain = blockchain;
  }

  /**
   * Checks whether the chain-head total difficulty is consistent with what a PoW chain should
   * accumulate from genesis, and repairs it if not.
   *
   * <p>After a SNAP sync session where the pivot block's TD was seeded from an ETH69 STATUS message
   * (which carries no real TD), the pivot and all subsequent blocks end up with a wildly wrong TD
   * in the DB. Detection heuristic: if {@code chainHeadTD < genesisBlockDifficulty ×
   * chainHeadNumber} the stored value cannot be correct (it's below even a
   * constant-genesis-difficulty chain).
   *
   * <p>Repair strategy: binary-search backward from the chain head to locate the last block whose
   * stored TD passes the monotonic lower-bound check, then forward-fill from that anchor.
   *
   * @param blockchain the blockchain to inspect and repair
   */
  public static void repairPoWTdIfNeeded(final DefaultBlockchain blockchain) {
    final long chainHeadNumber = blockchain.getChainHeadBlockNumber();
    if (chainHeadNumber < 2) {
      return;
    }

    final Difficulty chainHeadTD = blockchain.getChainHead().getTotalDifficulty();

    final BlockHeader genesisHeader =
        blockchain
            .getBlockHeader(0L)
            .orElseThrow(() -> new IllegalStateException("Genesis block header missing"));
    final BigInteger genesisDifficulty = genesisHeader.getDifficulty().getAsBigInteger();

    // minExpectedTD: lower bound for a chain where every block has exactly genesis difficulty
    final BigInteger minExpectedTD =
        genesisDifficulty.multiply(BigInteger.valueOf(chainHeadNumber));

    if (chainHeadTD.getAsBigInteger().compareTo(minExpectedTD) >= 0) {
      LOG.debug(
          "PoW TD integrity check passed: chainHeadTD={} at block {}",
          chainHeadTD,
          chainHeadNumber);
      return;
    }

    LOG.warn(
        "PoW TD integrity check FAILED: stored chainHeadTD={} at block {} is below minimum "
            + "expected {} (genesis_diff × block_count). "
            + "SNAP sync pivot was likely seeded with a wrong ETH69 STATUS TD. Running repair...",
        chainHeadTD,
        chainHeadNumber,
        Difficulty.of(minExpectedTD));

    // Binary search: find the last block whose TD passes the lower-bound check.
    // Blocks before the corrupt pivot have correct TDs; blocks at and after have wrong TDs.
    long lo = 0L;
    long hi = chainHeadNumber;
    while (hi - lo > 1) {
      final long mid = (lo + hi) / 2;
      if (isTdCorrect(blockchain, mid, genesisDifficulty)) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    final long anchor = lo;

    LOG.info(
        "PoW TD repair: last correct-TD block is {} (first corrupt block is {}). "
            + "Repairing {} blocks from anchor {} to chain head {}...",
        anchor,
        hi,
        chainHeadNumber - anchor,
        anchor,
        chainHeadNumber);

    new PoWTdBackfillStep(blockchain).backfillTd(anchor, chainHeadNumber);
  }

  private static boolean isTdCorrect(
      final DefaultBlockchain blockchain, final long blockNumber, final BigInteger genesisDiff) {
    return blockchain
        .getBlockHeader(blockNumber)
        .flatMap(h -> blockchain.getTotalDifficultyByHash(h.getHash()))
        .map(
            td ->
                td.getAsBigInteger()
                        .compareTo(genesisDiff.multiply(BigInteger.valueOf(blockNumber)))
                    >= 0)
        .orElse(false);
  }

  /**
   * Computes and stores total difficulty for every block from {@code anchorBlockNumber + 1} to
   * {@code pivotBlockNumber} (inclusive). The anchor block must already have TD in the DB (genesis
   * always does).
   *
   * @param anchorBlockNumber block whose TD is already in the DB and serves as the starting point
   * @param pivotBlockNumber last block to backfill (inclusive)
   */
  void backfillTd(final long anchorBlockNumber, final long pivotBlockNumber) {
    if (anchorBlockNumber >= pivotBlockNumber) {
      LOG.debug(
          "PoW TD backfill: anchor {} >= pivot {}, nothing to do",
          anchorBlockNumber,
          pivotBlockNumber);
      return;
    }

    final BlockHeader anchorHeader =
        blockchain
            .getBlockHeader(anchorBlockNumber)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "PoW TD backfill: anchor header missing at block " + anchorBlockNumber));

    final Difficulty anchorTd =
        blockchain
            .getTotalDifficultyByHash(anchorHeader.getHash())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "PoW TD backfill: anchor TD missing at block " + anchorBlockNumber));

    LOG.info(
        "PoW TD backfill: starting forward pass from block {} (TD={}) to pivot {}",
        anchorBlockNumber,
        anchorTd,
        pivotBlockNumber);

    final Instant startTime = Instant.now();
    final long totalBlocks = pivotBlockNumber - anchorBlockNumber;
    Difficulty td = anchorTd;
    BlockchainStorage.Updater updater = blockchain.getBlockchainStorage().updater();
    long batchCount = 0;

    for (long n = anchorBlockNumber + 1; n <= pivotBlockNumber; n++) {
      final long blockNumber = n;
      final BlockHeader header =
          blockchain
              .getBlockHeader(n)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "PoW TD backfill: header missing at block " + blockNumber));
      td = td.add(header.getDifficulty());
      updater.putTotalDifficulty(header.getHash(), td);
      batchCount++;

      if (batchCount % BATCH_SIZE == 0) {
        updater.commit();
        updater = blockchain.getBlockchainStorage().updater();
        final long written = n - anchorBlockNumber;
        final long pct = written * 100 / totalBlocks;
        LOG.info("PoW TD backfill progress: {}/{} blocks ({}%)", written, totalBlocks, pct);
      }
    }
    updater.commit();

    final Duration elapsed = Duration.between(startTime, Instant.now());
    LOG.info(
        "PoW TD backfill complete: wrote TD for {} blocks in {}s (pivot TD={})",
        totalBlocks,
        elapsed.toSeconds(),
        td);
  }
}
