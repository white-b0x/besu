/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ClassicBlockProcessor} ECIP-1017 era-based monetary policy.
 *
 * <p>ECIP-1017 reduces block and uncle rewards by 20% each era. An era is 5,000,000 blocks on ETC
 * mainnet and 2,000,000 blocks on Mordor.
 *
 * <p>Reward formula: blockReward * (4/5)^era
 */
public class ClassicBlockProcessorTest {

  private static final Wei BLOCK_REWARD = Wei.fromEth(5);

  private ClassicBlockProcessor createProcessor(final long eraLength) {
    return new ClassicBlockProcessor(
        mock(MainnetTransactionProcessor.class),
        mock(AbstractBlockProcessor.TransactionReceiptFactory.class),
        BLOCK_REWARD,
        BlockHeader::getCoinbase,
        true,
        OptionalLong.of(eraLength),
        mock(ProtocolSchedule.class),
        BalConfiguration.DEFAULT);
  }

  private ClassicBlockProcessor createDefaultProcessor() {
    return createProcessor(5_000_000L);
  }

  private ClassicBlockProcessor createMordorProcessor() {
    return createProcessor(2_000_000L);
  }

  // --- Era 0 (full rewards) ---

  @Test
  public void era0CoinbaseRewardNoOmmers() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 1L, 0);
    // Era 0: 5 ETC + 0 ommers bonus = 5 ETC
    assertThat(reward).isEqualTo(Wei.fromEth(5));
  }

  @Test
  public void era0CoinbaseRewardWithOneOmmer() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 1L, 1);
    // Era 0: 5 ETC + (5 * 1 / 32) = 5 + 0.15625 = 5.15625 ETC
    Wei expected = Wei.fromEth(5).plus(Wei.fromEth(5).divide(32));
    assertThat(reward).isEqualTo(expected);
  }

  @Test
  public void era0CoinbaseRewardWithTwoOmmers() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 1L, 2);
    // Era 0: 5 ETC + (5 * 2 / 32) = 5 + 0.3125 = 5.3125 ETC
    Wei expected = Wei.fromEth(5).plus(Wei.fromEth(5).multiply(2).divide(32));
    assertThat(reward).isEqualTo(expected);
  }

  @Test
  public void era0OmmerRewardDistance1() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // blockNumber=10, ommerNumber=9, distance=1
    Wei reward = processor.getOmmerReward(BLOCK_REWARD, 10L, 9L);
    // Era 0, distance-based: 5 - 5 * 1 / 8 = 5 - 0.625 = 4.375 ETC
    Wei expected = Wei.fromEth(5).subtract(Wei.fromEth(5).multiply(1).divide(8));
    assertThat(reward).isEqualTo(expected);
  }

  @Test
  public void era0OmmerRewardDistance7() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // blockNumber=10, ommerNumber=3, distance=7
    Wei reward = processor.getOmmerReward(BLOCK_REWARD, 10L, 3L);
    // Era 0, distance-based: 5 - 5 * 7 / 8 = 5 - 4.375 = 0.625 ETC
    Wei expected = Wei.fromEth(5).subtract(Wei.fromEth(5).multiply(7).divide(8));
    assertThat(reward).isEqualTo(expected);
  }

  // --- Era 1 (80% of era 0) ---

  @Test
  public void era1CoinbaseRewardNoOmmers() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 5,000,001 is in era 1 (getBlockEra = (5000001 - 1) % 5000000 = 0, base=5000000, d=1)
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 5_000_001L, 0);
    // Era 1: 5 * (4/5) = 4 ETC
    assertThat(reward).isEqualTo(Wei.fromEth(4));
  }

  @Test
  public void era1CoinbaseRewardWithOneOmmer() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 5_000_001L, 1);
    // Era 1: 4 ETC + (4 * 1 / 32) = 4 + 0.125 = 4.125 ETC
    Wei expected = Wei.fromEth(4).plus(Wei.fromEth(4).divide(32));
    assertThat(reward).isEqualTo(expected);
  }

  @Test
  public void era1OmmerRewardIsFlat() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Era 1+: flat 1/32 of winner reward, regardless of distance
    Wei reward1 = processor.getOmmerReward(BLOCK_REWARD, 5_000_010L, 5_000_009L);
    Wei reward7 = processor.getOmmerReward(BLOCK_REWARD, 5_000_010L, 5_000_003L);
    // Era 1: flat 4 / 32 = 0.125 ETC
    Wei expected = Wei.fromEth(4).divide(32);
    assertThat(reward1).isEqualTo(expected);
    assertThat(reward7).isEqualTo(expected);
  }

  // --- Era 2 (64% of era 0) ---

  @Test
  public void era2CoinbaseReward() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 10,000,001 is in era 2
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 10_000_001L, 0);
    // Era 2: 5 * (4/5)^2 = 5 * 16/25 = 3.2 ETC
    Wei expected = Wei.of(BLOCK_REWARD.toBigInteger().multiply(java.math.BigInteger.valueOf(16)).divide(java.math.BigInteger.valueOf(25)));
    assertThat(reward).isEqualTo(expected);
  }

  // --- Era 3 ---

  @Test
  public void era3CoinbaseReward() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 15,000,001 is in era 3
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 15_000_001L, 0);
    // Era 3: 5 * (4/5)^3 = 5 * 64/125 = 2.56 ETC
    Wei expected = Wei.of(BLOCK_REWARD.toBigInteger().multiply(java.math.BigInteger.valueOf(64)).divide(java.math.BigInteger.valueOf(125)));
    assertThat(reward).isEqualTo(expected);
  }

  // --- Era 4 ---

  @Test
  public void era4CoinbaseReward() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 20,000,001 is in era 4
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 20_000_001L, 0);
    // Era 4: 5 * (4/5)^4 = 5 * 256/625 = 2.048 ETC
    Wei expected = Wei.of(BLOCK_REWARD.toBigInteger().multiply(java.math.BigInteger.valueOf(256)).divide(java.math.BigInteger.valueOf(625)));
    assertThat(reward).isEqualTo(expected);
  }

  // --- Mordor (2M era length) ---

  @Test
  public void mordorEra0() {
    ClassicBlockProcessor processor = createMordorProcessor();
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 1L, 0);
    assertThat(reward).isEqualTo(Wei.fromEth(5));
  }

  @Test
  public void mordorEra1() {
    ClassicBlockProcessor processor = createMordorProcessor();
    // Mordor block 2,000,001 is in era 1
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 2_000_001L, 0);
    assertThat(reward).isEqualTo(Wei.fromEth(4));
  }

  @Test
  public void mordorEra2() {
    ClassicBlockProcessor processor = createMordorProcessor();
    // Mordor block 4,000,001 is in era 2
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 4_000_001L, 0);
    Wei expected = Wei.of(BLOCK_REWARD.toBigInteger().multiply(java.math.BigInteger.valueOf(16)).divide(java.math.BigInteger.valueOf(25)));
    assertThat(reward).isEqualTo(expected);
  }

  // --- Era boundaries ---

  @Test
  public void eraBoundaryLastBlockOfEra0() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 5,000,000 is the last block of era 0
    // getBlockEra: (5000000 - 1) % 5000000 = 4999999, base = 5000000 - 4999999 = 1, d = 1/5000000 = 0
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 5_000_000L, 0);
    assertThat(reward).isEqualTo(Wei.fromEth(5));
  }

  @Test
  public void eraBoundaryFirstBlockOfEra1() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 5,000,001: (5000001 - 1) % 5000000 = 0, base = 5000000, d = 1
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 5_000_001L, 0);
    assertThat(reward).isEqualTo(Wei.fromEth(4));
  }

  @Test
  public void genesisBlockReward() {
    ClassicBlockProcessor processor = createDefaultProcessor();
    // Block 0 should be era 0
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 0L, 0);
    assertThat(reward).isEqualTo(Wei.fromEth(5));
  }

  // --- Default era length (5M) when not specified ---

  @Test
  public void defaultEraLengthWhenNotSpecified() {
    ClassicBlockProcessor processor =
        new ClassicBlockProcessor(
            mock(MainnetTransactionProcessor.class),
            mock(AbstractBlockProcessor.TransactionReceiptFactory.class),
            BLOCK_REWARD,
            BlockHeader::getCoinbase,
            true,
            OptionalLong.empty(), // Not specified — should default to 5M
            mock(ProtocolSchedule.class),
            BalConfiguration.DEFAULT);

    // Block 5,000,001 should be era 1 with default 5M era length
    Wei reward = processor.getCoinbaseReward(BLOCK_REWARD, 5_000_001L, 0);
    assertThat(reward).isEqualTo(Wei.fromEth(4));
  }
}
