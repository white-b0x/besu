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
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.OLYMPIA;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.SPIRAL;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.blockhash.FrontierPreExecutionProcessor;
import org.hyperledger.besu.ethereum.mainnet.blockhash.OlympiaPreExecutionProcessor;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that the Olympia protocol spec (ECIP-1111 + ECIP-1121) is correctly wired.
 * Validates fork ID, gas calculator, fee market, block processor type, and precompiles.
 */
public class OlympiaProtocolSpecsTest {

  private static final BigInteger CHAIN_ID = BigInteger.valueOf(61);
  private static final long SPIRAL_BLOCK = 19_250_000L;
  private static final long OLYMPIA_BLOCK = 24_751_337L;
  private static final Address TREASURY =
      Address.fromHexString("0xCfE1e0ECbff745e6c800fF980178a8dDEf94bEe2");

  private StubGenesisConfigOptions config;
  private ProtocolSchedule schedule;

  @BeforeEach
  public void setup() {
    config = new StubGenesisConfigOptions();
    config.chainId(CHAIN_ID);

    // Set up all Classic forks through Olympia
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
    config.spiral(SPIRAL_BLOCK);
    config.olympia(OLYMPIA_BLOCK);
    config.olympiaTreasuryAddress(TREASURY);

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
  public void olympiaForkIdentified() {
    assertThat(specAt(OLYMPIA_BLOCK).getHardforkId()).isEqualTo(OLYMPIA);
  }

  @Test
  public void spiralStillIdentifiedBeforeOlympia() {
    assertThat(specAt(SPIRAL_BLOCK).getHardforkId()).isEqualTo(SPIRAL);
  }

  @Test
  public void spiralStillActiveJustBeforeOlympia() {
    assertThat(specAt(OLYMPIA_BLOCK - 1).getHardforkId()).isEqualTo(SPIRAL);
  }

  // --- Gas calculator ---

  @Test
  public void olympiaUsesOsakaGasCalculator() {
    assertThat(specAt(OLYMPIA_BLOCK).getGasCalculator())
        .isInstanceOf(OsakaGasCalculator.class);
  }

  @Test
  public void spiralStillUsesShanghaiGasCalculator() {
    assertThat(specAt(SPIRAL_BLOCK).getGasCalculator())
        .isInstanceOf(ShanghaiGasCalculator.class);
  }

  // --- Block processor type ---

  @Test
  public void olympiaUsesOlympiaBlockProcessor() {
    assertThat(specAt(OLYMPIA_BLOCK).getBlockProcessor())
        .isInstanceOf(OlympiaBlockProcessor.class);
  }

  @Test
  public void spiralUsesClassicBlockProcessor() {
    assertThat(specAt(SPIRAL_BLOCK).getBlockProcessor())
        .isInstanceOf(ClassicBlockProcessor.class);
  }

  @Test
  public void olympiaBlockProcessorIsAlsoClassicBlockProcessor() {
    // OlympiaBlockProcessor extends ClassicBlockProcessor
    assertThat(specAt(OLYMPIA_BLOCK).getBlockProcessor())
        .isInstanceOf(ClassicBlockProcessor.class);
  }

  // --- Fee market ---

  @Test
  public void olympiaHasEip1559BaseFeeMarket() {
    ProtocolSpec olympia = specAt(OLYMPIA_BLOCK);
    assertThat(olympia.getFeeMarket().implementsBaseFee())
        .as("Olympia must enable EIP-1559 base fee market")
        .isTrue();
  }

  @Test
  public void spiralUsesLegacyFeeMarket() {
    ProtocolSpec spiral = specAt(SPIRAL_BLOCK);
    assertThat(spiral.getFeeMarket().implementsBaseFee())
        .as("Spiral must NOT enable EIP-1559 base fee")
        .isFalse();
  }

  @Test
  public void allPreOlympiaForksUseLegacyFeeMarket() {
    long[] forkBlocks = {
        3_000_000L, 5_000_000L, 5_900_000L, 8_772_000L,
        9_573_000L, 10_500_839L, 11_700_000L, 13_189_133L,
        14_525_000L, SPIRAL_BLOCK
    };
    for (long block : forkBlocks) {
      assertThat(specAt(block).getFeeMarket().implementsBaseFee())
          .as("Fork at block %d should use legacy fee market", block)
          .isFalse();
    }
  }

  // --- No withdrawals on ETC ---

  @Test
  public void olympiaHasNoWithdrawalsProcessor() {
    ProtocolSpec olympia = specAt(OLYMPIA_BLOCK);
    assertThat(olympia.getWithdrawalsProcessor())
        .as("Olympia must NOT have withdrawals processor (ETC is PoW)")
        .isEmpty();
  }

  // --- Hardfork ID name ---

  @Test
  public void olympiaHardforkIdName() {
    assertThat(specAt(OLYMPIA_BLOCK).getHardforkId().name()).isEqualTo("OLYMPIA");
  }

  // --- Deferred EIP wiring ---

  @Test
  public void olympiaUsesOlympiaPreExecutionProcessor() {
    assertThat(specAt(OLYMPIA_BLOCK).getPreExecutionProcessor())
        .isInstanceOf(OlympiaPreExecutionProcessor.class);
  }

  @Test
  public void spiralUsesFrontierPreExecutionProcessor() {
    assertThat(specAt(SPIRAL_BLOCK).getPreExecutionProcessor())
        .isInstanceOf(FrontierPreExecutionProcessor.class);
  }

  @Test
  public void olympiaUsesOlympiaGasLimitCalculator() {
    assertThat(specAt(OLYMPIA_BLOCK).getGasLimitCalculator())
        .isInstanceOf(OlympiaTargetingGasLimitCalculator.class);
  }

  @Test
  public void spiralDoesNotUseOlympiaGasLimitCalculator() {
    assertThat(specAt(SPIRAL_BLOCK).getGasLimitCalculator())
        .isNotInstanceOf(OlympiaTargetingGasLimitCalculator.class);
  }
}
