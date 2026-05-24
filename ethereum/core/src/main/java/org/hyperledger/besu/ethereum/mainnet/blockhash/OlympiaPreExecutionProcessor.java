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
package org.hyperledger.besu.ethereum.mainnet.blockhash;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.ethereum.mainnet.systemcall.InvalidSystemCallAddressException;
import org.hyperledger.besu.ethereum.mainnet.systemcall.SystemCallProcessor;
import org.hyperledger.besu.ethereum.vm.Eip7709BlockHashLookup;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EIP-2935 pre-execution processor for ETC Olympia hard fork. Stores parent block hashes in a
 * system contract with 8191-slot rotating storage, enabling smart contracts to access historical
 * block hashes beyond the 256-block BLOCKHASH window.
 *
 * <p>Extends {@link FrontierPreExecutionProcessor} directly (NOT {@link
 * CancunPreExecutionProcessor}) because ETC is PoW and has no beacon chain — EIP-4788 beacon root
 * storage does not apply.
 *
 * <p>Combines EIP-2935 block hash storage with EIP-7709 block hash lookup (reads from system
 * contract storage instead of chain traversal).
 *
 * <p>At fork activation, deploys the system contract if it does not already exist in state. This
 * matches core-geth behavior for live network forks where the contract is not in the genesis alloc.
 */
public class OlympiaPreExecutionProcessor extends FrontierPreExecutionProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(OlympiaPreExecutionProcessor.class);

  private static final Address HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0x0000f90827f1c53a10cb7a02335b175320002935");

  /** EIP-2935 system contract bytecode — same as Prague/core-geth Olympia. */
  private static final Bytes HISTORY_STORAGE_CODE =
      Bytes.fromHexString(
          "0x3373fffffffffffffffffffffffffffffffffffffffe14604657602036036042575f35600143038111604257611fff81430311604257611fff9006545f5260205ff35b5f5ffd5b5f35611fff60014303065500");

  protected final Address historyStorageAddress;

  /** Constructs an OlympiaPreExecutionProcessor with the default history storage address. */
  public OlympiaPreExecutionProcessor() {
    this(HISTORY_STORAGE_ADDRESS);
  }

  /**
   * Constructs an OlympiaPreExecutionProcessor with a specified history storage address. This
   * constructor is primarily used for testing.
   *
   * @param historyStorageAddress the address of the contract storing the history
   */
  @VisibleForTesting
  public OlympiaPreExecutionProcessor(final Address historyStorageAddress) {
    this.historyStorageAddress = historyStorageAddress;
  }

  @Override
  public Void process(
      final BlockProcessingContext context,
      final Optional<AccessLocationTracker> accessLocationTracker) {
    // Deploy EIP-2935 contract at fork activation if it doesn't exist in state.
    // This handles live network forks (Mordor/mainnet) where the contract is not in genesis alloc.
    // After the first post-fork block, the contract exists and this check is a no-op.
    ensureContractDeployed(context.getWorldState());

    // Store parent block hash via system call (EIP-2935)
    final SystemCallProcessor processor =
        new SystemCallProcessor(context.getProtocolSpec().getTransactionProcessor());
    final Bytes inputData = context.getBlockHeader().getParentHash().getBytes();
    try {
      processor.process(historyStorageAddress, context, inputData, accessLocationTracker);
    } catch (final InvalidSystemCallAddressException e) {
      LOG.warn("EIP-2935: invalid system call address: {}", historyStorageAddress);
    }
    return null;
  }

  /**
   * EIP-7709: BLOCKHASH reads from system contract storage instead of chain traversal. More
   * efficient and enables light client support.
   */
  @Override
  public BlockHashLookup createBlockHashLookup(
      final Blockchain blockchain, final ProcessableBlockHeader blockHeader) {
    return new Eip7709BlockHashLookup(historyStorageAddress);
  }

  @Override
  public Optional<Address> getHistoryContract() {
    return Optional.of(historyStorageAddress);
  }

  // No getBeaconRootsContract() override — ETC is PoW, no beacon chain.
  // Inherits default Optional.empty() from PreExecutionProcessor interface.

  private void ensureContractDeployed(final MutableWorldState worldState) {
    final WorldUpdater updater = worldState.updater();
    if (updater.get(historyStorageAddress) == null) {
      LOG.info(
          "EIP-2935: deploying history storage contract at fork activation: {}",
          historyStorageAddress);
      final MutableAccount account = updater.createAccount(historyStorageAddress);
      account.setNonce(1);
      account.setCode(HISTORY_STORAGE_CODE);
      updater.commit();
    }
  }
}
