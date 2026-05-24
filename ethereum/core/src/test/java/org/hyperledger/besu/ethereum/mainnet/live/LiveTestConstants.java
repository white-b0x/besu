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

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;

/** Shared constants and helpers for live ETC chain tests. */
public final class LiveTestConstants {

  private LiveTestConstants() {}

  // --- Chain IDs ---
  public static final long MORDOR_CHAIN_ID = 63L;
  public static final long ETC_MAINNET_CHAIN_ID = 61L;

  // --- Genesis hashes ---
  public static final String MORDOR_GENESIS_HASH =
      "0xa68ebde7932f0a5b0e5be5cd3a0e5b44bb8a3229253f3ab032ef8b32e5c3ef13";
  public static final String ETC_MAINNET_GENESIS_HASH =
      "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3";

  // --- RPC endpoints ---
  /** Besu Mordor node (local). */
  public static final String MORDOR_RPC = "http://localhost:8548";

  /** Public ETC mainnet RPC (Rivet). */
  public static final String ETC_MAINNET_RPC = "https://etc.rivet.link";

  // --- ECIP constants ---
  /** ECIP-1017 era rounds: 5M blocks on mainnet, 2M on Mordor. */
  public static final long ETC_MAINNET_ERA_ROUNDS = 5_000_000L;

  public static final long MORDOR_ERA_ROUNDS = 2_000_000L;

  /** ECIP-1099 (Thanos) fork block on Mordor: epoch doubling from 30K to 60K. */
  public static final long MORDOR_ECIP1099_BLOCK = 2_520_000L;

  /** Spiral fork block on Mordor. */
  public static final long MORDOR_SPIRAL_BLOCK = 9_957_000L;

  /** Spiral fork block on ETC mainnet. */
  public static final long ETC_MAINNET_SPIRAL_BLOCK = 19_250_000L;

  /** Era 1 boundary on ETC mainnet (block 5M). */
  public static final long ETC_MAINNET_ERA1_BOUNDARY = 5_000_000L;

  // --- Base block reward ---
  /** Base block reward: 5 ETC in era 0, reduced 20% per era. */
  public static final BigInteger ERA0_REWARD_WEI =
      new BigInteger("5000000000000000000"); // 5 * 10^18

  // --- Helpers ---

  /** Build a web3j client for the given RPC URL. */
  public static Web3j buildWeb3j(final String rpcUrl) {
    return Web3j.build(new HttpService(rpcUrl));
  }

  /** Check if the node at the given RPC endpoint is reachable. */
  public static boolean isNodeAvailable(final Web3j web3j) {
    try {
      web3j.ethChainId().send();
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  /** Fetch a block by number. */
  public static EthBlock.Block getBlock(final Web3j web3j, final long blockNumber)
      throws IOException {
    EthBlock response =
        web3j
            .ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), false)
            .send();
    return response.getBlock();
  }

  /** Fetch the latest block. */
  public static EthBlock.Block getLatestBlock(final Web3j web3j) throws IOException {
    EthBlock response =
        web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
    return response.getBlock();
  }
}
