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
package org.hyperledger.besu.cli.config;

import org.hyperledger.besu.config.DiscoveryOptions;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.NetworkDefinition;
import org.hyperledger.besu.ethereum.p2p.discovery.dns.EthereumNodeRecord;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * The Eth network config.
 *
 * @param genesisConfig Genesis Config File
 * @param networkId Network Id
 * @param enodeBootNodes Enode Boot Nodes
 * @param enrBootNodes ENR Boot Nodes
 * @param dnsDiscoveryUrls DNS Discovery URLs, in priority order (primary first)
 */
public record EthNetworkConfig(
    GenesisConfig genesisConfig,
    BigInteger networkId,
    List<EnodeURLImpl> enodeBootNodes,
    List<EthereumNodeRecord> enrBootNodes,
    List<String> dnsDiscoveryUrls) {

  /**
   * Validate parameters on new record creation
   *
   * @param genesisConfig the genesis config
   * @param networkId the network id
   * @param enodeBootNodes the Enode boot nodes
   * @param enrBootNodes the ENR boot nodes
   * @param dnsDiscoveryUrls the dns discovery urls
   */
  @SuppressWarnings(
      "MethodInputParametersMustBeFinal") // needed since record constructors are not yet supported
  public EthNetworkConfig {
    Objects.requireNonNull(genesisConfig);
    Objects.requireNonNull(enodeBootNodes);
    Objects.requireNonNull(enrBootNodes);
    dnsDiscoveryUrls = dnsDiscoveryUrls != null ? dnsDiscoveryUrls : List.of();
  }

  /**
   * Gets network config.
   *
   * @param networkDefinition the network name
   * @return the network config
   */
  public static EthNetworkConfig getNetworkConfig(final NetworkDefinition networkDefinition) {
    final URL genesisSource = jsonConfigSource(networkDefinition.getGenesisFile());
    final GenesisConfig genesisConfig = GenesisConfig.fromSource(genesisSource);
    final DiscoveryOptions discoveryOptions =
        genesisConfig.getConfigOptions().getDiscoveryOptions();

    final List<EnodeURLImpl> enodeBootNodes =
        discoveryOptions
            .getBootNodes()
            .map(nodes -> nodes.stream().map(EnodeURLImpl::fromString).toList())
            .orElse(List.of());

    final List<EthereumNodeRecord> enrBootNodes =
        discoveryOptions
            .getV5BootNodes()
            .map(nodes -> nodes.stream().map(EthereumNodeRecord::fromEnr).toList())
            .orElse(List.of());

    return new EthNetworkConfig(
        genesisConfig,
        networkDefinition.getNetworkId(),
        enodeBootNodes,
        enrBootNodes,
        discoveryOptions.getDiscoveryDnsUrls());
  }

  private static URL jsonConfigSource(final String resourceName) {
    return EthNetworkConfig.class.getResource(resourceName);
  }

  /**
   * Json config string.
   *
   * @param network the named network
   * @return the json string
   */
  public static String jsonConfig(final NetworkDefinition network) {
    try (final InputStream genesisFileInputStream =
        EthNetworkConfig.class.getResourceAsStream(network.getGenesisFile())) {
      return new String(genesisFileInputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | NullPointerException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The type Builder. */
  public static class Builder {

    private List<String> dnsDiscoveryUrls;
    private GenesisConfig genesisConfig;
    private BigInteger networkId;
    private List<EnodeURLImpl> enodeBootNodes;
    private List<EthereumNodeRecord> enrBootNodes;

    /**
     * Instantiates a new Builder.
     *
     * @param ethNetworkConfig the eth network config
     */
    public Builder(final EthNetworkConfig ethNetworkConfig) {
      this.genesisConfig = ethNetworkConfig.genesisConfig;
      this.networkId = ethNetworkConfig.networkId;
      this.enodeBootNodes = ethNetworkConfig.enodeBootNodes;
      this.enrBootNodes = ethNetworkConfig.enrBootNodes;
      this.dnsDiscoveryUrls = ethNetworkConfig.dnsDiscoveryUrls;
    }

    /**
     * Sets genesis config file.
     *
     * @param genesisConfig the genesis config
     * @return this builder
     */
    public Builder setGenesisConfig(final GenesisConfig genesisConfig) {
      this.genesisConfig = genesisConfig;
      return this;
    }

    /**
     * Sets network id.
     *
     * @param networkId the network id
     * @return this builder
     */
    public Builder setNetworkId(final BigInteger networkId) {
      this.networkId = networkId;
      return this;
    }

    /**
     * Sets boot nodes.
     *
     * @param enodeBootNodes the boot nodes
     * @return this builder
     */
    public Builder setEnodeBootNodes(final List<EnodeURLImpl> enodeBootNodes) {
      this.enodeBootNodes = enodeBootNodes;
      return this;
    }

    /**
     * Sets ENR boot nodes.
     *
     * @param enrBootNodes the boot nodes
     * @return this builder
     */
    public Builder setEnrBootNodes(final List<EthereumNodeRecord> enrBootNodes) {
      this.enrBootNodes = enrBootNodes;
      return this;
    }

    /**
     * Sets dns discovery urls.
     *
     * @param dnsDiscoveryUrls the dns discovery urls, in priority order
     * @return this builder
     */
    public Builder setDnsDiscoveryUrls(final List<String> dnsDiscoveryUrls) {
      this.dnsDiscoveryUrls = dnsDiscoveryUrls;
      return this;
    }

    /**
     * Build eth network config.
     *
     * @return the eth network config
     */
    public EthNetworkConfig build() {
      return new EthNetworkConfig(
          genesisConfig, networkId, enodeBootNodes, enrBootNodes, dnsDiscoveryUrls);
    }
  }
}
