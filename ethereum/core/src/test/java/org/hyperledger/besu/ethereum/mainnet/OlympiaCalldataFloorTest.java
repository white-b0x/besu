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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * EIP-7623 calldata floor cost tests for OsakaGasCalculator (active at Olympia via ECIP-1121).
 *
 * <p>Floor formula: 21000 + tokens × 10, where tokens = zeroBytes + nonzeroBytes × 4.
 * ShanghaiGasCalculator (Spiral, pre-Olympia) returns 0 — no floor constraint.
 */
public class OlympiaCalldataFloorTest {

  private static final KeyPair KEY_PAIR = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  private Transaction txWithPayload(final Bytes payload) {
    return new TransactionTestFixture().payload(payload).createTransaction(KEY_PAIR);
  }

  @Test
  public void calldataFloorAppliedForAllZeroData() {
    // 100 zero bytes: tokens = 100*1 = 100, floor = 21000 + 100*10 = 22000.
    // Standard calldata component: 100*4 = 400 → total intrinsic = 21400. Floor exceeds standard.
    final Transaction tx = txWithPayload(Bytes.wrap(new byte[100]));
    assertThat(new OsakaGasCalculator().transactionFloorCost(tx))
        .as("EIP-7623 floor for 100 zero bytes: 21000 + 100*10 = 22000")
        .isEqualTo(22_000L);
  }

  @Test
  public void calldataFloorAppliedForAllNonzeroData() {
    // 100 nonzero bytes: tokens = 100*4 = 400, floor = 21000 + 400*10 = 25000.
    // Standard calldata: 100*16 = 1600 → total intrinsic = 22600. Floor (25000) exceeds standard.
    final byte[] nonzero = new byte[100];
    Arrays.fill(nonzero, (byte) 0xFF);
    final Transaction tx = txWithPayload(Bytes.wrap(nonzero));
    assertThat(new OsakaGasCalculator().transactionFloorCost(tx))
        .as("EIP-7623 floor for 100 nonzero bytes: 21000 + 400*10 = 25000")
        .isEqualTo(25_000L);
  }

  @Test
  public void calldataFloorMixedData() {
    // 50 zero + 50 nonzero: tokens = 50 + 50*4 = 250, floor = 21000 + 250*10 = 23500.
    final byte[] mixed = new byte[100];
    Arrays.fill(mixed, 50, 100, (byte) 0xFF);
    final Transaction tx = txWithPayload(Bytes.wrap(mixed));
    assertThat(new OsakaGasCalculator().transactionFloorCost(tx))
        .as("EIP-7623 floor for 50 zero + 50 nonzero: 21000 + 250*10 = 23500")
        .isEqualTo(23_500L);
  }

  @Test
  public void calldataFloorNotActiveBeforeOlympia() {
    // ShanghaiGasCalculator (Spiral) does NOT implement EIP-7623.
    // transactionFloorCost() returns 0 — no floor constraint applies pre-Olympia.
    final Transaction tx = txWithPayload(Bytes.wrap(new byte[100]));
    assertThat(new ShanghaiGasCalculator().transactionFloorCost(tx))
        .as("Spiral (ShanghaiGasCalculator) must return 0 for transactionFloorCost — no EIP-7623")
        .isZero();
  }
}
