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
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.OLYMPIA_BLOCK;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Bug 4 regression tests for LondonFeeMarket.computeBaseFee() decrease branch.
 *
 * <p>Before fix: with parentBaseFee=1 wei and gasUsed=0, integer division gave feeDelta=0, causing
 * baseFee to stay at 1 wei forever. Fix: floor feeDelta at 1 wei on the decrease branch, matching
 * go-ethereum's math.BigMax(feeDelta, big1) and Fukuii's BaseFeeCalculator.
 */
public class OlympiaBaseFeeTest {

  // Half of the 60M gas target is the EIP-1559 gas target (gasLimit / slackCoefficient)
  private static final long GAS_TARGET = 30_000_000L;

  private LondonFeeMarket feeMarket() {
    return new LondonFeeMarket(OLYMPIA_BLOCK, Optional.empty());
  }

  @Test
  public void baseFeeDecreaseFlooredAt1WeiWhenParentIs1Wei() {
    // BUG 4 regression: without floor, feeDelta = 1 * target / target / 8 = 0.
    // baseFee = 1 - 0 = 1 → stuck at 1 wei forever on empty blocks.
    // With fix: feeDelta = max(0, 1) = 1, baseFee = max(1-1, 0) = 0 → correctly decreases.
    final LondonFeeMarket fm = feeMarket();
    final Wei result = fm.computeBaseFee(OLYMPIA_BLOCK + 1, Wei.ONE, 0L, GAS_TARGET);
    assertThat(result.toLong())
        .as("parentBaseFee=1 wei on empty block must DECREASE to 0 (not stuck at 1 forever)")
        .isZero();
  }

  @Test
  public void baseFeeEventuallyDecaysToZeroFrom1Wei() {
    // Fukuii OlympiaBaseFeeSpec parity: baseFee eventually decays to 0 from 1 wei on empty blocks.
    final LondonFeeMarket fm = feeMarket();
    Wei baseFee = Wei.ONE;

    for (int block = 1; block <= 100; block++) {
      baseFee = fm.computeBaseFee(OLYMPIA_BLOCK + block, baseFee, 0L, GAS_TARGET);
      if (baseFee.isZero()) {
        break;
      }
    }

    assertThat(baseFee)
        .as("baseFee must eventually decay to 0 from 1 wei — Fukuii OlympiaBaseFeeSpec parity")
        .isEqualTo(Wei.ZERO);
  }

  @Test
  public void baseFeeFloorsAtZeroNotNegative() {
    // computeBaseFee must never produce a negative value (uint256 underflow guard).
    final LondonFeeMarket fm = feeMarket();

    for (long parentFee = 0; parentFee <= 20; parentFee++) {
      final Wei result = fm.computeBaseFee(OLYMPIA_BLOCK + 1, Wei.of(parentFee), 0L, GAS_TARGET);
      assertThat(result.getAsBigInteger().signum())
          .as("computeBaseFee must never return negative (parentFee=%d)", parentFee)
          .isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  public void baseFeeDecreasesOnEmptyBlocks() {
    // General decrease: parentBaseFee=100, gasUsed=0 → fee decreases.
    final LondonFeeMarket fm = feeMarket();
    final Wei result = fm.computeBaseFee(OLYMPIA_BLOCK + 1, Wei.of(100L), 0L, GAS_TARGET);
    assertThat(result.toLong())
        .as("BaseFee must decrease when gasUsed=0 (empty block, below target)")
        .isLessThan(100L);
  }

  @Test
  public void baseFeeIncreasesOnFullBlocks() {
    // Increase branch: gasUsed = 2 * target (full block) → fee increases.
    // The increase branch already has max(feeDelta, 1). Regression guard.
    final LondonFeeMarket fm = feeMarket();
    final Wei result =
        fm.computeBaseFee(OLYMPIA_BLOCK + 1, Wei.of(1_000_000_000L), GAS_TARGET * 2, GAS_TARGET);
    assertThat(result.toLong())
        .as("BaseFee must increase on a full block (gasUsed=2×target)")
        .isGreaterThan(1_000_000_000L);
  }

  @Test
  public void olympiaInitialBaseFeeMustBeOneGwei() {
    // At the Olympia fork block itself, computeBaseFee returns the genesis initial value (1 Gwei).
    final LondonFeeMarket fm = feeMarket();
    final Wei result = fm.computeBaseFee(OLYMPIA_BLOCK, Wei.ZERO, 0L, GAS_TARGET);
    assertThat(result)
        .as("Olympia activation block must return 1 Gwei initial base fee (ECIP-1111 / EIP-1559)")
        .isEqualTo(Wei.of(1_000_000_000L));
  }

  @Test
  public void baseFeeStableAtTargetGasUsed() {
    // When gasUsed == gasTarget, base fee is unchanged.
    final LondonFeeMarket fm = feeMarket();
    final Wei parentFee = Wei.of(1_500_000_000L);
    final Wei result = fm.computeBaseFee(OLYMPIA_BLOCK + 1, parentFee, GAS_TARGET, GAS_TARGET);
    assertThat(result).as("BaseFee must be stable when gasUsed == gasTarget").isEqualTo(parentFee);
  }
}
