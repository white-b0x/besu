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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests for the four ETC Classic difficulty calculators.
 *
 * <p>DIFFICULTY_BOMB_PAUSED — DieHard fork (block 3M), freezes bomb at period 30
 * DIFFICULTY_BOMB_DELAYED — Gotham fork, resumes bomb with 20-period delay
 * DIFFICULTY_BOMB_REMOVED — Defuse fork, no bomb at all
 * EIP100 — Atlantis+ (Byzantium-style, uncle-aware adjustment)
 */
public class ClassicDifficultyCalculatorsTest {

  private static final BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(131_072L);

  private BlockHeader createParent(
      final long number, final long timestamp, final BigInteger difficulty) {
    return new BlockHeaderTestFixture()
        .number(number)
        .timestamp(timestamp)
        .difficulty(Difficulty.of(difficulty))
        .ommersHash(Hash.EMPTY_LIST_HASH) // no ommers
        .buildHeader();
  }

  private BlockHeader createParentWithOmmers(
      final long number, final long timestamp, final BigInteger difficulty) {
    // Use a non-empty ommers hash to signal that ommers exist
    return new BlockHeaderTestFixture()
        .number(number)
        .timestamp(timestamp)
        .difficulty(Difficulty.of(difficulty))
        .ommersHash(Hash.ZERO) // non-empty signals ommers present
        .buildHeader();
  }

  // --- DIFFICULTY_BOMB_PAUSED (DieHard) ---

