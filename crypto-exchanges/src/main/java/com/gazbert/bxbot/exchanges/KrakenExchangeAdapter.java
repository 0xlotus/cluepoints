
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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Exchange Adapter for integrating with the Kraken exchange. The Kraken API is documented <a
 * href="https://www.kraken.com/en-gb/help/api">here</a>.
 *
 * <p><strong> DISCLAIMER: This Exchange Adapter is provided as-is; it might have bugs in it and you
 * could lose money. Despite running live on Kraken, it has only been unit tested up until the point
 * of calling the {@link #sendPublicRequestToExchange(String, Map)} and {@link
 * #sendAuthenticatedRequestToExchange(String, Map)} methods. Use it at our own risk! </strong>
 *
 * <p>It only supports <a
 * href="https://support.kraken.com/hc/en-us/articles/203325783-Market-and-Limit-Orders">limit
 * orders</a> at the spot price; it does not support <a
 * href="https://support.kraken.com/hc/en-us/sections/200560633-Leverage-and-Margin">leverage and
 * margin</a> trading.
 *
 * <p>Exchange fees are loaded from the exchange.yaml file on startup; they are not fetched from the
 * exchange at runtime as the Kraken REST API does not support this. The fees are used across all
 * markets. Make sure you keep an eye on the <a href="https://www.kraken.com/help/fees">exchange
 * fees</a> and update the config accordingly.
 *
 * <p>The Kraken API has call rate limits - see <a
 * href="https://www.kraken.com/en-gb/help/api#api-call-rate-limit">API Call Rate Limit</a> for
 * details.
 *
 * <p>Kraken markets assets (e.g. currencies) can be referenced using their ISO4217-A3 names in the
 * case of ISO registered names, their 3 letter commonly used names in the case of unregistered
 * names, or their X-ISO4217-A3 code (see http://www.ifex-project.org/).
 *
 * <p>This adapter expects the market id to use the 3 letter commonly used names, e.g. you access
 * the XBT/USD market using 'XBTUSD'. Note: the exchange always returns the market id back in the
 * X-ISO4217-A3 format, i.e. 'XXBTZUSD'. The reason for doing this is because the Open Order
 * response contains the asset pair in the 3 letter format ('XBTUSD'), and we need to be able to
 * filter only the orders for the given market id.
 *
 * <p>The exchange regularly goes down for maintenance. If the keep-alive-during-maintenance
 * config-item is set to true in the exchange.yaml config file, the bot will stay alive and wait
 * until the next trade cycle.
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
public final class KrakenExchangeAdapter extends AbstractExchangeAdapter
    implements ExchangeAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final String KRAKEN_BASE_URI = "https://api.kraken.com/";
  private static final String KRAKEN_API_VERSION = "0";
  private static final String KRAKEN_PUBLIC_PATH = "/public/";
  private static final String KRAKEN_PRIVATE_PATH = "/private/";
  private static final String PUBLIC_API_BASE_URL =
      KRAKEN_BASE_URI + KRAKEN_API_VERSION + KRAKEN_PUBLIC_PATH;
  private static final String AUTHENTICATED_API_URL =
      KRAKEN_BASE_URI + KRAKEN_API_VERSION + KRAKEN_PRIVATE_PATH;

  private static final String UNEXPECTED_ERROR_MSG =
      "Unexpected error has occurred in Kraken Exchange Adapter. ";
  private static final String UNEXPECTED_IO_ERROR_MSG =
      "Failed to connect to Exchange due to unexpected IO error.";

  private static final String UNDER_MAINTENANCE_WARNING_MESSAGE =
      "Exchange is undergoing maintenance - keep alive is" + " true.";
  private static final String FAILED_TO_GET_MARKET_ORDERS =
      "Failed to get Market Order Book from exchange. Details: ";
  private static final String FAILED_TO_GET_BALANCE =
      "Failed to get Balance from exchange. Details: ";
  private static final String FAILED_TO_GET_TICKER =
      "Failed to get Ticker from exchange. Details: ";

  private static final String FAILED_TO_GET_OPEN_ORDERS =
      "Failed to get Open Orders from exchange. Details: ";
  private static final String FAILED_TO_ADD_ORDER = "Failed to Add Order on exchange. Details: ";
  private static final String FAILED_TO_CANCEL_ORDER =
      "Failed to Cancel Order on exchange. Details: ";

  private static final String PRICE = "price";

  private static final String KEY_PROPERTY_NAME = "key";
  private static final String SECRET_PROPERTY_NAME = "secret";

  private static final String BUY_FEE_PROPERTY_NAME = "buy-fee";
  private static final String SELL_FEE_PROPERTY_NAME = "sell-fee";

  private static final String KEEP_ALIVE_DURING_MAINTENANCE_PROPERTY_NAME =
      "keep-alive-during-maintenance";
  private static final String EXCHANGE_UNDERGOING_MAINTENANCE_RESPONSE = "EService:Unavailable";

  private long nonce = 0;

  private BigDecimal buyFeePercentage;
  private BigDecimal sellFeePercentage;

  private boolean keepAliveDuringMaintenance;

  private String key = "";
  private String secret = "";

  private Mac mac;
  private boolean initializedMacAuthentication = false;

  private Gson gson;

  @Override
  public void init(ExchangeConfig config) {
    LOG.info(() -> "About to initialise Kraken ExchangeConfig: " + config);
    setAuthenticationConfig(config);
    setNetworkConfig(config);
    setOtherConfig(config);

    nonce = System.currentTimeMillis() / 1000;
    initSecureMessageLayer();
    initGson();
  }

  // --------------------------------------------------------------------------
  // Kraken API Calls adapted to the Trading API.
  // See https://www.kraken.com/en-gb/help/api
  // --------------------------------------------------------------------------

  @Override
  public MarketOrderBook getMarketOrders(String marketId)
      throws TradingApiException, ExchangeNetworkException {

    ExchangeHttpResponse response;

    try {
      final Map<String, String> params = createRequestParamMap();
      params.put("pair", marketId);

      response = sendPublicRequestToExchange("Depth", params);
      LOG.debug(() -> "Market Orders response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {
        final Type resultType =
            new TypeToken<KrakenResponse<KrakenMarketOrderBookResult>>() {}.getType();
        final KrakenResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

        final List errors = krakenResponse.error;
        if (errors == null || errors.isEmpty()) {
          return adaptKrakenOrderBook(krakenResponse, marketId);

        } else {
          if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
            LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
            throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
          }

          final String errorMsg = FAILED_TO_GET_MARKET_ORDERS + response;
          LOG.error(errorMsg);
          throw new TradingApiException(errorMsg);
        }

      } else {
        final String errorMsg = FAILED_TO_GET_MARKET_ORDERS + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public List<OpenOrder> getYourOpenOrders(String marketId)
      throws TradingApiException, ExchangeNetworkException {

    ExchangeHttpResponse response;

    try {
      response = sendAuthenticatedRequestToExchange("OpenOrders", null);
      LOG.debug(() -> "Open Orders response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

        final Type resultType = new TypeToken<KrakenResponse<KrakenOpenOrderResult>>() {}.getType();
        final KrakenResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

        final List errors = krakenResponse.error;
        if (errors == null || errors.isEmpty()) {
          return adaptKrakenOpenOrders(krakenResponse, marketId);

        } else {
          if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
            LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
            throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
          }

          final String errorMsg = FAILED_TO_GET_OPEN_ORDERS + response;
          LOG.error(errorMsg);
          throw new TradingApiException(errorMsg);
        }

      } else {
        final String errorMsg = FAILED_TO_GET_OPEN_ORDERS + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public String createOrder(
      String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price)
      throws TradingApiException, ExchangeNetworkException {

    ExchangeHttpResponse response;

    try {
      final Map<String, String> params = createRequestParamMap();
      params.put("pair", marketId);

      if (orderType == OrderType.BUY) {
        params.put("type", "buy");
      } else if (orderType == OrderType.SELL) {
        params.put("type", "sell");
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

      params.put("ordertype", "limit"); // this exchange adapter only supports limit orders
      params.put(PRICE, new DecimalFormat("#.########", getDecimalFormatSymbols()).format(price));
      params.put(
          "volume", new DecimalFormat("#.########", getDecimalFormatSymbols()).format(quantity));

      response = sendAuthenticatedRequestToExchange("AddOrder", params);
      LOG.debug(() -> "Create Order response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

        final Type resultType = new TypeToken<KrakenResponse<KrakenAddOrderResult>>() {}.getType();
        final KrakenResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

        final List errors = krakenResponse.error;
        if (errors == null || errors.isEmpty()) {

          // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
          final KrakenAddOrderResult krakenAddOrderResult =
              (KrakenAddOrderResult) krakenResponse.result;

          // Just return the first one. Why an array?
          return krakenAddOrderResult.txid.get(0);

        } else {
          if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
            LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
            throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
          }

          final String errorMsg = FAILED_TO_ADD_ORDER + response;
          LOG.error(errorMsg);
          throw new TradingApiException(errorMsg);
        }

      } else {
        final String errorMsg = FAILED_TO_ADD_ORDER + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
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
    ExchangeHttpResponse response;

    try {
      final Map<String, String> params = createRequestParamMap();
      params.put("txid", orderId);

      response = sendAuthenticatedRequestToExchange("CancelOrder", params);
      LOG.debug(() -> "Cancel Order response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

        final Type resultType =
            new TypeToken<KrakenResponse<KrakenCancelOrderResult>>() {}.getType();
        final KrakenResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

        final List errors = krakenResponse.error;
        if (errors == null || errors.isEmpty()) {
          return adaptKrakenCancelOrderResult(krakenResponse);

        } else {
          if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
            LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
            throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
          }

          final String errorMsg = FAILED_TO_CANCEL_ORDER + response;
          LOG.error(errorMsg);
          throw new TradingApiException(errorMsg);
        }

      } else {
        final String errorMsg = FAILED_TO_CANCEL_ORDER + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public BigDecimal getLatestMarketPrice(String marketId)
      throws TradingApiException, ExchangeNetworkException {

    ExchangeHttpResponse response;

    try {
      final Map<String, String> params = createRequestParamMap();
      params.put("pair", marketId);

      response = sendPublicRequestToExchange("Ticker", params);
      LOG.debug(() -> "Latest Market Price response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

        final Type resultType = new TypeToken<KrakenResponse<KrakenTickerResult>>() {}.getType();
        final KrakenResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

        final List errors = krakenResponse.error;
        if (errors == null || errors.isEmpty()) {

          // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
          final KrakenTickerResult tickerResult = (KrakenTickerResult) krakenResponse.result;

          // 'c' key into map is the last market price: last trade closed array(<price>, <lot
          // volume>)
          return new BigDecimal(tickerResult.get("c"));

        } else {

          if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
            LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
            throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
          }

          final String errorMsg = FAILED_TO_GET_TICKER + response;
          LOG.error(errorMsg);
          throw new TradingApiException(errorMsg);
        }

      } else {
        final String errorMsg = FAILED_TO_GET_TICKER + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;
    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public BalanceInfo getBalanceInfo() throws TradingApiException, ExchangeNetworkException {

    ExchangeHttpResponse response;

    try {
      response = sendAuthenticatedRequestToExchange("Balance", null);
      LOG.debug(() -> "Balance Info response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {
        final Type resultType = new TypeToken<KrakenResponse<KrakenBalanceResult>>() {}.getType();
        return adaptKrakenBalanceInfo(response, resultType);

      } else {
        final String errorMsg = FAILED_TO_GET_BALANCE + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  /*
   * Kraken does not provide API call for fetching % buy fee; it only provides the fee monetary
   * value for a given order via the OpenOrders API call. We load the % fee statically from
   * exchange.yaml file.
   */
  @Override
  public BigDecimal getPercentageOfBuyOrderTakenForExchangeFee(String marketId) {
    return buyFeePercentage;
  }

  /*
   * Kraken does not provide API call for fetching % sell fee; it only provides the fee monetary
   * value for a given order via the OpenOrders API call. We load the % fee statically from
   * exchange.yaml file.
   */
  @Override
  public BigDecimal getPercentageOfSellOrderTakenForExchangeFee(String marketId) {
    return sellFeePercentage;
  }

  @Override
  public String getImplName() {
    return "Kraken API v1";
  }

  @Override
  public Ticker getTicker(String marketId) throws TradingApiException, ExchangeNetworkException {

    ExchangeHttpResponse response;

    try {
      final Map<String, String> params = createRequestParamMap();
      params.put("pair", marketId);

      response = sendPublicRequestToExchange("Ticker", params);
      LOG.debug(() -> "Ticker response: " + response);

      if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {

        final Type resultType = new TypeToken<KrakenResponse<KrakenTickerResult>>() {}.getType();
        final KrakenResponse krakenResponse = gson.fromJson(response.getPayload(), resultType);

        final List errors = krakenResponse.error;
        if (errors == null || errors.isEmpty()) {

          // Assume we'll always get something here if errors array is empty; else blow fast wih NPE
          final KrakenTickerResult tickerResult = (KrakenTickerResult) krakenResponse.result;

          // ouch!
          return new TickerImpl(
              new BigDecimal(tickerResult.get("c")), // last trade
              new BigDecimal(tickerResult.get("b")), // bid
              new BigDecimal(tickerResult.get("a")), // ask
              new BigDecimal(tickerResult.get("l")), // low 24h
              new BigDecimal(tickerResult.get("h")), // high 24hr
              new BigDecimal(tickerResult.get("o")), // open
              new BigDecimal(tickerResult.get("v")), // volume 24hr
              new BigDecimal(tickerResult.get("p")), // vwap 24hr
              null); // timestamp not supplied by Kraken

        } else {
          if (isExchangeUndergoingMaintenance(response) && keepAliveDuringMaintenance) {
            LOG.warn(() -> UNDER_MAINTENANCE_WARNING_MESSAGE);
            throw new ExchangeNetworkException(UNDER_MAINTENANCE_WARNING_MESSAGE);
          }

          final String errorMsg = FAILED_TO_GET_TICKER + response;
          LOG.error(errorMsg);
          throw new TradingApiException(errorMsg);
        }

      } else {
        final String errorMsg = FAILED_TO_GET_TICKER + response;
        LOG.error(errorMsg);
        throw new TradingApiException(errorMsg);
      }

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  // --------------------------------------------------------------------------
  //  GSON classes for JSON responses.
  //  See https://www.kraken.com/en-gb/help/api
  // --------------------------------------------------------------------------

  /**
   * GSON base class for all Kraken responses.
   *
   * <p>All Kraken responses have the following format:
   *
   * <pre>
   *
   * error = array of error messages in the format of:
   *
   * {char-severity code}{string-error category}:{string-error type}[:{string-extra info}]
   *    - severity code can be E for error or W for warning
   *
   * result = result of API call (may not be present if errors occur)
   *
   * </pre>
   *
   * <p>The result Type is what varies with each API call.
   */
  private static class KrakenResponse<T> {

    List<String> error;
    T result;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("error", error).add("result", result).toString();
    }
  }

  /** GSON class that wraps Depth API call result - the Market Order Book. */
  private static class KrakenMarketOrderBookResult extends HashMap<String, KrakenOrderBook> {

    private static final long serialVersionUID = -4913711010647027721L;
  }

  /** GSON class that wraps a Balance API call result. */
  private static class KrakenBalanceResult extends HashMap<String, BigDecimal> {

    private static final long serialVersionUID = -4919711010747027759L;
  }

  /** GSON class that wraps a Ticker API call result. */
  private static class KrakenTickerResult extends HashMap<String, String> {

    private static final long serialVersionUID = -4913711010647027759L;

    KrakenTickerResult() {
    }
  }

  /** GSON class that wraps an Open Order API call result - your open orders. */
  private static class KrakenOpenOrderResult {

    Map<String, KrakenOpenOrder> open;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("open", open).toString();
    }
  }

  /** GSON class the represents a Kraken Open Order. */
  private static class KrakenOpenOrder {

    String refid;
    String userref;
    String status;
    double opentm;
    double starttm;
    double expiretm;
    KrakenOpenOrderDescription descr;
    BigDecimal vol;

    @SerializedName("vol_exec")
    BigDecimal volExec;

    BigDecimal cost;
    BigDecimal fee;
    BigDecimal price;
    String misc;
    String oflags;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("refid", refid)
          .add("userref", userref)
          .add("status", status)
          .add("opentm", opentm)
          .add("starttm", starttm)
          .add("expiretm", expiretm)
          .add("descr", descr)
          .add("vol", vol)
          .add("volExec", volExec)
          .add("cost", cost)
          .add("fee", fee)
          .add(PRICE, price)
          .add("misc", misc)
          .add("oflags", oflags)
          .toString();
    }
  }

  /** GSON class the represents a Kraken Open Order description. */
  private static class KrakenOpenOrderDescription {

    String pair;
    String type;
    String ordertype;
    BigDecimal price;
    BigDecimal price2;
    String leverage;
    String order;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("pair", pair)
          .add("type", type)
          .add("ordertype", ordertype)
          .add(PRICE, price)
          .add("price2", price2)
          .add("leverage", leverage)
          .add("order", order)
          .toString();
    }
  }

  /** GSON class representing an AddOrder result. */
  private static class KrakenAddOrderResult {

    KrakenAddOrderResultDescription descr;
    List<String> txid; // why is this a list/array?

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("descr", descr).add("txid", txid).toString();
    }
  }

  /** GSON class representing an AddOrder result description. */
  private static class KrakenAddOrderResultDescription {

    String order;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("order", order).toString();
    }
  }

  /** GSON class representing a CancelOrder result. */
  private static class KrakenCancelOrderResult {

    int count;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("count", count).toString();
    }
  }

  /** GSON class for a Market Order Book. */
  private static class KrakenOrderBook {

    List<KrakenMarketOrder> bids;
    List<KrakenMarketOrder> asks;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("bids", bids).add("asks", asks).toString();
    }
  }

  /**
   * GSON class for holding Market Orders. First element in array is price, second element is
   * amount, 3rd is UNIX time.
   */
  private static class KrakenMarketOrder extends ArrayList<BigDecimal> {

    private static final long serialVersionUID = -4959711260742077759L;
  }

  /**
   * Custom GSON Deserializer for Ticker API call result.
   *
   * <p>Have to do this because last entry in the Ticker param map is a String, not an array like
   * the rest of 'em!
   */
  private static class KrakenTickerResultDeserializer
      implements JsonDeserializer<KrakenTickerResult> {

    KrakenTickerResultDeserializer() {
    }

    public KrakenTickerResult deserialize(
        JsonElement json, Type type, JsonDeserializationContext context) {

      final KrakenTickerResult krakenTickerResult = new KrakenTickerResult();
      if (json.isJsonObject()) {

        final JsonObject jsonObject = json.getAsJsonObject();

        // assume 1 (KV) entry as per API spec - the K is the market id, the V is a Map of ticker
        // params
        final JsonElement tickerParams = jsonObject.entrySet().iterator().next().getValue();