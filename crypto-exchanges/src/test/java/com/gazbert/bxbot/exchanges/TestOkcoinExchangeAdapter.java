/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Gareth Jon Lynch
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

package com.gazbert.crypto.exchanges;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gazbert.crypto.exchange.api.AuthenticationConfig;
import com.gazbert.crypto.exchange.api.ExchangeConfig;
import com.gazbert.crypto.exchange.api.NetworkConfig;
import com.gazbert.crypto.exchange.api.OtherConfig;
import com.gazbert.crypto.trading.api.BalanceInfo;
import com.gazbert.crypto.trading.api.ExchangeNetworkException;
import com.gazbert.crypto.trading.api.MarketOrderBook;
import com.gazbert.crypto.trading.api.OpenOrder;
import com.gazbert.crypto.trading.api.OrderType;
import com.gazbert.crypto.trading.api.Ticker;
import com.gazbert.crypto.trading.api.TradingApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Tests the behaviour of the OKCoin Exchange Adapter.
 *
 * <p>DO NOT USE: See https://github.com/gazbert/crypto/issues/122
 *
 * @author gazbert
 * @deprecated #120 : The OKCoin V1 API is now deprecated and no longer works - adapter needs
 *     updating to use V3 API.
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({
    "javax.crypto.*",
    "javax.management.*",
    "com.sun.org.apache.xerces.*",
    "javax.xml.parsers.*",
    "org.xml.sax.*",
    "org.w3c.dom.*"
})
@PrepareForTest(OkCoinExchangeAdapter.class)
@Deprecated(forRemoval = true)
public class TestOkcoinExchangeAdapter extends AbstractExchangeAdapterTest {

  private static final String DEPTH_JSON_RESPONSE = "./src/test/exchange-data/okcoin/depth.json";
  private static final String USERINFO_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/userinfo.json";
  private static final String USERINFO_ERROR_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/userinfo-error.json";
  private static final String TICKER_JSON_RESPONSE = "./src/test/exchange-data/okcoin/ticker.json";
  private static final String ORDER_INFO_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/order_info.json";
  private static final String ORDER_INFO_ERROR_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/order_info-error.json";
  private static final String TRADE_BUY_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/trade_buy.json";
  private static final String TRADE_SELL_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/trade_sell.json";
  private static final String TRADE_ERROR_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/trade-error.json";
  private static final String CANCEL_ORDER_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/cancel_order.json";
  private static final String CANCEL_ORDER_ERROR_JSON_RESPONSE =
      "./src/test/exchange-data/okcoin/cancel_order-error.json";

  private static final String DEPTH = "depth.do";
  private static final String ORDER_INFO = "order_info.do";
  private static final String USERINFO = "userinfo.do";
  private static final String TICKER = "ticker.do";
  private static final String TRADE = "trade.do";
  private static final String CANCEL_ORDER = "cancel_order.do";

  private static final String MARKET_ID = "btc_usd";
  private static final BigDecimal BUY_ORDER_PRICE = new BigDecimal("200.18");
  private static final BigDecimal BUY_ORDER_QUANTITY = new BigDecimal("0.01");
  private static final BigDecimal SELL_ORDER_PRICE = new BigDecimal("300.176");
  private static final BigDecimal SELL_ORDER_QUANTITY = new BigDecimal("0.01");
  private static final String ORDER_ID_TO_CANCEL = "99671870";

  private static final String MOCKED_CREATE_REQUEST_PARAM_MAP_METHOD = "createRequestParamMap";
  private static final String MOCKED_SEND_AUTHENTICATED_REQUEST_TO_EXCHANGE_METHOD =
      "sendAuthenticatedRequestToExchange";
  private static final String MOCKED_SEND_PUBLIC_REQUEST_TO_EXCHANGE_METHOD =
      "sendPublicRequestToExchange";
  private static final String MOCKED_CREATE_REQUEST_HEADER_MAP_METHOD = "createHeaderParamMap";
  private static final String MOCKED_MAKE_NETWORK_REQUEST_METHOD = "makeNetworkRequest";

  private static final String KEY = "key123";
  private static final String SECRET = "notGonnaTellYa";
  private static final List<Integer> nonFatalNetworkErrorCodes = Arrays.asList(502, 503, 504);
  private static final List<String> nonFatalNetworkErrorMessages =
      Arrays.asList(
          "Connection refused",
          "Connection reset",
          "Remote host closed connection during handshake");

  private static final String OKCOIN_API_VERSION = "v1";
  private static final String PUBLIC_API_BASE_URL =
      "https://www.okcoin.com/api/" + OKCOIN_API_VERSION + "/";
  private static final String AUTHENTICATED_API_URL = PUBLIC_API_BASE_URL;

  private ExchangeConfig exchangeConfig;
  private AuthenticationConfig authenticationConfig;
  private NetworkConfig networkConfig;
  private OtherConfig otherConfig;

  /** Create some exchange config - the TradingEngine would normally do this. */
  @Before
  public void setupForEachTest() {
    authenticationConfig = PowerMock.createMock(AuthenticationConfig.class);
    expect(authenticationConfig.getItem("key")).andReturn(KEY);
    expect(authenticationConfig.getItem("secret")).andReturn(SECRET);

    networkConfig = PowerMock.createMock(NetworkConfig.class);
    expect(networkConfig.getConnectionTimeout()).andReturn(30);
    expect(networkConfig.getNonFatalErrorCodes()).andReturn(nonFatalNetworkErrorCodes);
    expect(networkConfig.getNonFatalErrorMessages()).andReturn(nonFatalNetworkErrorMessages);

    otherConfig = PowerMock.createMock(OtherConfig.class);
    expect(otherConfig.getItem("buy-fee")).andReturn("0.2");
    expect(otherConfig.getItem("sell-fee")).andReturn("0.2");

    exchangeConfig = PowerMock.createMock(Exch