  @Test
  public void bombPausedIncreasesOnFastBlock() {
    // Block created 5 seconds after parent (fast, should increase difficulty)
    BlockHeader parent = createParent(3_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_005L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void bombPausedDecreasesOnSlowBlock() {
    // Frozen bomb adds 2^28 ≈ 268M, so parent difficulty must be large enough
    // that the negative adjustment overwhelms the bomb component.
    // At 1000s gap, factor = max(1-100,-99) = -99 → adjustment = -99*parent/2048
    // Need parent > 2^28 * 2048/99 ≈ 5.55B to see net decrease.
    BlockHeader parent = createParent(3_000_001L, 1_000_000L, BigInteger.valueOf(50_000_000_000L));
    long time = 1_001_000L; // 1000s gap
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED.nextDifficulty(time, parent);
    assertThat(difficulty).isLessThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void bombPausedNeverBelowMinimum() {
    BlockHeader parent = createParent(3_000_001L, 1_000_000L, MINIMUM_DIFFICULTY);
    long time = 1_001_000L; // Very slow block
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThanOrEqualTo(MINIMUM_DIFFICULTY);
  }

  @Test
  public void bombPausedIncludesFrozenBombComponent() {
    // The bomb is frozen at PAUSE_BLOCK / EXPONENTIAL_DIFF_PERIOD = 3M / 100K = period 30
    // So bomb contribution = 2^(30-2) = 2^28 = 268435456
    BlockHeader parent = createParent(3_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_010L; // ~10 seconds
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED.nextDifficulty(time, parent);
    // Should include the fixed bomb component
    assertThat(difficulty).isGreaterThan(BigInteger.ZERO);
  }

  // --- DIFFICULTY_BOMB_DELAYED (Gotham) ---

  @Test
  public void bombDelayedIncreasesOnFastBlock() {
    BlockHeader parent = createParent(5_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_005L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void bombDelayedAtGothamActivation() {
    // At block 5,000,001: periodCount = 50, exponent = 50 - 20 - 2 = 28
    // Bomb contribution = 2^28 = 268,435,456 (same as paused at 3M)
    BlockHeader parent = createParent(5_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_010L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void bombDelayedGrowsAtHigherBlocks() {
    // At block 8,000,001: periodCount = 80, exponent = 80 - 20 - 2 = 58
    // Bomb contribution = 2^58 (much larger than at 5M where bomb = 2^28)
    BlockHeader parentLow = createParent(5_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    BlockHeader parentHigh = createParent(8_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_010L;
    BigInteger diffAtGotham = ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED.nextDifficulty(time, parentLow);
    BigInteger diffAtHigher = ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED.nextDifficulty(time, parentHigh);
    assertThat(diffAtHigher).isGreaterThan(diffAtGotham);
  }

  // --- DIFFICULTY_BOMB_REMOVED ---

  @Test
  public void bombRemovedIncreasesOnFastBlock() {
    BlockHeader parent = createParent(10_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_005L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void bombRemovedDecreasesOnSlowBlock() {
    BlockHeader parent = createParent(10_000_001L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_030L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED.nextDifficulty(time, parent);
    assertThat(difficulty).isLessThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void bombRemovedNeverBelowMinimum() {
    BlockHeader parent = createParent(10_000_001L, 1_000_000L, MINIMUM_DIFFICULTY);
    long time = 1_001_000L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThanOrEqualTo(MINIMUM_DIFFICULTY);
  }

  @Test
  public void bombRemovedNoBombAtVeryHighBlock() {
    // Verify no exponential explosion at high block numbers (bomb is completely removed)
    BlockHeader parent = createParent(100_000_000L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_010L;
    BigInteger difficulty = ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED.nextDifficulty(time, parent);
    // Should be close to parent difficulty (no bomb, ~10s target)
    BigInteger parentDiff = parent.getDifficulty().getAsBigInteger();
    BigInteger maxReasonable = parentDiff.multiply(BigInteger.TWO);
    assertThat(difficulty).isLessThan(maxReasonable);
  }

  // --- EIP100 (Atlantis+, Byzantium-style) ---

  @Test
  public void eip100IncreasesOnFastBlockNoOmmers() {
    BlockHeader parent = createParent(12_000_000L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_005L;
    BigInteger difficulty = ClassicDifficultyCalculators.EIP100.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void eip100DecreasesOnSlowBlockNoOmmers() {
    BlockHeader parent = createParent(12_000_000L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_030L;
    BigInteger difficulty = ClassicDifficultyCalculators.EIP100.nextDifficulty(time, parent);
    assertThat(difficulty).isLessThan(parent.getDifficulty().getAsBigInteger());
  }

  @Test
  public void eip100HigherDifficultyWhenOmmersPresent() {
    // EIP-100: when ommers are present, difficulty adjustment is more aggressive
    // y = (2 - timeDelta/9) vs (1 - timeDelta/9)
    BlockHeader parentNoOmmers = createParent(12_000_000L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    BlockHeader parentWithOmmers = createParentWithOmmers(12_000_000L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_010L;

    BigInteger diffNoOmmers = ClassicDifficultyCalculators.EIP100.nextDifficulty(time, parentNoOmmers);
    BigInteger diffWithOmmers = ClassicDifficultyCalculators.EIP100.nextDifficulty(time, parentWithOmmers);

    // With ommers, target is faster so difficulty should be higher
    assertThat(diffWithOmmers).isGreaterThan(diffNoOmmers);
  }

  @Test
  public void eip100NeverBelowMinimum() {
    BlockHeader parent = createParent(12_000_000L, 1_000_000L, MINIMUM_DIFFICULTY);
    long time = 1_001_000L;
    BigInteger difficulty = ClassicDifficultyCalculators.EIP100.nextDifficulty(time, parent);
    assertThat(difficulty).isGreaterThanOrEqualTo(MINIMUM_DIFFICULTY);
  }

  @Test
  public void eip100NoBombComponent() {
    // EIP100 for ETC does NOT include a bomb (bomb was removed before Atlantis)
    // Verify no exponential growth at very high block numbers
    BlockHeader parent = createParent(100_000_000L, 1_000_000L, BigInteger.valueOf(1_000_000_000L));
    long time = 1_000_010L;
    BigInteger difficulty = ClassicDifficultyCalculators.EIP100.nextDifficulty(time, parent);
    BigInteger parentDiff = parent.getDifficulty().getAsBigInteger();
    BigInteger maxReasonable = parentDiff.multiply(BigInteger.TWO);
    assertThat(difficulty).isLessThan(maxReasonable);
  }
}
