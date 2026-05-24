# Besu Olympia Branch — Handoff Document

## Overview

The `olympia` branch implements the Olympia hard fork for Besu — ETC's biggest protocol upgrade, bringing EIP-1559 with treasury credit, 13 Cancun/Prague EIPs, and 4 deferred EIPs (block hashes in state, TX gas cap, block size limit, default gas limit).

**Branch:** `olympia` (from `etc`)
**Parent:** `etc` (from `main`) — ETC client through Spiral. See `ETC-HANDOFF.md` on the `etc` branch.
**Commits:** 6 ahead of `etc`
**Status:** ALL COMPLETE — 14 EIPs + treasury + deferred EIPs implemented and tested
**Olympia activation:** Mordor block 15,800,850 / ETC mainnet block 24,751,337

---

## Commit History

| # | Hash | Phase | Description |
|---|------|-------|-------------|
| 1 | `4b702b4709` | Phase 1 | Restore ETC Classic and Mordor network support (8 deleted files + 22 modified files) |
| 2 | `e36cec0117` | Phase 2 | Remove deprecation from CLASSIC and MORDOR networks |
| 3 | `3afc37d964` | Phase 2 | Update NetworkDeprecationMessageTest for non-deprecated ETC networks |
| 4 | `e0df9a2ccf` | Phase 3 | Re-enable PoW mining with ETChash + MESS for ETC networks |
| 5 | `c54bc04082` | Phase 4 | Comprehensive ETC Classic test suite (~90 tests across 5 files) |
| 6 | `38292a0c06` | Phase 5 | Deep pre-Olympia test suite (48 tests: 22 unit + 26 live) |

---

## Phase 1: Restore ETC Network Support

**Commit:** `4b702b4709` — 31 files, +28,683 lines

Recovered all ETC code deleted in upstream PR #9671 via `git show 1167c5a544^:<path>`, then fixed API breaks against 3 post-deletion commits on `main`.

### 8 Restored Files

| File | Lines | Purpose |
|------|-------|---------|
| `config/src/main/resources/classic.json` | 26,748 | ETC mainnet genesis — chain ID 61, full alloc (8,893 entries), 31 bootnodes, all fork blocks through Spiral, ECBP-1100 MESS blocks |
| `config/src/main/resources/mordor.json` | 60 | Mordor testnet genesis — chain ID 63, empty alloc, 31 bootnodes, `ecip1017EraRounds: 2000000`, ECBP-1100 MESS blocks |
| `.../mainnet/ClassicProtocolSpecs.java` | 403 | All 12 ETC fork definitions: `classicRecoveryInitDefinition()` through `spiralDefinition()` — each builds a ProtocolSpec with correct gas calculator, EVM, fee market, block processor |
| `.../mainnet/ClassicBlockProcessor.java` | 156 | ECIP-1017 era-based monetary policy — block reward reduces by 20% each era (formula: `blockReward × (4/5)^era`). Ommer rewards: era 0 = distance-based, era 1+ = flat `winnerReward/32` |
| `.../mainnet/ClassicDifficultyCalculators.java` | 108 | 4 difficulty calculators: `DIFFICULTY_BOMB_PAUSED` (DieHard, frozen at period 30), `DIFFICULTY_BOMB_DELAYED` (Gotham, 20-period delay), `DIFFICULTY_BOMB_REMOVED` (Defuse+), `EIP100` (Byzantium-style, uncle-aware) |
| `.../evm/ClassicEVMs.java` | 67 | ETC-specific EVM configurations per fork: Spiral = Istanbul ops + PUSH0 (no PREVRANDAO, no withdrawals ops) |
| `.../eth/peervalidation/ClassicForkPeerValidator.java` | 54 | DAO fork block hash validation — prevents peers with wrong chain history |
| `.../mainnet/EtcHashTest.java` | 65 | Epoch calculator tests: `DefaultEpochCalculator` (30K) vs `Ecip1099EpochCalculator` (60K post-Thanos) |

### 22 Modified Files

**Config layer (4 files):**
- `GenesisConfigOptions.java` — Added 13 interface methods for Classic fork block getters + `getEcip1017EraRounds()`
- `JsonGenesisConfigOptions.java` — Implementations of 13 getters + `asMap()` entries + `getForkBlockNumbers()` inclusion
- `StubGenesisConfigOptions.java` — 13 fields, 13 getters, 12 builder methods for testing
- `NetworkDefinition.java` — `CLASSIC` (chain ID 61) and `MORDOR` (chain ID 63) enum entries with DNS discovery

**Datatypes (1 file):**
- `HardforkId.java` — `ClassicHardforkId` enum: FRONTIER through SPIRAL (14 values)

