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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ECIP-1122 SHOULD requirement: emit a warning when a peer block's gasLimit is below the
 * network-scheduled gas limit target for the block's epoch.
 *
 * <p>Gas limit targets (ETC network-authoritative values, not protocol rejections):
 *
 * <ul>
 *   <li>Spiral epoch (pre-Olympia): 8,000,000
 *   <li>Olympia epoch and later: 60,000,000
 * </ul>
 *
 * <p>The block is still accepted — miners are not required to mine at the target — but operators
 * are warned when a peer is mining below the gas limit target. Mirrors the core-geth {@code
 * ForkGasTarget} check in {@code VerifyEIP1559Header}.
 */
public class EtcGasLimitWarnRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(EtcGasLimitWarnRule.class);

  /** Pre-Olympia (Spiral epoch) gas limit target per ECIP-1122. */
  private static final long SPIRAL_GAS_LIMIT = 8_000_000L;

  /** Olympia epoch gas limit target per ECIP-1122. */
  private static final long OLYMPIA_GAS_LIMIT = 60_000_000L;

  private final long olympiaBlockNumber;

  public EtcGasLimitWarnRule(final long olympiaBlockNumber) {
    this.olympiaBlockNumber = olympiaBlockNumber;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final long blockNumber = header.getNumber();
    final long limit = blockNumber >= olympiaBlockNumber ? OLYMPIA_GAS_LIMIT : SPIRAL_GAS_LIMIT;

    if (header.getGasLimit() < limit) {
      LOG.warn(
          "Peer block gas limit below network gas limit target (ECIP-1122 SHOULD): block={}, gasLimit={}, target={}",
          blockNumber,
          header.getGasLimit(),
          limit);
    }

    // Warning only — block is still accepted.
    return true;
  }

  @Override
  public String toString() {
    return "EtcGasLimitWarn";
  }
}
