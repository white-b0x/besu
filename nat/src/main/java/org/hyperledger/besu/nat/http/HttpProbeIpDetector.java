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
package org.hyperledger.besu.nat.http;

import org.hyperledger.besu.nat.core.IpDetector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the node's external IP address by querying a public HTTP endpoint. Returns the raw
 * plain-text response body trimmed as the IP address string.
 *
 * <p>Mirrors the strategy used by Nethermind's IP detection implementation, which probes the same
 * set of endpoints as a fallback when UPnP is unavailable.
 */
public class HttpProbeIpDetector implements IpDetector {

  private static final Logger LOG = LoggerFactory.getLogger(HttpProbeIpDetector.class);

  // Public IP probe endpoints — same services used by Nethermind
  private static final List<String> PROBE_URLS =
      List.of("https://icanhazip.com", "https://checkip.amazonaws.com", "https://api4.ipify.org");

  private static final int TIMEOUT_MS = 3000;

  /** Default constructor */
  public HttpProbeIpDetector() {}

  @Override
  public Optional<String> detectAdvertisedIp() throws Exception {
    for (final String url : PROBE_URLS) {
      try {
        final Optional<String> result = httpProbe(url);
        if (result.isPresent()) {
          LOG.debug("HTTP probe to {} returned external IP: {}", url, result.get());
          return result;
        }
      } catch (final Exception e) {
        LOG.debug("HTTP probe to {} failed: {}", url, e.getMessage());
      }
    }
    return Optional.empty();
  }

  private Optional<String> httpProbe(final String url) throws Exception {
    final HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
    conn.setConnectTimeout(TIMEOUT_MS);
    conn.setReadTimeout(TIMEOUT_MS);
    conn.setRequestMethod("GET");
    conn.setInstanceFollowRedirects(false);

    try {
      if (conn.getResponseCode() != 200) {
        return Optional.empty();
      }

      try (final BufferedReader reader =
          new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        final String line = reader.readLine();
        if (line == null || line.isBlank()) {
          return Optional.empty();
        }
        final String ip = line.trim();
        // Reject multi-word or multi-line responses — not a valid IP
        if (ip.contains(" ") || ip.contains("\n")) {
          return Optional.empty();
        }
        return Optional.of(ip);
      }
    } finally {
      conn.disconnect();
    }
  }
}
