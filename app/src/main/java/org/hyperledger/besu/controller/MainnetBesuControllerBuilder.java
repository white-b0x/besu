/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.controller;

import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.DefaultBlockScheduler;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.PoWMinerExecutor;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ArtificialFinality;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Mainnet besu controller builder. */
public class MainnetBesuControllerBuilder extends BesuControllerBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(MainnetBesuControllerBuilder.class);

  private EpochCalculator epochCalculator = new EpochCalculator.DefaultEpochCalculator();

  /** Default constructor. */
  public MainnetBesuControllerBuilder() {}

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningConfiguration miningConfiguration,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {

    final PoWMinerExecutor executor =
        new PoWMinerExecutor(
            protocolContext,
            protocolSchedule,
            transactionPool,
            miningConfiguration,
            new DefaultBlockScheduler(
                MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT,
                MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S,
                clock),
            epochCalculator,
            ethProtocolManager.ethContext().getScheduler());

    final PoWMiningCoordinator miningCoordinator =
        new PoWMiningCoordinator(protocolContext.getBlockchain(), executor, syncState);
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);
    if (miningConfiguration.isMiningEnabled()) {
      miningCoordinator.enable();
    }

    // Set MESS-aware block choice rule if ECBP-1100 is configured
    setupMessBlockChoiceRule(protocolContext.getBlockchain());

    return miningCoordinator;
  }

  /**
   * If ECBP-1100 (MESS) is configured, wrap the default block choice rule with one that applies the
   * MESS antigravity check before allowing reorgs.
   */
  private void setupMessBlockChoiceRule(final Blockchain blockchain) {
    final OptionalLong activationBlock = genesisConfigOptions.getEcbp1100Block();
    final OptionalLong deactivationBlock = genesisConfigOptions.getEcbp1100DeactivateBlock();
    final OptionalLong reactivationBlock = genesisConfigOptions.getOlympiaBlockNumber();

    if (activationBlock.isEmpty()) {
      return;
    }

    LOG.info(
        "ECBP-1100 (MESS) configured: activation={}, deactivation={}, reactivation(Olympia)={}",
        activationBlock.getAsLong(),
        deactivationBlock.isPresent() ? deactivationBlock.getAsLong() : "none",
        reactivationBlock.isPresent() ? reactivationBlock.getAsLong() : "none");

    final Comparator<BlockHeader> baseRule = blockchain.getBlockChoiceRule();
    blockchain.setBlockChoiceRule(
        (newHeader, currentHeader) -> {
          final int baseResult = baseRule.compare(newHeader, currentHeader);
          if (baseResult <= 0) {
            return baseResult; // No reorg needed, MESS irrelevant
          }

          // Check if MESS is active for the current block
          if (!ArtificialFinality.isActive(
              currentHeader.getNumber(), activationBlock, deactivationBlock, reactivationBlock)) {
            return baseResult;
          }

          // Find common ancestor and apply MESS check
          return applyMessCheck(blockchain, currentHeader, newHeader, baseResult);
        });
  }

  private int applyMessCheck(
      final Blockchain blockchain,
      final BlockHeader currentHeader,
      final BlockHeader newHeader,
      final int baseResult) {
    // Walk back from both headers to find the common ancestor
    BlockHeader oldH = currentHeader;
    BlockHeader newH = newHeader;

    // Equalize heights
    while (oldH.getNumber() > newH.getNumber()) {
      final Optional<BlockHeader> parent = blockchain.getBlockHeader(oldH.getParentHash());
      if (parent.isEmpty()) return baseResult;
      oldH = parent.get();
    }
    while (newH.getNumber() > oldH.getNumber()) {
      final Optional<BlockHeader> parent = blockchain.getBlockHeader(newH.getParentHash());
      if (parent.isEmpty()) return baseResult;
      newH = parent.get();
    }

    // Find common ancestor
    while (!oldH.getHash().equals(newH.getHash())) {
      final Optional<BlockHeader> oldParent = blockchain.getBlockHeader(oldH.getParentHash());
      final Optional<BlockHeader> newParent = blockchain.getBlockHeader(newH.getParentHash());
      if (oldParent.isEmpty() || newParent.isEmpty()) return baseResult;
      oldH = oldParent.get();
      newH = newParent.get();
    }
    final BlockHeader commonAncestor = oldH;

    // Get subchain total difficulties
    final Optional<Difficulty> commonAncestorTD =
        blockchain.getTotalDifficultyByHash(commonAncestor.getHash());
    final Optional<Difficulty> localTD =
        blockchain.getTotalDifficultyByHash(currentHeader.getHash());
    final Optional<Difficulty> proposedTD =
        blockchain.getTotalDifficultyByHash(newHeader.getHash());

    if (commonAncestorTD.isEmpty() || localTD.isEmpty() || proposedTD.isEmpty()) {
      return baseResult;
    }

    final Difficulty localSubchainTD =
        Difficulty.of(localTD.get().toBigInteger().subtract(commonAncestorTD.get().toBigInteger()));
    final Difficulty proposedSubchainTD =
        Difficulty.of(
            proposedTD.get().toBigInteger().subtract(commonAncestorTD.get().toBigInteger()));

    final long timeDelta = currentHeader.getTimestamp() - commonAncestor.getTimestamp();

    if (ArtificialFinality.shouldRejectReorg(timeDelta, localSubchainTD, proposedSubchainTD)) {
      LOG.warn(
          "ECBP1100-MESS rejected reorg: common={} current={} proposed={} timeDelta={}s",
          commonAncestor.getNumber(),
          currentHeader.getNumber(),
          newHeader.getNumber(),
          timeDelta);
      return -1; // Reject reorg
    }

    return baseResult;
  }

  @Override
  protected ConsensusContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    return null;
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return new NoopPluginServiceFactory();
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return MainnetProtocolSchedule.fromConfig(
        genesisConfigOptions,
        Optional.of(dataStorageConfiguration.getRevertReasonEnabled()),
        Optional.of(evmConfiguration),
        super.miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
  }

  @Override
  protected void prepForBuild() {
    genesisConfigOptions
        .getThanosBlockNumber()
        .ifPresent(
            activationBlock -> epochCalculator = new EpochCalculator.Ecip1099EpochCalculator());
  }
}