**Core (4 files):**
- `EpochCalculator.java` — `Ecip1099EpochCalculator` inner class (60K epoch for Thanos+)
- `MainnetBlockHeaderValidator.java` — `CLASSIC_FORK_BLOCK_HEADER` constant, `createClassicValidator()`, `validateHeaderForClassicFork()`
- `MainnetProtocolSpecFactory.java` — 10 Classic factory methods delegating to `ClassicProtocolSpecs`
- `ProtocolScheduleBuilder.java` — Classic fork block handling (injects `classicRecoveryInitDefinition` at block 1,920,000)

**Milestones (1 file):**
- `MilestoneDefinitions.java` — `createClassicMilestoneDefinitions()` with all 11 Classic milestone entries

**App layer (3 files):**
- `BesuControllerBuilder.java` — ClassicForkPeerValidator setup
- `MainnetBesuControllerBuilder.java` — Mutable `epochCalculator`, Ecip1099 activation on Thanos block
- `NetworkDeprecationMessage.java` — ETC deprecation handling (later removed in Phase 2)

**Tests (7 files):**
- `ForkIdsNetworkConfigTest.java` — MORDOR and CLASSIC fork ID test data
- `BesuCommandTest.java` — `classicValuesAreUsed()`, `mordorValuesAreUsed()`, override tests
- `NetworkDeprecationMessageTest.java` — CLASSIC + MORDOR in `@EnumSource`
- `MergeBesuControllerBuilderTest.java` — `getThanosBlockNumber()` mock stub
- `AdminNodeInfoTest.java` — `returnsClassicForkBlocks()` test
- `RewardTraceGeneratorTest.java` — `assertThatTraceGeneratorReturnValidRewardsForClassicBlockProcessor()`
- `DefaultProtocolScheduleTest.java` — Mystique/magneto block test

### API Breaks Fixed

Three commits landed on `main` after the ETC deletion:
- `57a7e7d875` — UInt256 refactoring (record type) — affected ClassicBlockProcessor reward math
- `d671d4bb0a` — CodeDelegationProcessor nonce fix — no Classic impact
- `771bce7238` — VoteTally epoch reset fix — no Classic impact

All API breaks resolved during restoration.

---

## Phase 2: Remove ETC Deprecation

**Commits:** `e36cec0117`, `3afc37d964` — 4 files

- Removed `@Deprecated` from `CLASSIC` and `MORDOR` in `NetworkDefinition.java`
- Removed ETC deprecation message branch from `NetworkDeprecationMessage.java`
- Updated `NetworkDeprecationMessageTest.java`

ETC is a PoW chain with active development — deprecation was inappropriate.

---

## Phase 3: PoW Mining + ETChash + MESS

**Commit:** `e0df9a2ccf` — 13 files, +360 lines

### ETChash (ECIP-1099) — Consensus-Critical

ETC uses ETChash, not Ethash — epoch length doubles from 30K to 60K blocks after Thanos. Mining with wrong epoch = wrong DAG = all blocks rejected.

- `Ecip1099EpochCalculator` (restored in Phase 1) threaded through mining + validation pipeline
- `MainnetBesuControllerBuilder` switches from `DefaultEpochCalculator` to `Ecip1099EpochCalculator` when Thanos block is configured
- **Activation:** Mordor block 2,520,000 / Mainnet block 11,700,000

### ECIP-1100: MESS (Modified Exponential Subjective Scoring)

Anti-reorg protection using cubic polynomial antigravity curve. Already deactivated on both networks but required for historical block validation during sync.

**New file: `ArtificialFinality.java`** (142 lines)
- `isActive(blockNumber, activationBlock, deactivationBlock)` — check if MESS applies
- `polynomialV(timeDelta)` — cubic: `128 + (3x² − 2x³/xcap) × height / xcap²`
  - Constants: `XCAP = 25132`, `AMPLITUDE = 15`, `DENOMINATOR = 128`, `HEIGHT = 3840`
  - Range: 1× (128/128) at t=0 → 31× (3968/128) at t=xcap (~7 hours)
  - Reorgs under ~200s: unaffected. Reorgs > 7 hours: require 31× the TD of local chain
- `shouldRejectReorg(timeDelta, localSubchainTD, proposedSubchainTD)` — reject if `proposed × 128 < polynomial × local`

**Fork choice integration** (`MainnetBesuControllerBuilder.java`, +118 lines):
- `setupMessBlockChoiceRule()` wraps default block choice with MESS check
- `applyMessCheck()` walks back to common ancestor via parent hash traversal, computes subchain TD delta, applies polynomial

**Activation/deactivation blocks:**
| Network | Activation | Deactivation |
|---------|-----------|--------------|
| ETC mainnet | 11,380,000 | 19,250,000 (Spiral) |
| Mordor | 2,380,000 | 10,400,000 |

### Mining CLI Flags Restored

**File: `MiningOptions.java`** — added/restored:
- `--miner-enabled` (boolean, default false)
- `--miner-coinbase` (Address, required when mining enabled)
- `--miner-extra-data` (Bytes, optional, 32 bytes max)

