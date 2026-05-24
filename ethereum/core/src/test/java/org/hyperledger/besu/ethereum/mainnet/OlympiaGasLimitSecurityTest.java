/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MAX_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MIN_GAS_LIMIT;

import org.hyperledger.besu.config.NetworkDefinition;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;

import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * EIP-7935: Gas limit security tests for the Olympia hard fork.
 *
 * <p>Verifies that the ETC network definitions target 60M gas, that the gas limit convergence
 * algorithm matches cross-client expectations (2,055 blocks from 8M to 60M), and that adversarial
 * miner populations cannot deviate the gas limit equilibrium beyond game-theoretic bounds.
 *
 * <p>Cross-client parity: core-geth CalcGasLimit() and fukuii calculateGasLimit() produce identical
 * convergence timing.
 */
public class OlympiaGasLimitSecurityTest {

  private static final long OLYMPIA_GAS_TARGET = 60_000_000L;
  private static final long PRE_OLYMPIA_GAS_LIMIT = 8_000_000L;
  private static final long ADJUSTMENT_FACTOR = 1024L;

  @Test
  public void classicNetworkTargetIs60M() {
    assertThat(NetworkDefinition.CLASSIC.getTargetGasLimit()).isEqualTo(OLYMPIA_GAS_TARGET);
  }

  @Test
  public void mordorNetworkTargetIs60M() {
    assertThat(NetworkDefinition.MORDOR.getTargetGasLimit()).isEqualTo(OLYMPIA_GAS_TARGET);
  }

  @Test
  public void olympiaTransactionGasCapIs2Pow24() {
    assertThat(OlympiaTargetingGasLimitCalculator.OLYMPIA_TRANSACTION_GAS_LIMIT_CAP)
        .isEqualTo(16_777_216L);
  }

  @Test
  public void convergenceFrom8MTo60MIn2055Blocks() {
    final FrontierTargetingGasLimitCalculator calc = new FrontierTargetingGasLimitCalculator();
    long gasLimit = PRE_OLYMPIA_GAS_LIMIT;
    final long threshold = OLYMPIA_GAS_TARGET * 99 / 100; // 99% of 60M

    int blocks = 0;
    while (gasLimit < threshold && blocks < 200_000) {
      gasLimit = calc.nextGasLimit(gasLimit, OLYMPIA_GAS_TARGET, blocks + 1);
      blocks++;
    }

    assertThat(gasLimit).isGreaterThanOrEqualTo(threshold);
    // Must match core-geth and fukuii: exactly 2,055 blocks
    assertThat(blocks).isEqualTo(2055);
  }

  @Test
  public void stableAt60MTarget() {
    final FrontierTargetingGasLimitCalculator calc = new FrontierTargetingGasLimitCalculator();
    final long next = calc.nextGasLimit(OLYMPIA_GAS_TARGET, OLYMPIA_GAS_TARGET, 1);
    assertThat(next).isEqualTo(OLYMPIA_GAS_TARGET);
  }

  @Test
  public void decreaseFromAbove60M() {
    final FrontierTargetingGasLimitCalculator calc = new FrontierTargetingGasLimitCalculator();
    final long aboveTarget = 80_000_000L;
    final long next = calc.nextGasLimit(aboveTarget, OLYMPIA_GAS_TARGET, 1);
    assertThat(next).isLessThan(aboveTarget);
    assertThat(next).isGreaterThanOrEqualTo(OLYMPIA_GAS_TARGET);
  }

  @Test
  public void perBlockDeltaRespectsBound() {
    final FrontierTargetingGasLimitCalculator calc = new FrontierTargetingGasLimitCalculator();
    final long maxDelta = PRE_OLYMPIA_GAS_LIMIT / ADJUSTMENT_FACTOR - 1;

    final long next = calc.nextGasLimit(PRE_OLYMPIA_GAS_LIMIT, OLYMPIA_GAS_TARGET, 1);
    final long delta = next - PRE_OLYMPIA_GAS_LIMIT;

    assertThat(delta).isEqualTo(maxDelta);
    assertThat(delta).isEqualTo(7811L); // 8_000_000 / 1024 - 1
  }

  @Test
  public void validationRejectsInvalidDelta() {
    final GasLimitRangeAndDeltaValidationRule rule =
        new GasLimitRangeAndDeltaValidationRule(
            DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT, Optional.empty());

    final BlockHeader parent =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT).buildHeader();

    // Valid: exact max delta (parent/1024 - 1 = 7811)
    final BlockHeader validHeader =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT + 7811).buildHeader();
    assertThat(rule.validate(validHeader, parent)).isTrue();

    // Invalid: exceeds ±1/1024 bound (delta = parent/1024 = 7812.5, rounds to 7812)
    final long bound = PRE_OLYMPIA_GAS_LIMIT / ADJUSTMENT_FACTOR;
    final BlockHeader invalidHeader =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT + bound).buildHeader();
    assertThat(rule.validate(invalidHeader, parent)).isFalse();
  }

  @Test
  public void adversarial70_30SplitEquilibriumNear60M() {
    final FrontierTargetingGasLimitCalculator calc = new FrontierTargetingGasLimitCalculator();
    final long adversaryTarget = 30_000_000L;

    // Run 4 seeds to reduce variance
    for (int seed = 0; seed < 4; seed++) {
      final Random rng = new Random(42 + seed);
      long gasLimit = PRE_OLYMPIA_GAS_LIMIT;

      // Simulate 10,000 blocks with 70% honest (60M) / 30% adversary (30M)
      for (int i = 0; i < 10_000; i++) {
        final long target = rng.nextInt(100) < 70 ? OLYMPIA_GAS_TARGET : adversaryTarget;
        gasLimit = calc.nextGasLimit(gasLimit, target, i + 1);
      }

      // With 70% honest hashrate, equilibrium should be within 5% of 60M
      assertThat(gasLimit)
          .as("seed=%d equilibrium=%d", seed, gasLimit)
          .isGreaterThanOrEqualTo(OLYMPIA_GAS_TARGET * 95 / 100);
      assertThat(gasLimit)
          .as("seed=%d equilibrium=%d", seed, gasLimit)
          .isLessThanOrEqualTo(OLYMPIA_GAS_TARGET * 105 / 100);
    }
  }

  @Test
  public void adversarial50_50SplitEquilibriumNearLowerTarget() {
    final FrontierTargetingGasLimitCalculator calc = new FrontierTargetingGasLimitCalculator();
    final long adversaryTarget = 30_000_000L;
    final Random rng = new Random(42);
    long gasLimit = OLYMPIA_GAS_TARGET; // Start at 60M

    // Simulate 10,000 blocks with 50/50 split
    for (int i = 0; i < 10_000; i++) {
      final long target = rng.nextInt(100) < 50 ? OLYMPIA_GAS_TARGET : adversaryTarget;
      gasLimit = calc.nextGasLimit(gasLimit, target, i + 1);
    }

    // With 50/50, equilibrium drifts toward the lower target (~30-35M range)
    assertThat(gasLimit).isLessThan(OLYMPIA_GAS_TARGET);
    assertThat(gasLimit).isGreaterThanOrEqualTo(adversaryTarget * 90 / 100);
  }
}
