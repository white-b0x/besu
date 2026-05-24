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

import org.hyperledger.besu.ethereum.core.Difficulty;

import java.math.BigInteger;
import java.util.OptionalLong;

/**
 * ECIP-1100: MESS (Modified Exponential Subjective Scoring).
 *
 * <p>Anti-reorg protection: large reorgs require exponentially higher TD to override local chain.
 * Uses a cubic polynomial to compute an "antigravity" multiplier (1x-31x) based on time since
 * common ancestor. Reorgs under ~200s are unaffected; peaks at ~7 hours (31x TD required).
 *
 * <p>Activation/deactivation blocks:
 *
 * <ul>
 *   <li>ETC mainnet: activated 11,380,000, deactivated 19,250,000 (Spiral)
 *   <li>Mordor: activated 2,380,000, deactivated 10,400,000
 * </ul>
 *
 * @see <a href="https://ecips.ethereumclassic.org/ECIPs/ecip-1100">ECIP-1100</a>
 * @see <a
 *     href="https://github.com/ethereumclassic/ECIPs/issues/374#issuecomment-694156719">Polynomial
 *     specification</a>
 */
public class ArtificialFinality {

  /** CURVE_FUNCTION_DENOMINATOR = 128 */
  private static final BigInteger DENOMINATOR = BigInteger.valueOf(128);

  /** xcap = 25132 = floor(8000*pi) */
  private static final BigInteger XCAP = BigInteger.valueOf(25132);

  /** ampl = 15 */
  private static final BigInteger AMPLITUDE = BigInteger.valueOf(15);

  /** height = DENOMINATOR * (ampl * 2) = 128 * 30 = 3840 */
  private static final BigInteger HEIGHT = DENOMINATOR.multiply(AMPLITUDE).multiply(BigInteger.TWO);

  private static final BigInteger THREE = BigInteger.valueOf(3);

  private ArtificialFinality() {}

  /**
   * Check if ECBP-1100 (MESS) is active at the given block number.
   *
   * @param blockNumber the current block number
   * @param activationBlock the activation block (empty if not configured)
   * @param deactivationBlock the deactivation block (empty if not configured)
   * @return true if MESS is active
   */
  public static boolean isActive(
      final long blockNumber,
      final OptionalLong activationBlock,
      final OptionalLong deactivationBlock) {
    if (activationBlock.isEmpty() || blockNumber < activationBlock.getAsLong()) {
      return false;
    }
    return deactivationBlock.isEmpty() || blockNumber < deactivationBlock.getAsLong();
  }

  /**
   * Compute the ECBP-1100 polynomial value for the given time delta. This is the "antigravity"
   * curve: a cubic polynomial that starts at DENOMINATOR (128, i.e. 1x multiplier) and rises to
   * DENOMINATOR + HEIGHT (128 + 3840 = 3968, i.e. ~31x multiplier).
   *
   * <p>Python reference:
   *
   * <pre>
   * def get_curve_function_numerator(x):
   *     xcap = 25132
   *     ampl = 15
   *     height = 128 * (ampl * 2)
   *     if x > xcap:
   *         x = xcap
   *     return 128 + (3 * x**2 - 2 * x**3 // xcap) * height // xcap ** 2
   * </pre>
   *
   * @param timeDelta time in seconds between current head and common ancestor
   * @return numerator value (denominator is always 128)
   */
  public static BigInteger polynomialV(final BigInteger timeDelta) {
    BigInteger x = timeDelta.compareTo(XCAP) > 0 ? XCAP : timeDelta;

    // 3 * x^2
    BigInteger term1 = x.pow(2).multiply(THREE);

    // 2 * x^3 / xcap
    BigInteger term2 = x.pow(3).multiply(BigInteger.TWO).divide(XCAP);

    // (3 * x^2 - 2 * x^3 / xcap) * height / xcap^2
    BigInteger result = term1.subtract(term2).multiply(HEIGHT).divide(XCAP.pow(2));

    // DENOMINATOR + result
    return DENOMINATOR.add(result);
  }

  /**
   * Check if a proposed reorg should be rejected by MESS.
   *
   * <p>A reorg is rejected if: proposedSubchainTD * DENOMINATOR < polynomialV(timeDelta) *
   * localSubchainTD
   *
   * @param timeDeltaSeconds time in seconds between current head and common ancestor
   * @param localSubchainTD total difficulty of local chain segment (from common ancestor to current
   *     head)
   * @param proposedSubchainTD total difficulty of proposed chain segment (from common ancestor to
   *     proposed head)
   * @return true if the reorg should be REJECTED (proposed chain doesn't have enough gravity)
   */
  public static boolean shouldRejectReorg(
      final long timeDeltaSeconds,
      final Difficulty localSubchainTD,
      final Difficulty proposedSubchainTD) {
    BigInteger x = BigInteger.valueOf(timeDeltaSeconds);
    BigInteger eq = polynomialV(x);

    // want = polynomialV(timeDelta) * localSubchainTD
    BigInteger want = eq.multiply(localSubchainTD.toBigInteger());

    // got = proposedSubchainTD * DENOMINATOR
    BigInteger got = proposedSubchainTD.toBigInteger().multiply(DENOMINATOR);

    // Reject if got < want (proposed chain doesn't meet the antigravity threshold)
    return got.compareTo(want) < 0;
  }
}
