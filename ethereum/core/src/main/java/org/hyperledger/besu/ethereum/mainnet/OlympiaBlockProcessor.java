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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block processor for the Olympia hard fork on Ethereum Classic.
 *
 * <p>Extends {@link ClassicBlockProcessor} to add EIP-1559 treasury credits per ECIP-1111. After
 * computing era-based miner rewards (ECIP-1017), credits {@code baseFee × gasUsed} to the
 * configured treasury address.
 */
public class OlympiaBlockProcessor extends ClassicBlockProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(OlympiaBlockProcessor.class);

  private final Optional<Address> treasuryAddress;

  public OlympiaBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final OptionalLong eraLen,
      final ProtocolSchedule protocolSchedule,
      final BalConfiguration balConfiguration,
      final Optional<Address> treasuryAddress) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        eraLen,
        protocolSchedule,
        balConfiguration);
    this.treasuryAddress = treasuryAddress;
  }

  @Override
  boolean rewardCoinbase(
      final MutableWorldState worldState,
      final BlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {
    // ECIP-1111: credit baseFee × gasUsed to treasury BEFORE miner/ommer rewards (spec ordering).
    final Optional<Wei> baseFee = header.getBaseFee();
    if (baseFee.isPresent()) {
      if (treasuryAddress.isEmpty()) {
        LOG.error(
            "Olympia active at block {} but treasury address is not configured"
                + " — baseFee revenue will not be credited",
            header.getNumber());
      } else if (header.getGasUsed() > 0) {
        final Wei credit = baseFee.get().multiply(header.getGasUsed());
        if (credit.greaterThan(Wei.ZERO)) {
          final WorldUpdater updater = worldState.updater();
          final MutableAccount treasury = updater.getOrCreate(treasuryAddress.get());
          treasury.incrementBalance(credit);
          updater.commit();
          LOG.trace(
              "Olympia treasury credit: {} wei to {} (baseFee={}, gasUsed={})",
              credit,
              treasuryAddress.get(),
              baseFee.get(),
              header.getGasUsed());
        }
      }
    }

    // Apply era-based miner and ommer rewards (ECIP-1017) after treasury credit.
    return super.rewardCoinbase(worldState, header, ommers, skipZeroBlockRewards);
  }
}
