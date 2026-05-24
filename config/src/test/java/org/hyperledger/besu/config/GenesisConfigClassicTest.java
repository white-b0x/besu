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
package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * Tests that ETC Classic genesis config options (Mordor and mainnet) are correctly parsed.
 * Validates all Classic fork getters + ECIP-1100 MESS getters + era rounds.
 */
public class GenesisConfigClassicTest {

  private GenesisConfigOptions loadMordorConfig() {
    return GenesisConfig.fromResource("/mordor.json").getConfigOptions();
  }

  private GenesisConfigOptions loadClassicConfig() {
    return GenesisConfig.fromResource("/classic.json").getConfigOptions();
  }

  // --- Mordor fork block numbers ---

  @Test
  public void mordorChainId() {
    assertThat(loadMordorConfig().getChainId()).hasValue(java.math.BigInteger.valueOf(63));
  }

  @Test
  public void mordorAtlantisBlock() {
    assertThat(loadMordorConfig().getAtlantisBlockNumber()).isEqualTo(OptionalLong.of(0));
  }

  @Test
  public void mordorAghartaBlock() {
    assertThat(loadMordorConfig().getAghartaBlockNumber()).isEqualTo(OptionalLong.of(301243));
  }

  @Test
  public void mordorPhoenixBlock() {
    assertThat(loadMordorConfig().getPhoenixBlockNumber()).isEqualTo(OptionalLong.of(999983));
  }

  @Test
  public void mordorThanosBlock() {
    assertThat(loadMordorConfig().getThanosBlockNumber()).isEqualTo(OptionalLong.of(2520000));
  }

  @Test
  public void mordorMagnetoBlock() {
    assertThat(loadMordorConfig().getMagnetoBlockNumber()).isEqualTo(OptionalLong.of(3985893));
  }

  @Test
  public void mordorMystiqueBlock() {
    assertThat(loadMordorConfig().getMystiqueBlockNumber()).isEqualTo(OptionalLong.of(5520000));
  }

  @Test
  public void mordorSpiralBlock() {
    assertThat(loadMordorConfig().getSpiralBlockNumber()).isEqualTo(OptionalLong.of(9957000));
  }

  @Test
  public void mordorOlympiaBlock() {
    assertThat(loadMordorConfig().getOlympiaBlockNumber()).isEqualTo(OptionalLong.of(15800850));
  }

  @Test
  public void mordorEcip1017EraRounds() {
    assertThat(loadMordorConfig().getEcip1017EraRounds()).isEqualTo(OptionalLong.of(2000000));
  }

  // --- Mordor ECIP-1100 MESS config ---

  @Test
  public void mordorEcbp1100Block() {
    assertThat(loadMordorConfig().getEcbp1100Block()).isEqualTo(OptionalLong.of(2380000));
  }

  @Test
  public void mordorEcbp1100DeactivateBlock() {
    assertThat(loadMordorConfig().getEcbp1100DeactivateBlock()).isEqualTo(OptionalLong.of(10400000));
  }

  // --- Classic mainnet fork block numbers ---

  @Test
  public void classicChainId() {
    assertThat(loadClassicConfig().getChainId()).hasValue(java.math.BigInteger.valueOf(61));
  }

  @Test
  public void classicAtlantisBlock() {
    assertThat(loadClassicConfig().getAtlantisBlockNumber()).isEqualTo(OptionalLong.of(8772000));
  }

  @Test
  public void classicAghartaBlock() {
    assertThat(loadClassicConfig().getAghartaBlockNumber()).isEqualTo(OptionalLong.of(9573000));
  }

  @Test
  public void classicPhoenixBlock() {
    assertThat(loadClassicConfig().getPhoenixBlockNumber()).isEqualTo(OptionalLong.of(10500839));
  }

  @Test
  public void classicThanosBlock() {
    assertThat(loadClassicConfig().getThanosBlockNumber()).isEqualTo(OptionalLong.of(11700000));
  }

  @Test
  public void classicMagnetoBlock() {
    assertThat(loadClassicConfig().getMagnetoBlockNumber()).isEqualTo(OptionalLong.of(13189133));
  }