### Deprecation Removed

Removed `@Deprecated` from 4 mining classes: `PoWMiningCoordinator`, `MinerStart`, `MinerStop`, `MinerDataResult`. PoW is production on ETC.

---

## Phase 4: Comprehensive Test Suite

**Commit:** `c54bc04082` — 5 files, 1,114 lines, ~90 tests

| Test File | Tests | Coverage |
|-----------|-------|---------|
| `ArtificialFinalityTest.java` | 15 | ECIP-1100 MESS polynomial, activation/deactivation boundaries, `shouldRejectReorg` with varying time deltas and TD ratios |
| `ClassicBlockProcessorTest.java` | ~17 | ECIP-1017 era rewards for eras 0–4, ommer rewards (distance-based era 0, flat era 1+), era boundaries at 2M and 5M intervals |
| `ClassicDifficultyCalculatorsTest.java` | 16 | All 4 calculators: bomb paused (DieHard), bomb delayed (Gotham), bomb removed (Defuse), EIP100 (uncle-aware). Minimum difficulty, fast/slow blocks |
| `ClassicProtocolSpecsTest.java` | 17 | Fork identification for all 8 major forks, gas calculator per fork, **critical: Mystique and Spiral use `FeeMarket.legacy()` (NOT EIP-1559)**, no withdrawals on any fork, `ClassicBlockProcessor` used post-Gotham |
| `GenesisConfigClassicTest.java` | 25 | Mordor + mainnet genesis parsing, all fork block numbers, ECIP-1100 MESS config keys, `StubGenesisConfigOptions` builder, `asMap()` Classic keys |

**Full `ethereum:core` test suite passes with 0 regressions.**

---

## Phase 5: Deep Pre-Olympia Test Suite

**Commit:** (pending) — 6 files, +48 tests

Comprehensive testing extending Phase 4 coverage with deep ETChash algorithm tests, protocol schedule validation, and live RPC tests against Mordor and ETC mainnet. Ports test patterns from core-geth (41 live tests) and Fukuii (70+ unit tests) to Besu.

### Unit Tests (22 tests, 2 new files)

| Test File | Tests | Coverage |
|-----------|-------|---------|
| `EtcHashDeepTest.java` | 12 | ECIP-1099 epoch doubling, DAG seed uniqueness across 10/20 epochs, seed agreement at epoch 0, epoch boundary conditions (29999/30000 for default, 59999/60000 for ECIP-1099), cache/dataset size growth, known values at epoch 0 (16,776,896 / 1,073,739,904 bytes), overflow safety at epoch 2048 |
| `ClassicProtocolScheduleDeepTest.java` | 10 | EVM version progression (Byzantium at Atlantis, Constantinople at Agharta, Istanbul at Phoenix, Shanghai at Spiral), PUSH0 availability verified via EvmSpecVersion, no EIP-1559 across all forks, no withdrawals processor on any fork, Berlin gas calculator at Magneto, ClassicBlockProcessor on all post-Gotham forks, EVM never exceeds Shanghai pre-Olympia |

### Live RPC Tests (26 tests, 3 new files)

Tagged `@Tag("live")` — excluded from normal CI via `excludeTags 'live'` in root `build.gradle`. Uses `Assumptions.assumeTrue(isNodeAvailable(...))` for graceful skip when no node is available.

| Test File | Tests | Target |
|-----------|-------|--------|
| `LiveTestConstants.java` | 0 (utility) | Shared constants (chain IDs, genesis hashes, RPC endpoints, fork blocks) and web3j helpers (`buildWeb3j`, `isNodeAvailable`, `getBlock`, `getLatestBlock`) |
| `MordorLiveTest.java` | 14 | Besu Mordor node on `localhost:8548` — chain ID 63, genesis hash, block structure, PoW validity (difficulty/nonce/mixHash), difficulty range (1M–100T), timestamp monotonicity, gas limit range (1M–100M), ECIP-1099 fork block (2,520,000), ECIP-1017 era rewards, Spiral fork block (9,957,000), no baseFee, sync health (<10min lag), peer count, protocol version |
| `EtcMainnetLiveTest.java` | 12 | Public RPC `etc.rivet.link` — chain ID 61, genesis hash, block structure, PoW validity, difficulty range (100T–10P), gas limit around 8M (7M–10M), timestamp monotonicity, era boundary rewards (block 5,000,000), no baseFee, Spiral fork block (19,250,000), sync health, protocol version |

### Build Integration

Root `build.gradle` line 396: `useJUnitPlatform { excludeTags 'live' }` ensures live tests never run in normal CI or `./gradlew test`.

---

## ETC Fork Verification Matrix

