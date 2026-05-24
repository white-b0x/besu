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
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.OLYMPIA;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.PHOENIX;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.SPIRAL;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.THANOS;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.AGHARTA_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.ATLANTIS_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.CHAIN_ID;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.CLASSIC_FORK_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.DEFUSE_BOMB_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.DIE_HARD_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.GOTHAM_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.MAGNETO_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.MYSTIQUE_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.OLYMPIA_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.PHOENIX_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.SPIRAL_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.THANOS_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MAX_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MIN_GAS_LIMIT;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.DieHardGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that Classic protocol specs (fork definitions) produce correct ProtocolSpec objects.
 * Validates gas calculators, fee market, hardfork IDs, and block processor types for each fork.
 */
public class ClassicProtocolSpecsTest {

  private StubGenesisConfigOptions config;
  private ProtocolSchedule schedule;

  @BeforeEach
  public void setup() {
    config = new StubGenesisConfigOptions();
    config.chainId(CHAIN_ID);

    // Set up an ETC mainnet-like schedule with all Classic forks
    config.classicForkBlock(CLASSIC_FORK_BLOCK);
    config.dieHard(DIE_HARD_BLOCK);
    config.gotham(GOTHAM_BLOCK);
    config.defuseDifficultyBomb(DEFUSE_BOMB_BLOCK);
    config.atlantis(ATLANTIS_BLOCK);
    config.agharta(AGHARTA_BLOCK);
    config.phoenix(PHOENIX_BLOCK);
    config.thanos(THANOS_BLOCK);
    config.magneto(MAGNETO_BLOCK);
    config.mystique(MYSTIQUE_BLOCK);
    config.spiral(SPIRAL_BLOCK);
    config.olympia(OLYMPIA_BLOCK);
    config.olympiaTreasuryAddress(OlympiaTestConstants.TREASURY);

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
    assertThat(specAt(DIE_HARD_BLOCK).getHardforkId()).isEqualTo(DIE_HARD);
  }

  @Test
  public void gothamFork() {
    assertThat(specAt(GOTHAM_BLOCK).getHardforkId()).isEqualTo(GOTHAM);
  }

  @Test
  public void atlantisFork() {
    assertThat(specAt(ATLANTIS_BLOCK).getHardforkId()).isEqualTo(ATLANTIS);
  }

  @Test
  public void phoenixFork() {
    assertThat(specAt(PHOENIX_BLOCK).getHardforkId()).isEqualTo(PHOENIX);
  }

  @Test
  public void thanosFork() {
    assertThat(specAt(THANOS_BLOCK).getHardforkId()).isEqualTo(THANOS);
  }

  @Test
  public void magnetoFork() {
    assertThat(specAt(MAGNETO_BLOCK).getHardforkId()).isEqualTo(MAGNETO);
  }

  @Test
  public void mystiqueFork() {
    assertThat(specAt(MYSTIQUE_BLOCK).getHardforkId()).isEqualTo(MYSTIQUE);
  }

  @Test
  public void spiralFork() {
    assertThat(specAt(SPIRAL_BLOCK).getHardforkId()).isEqualTo(SPIRAL);
  }

  // --- Gas calculators ---

  @Test
  public void dieHardUsesDieHardGasCalculator() {
    assertThat(specAt(DIE_HARD_BLOCK).getGasCalculator()).isInstanceOf(DieHardGasCalculator.class);
  }

  @Test
  public void atlantisUsesSpuriousDragonGasCalculator() {
    assertThat(specAt(ATLANTIS_BLOCK).getGasCalculator())
        .isInstanceOf(SpuriousDragonGasCalculator.class);
  }

  @Test
  public void phoenixUsesIstanbulGasCalculator() {
    assertThat(specAt(PHOENIX_BLOCK).getGasCalculator()).isInstanceOf(IstanbulGasCalculator.class);
  }

  @Test
  public void magnetoUsesBerlinGasCalculator() {
    assertThat(specAt(MAGNETO_BLOCK).getGasCalculator()).isInstanceOf(BerlinGasCalculator.class);
  }

  @Test
  public void mystiqueUsesLondonGasCalculator() {
    assertThat(specAt(MYSTIQUE_BLOCK).getGasCalculator()).isInstanceOf(LondonGasCalculator.class);
  }

  @Test
  public void spiralUsesShanghaiGasCalculator() {
    assertThat(specAt(SPIRAL_BLOCK).getGasCalculator()).isInstanceOf(ShanghaiGasCalculator.class);
  }

  // --- CRITICAL: Mystique does NOT enable EIP-1559 ---

  @Test
  public void mystiqueUsesLegacyFeeMarket() {
    ProtocolSpec mystique = specAt(MYSTIQUE_BLOCK);
    assertThat(mystique.getFeeMarket().implementsBaseFee())
        .as("Mystique (ECIP-1104) must NOT enable EIP-1559 base fee")
        .isFalse();
  }

  // --- CRITICAL: Spiral does NOT enable EIP-1559 ---

  @Test
  public void spiralUsesLegacyFeeMarket() {
    ProtocolSpec spiral = specAt(SPIRAL_BLOCK);
    assertThat(spiral.getFeeMarket().implementsBaseFee())
        .as("Spiral (ECIP-1109) must NOT enable EIP-1559 base fee")
        .isFalse();
  }

  // --- CRITICAL: No withdrawals on ETC ---

