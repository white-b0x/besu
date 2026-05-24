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
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.ATLANTIS;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.DIE_HARD;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.GOTHAM;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.MAGNETO;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.MYSTIQUE;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.PHOENIX;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.SPIRAL;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.THANOS;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.DieHardGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that Classic protocol specs (fork definitions) produce correct ProtocolSpec objects.
 * Validates gas calculators, fee market, hardfork IDs, and block processor types for each fork.
 */
public class ClassicProtocolSpecsTest {

  private static final BigInteger CHAIN_ID = BigInteger.valueOf(61);
  private StubGenesisConfigOptions config;
  private ProtocolSchedule schedule;

  @BeforeEach
  public void setup() {
    config = new StubGenesisConfigOptions();
    config.chainId(CHAIN_ID);

    // Set up an ETC mainnet-like schedule with all Classic forks
    config.classicForkBlock(1_920_000L);
    config.dieHard(3_000_000L);
    config.gotham(5_000_000L);
    config.defuseDifficultyBomb(5_900_000L);
    config.atlantis(8_772_000L);
    config.agharta(9_573_000L);
    config.phoenix(10_500_839L);
    config.thanos(11_700_000L);
    config.magneto(13_189_133L);
    config.mystique(14_525_000L);
    config.spiral(19_250_000L);

    ProtocolScheduleBuilder builder =
        new ProtocolScheduleBuilder(
            config,
            Optional.of(CHAIN_ID),
            ProtocolSpecAdapters.create(Long.MAX_VALUE, Function.identity()),
            false, // isRevertReasonEnabled
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false, // isParallelTxProcessingEnabled
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem());

    schedule = builder.createProtocolSchedule();
  }

  private ProtocolSpec specAt(final long blockNumber) {
    return schedule.getByBlockHeader(
        new BlockHeaderTestFixture().number(blockNumber).timestamp(0L).buildHeader());
  }

  // --- Fork identification ---

  @Test
  public void dieHardFork() {
    assertThat(specAt(3_000_000L).getHardforkId()).isEqualTo(DIE_HARD);
  }

  @Test
  public void gothamFork() {
    assertThat(specAt(5_000_000L).getHardforkId()).isEqualTo(GOTHAM);
  }

  @Test
  public void atlantisFork() {
    assertThat(specAt(8_772_000L).getHardforkId()).isEqualTo(ATLANTIS);
  }

  @Test
  public void phoenixFork() {
    assertThat(specAt(10_500_839L).getHardforkId()).isEqualTo(PHOENIX);
  }

  @Test
  public void thanosFork() {
    assertThat(specAt(11_700_000L).getHardforkId()).isEqualTo(THANOS);
  }

  @Test
  public void magnetoFork() {
    assertThat(specAt(13_189_133L).getHardforkId()).isEqualTo(MAGNETO);
  }

  @Test
  public void mystiqueFork() {
    assertThat(specAt(14_525_000L).getHardforkId()).isEqualTo(MYSTIQUE);
  }

  @Test
  public void spiralFork() {
    assertThat(specAt(19_250_000L).getHardforkId()).isEqualTo(SPIRAL);
  }

  // --- Gas calculators ---

  @Test
  public void dieHardUsesDieHardGasCalculator() {
    assertThat(specAt(3_000_000L).getGasCalculator()).isInstanceOf(DieHardGasCalculator.class);
  }

  @Test
  public void atlantisUsesSpuriousDragonGasCalculator() {
    assertThat(specAt(8_772_000L).getGasCalculator()).isInstanceOf(SpuriousDragonGasCalculator.class);
  }

  @Test
  public void phoenixUsesIstanbulGasCalculator() {
    assertThat(specAt(10_500_839L).getGasCalculator()).isInstanceOf(IstanbulGasCalculator.class);
  }

  @Test
  public void magnetoUsesBerlinGasCalculator() {
    assertThat(specAt(13_189_133L).getGasCalculator()).isInstanceOf(BerlinGasCalculator.class);
  }

  @Test
  public void mystiqueUsesLondonGasCalculator() {
    assertThat(specAt(14_525_000L).getGasCalculator()).isInstanceOf(LondonGasCalculator.class);
  }

  @Test
  public void spiralUsesShanghaiGasCalculator() {
    assertThat(specAt(19_250_000L).getGasCalculator()).isInstanceOf(ShanghaiGasCalculator.class);
  }

  // --- CRITICAL: Mystique does NOT enable EIP-1559 ---

  @Test
  public void mystiqueUsesLegacyFeeMarket() {
    ProtocolSpec mystique = specAt(14_525_000L);
    assertThat(mystique.getFeeMarket().implementsBaseFee())
        .as("Mystique (ECIP-1104) must NOT enable EIP-1559 base fee")
        .isFalse();
  }

  // --- CRITICAL: Spiral does NOT enable EIP-1559 ---

  @Test
  public void spiralUsesLegacyFeeMarket() {
    ProtocolSpec spiral = specAt(19_250_000L);
    assertThat(spiral.getFeeMarket().implementsBaseFee())
        .as("Spiral (ECIP-1109) must NOT enable EIP-1559 base fee")
        .isFalse();
  }

  // --- CRITICAL: No withdrawals on ETC ---

  @Test
  public void spiralHasNoWithdrawalsProcessor() {
    ProtocolSpec spiral = specAt(19_250_000L);
    assertThat(spiral.getWithdrawalsProcessor())
        .as("Spiral must NOT have withdrawals processor (ETC is PoW)")
        .isEmpty();
  }

  @Test
  public void mystiqueHasNoWithdrawalsProcessor() {
    ProtocolSpec mystique = specAt(14_525_000L);
    assertThat(mystique.getWithdrawalsProcessor())
        .as("Mystique must NOT have withdrawals processor")
        .isEmpty();
  }

  // --- Block processor is ClassicBlockProcessor after Gotham ---

  @Test
  public void gothamUsesClassicBlockProcessor() {
    assertThat(specAt(5_000_000L).getBlockProcessor()).isInstanceOf(ClassicBlockProcessor.class);
  }

  @Test
  public void spiralUsesClassicBlockProcessor() {
    assertThat(specAt(19_250_000L).getBlockProcessor()).isInstanceOf(ClassicBlockProcessor.class);
  }

  // --- All forks use legacy fee market (no EIP-1559 until Olympia) ---

  @Test
  public void allClassicForksUseLegacyFeeMarket() {
    long[] forkBlocks = {
      3_000_000L, 5_000_000L, 5_900_000L, 8_772_000L,
      9_573_000L, 10_500_839L, 11_700_000L, 13_189_133L,
      14_525_000L, 19_250_000L
    };
    for (long block : forkBlocks) {
      assertThat(specAt(block).getFeeMarket().implementsBaseFee())
          .as("Fork at block %d should use legacy fee market", block)
          .isFalse();
    }
  }
}