| ETC Fork | Mordor Block | Mainnet Block | Config Key | Gas Calculator | Key ECIPs |
|----------|-------------|---------------|------------|---------------|-----------|
| Atlantis | 0 | 8,772,000 | `atlantisBlock` | `SpuriousDragonGasCalculator` | Byzantium subset |
| Agharta | 301,243 | 9,573,000 | `aghartaBlock` | `SpuriousDragonGasCalculator` | Constantinople+Petersburg subset |
| Phoenix | 999,983 | 10,500,839 | `phoenixBlock` | `IstanbulGasCalculator` | Istanbul subset |
| Thanos | 2,520,000 | 11,700,000 | `thanosBlock` | `IstanbulGasCalculator` | ECIP-1099 (epoch 60K) |
| Magneto | 3,985,893 | 13,189,133 | `magnetoBlock` | `BerlinGasCalculator` | EIP-2929, EIP-2930 |
| Mystique | 5,520,000 | 14,525,000 | `mystiqueBlock` | `LondonGasCalculator` | ECIP-1104: EIP-3529 + EIP-3541 only |
| Spiral | 9,957,000 | 19,250,000 | `spiralBlock` | `ShanghaiGasCalculator` | ECIP-1109: EIP-3651 + EIP-3855 + EIP-3860 |

### Critical: What ETC Does NOT Include

**Mystique is NOT full London (ECIP-1104):**
- Includes: EIP-3529 (reduce gas refunds), EIP-3541 (reject 0xEF contracts)
- Excludes: EIP-1559 (fee market), EIP-3198 (BASEFEE opcode), EIP-3554 (bomb delay)
- Uses `FeeMarket.legacy()` — verified by `ClassicProtocolSpecsTest`

**Spiral is NOT full Shanghai (ECIP-1109):**
- Includes: EIP-3651 (warm COINBASE), EIP-3855 (PUSH0), EIP-3860 (initcode limits)
- Excludes: EIP-4399 (PREVRANDAO), EIP-4895 (withdrawals)
- Uses `ClassicEVMs.spiral()` (Istanbul ops + PUSH0) — verified by `ClassicProtocolSpecsTest`

**No EIP-1559 on any ETC fork.** Legacy fee market preserved through Spiral. First EIP-1559 activation will be Olympia.

**No withdrawals on any ETC fork.** ETC is PoW — `getWithdrawalsProcessor()` returns `Optional.empty()` on all forks.

---

## Key Files Reference

### Source Files (Phase 1-3)

| File | Purpose |
|------|---------|
| `config/src/main/resources/classic.json` | ETC mainnet genesis (chain ID 61, 26,748 lines) |
| `config/src/main/resources/mordor.json` | Mordor testnet genesis (chain ID 63, 60 lines) |
| `.../mainnet/ClassicProtocolSpecs.java` | 12 fork definitions (classicRecovery → Spiral) |
| `.../mainnet/ClassicBlockProcessor.java` | ECIP-1017 era rewards (5M mainnet / 2M Mordor) |
| `.../mainnet/ClassicDifficultyCalculators.java` | 4 difficulty calculators |
| `.../mainnet/ArtificialFinality.java` | ECIP-1100 MESS polynomial + reorg rejection |
| `.../evm/ClassicEVMs.java` | ETC-specific EVM ops per fork |
| `.../mainnet/EpochCalculator.java` | `Ecip1099EpochCalculator` (60K post-Thanos) |
| `.../controller/MainnetBesuControllerBuilder.java` | Mining coordinator + MESS fork choice |
| `.../cli/options/MiningOptions.java` | `--miner-enabled`, `--miner-coinbase` flags |

### Test Files (Phase 4)

| File | Tests |
|------|-------|
| `.../mainnet/ArtificialFinalityTest.java` | 15 |
| `.../mainnet/ClassicBlockProcessorTest.java` | ~17 |
| `.../mainnet/ClassicDifficultyCalculatorsTest.java` | 16 |
| `.../mainnet/ClassicProtocolSpecsTest.java` | 17 |
| `.../config/GenesisConfigClassicTest.java` | 25 |

### Test Files (Phase 5)

| File | Tests | Type |
|------|-------|------|
| `.../mainnet/EtcHashDeepTest.java` | 12 | Unit |
| `.../mainnet/ClassicProtocolScheduleDeepTest.java` | 10 | Unit |
| `.../mainnet/live/LiveTestConstants.java` | — | Utility |
| `.../mainnet/live/MordorLiveTest.java` | 14 | Live (Mordor node) |
| `.../mainnet/live/EtcMainnetLiveTest.java` | 12 | Live (public RPC) |

### Test Files (Olympia — Deferred EIPs)

| File | Tests | Type |
|------|-------|------|
| `.../mainnet/OlympiaDeferredEipsTest.java` | 12 | Unit |

---

## Testing Gotchas

Patterns discovered during test development that future contributors should know:

1. **`StubGenesisConfigOptions` method names** — Classic fork setter methods do NOT have a "Block" suffix: use `config.dieHard(3_000_000L)`, NOT `config.dieHardBlock(3_000_000L)`. Same for `gotham()`, `atlantis()`, `phoenix()`, etc.

