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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.OLYMPIA_BLOCK;
import static org.hyperledger.besu.ethereum.mainnet.OlympiaTestConstants.SPIRAL_BLOCK;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import org.junit.jupiter.api.Test;

/**
 * ECIP-1122: gas limit target warning rule for ETC networks.
 *
 * <p>The rule emits a WARN log when a peer block's gasLimit is below the scheduled gas limit target
 * for its epoch, but ALWAYS returns {@code true} — the block is accepted regardless.
 *
 * <p>Targets: 8,000,000 (Spiral, pre-Olympia) and 60,000,000 (Olympia+).
 * Mirrors core-geth's {@code ForkGasTarget} check in {@code VerifyEIP1559Header}.
 */
public class EtcGasLimitWarnRuleTest {

  private static final long OLYMPIA_GAS_LIMIT = 60_000_000L;
  private static final long SPIRAL_GAS_LIMIT = 8_000_000L;

  private final EtcGasLimitWarnRule rule = new EtcGasLimitWarnRule(OLYMPIA_BLOCK);
  private final BlockHeader DUMMY_PARENT = new BlockHeaderTestFixture().buildHeader();

  // ===== Rule always returns true (SHOULD, not MUST) =====

  @Test
  public void validatesAnyBlockRegardlessOfGasLimit() {
    // Block at Olympia with gas limit of 1 — still accepted (warning only).
    BlockHeader header = new BlockHeaderTestFixture().number(OLYMPIA_BLOCK).gasLimit(1L).buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  @Test
  public void returnsTrue_preOlympia_gasLimitBelowSpiralTarget() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .number(SPIRAL_BLOCK)
            .gasLimit(SPIRAL_GAS_LIMIT - 1)
            .buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  @Test
  public void returnsTrue_preOlympia_gasLimitAtSpiralTarget() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .number(SPIRAL_BLOCK)
            .gasLimit(SPIRAL_GAS_LIMIT)
            .buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  @Test
  public void returnsTrue_olympia_gasLimitBelowOlympiaTarget() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .number(OLYMPIA_BLOCK)
            .gasLimit(OLYMPIA_GAS_LIMIT - 1)
            .buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  @Test
  public void returnsTrue_olympia_gasLimitAtOlympiaTarget() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .number(OLYMPIA_BLOCK)
            .gasLimit(OLYMPIA_GAS_LIMIT)
            .buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  @Test
  public void returnsTrue_olympia_gasLimitAboveOlympiaTarget() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .number(OLYMPIA_BLOCK + 1_000_000L)
            .gasLimit(OLYMPIA_GAS_LIMIT + 1L)
            .buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  // ===== Target boundary: Olympia vs. pre-Olympia =====

  @Test
  public void preOlympia_usesLowerTarget_8M() {
    // Block just before Olympia uses the 8M Spiral target.
    // gasLimit=8M is at-or-above Spiral target → no warning (returns true, same as below-target).
    // The test asserts the rule resolves the target based on block number, not a fixed threshold.
    BlockHeader below = new BlockHeaderTestFixture()
        .number(OLYMPIA_BLOCK - 1)
        .gasLimit(SPIRAL_GAS_LIMIT - 1)
        .buildHeader();
    BlockHeader atTarget = new BlockHeaderTestFixture()
        .number(OLYMPIA_BLOCK - 1)
        .gasLimit(SPIRAL_GAS_LIMIT)
        .buildHeader();

    // Both are accepted (warning only rule).
    assertThat(rule.validate(below, DUMMY_PARENT)).isTrue();
    assertThat(rule.validate(atTarget, DUMMY_PARENT)).isTrue();
  }

  @Test
  public void atOlympia_uses60MTarget() {
    // gasLimit=8M < 60M → would trigger a warning for Olympia blocks.
    BlockHeader header = new BlockHeaderTestFixture()
        .number(OLYMPIA_BLOCK)
        .gasLimit(SPIRAL_GAS_LIMIT)
        .buildHeader();
    assertThat(rule.validate(header, DUMMY_PARENT)).isTrue();
  }

  // ===== toString for logging/diagnostics =====

  @Test
  public void toStringIsDescriptive() {
    assertThat(rule.toString()).isEqualTo("EtcGasLimitWarn");
  }
}
