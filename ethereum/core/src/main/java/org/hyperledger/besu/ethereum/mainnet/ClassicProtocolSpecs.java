/*
 * Copyright ConsenSys AG.
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

import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.AGHARTA;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.ATLANTIS;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.CLASSIC_RECOVERY_INIT;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.CLASSIC_TANGERINE_WHISTLE;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.DEFUSE_DIFFICULTY_BOMB;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.DIE_HARD;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.GOTHAM;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.MAGNETO;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.MYSTIQUE;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.OLYMPIA;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.PHOENIX;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.SPIRAL;
import static org.hyperledger.besu.datatypes.HardforkId.ClassicHardforkId.THANOS;
import static org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs.powHasher;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.PowAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.MainnetBlockValidatorBuilder;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.blockhash.OlympiaPreExecutionProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.BaseFeeMarketBlockHeaderGasPriceValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.CalculatedDifficultyValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ExtraDataMaxLengthValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ProofOfWorkValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;
import org.hyperledger.besu.evm.ClassicEVMs;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.DieHardGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.CodeDelegationService;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class ClassicProtocolSpecs {
  private static final Wei MAX_BLOCK_REWARD = Wei.fromEth(5);

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private ClassicProtocolSpecs() {
    // utility class
  }

  public static ProtocolSpecBuilder classicRecoveryInitDefinition(
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return MainnetProtocolSpecs.homesteadDefinition(
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                MainnetBlockHeaderValidator.createClassicValidator())
        .hardforkId(CLASSIC_RECOVERY_INIT);
  }

  public static ProtocolSpecBuilder tangerineWhistleDefinition(
      final Optional<BigInteger> chainId,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return MainnetProtocolSpecs.homesteadDefinition(
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .isReplayProtectionSupported(true)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(), gasLimitCalculator, true, chainId))
        .hardforkId(CLASSIC_TANGERINE_WHISTLE);
  }

  public static ProtocolSpecBuilder dieHardDefinition(
      final Optional<BigInteger> chainId,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return tangerineWhistleDefinition(
            chainId,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(DieHardGasCalculator::new)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED)
        .hardforkId(DIE_HARD);
  }

  public static ProtocolSpecBuilder gothamDefinition(
      final Optional<BigInteger> chainId,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return dieHardDefinition(
            chainId,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .blockReward(MAX_BLOCK_REWARD)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_DELAYED)
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                protocolSchedule,
                balConfig) ->
                new ClassicBlockProcessor(
                    transactionProcessor,
                    transactionReceiptFactory,
                    blockReward,
                    miningBeneficiaryCalculator,
                    skipZeroBlockRewards,
                    genesisConfigOptions.getEcip1017EraRounds(),
                    protocolSchedule,
                    balConfig))
        .hardforkId(GOTHAM);
  }

  public static ProtocolSpecBuilder defuseDifficultyBombDefinition(
      final Optional<BigInteger> chainId,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return gothamDefinition(
            chainId,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_REMOVED)
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(), gasLimitCalculator, true, chainId))
        .hardforkId(DEFUSE_DIFFICULTY_BOMB);
  }

  public static ProtocolSpecBuilder atlantisDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return gothamDefinition(
            chainId,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .evmBuilder(MainnetEVMs::byzantium)
        .evmConfiguration(evmConfiguration)
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .skipZeroBlockRewards(true)
        .messageCallProcessorBuilder(MessageCallProcessor::new)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(ClassicDifficultyCalculators.EIP100)
        .transactionReceiptFactory(
            new MainnetProtocolSpecs.ByzantiumTransactionReceiptFactory(enableRevertReason))
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm, true, Collections.singletonList(MaxCodeSizeRule.from(evm)), 1))
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(false)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                    .build())
        .hardforkId(ATLANTIS);
  }

  public static ProtocolSpecBuilder aghartaDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return atlantisDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .evmBuilder(MainnetEVMs::constantinople)
        .gasCalculator(PetersburgGasCalculator::new)
        .evmBuilder(MainnetEVMs::constantinople)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .hardforkId(AGHARTA);
  }

  public static ProtocolSpecBuilder phoenixDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return aghartaDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(IstanbulGasCalculator::new)
        .evmBuilder(
            (gasCalculator, evmConfig) ->
                MainnetEVMs.istanbul(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .hardforkId(PHOENIX);
  }

  public static ProtocolSpecBuilder thanosDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return phoenixDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                MainnetBlockHeaderValidator.createPgaBlockHeaderValidator(
                    new EpochCalculator.Ecip1099EpochCalculator(), powHasher(PowAlgorithm.ETHASH)))
        .ommerHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                MainnetBlockHeaderValidator.createLegacyFeeMarketOmmerValidator(
                    new EpochCalculator.Ecip1099EpochCalculator(), powHasher(PowAlgorithm.ETHASH)))
        .hardforkId(THANOS);
  }

  public static ProtocolSpecBuilder magnetoDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return thanosDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(BerlinGasCalculator::new)
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    true,
                    chainId,
                    Set.of(TransactionType.FRONTIER, TransactionType.ACCESS_LIST)))
        .transactionReceiptFactory(
            new MainnetProtocolSpecs.BerlinTransactionReceiptFactory(enableRevertReason))
        .hardforkId(MAGNETO);
  }

  public static ProtocolSpecBuilder mystiqueDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return magnetoDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        .gasCalculator(LondonGasCalculator::new)
        .contractCreationProcessorBuilder(
            evm ->
                new ContractCreationProcessor(
                    evm, true, List.of(MaxCodeSizeRule.from(evm), PrefixCodeRule.of()), 1))
        .hardforkId(MYSTIQUE);
  }

  public static ProtocolSpecBuilder spiralDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    return mystiqueDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        // EIP-3860
        .gasCalculator(ShanghaiGasCalculator::new)
        // EIP-3855
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                ClassicEVMs.spiral(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        // EIP-3651
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.frontier())
                    .build())
        .hardforkId(SPIRAL);
  }

  public static ProtocolSpecBuilder olympiaDefinition(
      final Optional<BigInteger> chainId,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration,
      final MiningConfiguration miningConfiguration,
      final boolean isParallelTxProcessingEnabled,
      final BalConfiguration balConfiguration,
      final MetricsSystem metricsSystem) {
    final long olympiaBlockNumber =
        genesisConfigOptions.getOlympiaBlockNumber().orElse(Long.MAX_VALUE);
    final Optional<Address> treasuryAddress = genesisConfigOptions.getOlympiaTreasuryAddress();
    return spiralDefinition(
            chainId,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration,
            isParallelTxProcessingEnabled,
            balConfiguration,
            metricsSystem)
        // ECIP-1121: OsakaGasCalculator for BLS12-381, P-256, EIP-7883 MODEXP, calldata floor
        .gasCalculator(OsakaGasCalculator::new)
        // ECIP-1121: Prague-level EVM minus blob ops
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                ClassicEVMs.olympia(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration))
        // ECIP-1111: EIP-1559 London fee market (no blobs)
        .feeMarketBuilder(
            MainnetProtocolSpecs.createFeeMarket(
                olympiaBlockNumber,
                genesisConfigOptions.isZeroBaseFee(),
                genesisConfigOptions.isFixedBaseFee(),
                false,
                miningConfiguration.getMinTransactionGasPrice(),
                (blobSchedule) ->
                    FeeMarket.london(
                        olympiaBlockNumber, genesisConfigOptions.getBaseFeePerGas())))
        // ECIP-1111: EIP-1559 elastic block gas limit + EIP-7825: 30M per-TX gas cap
        .gasLimitCalculatorBuilder(
            (feeMarket, gasCalculator, blobSchedule) ->
                new OlympiaTargetingGasLimitCalculator(
                    olympiaBlockNumber, (BaseFeeMarket) feeMarket))
        // ECIP-1121: BLS12-381 + MODEXP(1024) + P256 precompiles (no KZG)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::olympia)
        // Transaction types: FRONTIER + ACCESS_LIST + EIP1559 + DELEGATE_CODE (no BLOB)
        .transactionValidatorFactoryBuilder(
            (evm, gasLimitCalculator, feeMarket) ->
                new TransactionValidatorFactory(
                    evm.getGasCalculator(),
                    gasLimitCalculator,
                    feeMarket,
                    true,
                    chainId,
                    Set.of(
                        TransactionType.FRONTIER,
                        TransactionType.ACCESS_LIST,
                        TransactionType.EIP1559,
                        TransactionType.DELEGATE_CODE),
                    Integer.MAX_VALUE))
        // EIP-1559 coinbase fee + EIP-7702 code delegation + warm coinbase
        .transactionProcessorBuilder(
            (gasCalculator,
                feeMarket,
                transactionValidatorFactory,
                contractCreationProcessor,
                messageCallProcessor) ->
                MainnetTransactionProcessor.builder()
                    .gasCalculator(gasCalculator)
                    .transactionValidatorFactory(transactionValidatorFactory)
                    .contractCreationProcessor(contractCreationProcessor)
                    .messageCallProcessor(messageCallProcessor)
                    .clearEmptyAccounts(true)
                    .warmCoinbase(true)
                    .maxStackSize(evmConfiguration.evmStackSize())
                    .feeMarket(feeMarket)
                    .coinbaseFeePriceCalculator(CoinbaseFeePriceCalculator.eip1559())
                    .codeDelegationProcessor(
                        new CodeDelegationProcessor(
                            chainId,
                            SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
                            new CodeDelegationService()))
                    .build())
        // Berlin receipt factory (supports Type-2/Type-4)
        .transactionReceiptFactory(
            new MainnetProtocolSpecs.BerlinTransactionReceiptFactory(enableRevertReason))
        // OlympiaBlockProcessor: era rewards + treasury credit
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                protocolSchedule,
                balConfig) ->
                new OlympiaBlockProcessor(
                    transactionProcessor,
                    transactionReceiptFactory,
                    blockReward,
                    miningBeneficiaryCalculator,
                    skipZeroBlockRewards,
                    genesisConfigOptions.getEcip1017EraRounds(),
                    protocolSchedule,
                    balConfig,
                    treasuryAddress))
        // Base fee market header validator with ECIP-1099 epoch calculator (60K epochs)
        .blockHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                createClassicBaseFeeMarketValidator((BaseFeeMarket) feeMarket))
        .ommerHeaderValidatorBuilder(
            (feeMarket, gasCalculator, gasLimitCalculator) ->
                createClassicBaseFeeMarketOmmerValidator((BaseFeeMarket) feeMarket))
        .blockBodyValidatorBuilder(BaseFeeBlockBodyValidator::new)
        // EIP-2935: block hash history system contract + EIP-7709 block hash lookup
        .preExecutionProcessor(new OlympiaPreExecutionProcessor())
        // EIP-7934: 8 MB block RLP size limit
        .blockValidatorBuilder(MainnetBlockValidatorBuilder::olympia)
        .hardforkId(OLYMPIA);
  }

  /**
   * Creates a base fee market block header validator using ECIP-1099 epoch calculator (60K epochs)
   * for PoW validation on ETC. This is the ETC equivalent of {@link
   * MainnetBlockHeaderValidator#createBaseFeeMarketValidator(BaseFeeMarket)}.
   */
  private static BlockHeaderValidator.Builder createClassicBaseFeeMarketValidator(
      final BaseFeeMarket baseFeeMarket) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(
            new GasLimitRangeAndDeltaValidationRule(
                5000, Long.MAX_VALUE, Optional.of(baseFeeMarket)))
        .addRule(
            new TimestampMoreRecentThanParent(MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(
            new TimestampBoundedByFutureParameter(MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new BaseFeeMarketBlockHeaderGasPriceValidationRule(baseFeeMarket))
        .addRule(
            new ProofOfWorkValidationRule(
                new EpochCalculator.Ecip1099EpochCalculator(),
                powHasher(PowAlgorithm.ETHASH),
                Optional.of(baseFeeMarket)));
  }

  private static BlockHeaderValidator.Builder createClassicBaseFeeMarketOmmerValidator(
      final BaseFeeMarket baseFeeMarket) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(
            new GasLimitRangeAndDeltaValidationRule(
                5000, Long.MAX_VALUE, Optional.of(baseFeeMarket)))
        .addRule(
            new TimestampMoreRecentThanParent(MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(
            new ProofOfWorkValidationRule(
                new EpochCalculator.Ecip1099EpochCalculator(),
                powHasher(PowAlgorithm.ETHASH),
                Optional.of(baseFeeMarket)))
        .addRule(new BaseFeeMarketBlockHeaderGasPriceValidationRule(baseFeeMarket));
  }
}
