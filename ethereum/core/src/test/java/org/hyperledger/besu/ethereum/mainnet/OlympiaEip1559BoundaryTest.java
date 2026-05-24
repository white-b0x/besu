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

import org.hyperledger.besu.evm.ClassicEVMs;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.BaseFeeOperation;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.MCopyOperation;
import org.hyperledger.besu.evm.operation.TLoadOperation;
import org.hyperledger.besu.evm.operation.TStoreOperation;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Opcode availability tests for the Spiral→Olympia EVM boundary (ECIP-1111 + ECIP-1121).
 *
 * <p>ECIP-1111 (EIP-3198): BASEFEE opcode (0x48) added at Olympia, absent from Spiral. ECIP-1121
 * (EIP-5656, EIP-1153): MCOPY (0x5e), TLOAD (0x5c), TSTORE (0x5d) added at Olympia. Blob EIPs
 * (EIP-4844 BLOBHASH 0x49, EIP-7516 BLOBBASEFEE 0x4a) are NOT in ETC Olympia.
 */
public class OlympiaEip1559BoundaryTest {

  private static final BigInteger ETC_CHAIN_ID = BigInteger.valueOf(61L);

  private EVM spiralEvm() {
    return ClassicEVMs.spiral(new ShanghaiGasCalculator(), ETC_CHAIN_ID, EvmConfiguration.DEFAULT);
  }

  private EVM olympiaEvm() {
    return ClassicEVMs.olympia(new OsakaGasCalculator(), ETC_CHAIN_ID, EvmConfiguration.DEFAULT);
  }

  // ===== BASEFEE (EIP-3198 / ECIP-1111) =====

  @Test
  public void basefeeOpcodeUndefinedPreOlympia() {
    assertThat(spiralEvm().getOperationsUnsafe()[0x48])
        .as("BASEFEE (0x48) must be absent from Spiral EVM (not added before Olympia)")
        .isInstanceOf(InvalidOperation.class);
  }

  @Test
  public void basefeeOpcodeDefinedAtOlympia() {
    assertThat(olympiaEvm().getOperationsUnsafe()[0x48])
        .as("BASEFEE (0x48) must be present in Olympia EVM (EIP-3198 / ECIP-1111)")
        .isInstanceOf(BaseFeeOperation.class);
  }

  // ===== MCOPY (EIP-5656 / ECIP-1121) =====

  @Test
  public void mcopyOpcodeUndefinedPreOlympia() {
    assertThat(spiralEvm().getOperationsUnsafe()[0x5e])
        .as("MCOPY (0x5e) must be absent from Spiral EVM (added at Olympia by ECIP-1121)")
        .isInstanceOf(InvalidOperation.class);
  }

  @Test
  public void mcopyOpcodeDefinedAtOlympia() {
    assertThat(olympiaEvm().getOperationsUnsafe()[0x5e])
        .as("MCOPY (0x5e) must be present in Olympia EVM (EIP-5656 / ECIP-1121)")
        .isInstanceOf(MCopyOperation.class);
  }

  // ===== TLOAD / TSTORE (EIP-1153 / ECIP-1121) =====

  @Test
  public void tloadTstoreUndefinedPreOlympia() {
    final var ops = spiralEvm().getOperationsUnsafe();
    assertThat(ops[0x5c])
        .as("TLOAD (0x5c) must be absent from Spiral EVM (added at Olympia by ECIP-1121)")
        .isInstanceOf(InvalidOperation.class);
    assertThat(ops[0x5d])
        .as("TSTORE (0x5d) must be absent from Spiral EVM (added at Olympia by ECIP-1121)")
        .isInstanceOf(InvalidOperation.class);
  }

  @Test
  public void tloadTstoreDefinedAtOlympia() {
    final var ops = olympiaEvm().getOperationsUnsafe();
    assertThat(ops[0x5c])
        .as("TLOAD (0x5c) must be present in Olympia EVM (EIP-1153 / ECIP-1121)")
        .isInstanceOf(TLoadOperation.class);
    assertThat(ops[0x5d])
        .as("TSTORE (0x5d) must be present in Olympia EVM (EIP-1153 / ECIP-1121)")
        .isInstanceOf(TStoreOperation.class);
  }

  // ===== Blob EIPs — excluded from ETC =====

  @Test
  public void blobhashAndBlobBasefeeAbsentFromOlympia() {
    // ETC has no blob transactions — BLOBHASH (EIP-4844) and BLOBBASEFEE (EIP-7516) must be absent.
    final var ops = olympiaEvm().getOperationsUnsafe();
    assertThat(ops[0x49])
        .as("BLOBHASH (0x49) must be absent from Olympia EVM (no blob transactions on ETC)")
        .isInstanceOf(InvalidOperation.class);
    assertThat(ops[0x4a])
        .as("BLOBBASEFEE (0x4a) must be absent from Olympia EVM (no blob transactions on ETC)")
        .isInstanceOf(InvalidOperation.class);
  }
}
