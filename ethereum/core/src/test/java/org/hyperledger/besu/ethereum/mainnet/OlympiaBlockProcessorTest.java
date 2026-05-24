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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableWorldState;

import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OlympiaBlockProcessor} treasury credit calculation (ECIP-1111).
 *
 * <p>The Olympia block processor extends ClassicBlockProcessor to credit {@code baseFee × gasUsed}
 * to the configured treasury address after applying standard ECIP-1017 era rewards.
 */
public class OlympiaBlockProcessorTest {

  private static final Wei BLOCK_REWARD = Wei.fromEth(5);
  private static final Address TREASURY =
      Address.fromHexString("0xCfE1e0ECbff745e6c800fF980178a8dDEf94bEe2");
  private static final Address COINBASE =
      Address.fromHexString("0x1000000000000000000000000000000000000001");

  private OlympiaBlockProcessor createProcessor(final Optional<Address> treasury) {
    return new OlympiaBlockProcessor(
        mock(MainnetTransactionProcessor.class),
        mock(AbstractBlockProcessor.TransactionReceiptFactory.class),
        BLOCK_REWARD,
        BlockHeader::getCoinbase,
        true,
        OptionalLong.of(5_000_000L),
        mock(ProtocolSchedule.class),
        BalConfiguration.DEFAULT,
        treasury);
  }

  private BlockHeader header(
      final long blockNumber, final Wei baseFee, final long gasUsed) {
    return new BlockHeaderTestFixture()
        .number(blockNumber)
        .coinbase(COINBASE)
        .baseFeePerGas(baseFee)
        .gasUsed(gasUsed)
        .timestamp(0L)
        .buildHeader();
  }

  // --- Treasury credit calculations ---

  @Test
  public void creditsTreasuryWithBaseFeeTimesGasUsed() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    Wei baseFee = Wei.of(1_000_000_000L); // 1 gwei
    long gasUsed = 21_000L;
    BlockHeader blockHeader = header(1L, baseFee, gasUsed);