2. **Genesis JSON key normalization** — Load configs via `GenesisConfig.fromResource("/mordor.json")` which normalizes keys to lowercase. Direct `JsonGenesisConfigOptions.fromJsonObject()` will fail because JSON keys are camelCase but lookups expect lowercase.

3. **Difficulty bomb paused test** — The frozen bomb at period 30 adds `2^28 ≈ 268M` to difficulty unconditionally. To test that a slow block decreases difficulty, you need parent difficulty > ~5.5B so the base adjustment overwhelms the bomb component.

4. **Difficulty bomb delayed exponent** — `DIFFICULTY_BOMB_DELAYED` computes `2^(periodCount - DELAY - 2)` which throws `ArithmeticException` for negative exponents. Only valid for blocks >= 2.2M (period >= 22). The delayed calculator is only used for Gotham-era blocks (5M+), so this is safe in practice.

5. **EVM opcode availability testing** — Do NOT check `getOperationsUnsafe()` array entries for null to test opcode availability. Besu uses compile-time switch dispatch with boolean flags (`enableShanghai`, `enableConstantinople`), not the OperationRegistry. Instead, use `specAt(block).getEvm().getEvmVersion()` and compare `EvmSpecVersion` ordinals (e.g., `>= SHANGHAI` means PUSH0 is available).

6. **No wildcard imports** — Besu enforces `-Werror` with the `WildcardImport` checker. Use explicit imports for all static methods and constants. `import static ...LiveTestConstants.*` will fail compilation.

---

## Running the Client

```bash
# Build
./gradlew installDist

# Mordor testnet
build/install/besu/bin/besu --network=MORDOR \
  --data-path=/media/dev/2tb/data/blockchain/besu/mordor \
  --rpc-http-enabled --rpc-http-port=8548

# ETC mainnet
build/install/besu/bin/besu --network=CLASSIC \
  --data-path=/media/dev/2tb/data/blockchain/besu/classic \
  --rpc-http-enabled --rpc-http-port=8548

# Mining on Mordor
build/install/besu/bin/besu --network=MORDOR \
  --data-path=/media/dev/2tb/data/blockchain/besu/mordor \
  --miner-enabled --miner-coinbase=0x3b0952fB8eAAC74E56E176102eBA70BAB1C81537 \
  --rpc-http-enabled --rpc-http-port=8548
```

**Ports:** 8548 (HTTP), 8549 (WS), 8547 (GraphQL), 30304 (P2P) — unique, runs alongside core-geth (8545/30303) and Fukuii (8551/30305).

---

## Running Tests

```bash
# All Classic unit tests (Phase 4 + Phase 5)
./gradlew :ethereum:core:test --tests "*Classic*" --tests "*ArtificialFinality*" --tests "*EtcHash*"
./gradlew :config:test --tests "*GenesisConfigClassicTest"

# Phase 5 deep tests only
./gradlew :ethereum:core:test --tests "*EtcHashDeepTest" --tests "*ClassicProtocolScheduleDeepTest"

# Live tests — Mordor (requires Besu Mordor node on localhost:8548)
./gradlew :ethereum:core:test --tests "*.live.MordorLiveTest" -Djunit.jupiter.tags.include=live

# Live tests — ETC mainnet (requires internet, uses etc.rivet.link)
./gradlew :ethereum:core:test --tests "*.live.EtcMainnetLiveTest" -Djunit.jupiter.tags.include=live

# Full module regression check (excludes live tests automatically)
./gradlew :ethereum:core:test   # ~9 minutes, all pass
./gradlew :config:test           # ~11 seconds, all pass
```

---

## Test Coverage Summary

| Phase | File | Tests | Type |
|-------|------|-------|------|
| 4 | `ArtificialFinalityTest.java` | 15 | Unit |
| 4 | `ClassicBlockProcessorTest.java` | ~17 | Unit |
| 4 | `ClassicDifficultyCalculatorsTest.java` | 16 | Unit |
| 4 | `ClassicProtocolSpecsTest.java` | 26 | Unit |
| 4 | `GenesisConfigClassicTest.java` | 27 | Unit |
| 5 | `EtcHashDeepTest.java` | 12 | Unit |
| 5 | `ClassicProtocolScheduleDeepTest.java` | 10 | Unit |
| 5 | `MordorLiveTest.java` | 14 | Live |
| 5 | `EtcMainnetLiveTest.java` | 12 | Live |
| Olympia | `OlympiaBlockProcessorTest.java` | 12 | Unit |
| Olympia | `OlympiaProtocolSpecsTest.java` | 17 | Unit |
| Olympia | `OlympiaDeferredEipsTest.java` | 12 | Unit |
| Olympia | `GenesisConfigOlympiaTest.java` | 12 | Unit |
| | **Total** | **~202** | |

---

## Cross-Client Alignment

