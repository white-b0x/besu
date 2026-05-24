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
import org.hyperledger.besu.ethereum.MainnetBlockValidatorBuilder;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.blockhash.FrontierPreExecutionProcessor;
import org.hyperledger.besu.ethereum.mainnet.blockhash.OlympiaPreExecutionProcessor;
import org.hyperledger.besu.ethereum.mainnet.blockhash.PreExecutionProcessor;
import org.hyperledger.besu.ethereum.vm.BlockchainBasedBlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Eip7709BlockHashLookup;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for Olympia deferred EIPs: EIP-2935 (block hashes in state), EIP-7825 (TX gas cap),
 * EIP-7934 (block RLP size limit), EIP-7935 (default gas limit — miner policy only).
 */
public class OlympiaDeferredEipsTest {

  private static final BigInteger CHAIN_ID = BigInteger.valueOf(61);
  private static final long SPIRAL_BLOCK = 19_250_000L;
  private static final long OLYMPIA_BLOCK = 24_751_337L;
  private static final Address TREASURY = OlympiaTestConstants.TREASURY;
  private static final Address HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0x0000f90827f1c53a10cb7a02335b175320002935");

  private ProtocolSchedule schedule;

  @BeforeEach
  public void setup() {
    StubGenesisConfigOptions config = new StubGenesisConfigOptions();
    config.chainId(CHAIN_ID);

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

  // ===== EIP-2935: Block hashes in state =====

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
  public void olympiaHasHistoryContract() {
    PreExecutionProcessor proc = specAt(OLYMPIA_BLOCK).getPreExecutionProcessor();
    assertThat(proc.getHistoryContract())
        .as("Olympia must have EIP-2935 history storage contract")
        .isPresent()
        .hasValue(HISTORY_STORAGE_ADDRESS);
  }

  @Test
  public void spiralHasNoHistoryContract() {
    PreExecutionProcessor proc = specAt(SPIRAL_BLOCK).getPreExecutionProcessor();
    assertThat(proc.getHistoryContract())
        .as("Spiral (pre-Olympia) must NOT have history contract")
        .isEmpty();
  }

  @Test
  public void olympiaHasNoBeaconRootsContract() {
    PreExecutionProcessor proc = specAt(OLYMPIA_BLOCK).getPreExecutionProcessor();
    assertThat(proc.getBeaconRootsContract())
        .as("Olympia must NOT have beacon roots contract (ETC is PoW)")
        .isEmpty();
  }

  @Test
  public void olympiaBlockHashLookupFromContract() {
    PreExecutionProcessor proc = specAt(OLYMPIA_BLOCK).getPreExecutionProcessor();
    assertThat(proc.createBlockHashLookup(null, null))
        .as("Olympia BLOCKHASH should read from EIP-7709 system contract")
        .isInstanceOf(Eip7709BlockHashLookup.class);
  }

  // ===== EIP-7825: Transaction gas cap (2^24 = 16,777,216) =====

  @Test
  public void olympiaHasTransactionGasLimitCap() {
    ProtocolSpec olympia = specAt(OLYMPIA_BLOCK);
    assertThat(olympia.getGasLimitCalculator().transactionGasLimitCap())
        .as("Olympia must enforce 2^24 per-transaction gas cap (EIP-7825)")
        .isEqualTo(16_777_216L);
  }

  @Test
  public void spiralHasNoTransactionGasLimitCap() {
    ProtocolSpec spiral = specAt(SPIRAL_BLOCK);
    assertThat(spiral.getGasLimitCalculator().transactionGasLimitCap())
        .as("Spiral (pre-Olympia) must have no per-TX gas cap")
        .isEqualTo(Long.MAX_VALUE);
  }

  @Test
  public void olympiaUsesOlympiaGasLimitCalculator() {
    ProtocolSpec olympia = specAt(OLYMPIA_BLOCK);
    assertThat(olympia.getGasLimitCalculator())
        .isInstanceOf(OlympiaTargetingGasLimitCalculator.class);
  }

  @Test
  public void olympiaGasLimitCapConstant() {
    assertThat(OlympiaTargetingGasLimitCalculator.OLYMPIA_TRANSACTION_GAS_LIMIT_CAP)
        .as("EIP-7825 constant must be 2^24 (16,777,216)")
        .isEqualTo(16_777_216L);
  }

  // ===== EIP-7934: Block RLP size limit (8 MB) =====

  @Test
  public void olympiaBlockSizeLimitConstant() {
    assertThat(MainnetBlockValidatorBuilder.OLYMPIA_MAX_RLP_BLOCK_SIZE)
        .as("EIP-7934 block size limit must be 8 MB")
        .isEqualTo(8_388_608);
  }

  // ===== EIP-7935: Default gas limit (60M) — documentation only =====

  @Test
  public void eip7935IsMinerPolicyOnly() {
    // EIP-7935 sets a recommended default gas limit of 60M.
    // This is NOT enforced in the protocol spec — it's miner configuration.
    // The gas limit calculator does NOT enforce a minimum block gas limit.
    ProtocolSpec olympia = specAt(OLYMPIA_BLOCK);
    assertThat(olympia.getGasLimitCalculator())
        .as("Gas limit calculator should be EIP-1559 elastic (not enforcing a minimum)")
        .isInstanceOf(OlympiaTargetingGasLimitCalculator.class);
  }
}
