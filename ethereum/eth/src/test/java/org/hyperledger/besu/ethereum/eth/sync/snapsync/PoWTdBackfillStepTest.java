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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;

import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PoWTdBackfillStepTest {

  private static final Difficulty BLOCK_DIFFICULTY = Difficulty.of(1_000_000L);

  private BlockDataGenerator gen;
  private Block genesis;
  private DefaultBlockchain blockchain;

  @BeforeEach
  void setUp() {
    gen = new BlockDataGenerator();
    genesis = gen.genesisBlock();
    blockchain = (DefaultBlockchain) createInMemoryBlockchain(genesis);
  }

  @Test
  void backfillTd_storesCorrectCumulativeTd_forEachBlock() {
    final int n = 5;
    final List<BlockHeader> headers = buildAndStoreHeadersWithoutTd(genesis.getHeader(), n);

    new PoWTdBackfillStep(blockchain).backfillTd(0, n);

    Difficulty td =
        blockchain
            .getTotalDifficultyByHash(genesis.getHeader().getHash())
            .orElseThrow(() -> new AssertionError("genesis TD missing"));
    for (final BlockHeader h : headers) {
      td = td.add(h.getDifficulty());
      assertThat(blockchain.getTotalDifficultyByHash(h.getHash()))
          .as("TD for block %d", h.getNumber())
          .contains(td);
    }
  }

  @Test
  void backfillTd_noOp_whenAnchorEqualsPivot() {
    // No writes, no exception
    new PoWTdBackfillStep(blockchain).backfillTd(0, 0);
  }

  @Test
  void backfillTd_noOp_whenAnchorGreaterThanPivot() {
    new PoWTdBackfillStep(blockchain).backfillTd(5, 3);
  }

  @Test
  void backfillTd_throwsWhenAnchorTdMissing() {
    // Store block 1 and 2 via storeBlockHeaders (no TD — SNAP sync style)
    final BlockHeader block1 = buildHeader(genesis.getHeader(), 1);
    final BlockHeader block2 = buildHeader(block1, 2);
    blockchain.storeBlockHeaders(List.of(block1, block2));

    // Anchor at block 1 which has no TD in DB → must throw
    assertThatThrownBy(() -> new PoWTdBackfillStep(blockchain).backfillTd(1, 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("anchor TD missing");
  }

  @Test
  void backfillTd_lastBlockHasTd_whenChainExceedsBatchSize() {
    final int n = PoWTdBackfillStep.BATCH_SIZE + 1;
    final List<BlockHeader> headers = buildAndStoreHeadersWithoutTd(genesis.getHeader(), n);

    new PoWTdBackfillStep(blockchain).backfillTd(0, n);

    // Last block crosses a batch boundary — must still have TD written
    assertThat(blockchain.getTotalDifficultyByHash(headers.getLast().getHash())).isPresent();
  }

  @Test
  void backfillTd_tdAtBatchBoundaryIsCorrect() {
    // Store exactly BATCH_SIZE headers (the boundary block is the last commit in batch 1)
    final int n = PoWTdBackfillStep.BATCH_SIZE;
    buildAndStoreHeadersWithoutTd(genesis.getHeader(), n);

    new PoWTdBackfillStep(blockchain).backfillTd(0, n);

    // Expected TD: genesis TD + n * BLOCK_DIFFICULTY
    final Difficulty genesisTd =
        blockchain.getTotalDifficultyByHash(genesis.getHeader().getHash()).orElseThrow();
    final Difficulty expectedTd =
        genesisTd.add(
            Difficulty.of(
                BLOCK_DIFFICULTY.getAsBigInteger().multiply(java.math.BigInteger.valueOf(n))));

    // Look up the header at block n directly from the blockchain
    final BlockHeader atBoundary = blockchain.getBlockHeader(n).orElseThrow();
    assertThat(blockchain.getTotalDifficultyByHash(atBoundary.getHash())).contains(expectedTd);
  }

  @Test
  void backfillTd_doesNotOverwriteAlreadyKnownTd() {
    // Block 1 appended normally (TD stored by appendBlock)
    final BlockDataGenerator.BlockOptions opts =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(1)
            .setParentHash(genesis.getHeader().getHash())
            .setDifficulty(BLOCK_DIFFICULTY);
    final Block block1 = gen.block(opts);
    blockchain.appendBlock(block1, gen.receipts(block1));

    final Difficulty block1TdBefore =
        blockchain.getTotalDifficultyByHash(block1.getHash()).orElseThrow();

    // Backfill from 0 to 1 — should write the same TD (idempotent)
    new PoWTdBackfillStep(blockchain).backfillTd(0, 1);

    assertThat(blockchain.getTotalDifficultyByHash(block1.getHash())).contains(block1TdBefore);
  }

  // --- helpers ---

  private List<BlockHeader> buildAndStoreHeadersWithoutTd(
      final BlockHeader parent, final int count) {
    final List<BlockHeader> headers = new ArrayList<>();
    BlockHeader prev = parent;
    for (int i = 1; i <= count; i++) {
      final BlockHeader h = buildHeader(prev, prev.getNumber() + 1);
      headers.add(h);
      prev = h;
    }
    blockchain.storeBlockHeaders(headers);
    return headers;
  }

  private BlockHeader buildHeader(final BlockHeader parent, final long number) {
    return gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(number)
                .setParentHash(parent.getHash())
                .setDifficulty(BLOCK_DIFFICULTY))
        .getHeader();
  }
}
