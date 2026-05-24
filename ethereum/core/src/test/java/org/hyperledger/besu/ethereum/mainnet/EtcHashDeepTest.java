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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

/**
 * Deep ETChash algorithm tests covering ECIP-1099 epoch doubling, DAG seed uniqueness, cache and
 * dataset size calculations, and boundary conditions.
 *
 * <p>Extends the basic tests in EtcHashTest (4 tests) and EthHashTest (3 tests) with
 * comprehensive coverage matching the core-geth and fukuii test suites.
 */
public class EtcHashDeepTest {

  private static final EpochCalculator DEFAULT_CALCULATOR =
      new EpochCalculator.DefaultEpochCalculator();
  private static final EpochCalculator ECIP1099_CALCULATOR =
      new EpochCalculator.Ecip1099EpochCalculator();

  // --- Seed uniqueness ---

  @Test
  public void ecip1099SeedUniquenessAcrossEpochs() {
    Set<Bytes> seeds = new HashSet<>();
    for (int epoch = 0; epoch < 10; epoch++) {
      long block = (long) epoch * 60_000L + 1L;
      byte[] seed = DirectAcyclicGraphSeed.dagSeed(block, ECIP1099_CALCULATOR);
      seeds.add(Bytes.wrap(Arrays.copyOf(seed, seed.length)));
    }
    assertThat(seeds).as("First 10 ECIP-1099 epochs should produce unique seeds").hasSize(10);
  }

  @Test
  public void defaultSeedUniquenessAcrossEpochs() {
    Set<Bytes> seeds = new HashSet<>();
    for (int epoch = 0; epoch < 20; epoch++) {
      long block = (long) epoch * 30_000L + 1L;
      byte[] seed = DirectAcyclicGraphSeed.dagSeed(block, DEFAULT_CALCULATOR);
      seeds.add(Bytes.wrap(Arrays.copyOf(seed, seed.length)));
    }
    assertThat(seeds).as("First 20 default epochs should produce unique seeds").hasSize(20);
  }

  @Test
  public void ecip1099SeedMatchesDefaultAtEpochZero() {
    // Both calculators should produce the same seed for epoch 0 (block 1)
    byte[] defaultSeed = DirectAcyclicGraphSeed.dagSeed(1L, DEFAULT_CALCULATOR);
    byte[] ecip1099Seed = DirectAcyclicGraphSeed.dagSeed(1L, ECIP1099_CALCULATOR);
    assertThat(ecip1099Seed)
        .as("Epoch 0 seed should be identical for both calculators")
        .isEqualTo(defaultSeed);
  }

  // --- Epoch boundary tests ---

  @Test
  public void ecip1099EpochBoundary59999() {
    assertThat(ECIP1099_CALCULATOR.cacheEpoch(59_999L))
        .as("Block 59,999 should be in ECIP-1099 epoch 0")
        .isEqualTo(0);
  }

  @Test
  public void ecip1099EpochBoundary60000() {
    assertThat(ECIP1099_CALCULATOR.cacheEpoch(60_000L))
        .as("Block 60,000 should be in ECIP-1099 epoch 1")
        .isEqualTo(1);
  }

  @Test
  public void defaultEpochBoundary29999() {
    assertThat(DEFAULT_CALCULATOR.cacheEpoch(29_999L))
        .as("Block 29,999 should be in default epoch 0")
        .isEqualTo(0);
  }

  @Test
  public void defaultEpochBoundary30000() {
    assertThat(DEFAULT_CALCULATOR.cacheEpoch(30_000L))
        .as("Block 30,000 should be in default epoch 1")
        .isEqualTo(1);
  }

  // --- Cache and dataset size tests ---

  @Test
  public void cacheSizeIncreasesByEpoch() {
    long sizeEpoch0 = EthHash.cacheSize(0);
    long sizeEpoch1 = EthHash.cacheSize(1);
    long sizeEpoch2 = EthHash.cacheSize(2);
    assertThat(sizeEpoch1).as("Cache size at epoch 1 > epoch 0").isGreaterThan(sizeEpoch0);
    assertThat(sizeEpoch2).as("Cache size at epoch 2 > epoch 1").isGreaterThan(sizeEpoch1);
  }

  @Test
  public void datasetSizeIncreasesByEpoch() {
    long sizeEpoch0 = EthHash.datasetSize(0);
    long sizeEpoch1 = EthHash.datasetSize(1);
    long sizeEpoch2 = EthHash.datasetSize(2);
    assertThat(sizeEpoch1).as("Dataset size at epoch 1 > epoch 0").isGreaterThan(sizeEpoch0);
    assertThat(sizeEpoch2).as("Dataset size at epoch 2 > epoch 1").isGreaterThan(sizeEpoch1);
  }

  @Test
  public void cacheSizeAtEpochZeroMatchesKnownValue() {
    // Epoch 0 cache size = 2^24 - 64 = 16,776,896 bytes (well-known Ethash constant)
    assertThat(EthHash.cacheSize(0)).isEqualTo(16_776_896L);
  }

  @Test
  public void datasetSizeAtEpochZeroMatchesKnownValue() {
    // Epoch 0 dataset size = 2^30 - 128 = 1,073,739,904 bytes (~1 GB)
    assertThat(EthHash.datasetSize(0)).isEqualTo(1_073_739_904L);
  }

  @Test
  public void highEpochDoesNotOverflow() {
    // Verify that high epoch numbers don't cause overflow or negative values
    long cacheSize = EthHash.cacheSize(2048);
    long datasetSize = EthHash.datasetSize(2048);
    assertThat(cacheSize).as("Cache size at epoch 2048 should be positive").isPositive();
    assertThat(datasetSize).as("Dataset size at epoch 2048 should be positive").isPositive();
    assertThat(datasetSize)
        .as("Dataset should always be larger than cache")
        .isGreaterThan(cacheSize);
  }
}