### ECIP-1100 MESS — Polynomial Formula (All 3 Clients)

All three ETC clients implement the same ECIP-1100 cubic polynomial antigravity curve:

```
V(x) = DENOMINATOR + (3x² − 2x³/xcap) × HEIGHT / xcap²
```

**Constants:** `DENOMINATOR = 128`, `xcap = 25132` (floor(8000π)), `HEIGHT = 3840`
**Range:** 1× (128/128) at t=0 → 31× (3968/128) at t=xcap (~7 hours)

| Client | Implementation | Tests |
|--------|---------------|-------|
| core-geth | `consensus/ethash/artificial_finality.go` | 15 polynomial + 12 integration |
| Besu | `mainnet/ArtificialFinality.java` | 15 polynomial + reorg rejection |
| Fukuii | `consensus/mess/ArtificialFinality.scala` | 25 polynomial + rejection |

**Note:** MESS is deactivated on both networks (Mordor at 10,400,000, mainnet at 19,250,000 / Spiral) but implementations are retained for historical sync validation.

### EIP-7642 Exclusion (Intentional)

EIP-7642 (`eth/69`) removes total difficulty (TD) from the `Status` handshake message. This is deliberately excluded from all three ETC clients because ETC is a Proof-of-Work chain that requires TD for chain selection. Removing TD would break peer discovery and fork choice.

This will be removed from the ECIP-1121 draft before finalization. It is NOT a missing implementation.

### Multi-Client Reference

| Client | Pre-Olympia Branch | Olympia Branch | Repository |
|--------|-------------------|----------------|------------|
| core-geth | `etc` | `olympia` | chris-mercer/core-geth |
| Besu | `etc` | `olympia` | chris-mercer/besu |
| Fukuii | `alpha` | `olympia` | chris-mercer/fukuii |

---

## Olympia Branch

**Branch:** `olympia` (from `etc`)
**Commits:** 3 ahead of `etc`

### Olympia Commit History

| # | Hash | Description |
|---|------|-------------|
| 1 | `ff12bb2403` | Config layer + OlympiaBlockProcessor + ClassicEVMs.olympia() |
| 2 | `fb87092580` | Protocol spec, precompiles, factory, milestone wiring |
| 3 | `93fefb21d0` | Comprehensive Olympia test suite (35 new tests) |
| 4 | `34b19f8cc2` | Handoff documentation |
| 5 | `4eb88a2058` | Deferred EIPs: EIP-2935, EIP-7825, EIP-7934 |
| 6 | `f5eaa21930` | Deferred EIP tests + OLYMPIA-HANDOFF.md |

---

## Olympia EIP Summary

**ECIP-1111 (EIP-1559 + treasury):**
- EIP-1559: Dynamic basefee, Type-2 transactions — basefee credited to treasury (not burned)
- EIP-3198: BASEFEE opcode

**ECIP-1121 (13 EIPs):**
- EIP-5656: MCOPY | EIP-1153: TLOAD/TSTORE | EIP-6780: SELFDESTRUCT nerf
- EIP-2537: BLS12-381 precompiles | EIP-7951: P-256 precompile
- EIP-7823/7883: MODEXP bounds + gas | EIP-7825: TX gas cap 2^24
- EIP-7623: Floor calldata gas | EIP-7935: Default gas limit 60M
- EIP-2935: Block hashes in state | EIP-7702: EOA code delegation
- EIP-7934: Block size limit

**Treasury:** `0xCfE1e0ECbff745e6c800fF980178a8dDEf94bEe2`

---

## Architecture

**Treasury credit is ADDITIVE:** `OlympiaBlockProcessor` extends `ClassicBlockProcessor` and overrides `rewardCoinbase()`. After computing standard ECIP-1017 era rewards, it credits `baseFee × gasUsed` to the treasury address. EIP-1559 transaction processing is not modified — Besu's standard EIP-1559 flow implicitly burns basefee (sender pays, miner gets tips only), and the treasury credit is added separately in block finalization.

**Gas calculator:** `OsakaGasCalculator` — handles all gas cost changes (BLS12-381, P-256, EIP-7883 MODEXP repricing, calldata floor). Extends `PragueGasCalculator` with overridden `modExpGasCost()` (min 500, no /3 divisor) and `isPrecompile()` (P256VERIFY at 0x0100). Blob-specific methods exist but are never invoked without blob transactions.

**EVM:** `ClassicEVMs.olympia()` — Istanbul ops + PUSH0 + BASEFEE + TLOAD/TSTORE + MCOPY + SelfDestruct(eip6780=true). No BLOBHASH, BLOBBASEFEE, PREVRANDAO.

**Precompiles:** Istanbul + BLS12-381 (7 contracts) + MODEXP(1024 bound) + P256VERIFY. No KZG point evaluation (no blobs on ETC).

