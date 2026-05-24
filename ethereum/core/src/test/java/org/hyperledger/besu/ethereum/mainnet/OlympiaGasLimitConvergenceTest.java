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

import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Full lifecycle convergence tests for OlympiaTargetingGasLimitCalculator.
 *
 * <p>Mirrors Fukuii OlympiaGasLimitSpec: monotonic convergence, stability at target, decrease from
 * above, and 2,055-block cross-client parity with core-geth.
 */
public class OlympiaGasLimitConvergenceTest {

  private static final long PRE_OLYMPIA_GAS_LIMIT = 8_000_000L;
  private static final long OLYMPIA_GAS_TARGET = 60_000_000L;

  private OlympiaTargetingGasLimitCalculator olympiaCalc() {
    final LondonFeeMarket feeMarket = new LondonFeeMarket(OLYMPIA_BLOCK, Optional.empty());
    return new OlympiaTargetingGasLimitCalculator(OLYMPIA_BLOCK, feeMarket);
  }

  @Test
  public void convergesMonotonicallyFrom8MTo60M() {
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();
    long gasLimit = PRE_OLYMPIA_GAS_LIMIT;
    boolean reached = false;

    for (int i = 0; i < 200_000; i++) {
      final long next = calc.nextGasLimit(gasLimit, OLYMPIA_GAS_TARGET, OLYMPIA_BLOCK + i + 1);
      assertThat(next)
          .as("Gas limit must not decrease toward 60M at block %d (was %d)", i + 1, gasLimit)
          .isGreaterThanOrEqualTo(gasLimit);
      assertThat(next)
          .as("Gas limit must never overshoot 60M at block %d", i + 1)
          .isLessThanOrEqualTo(OLYMPIA_GAS_TARGET);
      gasLimit = next;
      if (gasLimit == OLYMPIA_GAS_TARGET) {
        reached = true;
        break;
      }
    }

    assertThat(reached).as("Gas limit must eventually reach exactly 60M").isTrue();
  }

  @Test
  public void stableAt60M() {
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();
    final long next = calc.nextGasLimit(OLYMPIA_GAS_TARGET, OLYMPIA_GAS_TARGET, OLYMPIA_BLOCK + 1);
    assertThat(next)
        .as("At 60M target, gas limit must remain stable at 60M")
        .isEqualTo(OLYMPIA_GAS_TARGET);
  }

  @Test
  public void decreasesFrom80MTo60M() {
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();
    long gasLimit = 80_000_000L;
    boolean reached = false;

    for (int i = 0; i < 200_000; i++) {
      final long next = calc.nextGasLimit(gasLimit, OLYMPIA_GAS_TARGET, OLYMPIA_BLOCK + i + 1);
      assertThat(next)
          .as("Gas limit must not increase when above 60M target at block %d", i + 1)
          .isLessThanOrEqualTo(gasLimit);
      assertThat(next)
          .as("Gas limit must not undershoot 60M target at block %d", i + 1)
          .isGreaterThanOrEqualTo(OLYMPIA_GAS_TARGET);
      gasLimit = next;
      if (gasLimit == OLYMPIA_GAS_TARGET) {
        reached = true;
        break;
      }
    }

    assertThat(reached).as("Gas limit must eventually decrease to exactly 60M").isTrue();
  }

  @Test
  public void convergenceBlockCountIs2055() {
    // Cross-client parity: core-geth and Fukuii both converge 8M→60M (99%) in exactly 2,055
    // blocks using the same ±1/1024 Yellow Paper algorithm.
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();
    long gasLimit = PRE_OLYMPIA_GAS_LIMIT;
    final long threshold = OLYMPIA_GAS_TARGET * 99 / 100;

    int blocks = 0;
    while (gasLimit < threshold && blocks < 200_000) {
      gasLimit = calc.nextGasLimit(gasLimit, OLYMPIA_GAS_TARGET, OLYMPIA_BLOCK + blocks + 1);
      blocks++;
    }

    assertThat(gasLimit).isGreaterThanOrEqualTo(threshold);
    assertThat(blocks)
        .as(
            "Cross-client parity (core-geth, Fukuii): 8M→99%% of 60M must take exactly 2,055 blocks")
        .isEqualTo(2055);
  }
}
