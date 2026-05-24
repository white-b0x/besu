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
package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;

import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Tests that Olympia hard fork config options are correctly parsed from genesis files
 * and properly handled by StubGenesisConfigOptions.
 */
public class GenesisConfigOlympiaTest {

  private static final Address EXPECTED_TREASURY =
      Address.fromHexString("0xCfE1e0ECbff745e6c800fF980178a8dDEf94bEe2");

  private GenesisConfigOptions loadMordorConfig() {
    return GenesisConfig.fromResource("/mordor.json").getConfigOptions();
  }

  private GenesisConfigOptions loadClassicConfig() {
    return GenesisConfig.fromResource("/classic.json").getConfigOptions();
  }

  // --- Mordor genesis ---

  @Test
  public void mordorOlympiaBlockNumber() {
    assertThat(loadMordorConfig().getOlympiaBlockNumber())
        .isEqualTo(OptionalLong.of(15_800_850L));
  }

  @Test
  public void mordorOlympiaTreasuryAddress() {
    assertThat(loadMordorConfig().getOlympiaTreasuryAddress())
        .isPresent()
        .hasValue(EXPECTED_TREASURY);
  }

  // --- Classic mainnet genesis ---

  @Test
  public void classicOlympiaBlockNumber() {
    assertThat(loadClassicConfig().getOlympiaBlockNumber())
        .isEqualTo(OptionalLong.of(24_751_337L));
  }

  @Test
  public void classicOlympiaTreasuryAddress() {
    assertThat(loadClassicConfig().getOlympiaTreasuryAddress())
        .isPresent()
        .hasValue(EXPECTED_TREASURY);
  }

  // --- asMap includes Olympia keys ---

  @Test
  public void mordorAsMapContainsOlympiaBlock() {
    var map = loadMordorConfig().asMap();
    assertThat(map).containsKey("olympiaBlock");
    assertThat(map.get("olympiaBlock")).isEqualTo(15_800_850L);
  }

  @Test
  public void mordorAsMapContainsOlympiaTreasuryAddress() {
    var map = loadMordorConfig().asMap();
    assertThat(map).containsKey("olympiaTreasuryAddress");
  }

  // --- getForkBlockNumbers includes Olympia ---

  @Test
  public void olympiaInForkBlockNumbers() {
    assertThat(loadMordorConfig().getForkBlockNumbers())
        .contains(15_800_850L);
  }

  // --- StubGenesisConfigOptions ---

  @Test
  public void stubConfigOlympiaBlockNumber() {
    StubGenesisConfigOptions stub = new StubGenesisConfigOptions();
    stub.olympia(100L);
    assertThat(stub.getOlympiaBlockNumber()).isEqualTo(OptionalLong.of(100));
  }

  @Test
  public void stubConfigOlympiaTreasuryAddress() {
    StubGenesisConfigOptions stub = new StubGenesisConfigOptions();
    stub.olympiaTreasuryAddress(EXPECTED_TREASURY);
    assertThat(stub.getOlympiaTreasuryAddress())
        .isPresent()
        .hasValue(EXPECTED_TREASURY);
  }

  @Test
  public void stubConfigDefaultsAreEmpty() {
    StubGenesisConfigOptions stub = new StubGenesisConfigOptions();
    assertThat(stub.getOlympiaBlockNumber()).isEmpty();
    assertThat(stub.getOlympiaTreasuryAddress()).isEmpty();
  }

  // --- EIP-2935 contract in genesis alloc ---

  private static final Address HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0x0000f90827f1c53a10cb7a02335b175320002935");

  @Test
  public void mordorAllocContainsEip2935Contract() {
    GenesisConfig genesis = GenesisConfig.fromResource("/mordor.json");
    boolean found =
        genesis.streamAllocations()
            .anyMatch(
                account ->
                    account.address().equals(HISTORY_STORAGE_ADDRESS)
                        && account.code() != null
                        && !account.code().isEmpty()
                        && account.nonce() == 1);
    assertThat(found)
        .as("Mordor genesis alloc must contain EIP-2935 history storage contract with nonce=1")
        .isTrue();
  }

  @Test
  public void classicAllocContainsEip2935Contract() {
    GenesisConfig genesis = GenesisConfig.fromResource("/classic.json");
    boolean found =
        genesis.streamAllocations()
            .anyMatch(
                account ->
                    account.address().equals(HISTORY_STORAGE_ADDRESS)
                        && account.code() != null
                        && !account.code().isEmpty()
                        && account.nonce() == 1);
    assertThat(found)
        .as("Classic genesis alloc must contain EIP-2935 history storage contract with nonce=1")
        .isTrue();
  }
}
