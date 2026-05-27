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

public interface EpochCalculator {
  public long epochStartBlock(final long block);

  public long cacheEpoch(final long block);

  final class DefaultEpochCalculator implements EpochCalculator {

    @Override
    public long epochStartBlock(final long block) {
      return cacheEpoch(block) * EthHash.EPOCH_LENGTH + 1;
    }

    @Override
    public long cacheEpoch(final long block) {
      return Long.divideUnsigned(block, EthHash.EPOCH_LENGTH);
    }
  }

  // ECIP-1099: doubles ETChash epoch length from 30K to 60K blocks at activation
  final class Ecip1099EpochCalculator implements EpochCalculator {
    private final long ecip1099FBlock;

    public Ecip1099EpochCalculator(final long ecip1099FBlock) {
      this.ecip1099FBlock = ecip1099FBlock;
    }

    @Override
    public long cacheEpoch(final long block) {
      if (block < ecip1099FBlock) {
        return Long.divideUnsigned(block, EthHash.EPOCH_LENGTH);
      }
      long preTransitionEpochs = Long.divideUnsigned(ecip1099FBlock, EthHash.EPOCH_LENGTH);
      long blocksAfterTransition = block - ecip1099FBlock;
      return preTransitionEpochs
          + Long.divideUnsigned(blocksAfterTransition, EthHash.EPOCH_LENGTH * 2);
    }

    @Override
    public long epochStartBlock(final long block) {
      if (block < ecip1099FBlock) {
        return cacheEpoch(block) * EthHash.EPOCH_LENGTH + 1;
      }
      long epochsSinceTransition =
          Long.divideUnsigned(block - ecip1099FBlock, EthHash.EPOCH_LENGTH * 2);
      return ecip1099FBlock + epochsSinceTransition * EthHash.EPOCH_LENGTH * 2 + 1;
    }
  }
}
