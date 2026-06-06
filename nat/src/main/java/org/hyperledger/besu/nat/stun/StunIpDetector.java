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
package org.hyperledger.besu.nat.stun;

import org.hyperledger.besu.nat.core.IpDetector;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the node's external IP address via RFC 5389 STUN Binding Request/Response. Parses the
 * XOR-MAPPED-ADDRESS attribute to determine the NAT-mapped public address.
 *
 * <p>Mirrors the strategy used by go-ethereum's {@code --nat=stun} option (p2p/nat/nat.go).
 */
public class StunIpDetector implements IpDetector {

  private static final Logger LOG = LoggerFactory.getLogger(StunIpDetector.class);

  // Public STUN servers — same set used by go-ethereum (stun.l.google.com is primary)
  private static final List<String> STUN_SERVERS =
      List.of("stun.l.google.com:19302", "stun1.l.google.com:19302", "stun.cloudflare.com:3478");

  private static final int TIMEOUT_MS = 3000;

  /** Default constructor */
  public StunIpDetector() {}

  @Override
  public Optional<String> detectAdvertisedIp() throws Exception {
    for (final String server : STUN_SERVERS) {
      final List<String> parts = Splitter.on(':').limit(2).splitToList(server);
      final String host = parts.get(0);
      final int port = Integer.parseInt(parts.get(1));
      try {
        final Optional<String> result = stunProbe(host, port);
        if (result.isPresent()) {
          LOG.debug("STUN server {} returned external IP: {}", server, result.get());
          return result;
        }
      } catch (final Exception e) {
        LOG.debug("STUN probe to {} failed: {}", server, e.getMessage());
      }
    }
    return Optional.empty();
  }

  private Optional<String> stunProbe(final String host, final int port) throws Exception {
    try (final DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(TIMEOUT_MS);

      // RFC 5389 §6: Binding Request — 20-byte fixed header, zero body attributes
      final byte[] request = new byte[20];
      final ByteBuffer hdr = ByteBuffer.wrap(request);
      hdr.putShort((short) 0x0001); // Message Type: Binding Request
      hdr.putShort((short) 0x0000); // Message Length: 0 (no body attributes)
      hdr.putInt(0x2112A442); // Magic Cookie (mandatory per RFC 5389)
      // 12-byte Transaction ID: all zeros (we accept any matching response)

      final InetAddress serverAddr = InetAddress.getByName(host);
      socket.send(new DatagramPacket(request, 20, serverAddr, port));

      final byte[] buf = new byte[1024];
      final DatagramPacket recv = new DatagramPacket(buf, buf.length);
      socket.receive(recv);

      return parseXorMappedAddress(buf, recv.getLength());
    }
  }

  /**
   * Parses the XOR-MAPPED-ADDRESS attribute (type 0x0020) from a STUN Binding Response. Returns the
   * de-XORed IPv4 address string, or empty if the attribute is absent or not IPv4.
   */
  private Optional<String> parseXorMappedAddress(final byte[] buf, final int len) throws Exception {
    final ByteBuffer resp = ByteBuffer.wrap(buf, 0, len);

    final int msgType = resp.getShort() & 0xFFFF;
    if (msgType != 0x0101) { // Must be Binding Response (success)
      return Optional.empty();
    }

    final int msgLen = resp.getShort() & 0xFFFF;
    resp.position(20); // skip 4-byte magic cookie + 12-byte transaction ID

    final int bodyEnd = 20 + msgLen;
    while (resp.position() < bodyEnd) {
      final int attrType = resp.getShort() & 0xFFFF;
      final int attrLen = resp.getShort() & 0xFFFF;
      final int attrStart = resp.position();

      if (attrType == 0x0020) { // XOR-MAPPED-ADDRESS
        resp.get(); // reserved byte
        final int family = resp.get() & 0xFF;
        if (family == 0x01) { // IPv4
          resp.getShort(); // XOR-mapped port (unused — IP only)
          final int xorAddr = resp.getInt() ^ 0x2112A442;
          final byte[] addrBytes = ByteBuffer.allocate(4).putInt(xorAddr).array();
          return Optional.of(InetAddress.getByAddress(addrBytes).getHostAddress());
        }
      }

      // Advance to the next attribute (padded to 4-byte boundary)
      resp.position(attrStart + ((attrLen + 3) & ~3));
    }

    return Optional.empty();
  }
}
