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
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MAX_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MIN_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.OLYMPIA_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.SPIRAL_BLOCK;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Port of Fukuii SpiralToOlympiaGasTransitionSpec.
 *
 * <p>Verifies that the Spiral→Olympia gas limit transition does NOT trigger a 2× jump, that the
 * Spiral boundary is stable, and that post-Olympia convergence works without a miner config target.
 */
public class SpiralToOlympiaGasTransitionTest {

  private static final long PRE_OLYMPIA_GAS_LIMIT = 8_000_000L;
  private static final long OLYMPIA_GAS_TARGET = 60_000_000L;

  private OlympiaTargetingGasLimitCalculator olympiaCalc() {
    final LondonFeeMarket feeMarket = new LondonFeeMarket(OLYMPIA_BLOCK, Optional.empty());
    return new OlympiaTargetingGasLimitCalculator(OLYMPIA_BLOCK, feeMarket);
  }

  private GasLimitRangeAndDeltaValidationRule validationRule() {
    return new GasLimitRangeAndDeltaValidationRule(
        DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT, Optional.empty());
  }

  @Test
  public void rejectsTwoTimesGasJumpAtOlympiaActivation() {
    // Bug 2 regression: the old code accepted ~16M at Olympia block via London 2× logic.
    // With Optional.empty(), the standard ±1/1024 rule applies — 16M must be rejected.
    final GasLimitRangeAndDeltaValidationRule rule = validationRule();
    final BlockHeader parent =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT).buildHeader();
    final BlockHeader header =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT * 2).buildHeader();
    assertThat(rule.validate(header, parent))
        .as("2× gas jump at Olympia activation must be REJECTED — Fukuii SpiralToOlympiaGasTransitionSpec parity")
        .isFalse();
  }

  @Test
  public void acceptsFirstOlympiaBlockWith1Over1024SteppedGasLimit() {
    // Canonical first Olympia block: delta = 8M/1024 - 1 = 7,811. Must be ACCEPTED.
    final GasLimitRangeAndDeltaValidationRule rule = validationRule();
    final BlockHeader parent =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT).buildHeader();
    final BlockHeader header =
        new BlockHeaderTestFixture().gasLimit(PRE_OLYMPIA_GAS_LIMIT + 7_811L).buildHeader();
    assertThat(rule.validate(header, parent))
        .as("Canonical first Olympia block (gasLimit=8,007,811) must be ACCEPTED — Fukuii parity")
        .isTrue();
  }

  @Test
  public void gasLimitDoesNotChangeAtSpiralActivation() {
    // At the Spiral activation block (≤ olympiaBlock gate), the calculator returns the parent
    // gas limit unchanged — no early creep toward 60M at Spiral.
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();
    final long result =
        calc.nextGasLimit(PRE_OLYMPIA_GAS_LIMIT, OLYMPIA_GAS_TARGET, SPIRAL_BLOCK);
    assertThat(result)
        .as("Gas limit must be unchanged at Spiral activation (no premature 60M targeting)")
        .isEqualTo(PRE_OLYMPIA_GAS_LIMIT);
  }

  @Test
  public void preOlympiaGasLimitDoesNotCreepToward60M() {
    // 100 blocks before Olympia: each block returns parent gas limit unchanged.
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();

    for (long block = OLYMPIA_BLOCK - 100; block <= OLYMPIA_BLOCK; block++) {
      final long next = calc.nextGasLimit(PRE_OLYMPIA_GAS_LIMIT, OLYMPIA_GAS_TARGET, block);
      assertThat(next)
          .as("Pre-Olympia gas limit must not creep toward 60M at block %d", block)
          .isEqualTo(PRE_OLYMPIA_GAS_LIMIT);
    }
  }

  @Test
  public void postOlympiaGasLimitConvergesEvenWithNoMinerTarget() {
    // Bug 3 regression: when miner passes targetGasLimit == currentGasLimit (no --target-gas-limit
    // flag set), the Frontier calc would freeze. OlympiaTargetingGasLimitCalculator must still
    // converge toward the hardcoded 60M target.
    final OlympiaTargetingGasLimitCalculator calc = olympiaCalc();
    final long result =
        calc.nextGasLimit(
            PRE_OLYMPIA_GAS_LIMIT,
            PRE_OLYMPIA_GAS_LIMIT, // miner sees same value — would freeze with Frontier calc
            OLYMPIA_BLOCK + 1);
    assertThat(result)
        .as("Post-Olympia: gas limit must increase toward 60M regardless of miner target")
        .isGreaterThan(PRE_OLYMPIA_GAS_LIMIT);
  }
}
