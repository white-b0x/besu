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

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deep protocol schedule validation for ETC Classic forks. Complements ClassicProtocolSpecsTest
 * (18 tests) with EVM version progression, transaction type restrictions, and cross-fork
 * consistency checks.
 */
public class ClassicProtocolScheduleDeepTest {

  private static final BigInteger CHAIN_ID = BigInteger.valueOf(61);

  // ETC mainnet fork blocks
  private static final long CLASSIC_FORK = 1_920_000L;
  private static final long DIE_HARD = 3_000_000L;
  private static final long GOTHAM = 5_000_000L;
  private static final long DEFUSE = 5_900_000L;
  private static final long ATLANTIS = 8_772_000L;
  private static final long AGHARTA = 9_573_000L;
  private static final long PHOENIX = 10_500_839L;
  private static final long THANOS = 11_700_000L;
  private static final long MAGNETO = 13_189_133L;
  private static final long MYSTIQUE = 14_525_000L;
  private static final long SPIRAL = 19_250_000L;

  private ProtocolSchedule schedule;

  @BeforeEach
  public void setup() {
    StubGenesisConfigOptions config = new StubGenesisConfigOptions();
    config.chainId(CHAIN_ID);
    config.classicForkBlock(CLASSIC_FORK);
    config.dieHard(DIE_HARD);
    config.gotham(GOTHAM);
    config.defuseDifficultyBomb(DEFUSE);
    config.atlantis(ATLANTIS);
    config.agharta(AGHARTA);
    config.phoenix(PHOENIX);
    config.thanos(THANOS);
    config.magneto(MAGNETO);
    config.mystique(MYSTIQUE);
    config.spiral(SPIRAL);

    ProtocolScheduleBuilder builder =
        new ProtocolScheduleBuilder(
            config,
            Optional.of(CHAIN_ID),
            ProtocolSpecAdapters.create(Long.MAX_VALUE, Function.identity()),
            false,
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            BalConfiguration.DEFAULT,
            new NoOpMetricsSystem());

    schedule = builder.createProtocolSchedule();
  }

  private ProtocolSpec specAt(final long blockNumber) {
    return schedule.getByBlockHeader(
        new BlockHeaderTestFixture().number(blockNumber).timestamp(0L).buildHeader());
  }

  // --- EVM version progression: PUSH0 requires Shanghai+ ---

  @Test
  public void preSpiralDoesNotUseShanghaiEvm() {
    EvmSpecVersion mystiqueVersion = specAt(MYSTIQUE).getEvm().getEvmVersion();
    assertThat(mystiqueVersion.ordinal())
        .as("Mystique EVM should be pre-Shanghai (no PUSH0)")
        .isLessThan(EvmSpecVersion.SHANGHAI.ordinal());
  }

  @Test
  public void spiralUsesShanghaiEvm() {
    EvmSpecVersion spiralVersion = specAt(SPIRAL).getEvm().getEvmVersion();
    assertThat(spiralVersion)
        .as("Spiral EVM should be SHANGHAI (enables PUSH0, EIP-3855)")
        .isEqualTo(EvmSpecVersion.SHANGHAI);
  }

  // --- EVM version progression: Byzantium at Atlantis ---

  @Test
  public void atlantisUsesByzantiumEvm() {
    EvmSpecVersion atlantisVersion = specAt(ATLANTIS).getEvm().getEvmVersion();
    assertThat(atlantisVersion.ordinal())
        .as("Atlantis should use at least Byzantium EVM (RETURNDATASIZE, STATICCALL)")
        .isGreaterThanOrEqualTo(EvmSpecVersion.BYZANTIUM.ordinal());
  }

  // --- EVM version progression: Constantinople at Agharta ---

  @Test
  public void aghartaUsesConstantinopleEvm() {
    EvmSpecVersion aghartaVersion = specAt(AGHARTA).getEvm().getEvmVersion();
    assertThat(aghartaVersion.ordinal())
        .as("Agharta should use at least Constantinople EVM (CREATE2, EXTCODEHASH)")
        .isGreaterThanOrEqualTo(EvmSpecVersion.CONSTANTINOPLE.ordinal());
  }

  // --- EVM version progression: Istanbul at Phoenix ---

  @Test
  public void phoenixUsesIstanbulEvm() {
    EvmSpecVersion phoenixVersion = specAt(PHOENIX).getEvm().getEvmVersion();
    assertThat(phoenixVersion.ordinal())
        .as("Phoenix should use at least Istanbul EVM (CHAINID, SELFBALANCE)")
        .isGreaterThanOrEqualTo(EvmSpecVersion.ISTANBUL.ordinal());
  }

  // --- No EIP-1559 before Olympia ---

  @Test
  public void noEip1559PreOlympia() {
    long[] allForks = {
      CLASSIC_FORK, DIE_HARD, GOTHAM, DEFUSE, ATLANTIS, AGHARTA, PHOENIX, THANOS, MAGNETO,
      MYSTIQUE, SPIRAL
    };
    for (long block : allForks) {
      assertThat(specAt(block).getFeeMarket().implementsBaseFee())
          .as("No EIP-1559 at block %d (pre-Olympia)", block)
          .isFalse();
    }
  }

  // --- No withdrawals on any ETC fork ---

  @Test
  public void noWithdrawalsAnyFork() {
    long[] allForks = {
      CLASSIC_FORK, DIE_HARD, GOTHAM, DEFUSE, ATLANTIS, AGHARTA, PHOENIX, THANOS, MAGNETO,
      MYSTIQUE, SPIRAL
    };
    for (long block : allForks) {
      assertThat(specAt(block).getWithdrawalsProcessor())
          .as("No withdrawals processor at block %d (ETC is PoW)", block)
          .isEmpty();
    }
  }

  // --- Access lists at Magneto (Berlin gas calculator) ---

  @Test
  public void magnetoEnablesBerlinAccessLists() {
    ProtocolSpec magneto = specAt(MAGNETO);
    assertThat(magneto.getGasCalculator())
        .as("Magneto should use Berlin gas calculator for access list support")
        .isInstanceOf(org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator.class);
  }

  // --- ClassicBlockProcessor across all post-Gotham forks ---

  @Test
  public void allClassicForksUseClassicBlockProcessor() {
    long[] postGothamForks = {
      GOTHAM, DEFUSE, ATLANTIS, AGHARTA, PHOENIX, THANOS, MAGNETO, MYSTIQUE, SPIRAL
    };
    for (long block : postGothamForks) {
      assertThat(specAt(block).getBlockProcessor())
          .as("Block %d should use ClassicBlockProcessor", block)
          .isInstanceOf(ClassicBlockProcessor.class);
    }
  }

  // --- EVM version never exceeds Shanghai on any pre-Olympia Classic fork ---

  @Test
  public void evmVersionNeverExceedsShanghaiPreOlympia() {
    long[] allForks = {
      CLASSIC_FORK, DIE_HARD, GOTHAM, DEFUSE, ATLANTIS, AGHARTA, PHOENIX, THANOS, MAGNETO,
      MYSTIQUE, SPIRAL
    };
    for (long block : allForks) {
      EvmSpecVersion version = specAt(block).getEvm().getEvmVersion();
      assertThat(version.ordinal())
          .as("Block %d EVM should not exceed Shanghai (no Cancun+ on ETC)", block)
          .isLessThanOrEqualTo(EvmSpecVersion.SHANGHAI.ordinal());
    }
  }
}