    boolean result = processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    assertThat(result).isTrue();
    Wei expectedCredit = baseFee.multiply(gasUsed); // 21,000 gwei
    Wei treasuryBalance = worldState.get(TREASURY).getBalance();
    assertThat(treasuryBalance).isEqualTo(expectedCredit);
  }

  @Test
  public void noCreditWhenGasUsedIsZero() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    BlockHeader blockHeader = header(1L, Wei.of(1_000_000_000L), 0L);

    processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    // Treasury account should not exist (never created)
    assertThat(worldState.get(TREASURY)).isNull();
  }

  @Test
  public void noCreditWhenNoTreasuryConfigured() {
    OlympiaBlockProcessor processor = createProcessor(Optional.empty());
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    BlockHeader blockHeader = header(1L, Wei.of(1_000_000_000L), 21_000L);

    boolean result = processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    assertThat(result).isTrue();
    // Only coinbase should have been credited (era reward), no treasury
    assertThat(worldState.get(TREASURY)).isNull();
    assertThat(worldState.get(COINBASE).getBalance())
        .as("Coinbase should still receive era reward")
        .isGreaterThan(Wei.ZERO);
  }

  @Test
  public void treasuryAndCoinbaseAreSeparateAccounts() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    Wei baseFee = Wei.of(1_000_000_000L);
    long gasUsed = 21_000L;
    BlockHeader blockHeader = header(1L, baseFee, gasUsed);

    processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    Wei treasuryBalance = worldState.get(TREASURY).getBalance();
    Wei coinbaseBalance = worldState.get(COINBASE).getBalance();

    // Treasury gets baseFee * gasUsed
    assertThat(treasuryBalance).isEqualTo(baseFee.multiply(gasUsed));
    // Coinbase gets era reward (5 ETC for era 0)
    assertThat(coinbaseBalance).isEqualTo(Wei.fromEth(5));
    // They should not be equal (proving separate accounting)
    assertThat(treasuryBalance).isNotEqualTo(coinbaseBalance);
  }

  @Test
  public void era0RewardPlusTreasuryCredit() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    Wei baseFee = Wei.of(2_000_000_000L); // 2 gwei
    long gasUsed = 100_000L;
    // Block 1 is era 0: 5 ETC miner reward
    BlockHeader blockHeader = header(1L, baseFee, gasUsed);

    processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    assertThat(worldState.get(COINBASE).getBalance())
        .as("Era 0 miner reward should be 5 ETC")
        .isEqualTo(Wei.fromEth(5));
    assertThat(worldState.get(TREASURY).getBalance())
        .as("Treasury credit should be baseFee * gasUsed")
        .isEqualTo(baseFee.multiply(gasUsed));
  }

  @Test
  public void era1RewardPlusTreasuryCredit() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    Wei baseFee = Wei.of(1_000_000_000L);
    long gasUsed = 50_000L;
    // Block 5,000,001 is era 1: 4 ETC miner reward
    BlockHeader blockHeader = header(5_000_001L, baseFee, gasUsed);

    processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    assertThat(worldState.get(COINBASE).getBalance())
        .as("Era 1 miner reward should be 4 ETC")
        .isEqualTo(Wei.fromEth(4));
    assertThat(worldState.get(TREASURY).getBalance())
        .as("Treasury credit should be baseFee * gasUsed")
        .isEqualTo(baseFee.multiply(gasUsed));
  }

  @Test
  public void treasuryAccountCreatedIfNotExists() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    // Verify treasury account doesn't exist before
    assertThat(worldState.get(TREASURY)).isNull();

    BlockHeader blockHeader = header(1L, Wei.of(1_000_000_000L), 21_000L);
    processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    // Treasury account should now exist with balance
    assertThat(worldState.get(TREASURY)).isNotNull();
    assertThat(worldState.get(TREASURY).getBalance()).isGreaterThan(Wei.ZERO);
  }

  @Test
  public void treasuryAccumulatesAcrossMultipleBlocks() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    Wei baseFee = Wei.of(1_000_000_000L);
    long gasUsed = 21_000L;

    // Process first block
    processor.rewardCoinbase(worldState, header(1L, baseFee, gasUsed), Collections.emptyList(), false);
    Wei afterFirst = worldState.get(TREASURY).getBalance();

    // Process second block
    processor.rewardCoinbase(worldState, header(2L, baseFee, gasUsed), Collections.emptyList(), false);
    Wei afterSecond = worldState.get(TREASURY).getBalance();

    Wei singleCredit = baseFee.multiply(gasUsed);
    assertThat(afterFirst).isEqualTo(singleCredit);
    assertThat(afterSecond).isEqualTo(singleCredit.multiply(2));
  }

  @Test
  public void knownValueCheck() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    // 1 gwei baseFee * 21000 gasUsed = 21,000,000,000,000 wei = 21,000 gwei
    Wei baseFee = Wei.of(1_000_000_000L); // 1 gwei
    long gasUsed = 21_000L;
    Wei expectedCredit = Wei.of(21_000_000_000_000L); // 21,000 gwei

    BlockHeader blockHeader = header(1L, baseFee, gasUsed);
    processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    assertThat(worldState.get(TREASURY).getBalance()).isEqualTo(expectedCredit);
  }

  @Test
  public void skipZeroBlockRewardsWithZeroReward() {
    // Test with zero block reward and skipZeroBlockRewards=true
    OlympiaBlockProcessor processor = new OlympiaBlockProcessor(
        mock(MainnetTransactionProcessor.class),
        mock(AbstractBlockProcessor.TransactionReceiptFactory.class),
        Wei.ZERO, // zero block reward
        BlockHeader::getCoinbase,
        true, // skipZeroBlockRewards
        OptionalLong.of(5_000_000L),
        mock(ProtocolSchedule.class),
        BalConfiguration.DEFAULT,
        Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    BlockHeader blockHeader = header(1L, Wei.of(1_000_000_000L), 21_000L);
    boolean result = processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), true);

    // When skipZeroBlockRewards and reward is zero, the super returns true early
    // but the treasury credit should still be zero (super skipped, treasury still runs)
    assertThat(result).isTrue();
  }

  @Test
  public void returnsTrue() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    MutableWorldState worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();

    BlockHeader blockHeader = header(1L, Wei.of(1_000_000_000L), 21_000L);
    boolean result = processor.rewardCoinbase(worldState, blockHeader, Collections.emptyList(), false);

    assertThat(result).isTrue();
  }

  @Test
  public void isInstanceOfClassicBlockProcessor() {
    OlympiaBlockProcessor processor = createProcessor(Optional.of(TREASURY));
    assertThat(processor).isInstanceOf(ClassicBlockProcessor.class);
  }
}
