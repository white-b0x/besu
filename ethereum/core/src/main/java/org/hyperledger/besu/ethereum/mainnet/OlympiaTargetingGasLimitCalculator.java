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

import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gas limit calculator for ETC Olympia (ECIP-1121).
 *
 * <p>EIP-7825: enforces a per-transaction gas cap of 2^24 (16,777,216).
 *
 * <p>EIP-7935: targets a block gas limit of 60,000,000. Post-Olympia, the gas limit converges from
 * the pre-fork level (≈ 8M) toward 60M via standard Yellow Paper ±1/1024 adjustment — no operator
 * flag required. Pre-Olympia blocks are returned unchanged (no early creep toward 60M).
 *
 * <p>The London fork-activation 2× doubling (ETH-specific, for gas-target field preservation) is
 * explicitly suppressed. ETC Olympia is a genuine 7.5× throughput increase converging over ≈ 2,055
 * blocks, not a field-doubling operation.
 */
public class OlympiaTargetingGasLimitCalculator extends LondonTargetingGasLimitCalculator {

  private static final Logger LOG =
      LoggerFactory.getLogger(OlympiaTargetingGasLimitCalculator.class);

  /** EIP-7825: per-transaction gas cap (2^24 = 16,777,216). */
  public static final long OLYMPIA_TRANSACTION_GAS_LIMIT_CAP = 16_777_216L;

  /** EIP-7935: block gas limit target — hardcoded, no operator flag required. */
  public static final long OLYMPIA_GAS_TARGET = 60_000_000L;

  private final long olympiaBlockNumber;

  public OlympiaTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    super(londonForkBlock, feeMarket);
    this.olympiaBlockNumber = londonForkBlock;
  }

  /**
   * Compute the next block gas limit.
   *
   * <ul>
   *   <li>Pre-Olympia (≤ activation block): return {@code currentGasLimit} unchanged.
   *   <li>Post-Olympia: Yellow Paper ±1/1024 convergence toward the hardcoded 60M target.
   * </ul>
   *
   * <p>The {@code targetGasLimit} parameter (miner config) is intentionally ignored post-Olympia;
   * the protocol target is fixed at {@link #OLYMPIA_GAS_TARGET}.
   */
  @Override
  public long nextGasLimit(
      final long currentGasLimit, final long targetGasLimit, final long newBlockNumber) {
    if (newBlockNumber <= olympiaBlockNumber) {
      return currentGasLimit;
    }

    final long nextGasLimit;
    if (currentGasLimit < OLYMPIA_GAS_TARGET) {
      nextGasLimit = Math.min(safeAddAtMost(currentGasLimit), OLYMPIA_GAS_TARGET);
    } else if (currentGasLimit > OLYMPIA_GAS_TARGET) {
      nextGasLimit = Math.max(safeSubAtMost(currentGasLimit), OLYMPIA_GAS_TARGET);
    } else {
      nextGasLimit = currentGasLimit;
    }

    if (nextGasLimit != currentGasLimit) {
      LOG.debug("Adjusting block gas limit from {} to {}", currentGasLimit, nextGasLimit);
    }

    return nextGasLimit;
  }

  @Override
  public long transactionGasLimitCap() {
    return OLYMPIA_TRANSACTION_GAS_LIMIT_CAP;
  }
}
