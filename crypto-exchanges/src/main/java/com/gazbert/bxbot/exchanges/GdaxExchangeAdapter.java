
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

import com.gazbert.crypto.exchange.api.AuthenticationConfig;
import com.gazbert.crypto.exchange.api.ExchangeAdapter;
import com.gazbert.crypto.exchange.api.ExchangeConfig;
import com.gazbert.crypto.exchange.api.OtherConfig;
import com.gazbert.crypto.exchanges.trading.api.impl.BalanceInfoImpl;
import com.gazbert.crypto.exchanges.trading.api.impl.MarketOrderBookImpl;
import com.gazbert.crypto.exchanges.trading.api.impl.MarketOrderImpl;
import com.gazbert.crypto.exchanges.trading.api.impl.OpenOrderImpl;
import com.gazbert.crypto.exchanges.trading.api.impl.TickerImpl;
import com.gazbert.crypto.trading.api.BalanceInfo;
import com.gazbert.crypto.trading.api.ExchangeNetworkException;
import com.gazbert.crypto.trading.api.MarketOrder;
import com.gazbert.crypto.trading.api.MarketOrderBook;
import com.gazbert.crypto.trading.api.OpenOrder;
import com.gazbert.crypto.trading.api.OrderType;
import com.gazbert.crypto.trading.api.Ticker;
import com.gazbert.crypto.trading.api.TradingApi;
import com.gazbert.crypto.trading.api.TradingApiException;
import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <strong>GDAX exchange has been superseded by Coinbase Pro: https://pro.coinbase.com/</strong>
 *
 * <p>DO NOT USE: See https://github.com/gazbert/crypto/pull/120
 *
 * <p>Exchange Adapter for integrating with the GDAX (formerly Coinbase) exchange. The GDAX API is
 * documented <a href="https://www.gdax.com/">here</a>.
 *
 * <p><strong> DISCLAIMER: This Exchange Adapter is provided as-is; it might have bugs in it and you
 * could lose money. Despite running live on GDAX, it has only been unit tested up until the point
 * of calling the {@link #sendPublicRequestToExchange(String, Map)} and {@link
 * #sendAuthenticatedRequestToExchange(String, String, Map)} methods. Use it at our own risk!
 * </strong>
 *
 * <p>This adapter only supports the GDAX <a href="https://docs.gdax.com/#api">REST API</a>. The
 * design of the API and documentation is excellent.
 *
 * <p>The adapter currently only supports <a href="https://docs.gdax.com/#place-a-new-order">Limit
 * Orders</a>. It was originally developed and tested for BTC-GBP market, but it should work for
 * BTC-USD.
 *
 * <p>Exchange fees are loaded from the exchange.yaml file on startup; they are not fetched from the
 * exchange at runtime as the GDAX REST API does not support this. The fees are used across all
 * markets. Make sure you keep an eye on the <a href="https://docs.gdax.com/#fees">exchange fees</a>
 * and update the config accordingly.
 *
 * <p>NOTE: GDAX requires all price values to be limited to 2 decimal places when creating orders.
 * This adapter truncates any prices with more than 2 decimal places and rounds using {@link
 * java.math.RoundingMode#HALF_EVEN}, E.g. 250.176 would be sent to the exchange as 250.18.
 *
 * <p>The Exchange Adapter is <em>not</em> thread safe. It expects to be called using a single
 * thread in order to preserve trade execution order. The {@link URLConnection} achieves this by
 * blocking/waiting on the input stream (response) for each API call.
 *
 * <p>The {@link TradingApi} calls will throw a {@link ExchangeNetworkException} if a network error
 * occurs trying to connect to the exchange. A {@link TradingApiException} is thrown for
 * <em>all</em> other failures.
 *
 * @author gazbert
 * @since 1.0
 * @deprecated #120 : GDAX exchange has been superseded by Coinbase Pro: https://pro.coinbase.com/ -
 *     this adapter will be removed in next release.
 */
@Deprecated(forRemoval = true)
public final class GdaxExchangeAdapter extends AbstractExchangeAdapter implements ExchangeAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final String PUBLIC_API_BASE_URL = "https://api.gdax.com/";
  private static final String AUTHENTICATED_API_URL = PUBLIC_API_BASE_URL;

  private static final String UNEXPECTED_ERROR_MSG =
      "Unexpected error has occurred in GDAX Exchange Adapter. ";
  private static final String UNEXPECTED_IO_ERROR_MSG =
      "Failed to connect to Exchange due to unexpected IO error.";

  private static final String PRODUCTS = "products/";
  private static final String PRICE = "price";

  private static final String PASSPHRASE_PROPERTY_NAME = "passphrase";
  private static final String KEY_PROPERTY_NAME = "key";
  private static final String SECRET_PROPERTY_NAME = "secret";

  private static final String BUY_FEE_PROPERTY_NAME = "buy-fee";
  private static final String SELL_FEE_PROPERTY_NAME = "sell-fee";

  private BigDecimal buyFeePercentage;
  private BigDecimal sellFeePercentage;

  private String passphrase = "";
  private String key = "";
  private String secret = "";

  private Mac mac;
  private boolean initializedMacAuthentication = false;

  private Gson gson;
