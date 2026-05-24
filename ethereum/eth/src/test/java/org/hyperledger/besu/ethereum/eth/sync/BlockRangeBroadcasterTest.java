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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessage;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.PeerReputation;
import org.hyperledger.besu.ethereum.eth.messages.BlockRangeUpdateMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BlockRangeBroadcasterTest {

  @Mock private Blockchain blockchain;
  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private EthPeer ethPeerWithSupport;
  @Mock private EthPeer ethPeerWithoutSupport;

  private BlockRangeBroadcaster blockRangeBroadcaster;
  private BlockRangeBroadcaster powBroadcaster;

  @BeforeEach
  public void setup() {
    when(ethContext.getEthMessages()).thenReturn(mock(EthMessages.class));
    when(ethContext.getScheduler()).thenReturn(mock(EthScheduler.class));
    blockRangeBroadcaster = spy(new BlockRangeBroadcaster(ethContext, blockchain));
    powBroadcaster = new BlockRangeBroadcaster(ethContext, blockchain, true);
  }

  @Test
  public void shouldBroadcastBlockRange() throws PeerConnection.PeerNotConnected {
    setupPeers(ethPeerWithSupport);
    when(ethPeerWithSupport.hasSupportForMessage(EthProtocolMessages.BLOCK_RANGE_UPDATE))
        .thenReturn(true);
    broadcastBlockRange();
    verify(ethPeerWithSupport, times(1)).send(any(BlockRangeUpdateMessage.class));
  }

  @Test
  public void shouldSendBlockRangeOnlyToEth69Peers() throws PeerConnection.PeerNotConnected {
    setupPeers(ethPeerWithoutSupport, ethPeerWithSupport);
    when(ethPeerWithSupport.hasSupportForMessage(EthProtocolMessages.BLOCK_RANGE_UPDATE))
        .thenReturn(true);
    when(ethPeerWithoutSupport.hasSupportForMessage(EthProtocolMessages.BLOCK_RANGE_UPDATE))
        .thenReturn(false);
    broadcastBlockRange();
    verify(ethPeerWithoutSupport, never()).send(any(BlockRangeUpdateMessage.class));
    verify(ethPeerWithSupport, times(1)).send(any(BlockRangeUpdateMessage.class));
  }

  private void setupPeers(final EthPeer... peers) {
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.of(peers).map(EthPeerImmutableAttributes::from));
    for (EthPeer ethPeer : peers) {
      ChainState chainState = Mockito.mock(ChainState.class);

      Mockito.when(ethPeer.chainState()).thenReturn(chainState);
      Mockito.when(chainState.getEstimatedHeight()).thenReturn(0L);
      Mockito.when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));
      Mockito.when(ethPeer.getReputation()).thenReturn(new PeerReputation());
      PeerConnection connection = mock(PeerConnection.class);
      Mockito.when(ethPeer.getConnection()).thenReturn(connection);
    }
  }

  private void broadcastBlockRange() {
    long startBlockNumber = 0L;
    long endBlockNumber = 1L;
    Hash endBlockHash = Hash.ZERO;
    blockRangeBroadcaster.broadcastBlockRange(startBlockNumber, endBlockNumber, endBlockHash);
  }

  @Test
  public void shouldSendCorrectBlockRangeToPeers() {
    final long expectedEarliestBlock = 10L;
    final long expectedLatestBlockNumber = 20L;
    final Hash expectedBlockHash = Hash.wrap(Bytes32.fromHexString("0x0B"));

    setupPeers(ethPeerWithoutSupport, ethPeerWithSupport);
    setupBlockchain(expectedEarliestBlock, expectedLatestBlockNumber, expectedBlockHash);

    blockRangeBroadcaster.broadcastBlockRange();
    verify(blockRangeBroadcaster, times(1))
        .broadcastBlockRange(expectedEarliestBlock, expectedLatestBlockNumber, expectedBlockHash);
  }

  private void setupBlockchain(
      final long earliestBlockNumber, final long latestBlockNumber, final Hash expectedBlockHash) {
    final BlockHeader latestBlockHeader = mock(BlockHeader.class);
    when(latestBlockHeader.getNumber()).thenReturn(latestBlockNumber);
    when(latestBlockHeader.getHash()).thenReturn(expectedBlockHash);
    when(blockchain.getEarliestBlockNumber()).thenReturn(Optional.of(earliestBlockNumber));
    when(blockchain.getChainHeadHeader()).thenReturn(latestBlockHeader);
  }

  @Test
  public void shouldNotDisconnectIfLatestBlockNumberIsGreaterThanEarliest() {
    final EthPeer peer = mock(EthPeer.class);
    handleBlockRangeUpdateMessage(peer, 0L, 1L);
    verify(peer, never()).disconnect(any());
  }

  @Test
  public void shouldNotDisconnectIfLatestBlockNumberIsEqualToEarliest() {
    final EthPeer peer = mock(EthPeer.class);
    handleBlockRangeUpdateMessage(peer, 0L, 0L);
    verify(peer, never()).disconnect(any());
  }

  @Test
  public void shouldDisconnectIfLatestBlockNumberIsLessThanEarliest() {
    final EthPeer peer = mock(EthPeer.class);
    handleBlockRangeUpdateMessage(peer, 1L, 0L);
    verify(peer)
        .disconnect(DisconnectMessage.DisconnectReason.SUBPROTOCOL_TRIGGERED_INVALID_BLOCK_RANGE);
  }

  private void handleBlockRangeUpdateMessage(
      final EthPeer peer, final long earliestBlockNumber, final long latestBlockNumber) {
    BlockRangeUpdateMessage message =
        BlockRangeUpdateMessage.create(earliestBlockNumber, latestBlockNumber, Hash.ZERO);
    EthMessage ethMessage = new EthMessage(peer, message);
    blockRangeBroadcaster.handleBlockRangeUpdateMessage(ethMessage);
  }

  // --- PoW ETH69 TD resolution tests ---

  @Test
  public void handleBlockRangeUpdate_updatesChainWeightViaDbLookup_whenPoWAndHashKnown() {
    final EthPeer powPeer = mock(EthPeer.class);
    final ChainState chainState = mock(ChainState.class);
    when(powPeer.chainState()).thenReturn(chainState);
    when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));

    final Difficulty realTD = Difficulty.of(new BigInteger("24244691155597214264244"));
    final Hash knownHash =
        Hash.wrap(
            org.apache.tuweni.bytes.Bytes32.fromHexString(
                "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    when(blockchain.getTotalDifficultyByHash(knownHash)).thenReturn(Optional.of(realTD));

    final BlockRangeUpdateMessage msg = BlockRangeUpdateMessage.create(0L, 24_565_949L, knownHash);
    powBroadcaster.handleBlockRangeUpdateMessage(new EthMessage(powPeer, msg));

    verify(chainState).statusReceived(knownHash, realTD);
  }

  @Test
  public void handleBlockRangeUpdate_doesNotResolveChainWeight_whenPoSChain() {
    final EthPeer peer = mock(EthPeer.class);

    // blockRangeBroadcaster has isPoWChain=false — peer.chainState() must never be accessed
    final BlockRangeUpdateMessage msg = BlockRangeUpdateMessage.create(0L, 1000L, Hash.ZERO);
    blockRangeBroadcaster.handleBlockRangeUpdateMessage(new EthMessage(peer, msg));

    verify(peer, never()).chainState();
  }

  @Test
  public void handleBlockRangeUpdate_doesNotDowngrade_whenResolvedTdIsLower() {
    final EthPeer powPeer = mock(EthPeer.class);
    final ChainState chainState = mock(ChainState.class);
    when(powPeer.chainState()).thenReturn(chainState);

    final Difficulty highTD = Difficulty.of(new BigInteger("24244691155597214264244"));
    final Difficulty lowerTD = Difficulty.of(new BigInteger("10000000"));
    when(chainState.getEstimatedTotalDifficulty()).thenReturn(highTD);

    final Hash hash =
        Hash.wrap(
            org.apache.tuweni.bytes.Bytes32.fromHexString(
                "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    when(blockchain.getTotalDifficultyByHash(hash)).thenReturn(Optional.of(lowerTD));

    final BlockRangeUpdateMessage msg = BlockRangeUpdateMessage.create(0L, 1000L, hash);
    powBroadcaster.handleBlockRangeUpdateMessage(new EthMessage(powPeer, msg));

    verify(chainState, never()).statusReceived(any(Hash.class), any(Difficulty.class));
  }

  @Test
  public void handleBlockRangeUpdate_usesProportionalScaling_whenHashAndNumberMiss() {
    final EthPeer powPeer = mock(EthPeer.class);
    final ChainState chainState = mock(ChainState.class);
    when(powPeer.chainState()).thenReturn(chainState);
    when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));

    final Difficulty ourTD = Difficulty.of(new BigInteger("24244691155597214264244"));
    final long ourBestNum = 24_565_949L;
    final long peerBlockNum = 24_566_100L;
    final Hash unknownHash =
        Hash.wrap(
            org.apache.tuweni.bytes.Bytes32.fromHexString(
                "0xabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd"));

    // Build ChainHead before entering any when() context to avoid nested-mock issues
    final BlockHeader headHeader = mock(BlockHeader.class);
    when(headHeader.getHash()).thenReturn(Hash.ZERO);
    final ChainHead ourChainHead = new ChainHead(headHeader, ourTD, ourBestNum);

    // Stub getChainHeadHeader() for the marginal-rate Tier-3 formula (ourCurrentDiff)
    final BlockHeader chainHeadHeader = mock(BlockHeader.class);
    when(chainHeadHeader.getDifficulty()).thenReturn(Difficulty.of(2_500_000_000_000L));
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadHeader);

    // Tier 1 and Tier 2 both miss → Tier 3 (marginal-rate) fires
    when(blockchain.getTotalDifficultyByHash(any())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(peerBlockNum)).thenReturn(Optional.empty());
    when(blockchain.getChainHead()).thenReturn(ourChainHead);
    when(blockchain.getChainHeadBlockNumber()).thenReturn(ourBestNum);

    final BlockRangeUpdateMessage msg =
        BlockRangeUpdateMessage.create(0L, peerBlockNum, unknownHash);
    powBroadcaster.handleBlockRangeUpdateMessage(new EthMessage(powPeer, msg));

    verify(chainState).statusReceived(eq(unknownHash), any(Difficulty.class));
  }

  @Test
  public void handleBlockRangeUpdate_proportionalScalingTd_matchesMarginalRateFormula() {
    final EthPeer powPeer = mock(EthPeer.class);
    final ChainState chainState = mock(ChainState.class);
    when(powPeer.chainState()).thenReturn(chainState);
    when(chainState.getEstimatedTotalDifficulty()).thenReturn(Difficulty.of(0));

    // Realistic ETC mainnet anchor values
    final Difficulty ourTD = Difficulty.of(new BigInteger("24244691155597214264244"));
    final long ourBestNum = 24_565_949L;
    final long ourCurrentDiffValue = 2_500_000_000_000L;
    final long peerBlockNum = 24_566_100L; // 151 blocks ahead

    final BlockHeader headHeader = mock(BlockHeader.class);
    when(headHeader.getHash()).thenReturn(Hash.ZERO);
    final ChainHead ourChainHead = new ChainHead(headHeader, ourTD, ourBestNum);

    final BlockHeader chainHeadHeader = mock(BlockHeader.class);
    when(chainHeadHeader.getDifficulty()).thenReturn(Difficulty.of(ourCurrentDiffValue));
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadHeader);

    when(blockchain.getTotalDifficultyByHash(any())).thenReturn(Optional.empty());
    when(blockchain.getBlockHeader(peerBlockNum)).thenReturn(Optional.empty());
    when(blockchain.getChainHead()).thenReturn(ourChainHead);
    when(blockchain.getChainHeadBlockNumber()).thenReturn(ourBestNum);

    final Hash unknownHash =
        Hash.wrap(
            org.apache.tuweni.bytes.Bytes32.fromHexString(
                "0x1234123412341234123412341234123412341234123412341234123412341234"));
    final BlockRangeUpdateMessage msg =
        BlockRangeUpdateMessage.create(0L, peerBlockNum, unknownHash);
    powBroadcaster.handleBlockRangeUpdateMessage(new EthMessage(powPeer, msg));

    // Expected: ourTD + ourCurrentDiff * gap * 9999/10000
    final long gap = peerBlockNum - ourBestNum; // 151
    final BigInteger expectedTd =
        ourTD
            .getAsBigInteger()
            .add(
                BigInteger.valueOf(ourCurrentDiffValue)
                    .multiply(BigInteger.valueOf(gap))
                    .multiply(BigInteger.valueOf(9999L))
                    .divide(BigInteger.valueOf(10000L)));
    verify(chainState).statusReceived(unknownHash, Difficulty.of(expectedTd));
  }
}
