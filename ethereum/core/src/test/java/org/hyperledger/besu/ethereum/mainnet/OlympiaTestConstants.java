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

import java.math.BigInteger;

import org.hyperledger.besu.datatypes.Address;

/**
 * Shared Olympia test constants — single source of truth for all ETC fork block numbers, chain ID,
 * and the ECIP-1112 treasury address. Update here; all test classes import from this class.
 *
 * <p>Treasury: demo v0.2 (Pure Solidity, CREATE-deployed on Mordor + ETC mainnet). Will change to
 * OZ 5.6 contract post-Olympia activation — update TREASURY here when deployment address changes.
 */
public final class OlympiaTestConstants {

  /** ETC mainnet chain ID. */
  public static final BigInteger CHAIN_ID = BigInteger.valueOf(61L);

  // ETC mainnet fork activation blocks
  public static final long CLASSIC_FORK_BLOCK = 1_920_000L;
  public static final long DIE_HARD_BLOCK = 3_000_000L;
  public static final long GOTHAM_BLOCK = 5_000_000L;
  public static final long DEFUSE_BOMB_BLOCK = 5_900_000L;
  public static final long ATLANTIS_BLOCK = 8_772_000L;
  public static final long AGHARTA_BLOCK = 9_573_000L;
  public static final long PHOENIX_BLOCK = 10_500_839L;
  public static final long THANOS_BLOCK = 11_700_000L;
  public static final long MAGNETO_BLOCK = 13_189_133L;
  public static final long MYSTIQUE_BLOCK = 14_525_000L;
  public static final long SPIRAL_BLOCK = 19_250_000L;
  public static final long OLYMPIA_BLOCK = 24_751_337L;

  /** ECIP-1112 treasury vault address (demo v0.2, ACTIVE in clients). */
  public static final Address TREASURY =
      Address.fromHexString("0x035b2e3c189B772e52F4C3DA6c45c84A3bB871bf");

  private OlympiaTestConstants() {}
}