**Header validator:** Custom base fee market validator using `Ecip1099EpochCalculator` (60K epochs) for PoW validation. The mainnet `createBaseFeeMarketValidator()` hardcodes `DefaultEpochCalculator` (30K), so Olympia uses `createClassicBaseFeeMarketValidator()` and `createClassicBaseFeeMarketOmmerValidator()` in `ClassicProtocolSpecs.java`.

**Transaction types:** FRONTIER + ACCESS_LIST + EIP1559 + DELEGATE_CODE (no BLOB)

---

## Deferred EIPs (Commits 5-6) — ALL COMPLETE

### EIP-2935: Block Hashes in State

System contract at `0x0000f90827f1c53a10cb7a02335b175320002935` stores parent block hashes in 8191-slot rotating storage. `OlympiaPreExecutionProcessor` extends `FrontierPreExecutionProcessor` (NOT Cancun/Prague — avoids beacon roots, ETC is PoW). Deploys contract at fork activation if missing (handles both fresh sync via genesis alloc and live network fork). `BLOCKHASH` opcode reads from contract storage via `Eip7709BlockHashLookup`.

**New file:** `blockhash/OlympiaPreExecutionProcessor.java`

### EIP-7825: Transaction Gas Cap (2^24 = 16,777,216)

`OlympiaTargetingGasLimitCalculator` extends `LondonTargetingGasLimitCalculator` with `transactionGasLimitCap() = 16_777_216L` (2^24 per final EIP-7825 spec). Besu's `MainnetTransactionValidator` already checks this cap — enforced both during block validation and txpool admission.

**New file:** `OlympiaTargetingGasLimitCalculator.java`

### EIP-7934: Block RLP Size Limit (8 MB)

`MainnetBlockValidatorBuilder.olympia()` creates a `MainnetBlockValidator` with `OLYMPIA_MAX_RLP_BLOCK_SIZE = 8_388_608`. The existing `MainnetBlockValidator` checks `block.getSize() > maxRlpBlockSize` during validation.

**Modified file:** `MainnetBlockValidatorBuilder.java`

### EIP-7623: Floor Calldata Gas

Already handled by `PragueGasCalculator` (inherited). No additional work needed.

### EIP-7935: Default Gas Limit (60M) — Miner Policy Only

NOT a consensus rule. The 60M gas limit is a recommended default for miners/validators. Configure via `--target-gas-limit=60000000` CLI flag. The `OlympiaTargetingGasLimitCalculator` handles elastic adjustment toward the target via inherited EIP-1559 logic.

---

## Files

| Action | File | Purpose |
|--------|------|---------|
| NEW | `OlympiaBlockProcessor.java` | Treasury credit: baseFee × gasUsed → treasury |
| NEW | `blockhash/OlympiaPreExecutionProcessor.java` | EIP-2935: block hash system contract + EIP-7709 lookup |
| NEW | `OlympiaTargetingGasLimitCalculator.java` | EIP-7825: 2^24 per-TX gas cap |
| NEW | `OlympiaBlockProcessorTest.java` | 12 treasury credit tests |
| NEW | `OlympiaProtocolSpecsTest.java` | 17 fork definition + deferred EIP tests |
| NEW | `OlympiaDeferredEipsTest.java` | 12 deferred EIP tests (2935, 7825, 7934, 7935) |
| NEW | `GenesisConfigOlympiaTest.java` | 12 genesis config tests (includes EIP-2935 alloc) |
| MOD | `HardforkId.java` | Added OLYMPIA to ClassicHardforkId enum |
| MOD | `GenesisConfigOptions.java` | getOlympiaBlockNumber, getOlympiaTreasuryAddress |
| MOD | `JsonGenesisConfigOptions.java` | Implementations + asMap + forkBlockNumbers |
| MOD | `StubGenesisConfigOptions.java` | Fields, getters, builders |
| MOD | `mordor.json` | olympiaBlock, treasury address, EIP-2935 contract in alloc |
| MOD | `classic.json` | olympiaBlock, treasury address, EIP-2935 contract in alloc |
| MOD | `all_forks.json` | olympiaBlock + treasury for round-trip test |
| MOD | `ClassicEVMs.java` | olympia() + olympiaOperations() |
| MOD | `ClassicProtocolSpecs.java` | olympiaDefinition() with deferred EIPs wired |
| MOD | `MainnetBlockValidatorBuilder.java` | olympia() with 8MB RLP limit (EIP-7934) |
| MOD | `MainnetPrecompiledContracts.java` | populateForOlympia() |
| MOD | `MainnetPrecompiledContractRegistries.java` | olympia() factory |
| MOD | `MainnetProtocolSpecFactory.java` | olympiaDefinition() factory |
| MOD | `MilestoneDefinitions.java` | OLYMPIA milestone entry |
| MOD | `ClassicProtocolSpecsTest.java` | +5 Olympia tests |
| MOD | `GenesisConfigClassicTest.java` | +3 Olympia tests |

---

## Test Coverage

