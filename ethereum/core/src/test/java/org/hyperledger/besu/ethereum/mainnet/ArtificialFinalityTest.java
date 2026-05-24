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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Difficulty;

import java.math.BigInteger;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/** Tests for ECIP-1100 (MESS) Artificial Finality. */
public class ArtificialFinalityTest {

  // Activation/deactivation blocks matching ETC mainnet
  private static final OptionalLong MAINNET_ACTIVATION = OptionalLong.of(11_380_000L);
  private static final OptionalLong MAINNET_DEACTIVATION = OptionalLong.of(19_250_000L);

  // Activation/deactivation blocks matching Mordor
  private static final OptionalLong MORDOR_ACTIVATION = OptionalLong.of(2_380_000L);
  private static final OptionalLong MORDOR_DEACTIVATION = OptionalLong.of(10_400_000L);

  // --- isActive tests ---

  @Test
  public void isActiveReturnsFalseWhenNotConfigured() {
    assertThat(ArtificialFinality.isActive(5_000_000L, OptionalLong.empty(), OptionalLong.empty()))
        .isFalse();
  }

  @Test
  public void isActiveReturnsFalseBeforeActivation() {
    assertThat(ArtificialFinality.isActive(11_379_999L, MAINNET_ACTIVATION, MAINNET_DEACTIVATION))
        .isFalse();
  }

  @Test
  public void isActiveReturnsTrueAtActivation() {
    assertThat(ArtificialFinality.isActive(11_380_000L, MAINNET_ACTIVATION, MAINNET_DEACTIVATION))
        .isTrue();
  }

  @Test
  public void isActiveReturnsTrueInMiddle() {
    assertThat(ArtificialFinality.isActive(15_000_000L, MAINNET_ACTIVATION, MAINNET_DEACTIVATION))
        .isTrue();
  }

  @Test
  public void isActiveReturnsFalseAtDeactivation() {
    assertThat(ArtificialFinality.isActive(19_250_000L, MAINNET_ACTIVATION, MAINNET_DEACTIVATION))
        .isFalse();
  }

  @Test
  public void isActiveReturnsFalseAfterDeactivation() {
    assertThat(ArtificialFinality.isActive(20_000_000L, MAINNET_ACTIVATION, MAINNET_DEACTIVATION))
        .isFalse();
  }

  @Test
  public void isActiveWithNoDeactivationStaysActive() {
    assertThat(ArtificialFinality.isActive(100_000_000L, MAINNET_ACTIVATION, OptionalLong.empty()))
        .isTrue();
  }

  @Test
  public void isActiveOnMordor() {
    assertThat(ArtificialFinality.isActive(5_000_000L, MORDOR_ACTIVATION, MORDOR_DEACTIVATION))
        .isTrue();
    assertThat(ArtificialFinality.isActive(10_400_000L, MORDOR_ACTIVATION, MORDOR_DEACTIVATION))
        .isFalse();
  }

  // --- polynomialV tests ---

  @Test
  public void polynomialAtZeroReturnsDenominator() {
    // At time delta 0, the antigravity should be 1x (= DENOMINATOR/DENOMINATOR = 128/128)
    BigInteger result = ArtificialFinality.polynomialV(BigInteger.ZERO);
    assertThat(result).isEqualTo(BigInteger.valueOf(128));
  }

  @Test
  public void polynomialAtXcapReturnsMaximum() {
    // At xcap (25132), the polynomial should reach its maximum: DENOMINATOR + HEIGHT = 128 + 3840 = 3968
    BigInteger result = ArtificialFinality.polynomialV(BigInteger.valueOf(25132));
    assertThat(result).isEqualTo(BigInteger.valueOf(3968));
  }

  @Test
  public void polynomialBeyondXcapClampedToMaximum() {
    // Values beyond xcap should clamp to the same max value
    BigInteger result = ArtificialFinality.polynomialV(BigInteger.valueOf(100_000));
    assertThat(result).isEqualTo(BigInteger.valueOf(3968));
  }

  @Test
  public void polynomialAt200SecondsIsNearBase() {
    // At 200 seconds the curve should still be near the base (1x multiplier)
    BigInteger result = ArtificialFinality.polynomialV(BigInteger.valueOf(200));
    // Value should be close to 128 (within a small margin)
    assertThat(result.longValue()).isBetween(128L, 135L);
  }

