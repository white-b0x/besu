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
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.TREASURY;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.evm.ClassicEVMs;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.DifficultyOperation;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.PrevRanDaoOperation;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ETC-specific exclusion tests: verifies that features belonging to Ethereum PoS (withdrawals,
 * PREVRANDAO, blob EIPs) are never present on any ETC fork.
 */
public class EtcExclusionTest {

  private ProtocolSchedule schedule;

  @BeforeEach
  public void setup() {
    StubGenesisConfigOptions config = new StubGenesisConfigOptions();
    config.chainId(CHAIN_ID);
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
    config.olympiaTreasuryAddress(TREASURY);

    schedule =
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
                new NoOpMetricsSystem())
            .createProtocolSchedule();
  }

  private ProtocolSpec specAt(final long blockNumber) {
    return schedule.getByBlockHeader(
        new BlockHeaderTestFixture().number(blockNumber).timestamp(0L).buildHeader());
  }

  // ===== No withdrawals on any ETC fork =====

  @Test
  public void noWithdrawalsOnAnyEtcFork() {
    // ETC is PoW — there are never validator withdrawals.
    long[] forkBlocks = {
      DIE_HARD_BLOCK, GOTHAM_BLOCK, DEFUSE_BOMB_BLOCK, ATLANTIS_BLOCK,
      AGHARTA_BLOCK, PHOENIX_BLOCK, THANOS_BLOCK, MAGNETO_BLOCK,
      MYSTIQUE_BLOCK, SPIRAL_BLOCK, OLYMPIA_BLOCK
    };
    for (long block : forkBlocks) {
      assertThat(specAt(block).getWithdrawalsProcessor())
          .as("Fork at block %d must NOT have withdrawals processor (ETC is PoW, no staking)", block)
          .isEmpty();
    }
  }

  // ===== DIFFICULTY opcode (0x44) — never replaced by PREVRANDAO on ETC =====

  @Test
  public void olympiaDifficultyOpcodeNotPrevRandao() {
    // ETC is PoW: opcode 0x44 must remain DIFFICULTY, not become PREVRANDAO (PoS-only).
    final var ops =
        ClassicEVMs.olympia(
                new OsakaGasCalculator(), BigInteger.valueOf(61L), EvmConfiguration.DEFAULT)
            .getOperationsUnsafe();
    assertThat(ops[0x44])
        .as("ETC Olympia must retain DIFFICULTY at 0x44 — PREVRANDAO is a PoS-only replacement")
        .isInstanceOf(DifficultyOperation.class)
        .isNotInstanceOf(PrevRanDaoOperation.class);
  }

  // ===== Blob opcodes absent at Olympia =====

  @Test
  public void olympiaHasNoBlobhashOpcode() {
    // BLOBHASH (EIP-4844, opcode 0x49) is a blob transaction opcode. ETC has no blob transactions.
    final var ops =
        ClassicEVMs.olympia(
                new OsakaGasCalculator(), BigInteger.valueOf(61L), EvmConfiguration.DEFAULT)
            .getOperationsUnsafe();
    assertThat(ops[0x49])
        .as("BLOBHASH (0x49) must be absent from Olympia EVM (no blob transactions on ETC)")
        .isInstanceOf(InvalidOperation.class);
  }
}