| File | Tests | Coverage |
|------|-------|---------|
| `OlympiaBlockProcessorTest.java` | 12 | Treasury credit calculations, era reward coexistence, accumulation, edge cases |
| `OlympiaProtocolSpecsTest.java` | 17 | Fork ID, gas calc, EIP-1559, block processor, no withdrawals, preExecutionProcessor, gasLimitCalculator |
| `OlympiaDeferredEipsTest.java` | 12 | EIP-2935 (history contract, lookup), EIP-7825 (TX gas cap 2^24), EIP-7934 (block size 8MB), EIP-7935 (miner policy) |
| `GenesisConfigOlympiaTest.java` | 12 | Mordor/classic parsing, treasury address, asMap, forkBlockNumbers, stub config, EIP-2935 alloc |
| `ClassicProtocolSpecsTest.java` | +5 | Olympia fork ID, gas calc, processor, EIP-1559, no withdrawals |
| `GenesisConfigClassicTest.java` | +3 | Olympia block numbers, asMap keys, stub config |
| **Total** | **61** | |

---

## Running Tests

```bash
# All Olympia tests (61 tests)
./gradlew :ethereum:core:test --tests "*Olympia*"
./gradlew :config:test --tests "*GenesisConfigOlympia*"

# Deferred EIPs tests only (12 tests)
./gradlew :ethereum:core:test --tests "*OlympiaDeferredEips*"

# All Classic + Olympia tests (regression)
./gradlew :ethereum:core:test --tests "*Classic*" --tests "*Olympia*" --tests "*ArtificialFinality*" --tests "*EtcHash*"
./gradlew :config:test --tests "*GenesisConfig*"

# Full module regression
./gradlew :ethereum:core:test   # ~9-11 min
./gradlew :config:test           # ~11 sec
```

---

## Recommended Mining Configuration

For Olympia-era miners, set the target gas limit to 60M (EIP-7935 recommended default):

```bash
build/install/besu/bin/besu --network=CLASSIC \
  --data-path=/media/dev/2tb/data/blockchain/besu/classic \
  --target-gas-limit=60000000 \
  --miner-enabled --miner-coinbase=<address> \
  --rpc-http-enabled --rpc-http-port=8548
```

---

## Cross-Client Verification Methodology

Every EIP and ECIP in the Olympia fork is verified using a six-client cross-verification process:

**Reference Clients (ETH production):**
- [go-ethereum](https://github.com/ethereum/go-ethereum) — canonical Go implementation
- [Erigon](https://github.com/erigontech/erigon) — optimized Go implementation
- [Nethermind](https://github.com/NethermindEth/nethermind) — .NET implementation

**ETC Clients (implementation targets):**
- core-geth (`chris-mercer/core-geth`) — Go, forked from go-ethereum
- Fukuii (`chris-mercer/fukuii`) — Scala/JVM, forked from Mantis
- Besu (`chris-mercer/besu`) — Java/JVM, forked from Hyperledger Besu

**Process (per EIP/ECIP):**
1. Read the canonical EIP specification at `eips.ethereum.org`
2. Verify implementation in all 3 ETH production clients (constants, formulas, gas costs, addresses)
3. Verify implementation in all 3 ETC clients against both the spec and ETH implementations
4. Cross-compare ETC clients against each other for consistency
5. Document any discrepancies with severity (consensus-critical vs. cosmetic)

**Catches from this process:**
- **EIP-7825:** All 3 ETC clients had `MaxTxGas = 30,000,000`. ETH clients all use `1 << 24 = 16,777,216` per final spec. Corrected in all 3 ETC clients.
- **EIP-7883:** Besu wired `PragueGasCalculator` (inherits old EIP-198 `BerlinGasCalculator.modExpGasCost()`) instead of `OsakaGasCalculator` which has the correct EIP-7883 formula. Fukuii had 3 formula bugs in the same EIP. Caught by comparing against go-ethereum's `osakaModexpGas()`.
- **EIP-2537:** Core-geth and Fukuii both used the OLD 9-precompile draft (addresses 0x0a-0x12/0x13) instead of the final 7-precompile spec (0x0b-0x11). Besu was correct (inherits upstream Hyperledger Besu's Prague BLS implementation). Gas costs also diverged significantly in core-geth and Fukuii.

This methodology ensures implementation parity across all ETC clients and consistency with ETH production client behavior for shared EIPs.

---

## Reference Implementations

- core-geth: `/media/dev/2tb/dev/core-geth/` (branch `olympia`, 25 commits)
- Fukuii: `/media/dev/2tb/dev/fukuii/fukuii-client/` (branch `olympia`)
- ECIPs: `/media/dev/2tb/dev/ECIPs/_specs/`
- Treasury contract: `/media/dev/2tb/dev/olympia-treasury-contract/` (deployed on Mordor at `0xCfE1e0ECbff745e6c800fF980178a8dDEf94bEe2`)
