/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 gazbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.gazbert.crypto.core.config.exchange;

import com.gazbert.crypto.domain.exchange.ExchangeConfig;
import com.gazbert.crypto.domain.exchange.NetworkConfig;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Util class for building the Exchange API config.
 *
 * @author gazbert
 */
public final class ExchangeApiConfigBuilder {

  private static final Logger LOG = LogManager.getLogger();

  private ExchangeApiConfigBuilder() {
  }

  /** Builds Exchange API config. */
  public static ExchangeConfigImpl buildConfig(ExchangeConfig exchangeConfig) {

    final ExchangeConfigImpl exchangeApiConfig = new ExchangeConfigImpl();
    exchangeApiConfig.setExchangeName(exchangeConfig.getName());
    exchangeApiConfig.setExchangeAdapter(exchangeConfig.getAdapter());

    final NetworkConfig networkConfig = exchangeConfig.getNetworkConfig();
    if (networkConfig != null) {
      final NetworkConfigImpl exchangeApiNetworkConfig = new NetworkConfigImpl();
      exchangeApiNetworkConfig.setConnectionTimeout(networkConfig.getConnectionTimeout());

      final List<Integer> nonFatalErrorCodes = networkConfig.getNonFatalErrorCodes();
      if (nonFatalErrorCodes != null && !nonFatalErrorCodes.isEmpty()) {
        exchangeApiNetworkConfig.setNonFatalErrorCodes(nonFatalErrorCodes);
      } else {
        LOG.info(
            () ->
                "No (optional) NetworkConfiguration NonFatalErrorCodes have been set for "
                    + "Exchange Adapter: "
                    + exchangeConfig.getAdapter());
      }

      final List<String> nonFatalErrorMessages = networkConfig.getNonFatalErrorMessages();
      if (nonFatalErrorMessages != null && !nonFatalErrorMessages.isEmpty()) {
        exchangeApiNetworkConfig.setNonFatalErrorMessages(nonFatalErrorMessages);
      } else {
        LOG.info(
            () ->
                "No (optional) NetworkConfiguration NonFatalErrorMessages have been set for "
                    + "Exchange Adapter: "
