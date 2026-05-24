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
package org.hyperledger.besu.ethereum.mainnet.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.MORDOR_CHAIN_ID;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.MORDOR_ECIP1099_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.MORDOR_ERA_ROUNDS;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.MORDOR_GENESIS_HASH;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.MORDOR_RPC;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.MORDOR_SPIRAL_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.buildWeb3j;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.getBlock;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.getLatestBlock;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.isNodeAvailable;

import java.math.BigInteger;
import java.time.Instant;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlock;

/**
 * Live integration tests against a running Mordor testnet Besu node on port 8548.
 *
 * <p>All tests are tagged {@code @Tag("live")} so they are excluded from normal CI builds. Run
 * manually with: {@code ./gradlew :ethereum:core:test --tests "*.live.MordorLiveTest"
 * -Djunit.jupiter.tags.include=live}
 */
@Tag("live")
public class MordorLiveTest {

  private static Web3j web3j;

  @BeforeAll
  static void setup() {
    web3j = buildWeb3j(MORDOR_RPC);
    Assumptions.assumeTrue(
        isNodeAvailable(web3j), "Mordor node not available at " + MORDOR_RPC);
  }

  @Test
  public void mordorChainId() throws Exception {
    BigInteger chainId = web3j.ethChainId().send().getChainId();
    assertThat(chainId.longValue()).isEqualTo(MORDOR_CHAIN_ID);
  }

  @Test
  public void mordorGenesisHash() throws Exception {
    EthBlock.Block genesis = getBlock(web3j, 0);
    assertThat(genesis).isNotNull();
    assertThat(genesis.getHash()).isEqualTo(MORDOR_GENESIS_HASH);
  }

  @Test
  public void mordorRecentBlockStructure() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    assertThat(block).isNotNull();
    assertThat(block.getHash()).isNotNull();
    assertThat(block.getParentHash()).isNotNull();
    assertThat(block.getMiner()).isNotNull();
    assertThat(block.getDifficulty()).isNotNull();
    assertThat(block.getNonceRaw()).isNotNull();
    assertThat(block.getMixHash()).isNotNull();
  }

  @Test
  public void mordorRecentBlockValidPoW() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    assertThat(block.getDifficulty()).isGreaterThan(BigInteger.ZERO);
    // Nonce is 8 bytes (16 hex chars + 0x prefix)
    assertThat(block.getNonceRaw()).isNotNull();
    assertThat(block.getNonceRaw().length()).isGreaterThanOrEqualTo(3); // at least "0x0"
    // MixHash is 32 bytes (64 hex chars + 0x prefix)
    assertThat(block.getMixHash()).isNotNull();
    assertThat(block.getMixHash().length()).isEqualTo(66);
  }

  @Test
  public void mordorDifficultyInRange() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    BigInteger difficulty = block.getDifficulty();
    BigInteger lowerBound = BigInteger.valueOf(1_000_000L); // 1M
    BigInteger upperBound = new BigInteger("100000000000000"); // 100T
    assertThat(difficulty)
        .as("Mordor difficulty should be between 1M and 100T")
        .isGreaterThan(lowerBound)
        .isLessThan(upperBound);
  }

  @Test
  public void mordorBlockTimestampsIncreasing() throws Exception {
    EthBlock.Block latest = getLatestBlock(web3j);
    long latestNumber = latest.getNumber().longValue();

    BigInteger prevTimestamp = BigInteger.ZERO;
    for (long i = latestNumber - 4; i <= latestNumber; i++) {
      EthBlock.Block block = getBlock(web3j, i);
      assertThat(block.getTimestamp())
          .as("Block %d timestamp should be >= previous", i)
          .isGreaterThanOrEqualTo(prevTimestamp);
      prevTimestamp = block.getTimestamp();
    }
  }

  @Test
  public void mordorGasLimitInRange() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    BigInteger gasLimit = block.getGasLimit();
    BigInteger lowerBound = BigInteger.valueOf(1_000_000L); // 1M
    BigInteger upperBound = BigInteger.valueOf(100_000_000L); // 100M
    assertThat(gasLimit)
        .as("Mordor gas limit should be between 1M and 100M")
        .isGreaterThan(lowerBound)
        .isLessThan(upperBound);
  }

  @Test
  public void mordorEcip1099ForkBlock() throws Exception {
    EthBlock.Block block = getBlock(web3j, MORDOR_ECIP1099_BLOCK);
    assertThat(block).as("ECIP-1099 fork block should exist").isNotNull();
    assertThat(block.getNumber().longValue()).isEqualTo(MORDOR_ECIP1099_BLOCK);
    assertThat(block.getHash()).isNotNull();
    assertThat(block.getDifficulty()).isGreaterThan(BigInteger.ZERO);
  }

  @Test
  public void mordorEcip1017EraReward() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    long blockNumber = block.getNumber().longValue();
    long era = blockNumber / MORDOR_ERA_ROUNDS;
    // Era should be >= 0 (we just verify the math is valid)
    assertThat(era).as("Mordor should be in a valid era").isGreaterThanOrEqualTo(0);
    // Current Mordor blocks are well past era 0 (block 15M+ / 2M = era 7+)
    assertThat(era).as("Mordor should be past era 0").isGreaterThan(0);
  }

  @Test
  public void mordorSpiralForkBlock() throws Exception {
    EthBlock.Block block = getBlock(web3j, MORDOR_SPIRAL_BLOCK);
    assertThat(block).as("Spiral fork block should exist").isNotNull();
    assertThat(block.getNumber().longValue()).isEqualTo(MORDOR_SPIRAL_BLOCK);
    assertThat(block.getHash()).isNotNull();
  }

  @Test
  public void mordorNoBaseFeePreSpiral() throws Exception {
    // Block at Spiral should not have baseFee (EIP-1559 is not enabled until Olympia)
    EthBlock.Block block = getBlock(web3j, MORDOR_SPIRAL_BLOCK);
    assertThat(block.getBaseFeePerGas())
        .as("No baseFee at Spiral (pre-Olympia)")
        .isNull();
  }

  @Test
  public void mordorSyncHealthy() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    long blockTimestamp = block.getTimestamp().longValue();
    long now = Instant.now().getEpochSecond();
    long ageSec = now - blockTimestamp;
    assertThat(ageSec)
        .as("Latest block should be within 10 minutes of wall clock")
        .isLessThan(600);
  }

  @Test
  public void mordorPeerCount() throws Exception {
    BigInteger peerCount = web3j.netPeerCount().send().getQuantity();
    assertThat(peerCount).as("Mordor node should have peers").isGreaterThan(BigInteger.ZERO);
  }

  @Test
  public void mordorProtocolVersion() throws Exception {
    String protocolVersion = web3j.ethProtocolVersion().send().getProtocolVersion();
    assertThat(protocolVersion)
        .as("Protocol version should be a non-empty string")
        .isNotNull()
        .isNotEmpty();
  }
}