  @Test
  public void classicMystiqueBlock() {
    assertThat(loadClassicConfig().getMystiqueBlockNumber()).isEqualTo(OptionalLong.of(14525000));
  }

  @Test
  public void classicSpiralBlock() {
    assertThat(loadClassicConfig().getSpiralBlockNumber()).isEqualTo(OptionalLong.of(19250000));
  }

  @Test
  public void classicOlympiaBlock() {
    assertThat(loadClassicConfig().getOlympiaBlockNumber()).isEqualTo(OptionalLong.of(24751337));
  }

  @Test
  public void classicEcip1017EraRoundsDefaultsToEmpty() {
    // Classic mainnet doesn't explicitly set ecip1017EraRounds; ClassicBlockProcessor defaults to 5M
    assertThat(loadClassicConfig().getEcip1017EraRounds()).isEmpty();
  }

  // --- Classic ECIP-1100 MESS config ---

  @Test
  public void classicEcbp1100Block() {
    assertThat(loadClassicConfig().getEcbp1100Block()).isEqualTo(OptionalLong.of(11380000));
  }

  @Test
  public void classicEcbp1100DeactivateBlock() {
    assertThat(loadClassicConfig().getEcbp1100DeactivateBlock()).isEqualTo(OptionalLong.of(19250000));
  }

  // --- StubGenesisConfigOptions builder ---

  @Test
  public void stubConfigOptionsReturnCorrectValues() {
    StubGenesisConfigOptions stub = new StubGenesisConfigOptions();
    stub.atlantis(100L)
        .agharta(200L)
        .phoenix(300L)
        .thanos(400L)
        .magneto(500L)
        .mystique(600L)
        .spiral(700L)
        .olympia(800L)
        .ecbp1100(900L)
        .ecbp1100Deactivate(1000L);

    assertThat(stub.getAtlantisBlockNumber()).isEqualTo(OptionalLong.of(100));
    assertThat(stub.getAghartaBlockNumber()).isEqualTo(OptionalLong.of(200));
    assertThat(stub.getPhoenixBlockNumber()).isEqualTo(OptionalLong.of(300));
    assertThat(stub.getThanosBlockNumber()).isEqualTo(OptionalLong.of(400));
    assertThat(stub.getMagnetoBlockNumber()).isEqualTo(OptionalLong.of(500));
    assertThat(stub.getMystiqueBlockNumber()).isEqualTo(OptionalLong.of(600));
    assertThat(stub.getSpiralBlockNumber()).isEqualTo(OptionalLong.of(700));
    assertThat(stub.getOlympiaBlockNumber()).isEqualTo(OptionalLong.of(800));
    assertThat(stub.getEcbp1100Block()).isEqualTo(OptionalLong.of(900));
    assertThat(stub.getEcbp1100DeactivateBlock()).isEqualTo(OptionalLong.of(1000));
  }

  @Test
  public void stubConfigOptionsDefaultsAreEmpty() {
    StubGenesisConfigOptions stub = new StubGenesisConfigOptions();
    assertThat(stub.getAtlantisBlockNumber()).isEmpty();
    assertThat(stub.getEcbp1100Block()).isEmpty();
    assertThat(stub.getEcbp1100DeactivateBlock()).isEmpty();
  }

  // --- asMap includes Classic keys ---

  @Test
  public void mordorAsMapContainsClassicForks() {
    var map = loadMordorConfig().asMap();
    assertThat(map).containsKey("atlantisBlock");
    assertThat(map).containsKey("aghartaBlock");
    assertThat(map).containsKey("phoenixBlock");
    assertThat(map).containsKey("thanosBlock");
    assertThat(map).containsKey("magnetoBlock");
    assertThat(map).containsKey("mystiqueBlock");
    assertThat(map).containsKey("spiralBlock");
    assertThat(map).containsKey("olympiaBlock");
    assertThat(map).containsKey("olympiaTreasuryAddress");
    assertThat(map).containsKey("ecbp1100Block");
    assertThat(map).containsKey("ecbp1100DeactivateBlock");
  }
}