  @Test
  public void spiralHasNoWithdrawalsProcessor() {
    ProtocolSpec spiral = specAt(SPIRAL_BLOCK);
    assertThat(spiral.getWithdrawalsProcessor())
        .as("Spiral must NOT have withdrawals processor (ETC is PoW)")
        .isEmpty();
  }

  @Test
  public void mystiqueHasNoWithdrawalsProcessor() {
    ProtocolSpec mystique = specAt(MYSTIQUE_BLOCK);
    assertThat(mystique.getWithdrawalsProcessor())
        .as("Mystique must NOT have withdrawals processor")
        .isEmpty();
  }

  // --- Block processor is ClassicBlockProcessor after Gotham ---

  @Test
  public void gothamUsesClassicBlockProcessor() {
    assertThat(specAt(GOTHAM_BLOCK).getBlockProcessor()).isInstanceOf(ClassicBlockProcessor.class);
  }

  @Test
  public void spiralUsesClassicBlockProcessor() {
    assertThat(specAt(SPIRAL_BLOCK).getBlockProcessor()).isInstanceOf(ClassicBlockProcessor.class);
  }

  // --- All forks use legacy fee market (no EIP-1559 until Olympia) ---

  @Test
  public void allPreOlympiaClassicForksUseLegacyFeeMarket() {
    long[] forkBlocks = {
      DIE_HARD_BLOCK, GOTHAM_BLOCK, DEFUSE_BOMB_BLOCK, ATLANTIS_BLOCK,
      AGHARTA_BLOCK, PHOENIX_BLOCK, THANOS_BLOCK, MAGNETO_BLOCK,
      MYSTIQUE_BLOCK, SPIRAL_BLOCK
    };
    for (long block : forkBlocks) {
      assertThat(specAt(block).getFeeMarket().implementsBaseFee())
          .as("Fork at block %d should use legacy fee market", block)
          .isFalse();
    }
  }

  // --- Olympia ---

  @Test
  public void olympiaFork() {
    assertThat(specAt(OLYMPIA_BLOCK).getHardforkId()).isEqualTo(OLYMPIA);
  }

  @Test
  public void olympiaUsesOsakaGasCalculator() {
    assertThat(specAt(OLYMPIA_BLOCK).getGasCalculator()).isInstanceOf(OsakaGasCalculator.class);
  }

  @Test
  public void olympiaUsesOlympiaBlockProcessor() {
    assertThat(specAt(OLYMPIA_BLOCK).getBlockProcessor()).isInstanceOf(OlympiaBlockProcessor.class);
  }

  @Test
  public void olympiaEnablesEip1559() {
    assertThat(specAt(OLYMPIA_BLOCK).getFeeMarket().implementsBaseFee())
        .as("Olympia must enable EIP-1559 base fee market")
        .isTrue();
  }

  @Test
  public void olympiaHasNoWithdrawalsProcessor() {
    assertThat(specAt(OLYMPIA_BLOCK).getWithdrawalsProcessor())
        .as("Olympia must NOT have withdrawals processor (ETC is PoW)")
        .isEmpty();
  }

  // --- Gas limit validator: no 2× doubling on ETC ---

  @Test
  public void olympiaValidatorAcceptsCanonicalFirstBlock() {
    // BUG 2 regression: with Optional.empty(), ±1/1024 is the only rule.
    // Canonical delta from 8M: 8_000_000/1024 - 1 = 7,811.
    final GasLimitRangeAndDeltaValidationRule rule =
        new GasLimitRangeAndDeltaValidationRule(
            DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT, Optional.empty());
    final BlockHeader parent = new BlockHeaderTestFixture().gasLimit(8_000_000L).buildHeader();
    final BlockHeader header = new BlockHeaderTestFixture().gasLimit(8_007_811L).buildHeader();
    assertThat(rule.validate(header, parent))
        .as("Canonical first Olympia block (gasLimit=8,007,811) must pass gas limit validation")
        .isTrue();
  }

  @Test
  public void olympiaValidatorRejects2xGasJump() {
    // BUG 2 regression: before fix, LondonFeeMarket.gasLimitValidationMode(olympiaBlock)
    // returned INITIAL, making effectiveParent=16M. That caused a 2× jump to be accepted
    // and the canonical block to be rejected — a consensus split with core-geth and Fukuii.
    final GasLimitRangeAndDeltaValidationRule rule =
        new GasLimitRangeAndDeltaValidationRule(
            DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT, Optional.empty());
    final BlockHeader parent = new BlockHeaderTestFixture().gasLimit(8_000_000L).buildHeader();
    final BlockHeader header = new BlockHeaderTestFixture().gasLimit(16_000_000L).buildHeader();
    assertThat(rule.validate(header, parent))
        .as("2× gas jump at Olympia must be REJECTED (London 2× must not apply to ETC)")
        .isFalse();
  }

  @Test
  public void mystiqueGasLimitDoesNotDoubleAtFork() {
    // Regression guard: no fork-activation gas doubling at Mystique (or any pre-Olympia fork).
    final GasLimitRangeAndDeltaValidationRule rule =
        new GasLimitRangeAndDeltaValidationRule(
            DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT, Optional.empty());
    final BlockHeader parent = new BlockHeaderTestFixture().gasLimit(8_000_000L).buildHeader();
    final BlockHeader header = new BlockHeaderTestFixture().gasLimit(8_007_811L).buildHeader();
    assertThat(rule.validate(header, parent))
        .as("Mystique fork must NOT trigger 2× gas doubling")
        .isTrue();
  }
}
