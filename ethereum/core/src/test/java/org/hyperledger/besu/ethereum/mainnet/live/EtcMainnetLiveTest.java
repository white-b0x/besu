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
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.ETC_MAINNET_CHAIN_ID;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.ETC_MAINNET_ERA1_BOUNDARY;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.ETC_MAINNET_GENESIS_HASH;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.ETC_MAINNET_RPC;
import static org.hyperledger.besu.ethereum.mainnet.live.LiveTestConstants.ETC_MAINNET_SPIRAL_BLOCK;
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
 * Live integration tests against ETC mainnet via public RPC (etc.rivet.link).
 *
 * <p>All tests are tagged {@code @Tag("live")} so they are excluded from normal CI builds. Run
 * manually with: {@code ./gradlew :ethereum:core:test --tests "*.live.EtcMainnetLiveTest"
 * -Djunit.jupiter.tags.include=live}
 */
@Tag("live")
public class EtcMainnetLiveTest {

  private static Web3j web3j;

  @BeforeAll
  static void setup() {
    web3j = buildWeb3j(ETC_MAINNET_RPC);
    Assumptions.assumeTrue(
        isNodeAvailable(web3j), "ETC mainnet RPC not available at " + ETC_MAINNET_RPC);
  }

  @Test
  public void etcMainnetChainId() throws Exception {
    BigInteger chainId = web3j.ethChainId().send().getChainId();
    assertThat(chainId.longValue()).isEqualTo(ETC_MAINNET_CHAIN_ID);
  }

  @Test
  public void etcMainnetGenesisHash() throws Exception {
    EthBlock.Block genesis = getBlock(web3j, 0);
    assertThat(genesis).isNotNull();
    assertThat(genesis.getHash()).isEqualTo(ETC_MAINNET_GENESIS_HASH);
  }

  @Test
  public void etcMainnetRecentBlockStructure() throws Exception {
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
  public void etcMainnetRecentBlockValidPoW() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    assertThat(block.getDifficulty()).isGreaterThan(BigInteger.ZERO);
    assertThat(block.getNonceRaw()).isNotNull();
    assertThat(block.getMixHash()).isNotNull();
    assertThat(block.getMixHash().length()).isEqualTo(66); // 0x + 64 hex chars
  }

  @Test
  public void etcMainnetDifficultyInRange() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    BigInteger difficulty = block.getDifficulty();
    BigInteger lowerBound = new BigInteger("100000000000000"); // 100T
    BigInteger upperBound = new BigInteger("10000000000000000"); // 10P
    assertThat(difficulty)
        .as("ETC mainnet difficulty should be between 100T and 10P")
        .isGreaterThan(lowerBound)
        .isLessThan(upperBound);
  }

  @Test
  public void etcMainnetGasLimitAround8M() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    BigInteger gasLimit = block.getGasLimit();
    BigInteger lowerBound = BigInteger.valueOf(7_000_000L);
    BigInteger upperBound = BigInteger.valueOf(10_000_000L);
    assertThat(gasLimit)
        .as("ETC mainnet gas limit should be between 7M and 10M")
        .isGreaterThan(lowerBound)
        .isLessThan(upperBound);
  }

  @Test
  public void etcMainnetBlockTimestampsIncreasing() throws Exception {
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
  public void etcMainnetEraBoundaryRewards() throws Exception {
    // Block at era 1 boundary (block 5,000,000) should exist and be valid
    EthBlock.Block block = getBlock(web3j, ETC_MAINNET_ERA1_BOUNDARY);
    assertThat(block).as("Era 1 boundary block should exist").isNotNull();
    assertThat(block.getNumber().longValue()).isEqualTo(ETC_MAINNET_ERA1_BOUNDARY);
    assertThat(block.getDifficulty()).isGreaterThan(BigInteger.ZERO);
    assertThat(block.getMiner()).isNotNull();
  }

  @Test
  public void etcMainnetNoBaseFeePreOlympia() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    assertThat(block.getBaseFeePerGas())
        .as("No baseFee on ETC mainnet (pre-Olympia)")
        .isNull();
  }

  @Test
  public void etcMainnetSpiralForkBlock() throws Exception {
    EthBlock.Block block = getBlock(web3j, ETC_MAINNET_SPIRAL_BLOCK);
    assertThat(block).as("Spiral fork block should exist").isNotNull();
    assertThat(block.getNumber().longValue()).isEqualTo(ETC_MAINNET_SPIRAL_BLOCK);
    assertThat(block.getHash()).isNotNull();
  }

  @Test
  public void etcMainnetSyncHealthy() throws Exception {
    EthBlock.Block block = getLatestBlock(web3j);
    long blockTimestamp = block.getTimestamp().longValue();
    long now = Instant.now().getEpochSecond();
    long ageSec = now - blockTimestamp;
    // Public RPC may lag slightly, allow 10 minutes
    assertThat(ageSec)
        .as("Latest block should be within 10 minutes of wall clock")
        .isLessThan(600);
  }

  @Test
  public void etcMainnetProtocolVersion() throws Exception {
    String protocolVersion = web3j.ethProtocolVersion().send().getProtocolVersion();
    assertThat(protocolVersion)
        .as("Protocol version should be a non-empty string")
        .isNotNull()
        .isNotEmpty();
  }
}