  @Test
  public void polynomialIncreasesBetween200And25132() {
    // Verify the curve is monotonically increasing
    BigInteger prev = ArtificialFinality.polynomialV(BigInteger.valueOf(200));
    for (long t = 1000; t <= 25132; t += 1000) {
      BigInteger curr = ArtificialFinality.polynomialV(BigInteger.valueOf(t));
      assertThat(curr).isGreaterThanOrEqualTo(prev);
      prev = curr;
    }
  }

  @Test
  public void polynomialMatchesCoreGethReferenceValues() {
    // Cross-check against core-geth's ecbp1100PolynomialV function
    // Python reference: 128 + (3 * x**2 - 2 * x**3 // xcap) * height // xcap ** 2

    // At x=0: 128 + 0 = 128
    assertThat(ArtificialFinality.polynomialV(BigInteger.ZERO))
        .isEqualTo(BigInteger.valueOf(128));

    // At x=12566 (half of xcap): midpoint of the cubic
    BigInteger midResult = ArtificialFinality.polynomialV(BigInteger.valueOf(12566));
    // Should be approximately 128 + 3840/2 = 2048, but cubic shape means it's slightly different
    assertThat(midResult.longValue()).isBetween(1900L, 2200L);

    // At x=25132 (xcap): 128 + 3840 = 3968
    assertThat(ArtificialFinality.polynomialV(BigInteger.valueOf(25132)))
        .isEqualTo(BigInteger.valueOf(3968));
  }

  // --- shouldRejectReorg tests ---

  @Test
  public void shouldNotRejectReorgAtZeroTimeDelta() {
    // At time delta 0, multiplier is 1x, so equal TD chains should not be rejected
    Difficulty localTD = Difficulty.of(1000);
    Difficulty proposedTD = Difficulty.of(1001);
    assertThat(ArtificialFinality.shouldRejectReorg(0, localTD, proposedTD)).isFalse();
  }

  @Test
  public void shouldRejectReorgWithInsufficientTDAtHighTimeDelta() {
    // At xcap (25132 seconds ≈ 7 hours), the multiplier is ~31x
    // So proposed TD needs to be 31x local TD to pass
    Difficulty localTD = Difficulty.of(1_000_000);
    Difficulty proposedTD = Difficulty.of(1_100_000); // Only 1.1x, way below 31x
    assertThat(ArtificialFinality.shouldRejectReorg(25132, localTD, proposedTD)).isTrue();
  }

  @Test
  public void shouldNotRejectReorgWithSufficientTDAtHighTimeDelta() {
    // At xcap, the multiplier is 3968/128 = 31x
    // Proposed TD of 32x should pass
    Difficulty localTD = Difficulty.of(1_000_000);
    Difficulty proposedTD = Difficulty.of(32_000_000); // 32x, above 31x requirement
    assertThat(ArtificialFinality.shouldRejectReorg(25132, localTD, proposedTD)).isFalse();
  }

  @Test
  public void shouldNotRejectReorgWithShortTimeDelta() {
    // At 100 seconds, the multiplier is essentially 1x
    Difficulty localTD = Difficulty.of(1_000_000);
    Difficulty proposedTD = Difficulty.of(1_100_000); // 1.1x should easily pass at ~1x multiplier
    assertThat(ArtificialFinality.shouldRejectReorg(100, localTD, proposedTD)).isFalse();
  }

  @Test
  public void shouldRejectReorgEqualTDAtNonZeroTimeDelta() {
    // Equal TDs should be rejected when time delta is non-trivial (multiplier > 1x)
    Difficulty localTD = Difficulty.of(1_000_000);
    Difficulty proposedTD = Difficulty.of(1_000_000);
    // At 5000 seconds the multiplier should be > 1x
    assertThat(ArtificialFinality.shouldRejectReorg(5000, localTD, proposedTD)).isTrue();
  }

  @Test
  public void shouldHandleVeryLargeTimeDelta() {
    // Beyond xcap, behavior should be same as at xcap (clamped)
    Difficulty localTD = Difficulty.of(1_000_000);
    Difficulty proposedTD = Difficulty.of(32_000_000); // 32x
    assertThat(ArtificialFinality.shouldRejectReorg(1_000_000, localTD, proposedTD)).isFalse();
  }
}
