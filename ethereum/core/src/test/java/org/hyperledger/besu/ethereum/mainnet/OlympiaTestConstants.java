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

import org.hyperledger.besu.datatypes.Address;

/**
 * Shared Olympia test constants. Update the treasury address here when the
 * deployment changes (demo v0.2 → demo v0.3 → production).
 *
 * <p>Demo v0.2: Pure Solidity, deployed via CREATE on Mordor + ETC mainnet.
 * Production: Will change to OZ 5.6 contract post-Olympia activation.
 */
public final class OlympiaTestConstants {

  /** ECIP-1112 treasury vault address. */
  public static final Address TREASURY =
      Address.fromHexString("0x035b2e3c189B772e52F4C3DA6c45c84A3bB871bf");

  private OlympiaTestConstants() {}
}
