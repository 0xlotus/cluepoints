
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Gareth Jon Lynch
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
import com.gazbert.crypto.trading.api.BalanceInfo;
import com.gazbert.crypto.trading.api.ExchangeNetworkException;
import com.gazbert.crypto.trading.api.MarketOrder;
import com.gazbert.crypto.trading.api.MarketOrderBook;
import com.gazbert.crypto.trading.api.OpenOrder;
import com.gazbert.crypto.trading.api.OrderType;
import com.gazbert.crypto.trading.api.TradingApi;
import com.gazbert.crypto.trading.api.TradingApiException;
import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
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
 * Exchange Adapter for integrating with the Gemini exchange. The Gemini API is documented <a
 * href="https://docs.gemini.com/rest-api/">here</a>.
 *
 * <p><strong> DISCLAIMER: This Exchange Adapter is provided as-is; it might have bugs in it and you
 * could lose money. Despite running live on Gemini, it has only been unit tested up until the point
 * of calling the {@link #sendPublicRequestToExchange(String)} and {@link
 * #sendAuthenticatedRequestToExchange(String, Map)} methods. Use it at our own risk! </strong>
 *
 * <p>The adapter only supports the REST implementation of the <a
 * href="https://docs.gemini.com/rest-api/">Trading API</a>.
 *
 * <p>Gemini operates <a href="https://docs.gemini.com/rest-api/#rate-limits">rate limits</a>:
 *
 * <ul>
 *   <li>For public API entry points, they limit requests to 120 requests per minute, and recommend
 *       that you do not exceed 1 request per second.
 *   <li>For private API entry points, they limit requests to 600 requests per minute, and recommend
 *       that you not exceed 5 requests per second.
 * </ul>
 *
 * <p>Exchange fees are loaded from the exchange.yaml file on startup; they are not fetched from the
 * exchange at runtime as the Gemini REST API does not support this. The fees are used across all
 * markets. Make sure you keep an eye on the <a href="https://gemini.com/fee-schedule/">exchange
 * fees</a> and update the config accordingly.
 *
 * <p>NOTE: Gemini requires "btcusd" and "ethusd" market price currency (USD) values to be limited
 * to 2 decimal places when creating orders - the adapter truncates any prices with more than 2
 * decimal places and rounds using {@link java.math.RoundingMode#HALF_EVEN}, E.g. 250.176 would be
 * sent to the exchange as 250.18. For the "ethbtc" market, price currency (BTC) values are limited
 * to 5 decimal places - the adapter will truncate and round accordingly.
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
 */
public final class GeminiExchangeAdapter extends AbstractExchangeAdapter
    implements ExchangeAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final String GEMINI_API_VERSION = "v1";
  private static final String PUBLIC_API_BASE_URL =
      "https://api.gemini.com/" + GEMINI_API_VERSION + "/";
  private static final String AUTHENTICATED_API_URL = PUBLIC_API_BASE_URL;

  private static final String UNEXPECTED_ERROR_MSG =
      "Unexpected error has occurred in Gemini Exchange Adapter. ";
  private static final String UNEXPECTED_IO_ERROR_MSG =
      "Failed to connect to Exchange due to unexpected IO error.";

  private static final String AMOUNT = "amount";
  private static final String PRICE = "price";

  private static final String KEY_PROPERTY_NAME = "key";
  private static final String SECRET_PROPERTY_NAME = "secret";

  private static final String BUY_FEE_PROPERTY_NAME = "buy-fee";
  private static final String SELL_FEE_PROPERTY_NAME = "sell-fee";

  /*
   * Markets on the exchange. Used for determining order price truncation/rounding policy.
   * See: https://docs.gemini.com/rest-api/#symbols-and-minimums
   */
  private enum MarketId {
    BTC_USD("btcusd"),
    ETH_USD("ethusd"),
    ETH_BTC("ethbtc");
    private final String market;

    MarketId(String market) {
      this.market = market;
    }

    public String getStringValue() {
      return market;
    }
  }

  private BigDecimal buyFeePercentage;
  private BigDecimal sellFeePercentage;

  private String key = "";
  private String secret = "";

  private Mac mac;
  private boolean initializedMacAuthentication = false;
  private long nonce = 0;

  private Gson gson;

  @Override
  public void init(ExchangeConfig config) {
    LOG.info(() -> "About to initialise Gemini ExchangeConfig: " + config);
    setAuthenticationConfig(config);
    setNetworkConfig(config);
    setOtherConfig(config);

    nonce = System.currentTimeMillis() / 1000;
    initSecureMessageLayer();
    initGson();
  }

  // --------------------------------------------------------------------------
  // Gemini REST Trade API Calls adapted to the Trading API.
  // See https://docs.gemini.com/rest-api/
  // --------------------------------------------------------------------------

  @Override
  public String createOrder(
      String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final Map<String, String> params = createRequestParamMap();

      params.put("symbol", marketId);

      // note we need to limit amount and price to 6 decimal places else exchange will barf with 400
      // response
      params.put(
          AMOUNT, new DecimalFormat("#.######", getDecimalFormatSymbols()).format(quantity));

      // Decimal precision of price varies with market price currency
      if (marketId.equals(MarketId.BTC_USD.getStringValue())
          || marketId.equals(MarketId.ETH_USD.getStringValue())) {
        params.put(PRICE, new DecimalFormat("#.##", getDecimalFormatSymbols()).format(price));
      } else if (marketId.equals(MarketId.ETH_BTC.getStringValue())) {
        params.put(PRICE, new DecimalFormat("#.#####", getDecimalFormatSymbols()).format(price));
      } else {
        final String errorMsg =
            "Invalid market id: "
                + marketId
                + " - Can only be "
                + MarketId.BTC_USD.getStringValue()
                + " or "
                + MarketId.ETH_USD.getStringValue()
                + " or "
                + MarketId.ETH_BTC.getStringValue();
        LOG.error(errorMsg);
        throw new IllegalArgumentException(errorMsg);
      }

      if (orderType == OrderType.BUY) {
        params.put("side", "buy");
      } else if (orderType == OrderType.SELL) {
        params.put("side", "sell");
      } else {
        final String errorMsg =
            "Invalid order type: "
                + orderType
                + " - Can only be "
                + OrderType.BUY.getStringValue()
                + " or "
                + OrderType.SELL.getStringValue();
        LOG.error(errorMsg);
        throw new IllegalArgumentException(errorMsg);
      }

      // this adapter only supports 'exchange limit' orders
      params.put("type", "exchange limit");

      final ExchangeHttpResponse response = sendAuthenticatedRequestToExchange("order/new", params);

      LOG.debug(() -> "Create Order response: " + response);

      final GeminiOpenOrder createOrderResponse =
          gson.fromJson(response.getPayload(), GeminiOpenOrder.class);
      final long id = createOrderResponse.orderId;
      if (id == 0) {
        final String errorMsg = "Failed to place order on exchange. Error response: " + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      } else {
        return Long.toString(createOrderResponse.orderId);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public boolean cancelOrder(String orderId, String marketIdNotNeeded)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final Map<String, String> params = createRequestParamMap();
      params.put("order_id", orderId);

      final ExchangeHttpResponse response =
          sendAuthenticatedRequestToExchange("order/cancel", params);

      LOG.debug(() -> "Cancel Order response: " + response);

      // Exchange returns order id and other details if successful, a 400 HTTP Status if the order
      // id was not recognised.
      gson.fromJson(response.getPayload(), GeminiOpenOrder.class);
      return true;

    } catch (ExchangeNetworkException | TradingApiException e) {
      if (e.getCause() != null && e.getCause().getMessage().contains("400")) {
        final String errorMsg =
            "Failed to cancel order on exchange. Did not recognise Order Id: " + orderId;
        LOG.error(errorMsg, e);
        return false;
      } else {
        throw e;
      }

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public List<OpenOrder> getYourOpenOrders(String marketId)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final ExchangeHttpResponse response = sendAuthenticatedRequestToExchange("orders", null);

      LOG.debug(() -> "Open Orders response: " + response);

      final GeminiOpenOrders geminiOpenOrders =
          gson.fromJson(response.getPayload(), GeminiOpenOrders.class);

      final List<OpenOrder> ordersToReturn = new ArrayList<>();
      for (final GeminiOpenOrder geminiOpenOrder : geminiOpenOrders) {

        if (!marketId.equalsIgnoreCase(geminiOpenOrder.symbol)) {
          continue;
        }

        OrderType orderType;
        switch (geminiOpenOrder.side) {
          case "buy":
            orderType = OrderType.BUY;
            break;
          case "sell":
            orderType = OrderType.SELL;
            break;
          default:
            throw new TradingApiException(
                "Unrecognised order type received in getYourOpenOrders(). Value: "
                    + geminiOpenOrder.type);
        }

        final OpenOrder order =
            new OpenOrderImpl(
                Long.toString(geminiOpenOrder.orderId),
                Date.from(Instant.ofEpochMilli(geminiOpenOrder.timestampms)),
                marketId,
                orderType,
                geminiOpenOrder.price,
                geminiOpenOrder.remainingAmount,
                geminiOpenOrder.originalAmount,
                geminiOpenOrder.price.multiply(
                    geminiOpenOrder.originalAmount) // total - not provided by Gemini :-(
                );

        ordersToReturn.add(order);
      }
      return ordersToReturn;

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public MarketOrderBook getMarketOrders(String marketId)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final ExchangeHttpResponse response = sendPublicRequestToExchange("book/" + marketId);

      LOG.debug(() -> "Market Orders response: " + response);

      final GeminiOrderBook orderBook = gson.fromJson(response.getPayload(), GeminiOrderBook.class);

      final List<MarketOrder> buyOrders = new ArrayList<>();
      for (GeminiMarketOrder geminiBuyOrder : orderBook.bids) {
        final MarketOrder buyOrder =