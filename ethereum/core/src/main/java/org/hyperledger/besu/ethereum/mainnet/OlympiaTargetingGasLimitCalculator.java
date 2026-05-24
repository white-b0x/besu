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

/**
 * Gas limit calculator for ETC Olympia hard fork. Extends the London EIP-1559 elastic block gas
 * limit calculator with a per-transaction gas cap of 2^24 (EIP-7825).
 *
 * <p>The transaction gas limit cap is enforced by {@link MainnetTransactionValidator#validate} which
 * checks {@code transaction.getGasLimit() > gasLimitCalculator.transactionGasLimitCap()}.
 */
public class OlympiaTargetingGasLimitCalculator extends LondonTargetingGasLimitCalculator {

  /** EIP-7825: Maximum gas limit per transaction (2^24 = 16,777,216). */
  public static final long OLYMPIA_TRANSACTION_GAS_LIMIT_CAP = 16_777_216L;

  public OlympiaTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    super(londonForkBlock, feeMarket);
  }

  @Override
  public long transactionGasLimitCap() {
    return OLYMPIA_TRANSACTION_GAS_LIMIT_CAP;
  }
}
