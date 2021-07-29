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
 * Exchange Adapter for integrating with the Bitfinex exchange. The Bitfinex API is documented <a
 * href="https://www.bitfinex.com/pages/api">here</a>.
 *
 * <p><strong> DISCLAIMER: This Exchange Adapter is provided as-is; it might have bugs in it and you
 * could lose money. Despite running live on Bitfinex, it has only been unit tested up until the
 * point of calling the {@link #sendPublicRequestToExchange(String)} and {@link
 * #sendAuthenticatedRequestToExchange(String, Map)} methods. Use it at our own risk!</strong>
 *
 * <p>The adapter uses v1 of the Bitfinex API - it is limited to 60 API calls per minute. It only
 * supports 'exchange' accounts; it does <em>not</em> support 'trading' (margin trading) accounts or
 * 'deposit' (liquidity SWAPs) accounts. Furthermore, the adapter does not support sending 'hidden'
 * orders.
 *
 * <p>There are different exchange fees for Takers and Makers - see <a
 * href="https://www.bitfinex.com/pages/fees">here.</a> This adapter will use the <em>Taker</em>
 * fees to keep things simple for now.
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
public final class BitfinexExchangeAdapter extends AbstractExchangeAdapter
    implements ExchangeAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private static final String BITFINEX_API_VERSION = "v1";
  private static final String PUBLIC_API_BASE_URL =
      "https://api.bitfinex.com/" + BITFINEX_API_VERSION + "/";
  private static final String AUTHENTICATED_API_URL = PUBLIC_API_BASE_URL;

  private static final String UNEXPECTED_ERROR_MSG =
      "Unexpected error has occurred in Bitfinex Exchange Adapter. ";
  private static final String UNEXPECTED_IO_ERROR_MSG =
      "Failed to connect to Exchange due to unexpected IO error.";

  private static final String ID = "id";
  private static final String EXCHANGE = "exchange";
  private static final String SYMBOL = "symbol";
  private static final String AMOUNT = "amount";
  private static final String PRICE = "price";
  private static final String TIMESTAMP = "timestamp";
  private static final String AVG_EXECUTION_PRICE = "avgExecutionPrice";
  private static final String IS_LIVE = "isLive";
  private static final String IS_CANCELLED = "isCancelled";
  private static final String IS_HIDDEN = "isHidden";
  private static final String WAS_FORCED = "wasForced";
  private static final String ORIGINAL_AMOUNT = "originalAmount";
  private static final String REMAINING_AMOUNT = "remainingAmount";
  private static final String EXECUTED_AMOUNT = "executedAmount";

  private static final String KEY_PROPERTY_NAME = "key";
  private static final String SECRET_PROPERTY_NAME = "secret";

  private String key = "";
  private String secret = "";

  private Mac mac;
  private boolean initializedMacAuthentication = false;
  private long nonce = 0;

  private Gson gson;

  @Override
  public void init(ExchangeConfig config) {
    LOG.info(() -> "About to initialise Bitfinex ExchangeConfig: " + config);
    setAuthenticationConfig(config);
    setNetworkConfig(config);

    nonce = System.currentTimeMillis() / 1000;
    initSecureMessageLayer();
    initGson();
  }

  // --------------------------------------------------------------------------
  // Bitfinex API Calls adapted to the Trading API.
  // See https://www.bitfinex.com/pages/api
  // --------------------------------------------------------------------------

  @Override
  public MarketOrderBook getMarketOrders(String marketId)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final ExchangeHttpResponse response = sendPublicRequestToExchange("book/" + marketId);
      LOG.debug(() -> "Market Orders response: " + response);

      final BitfinexOrderBook orderBook =
          gson.fromJson(response.getPayload(), BitfinexOrderBook.class);

      final List<MarketOrder> buyOrders = new ArrayList<>();
      for (BitfinexMarketOrder bitfinexBuyOrder : orderBook.bids) {
        final MarketOrder buyOrder =
            new MarketOrderImpl(
                OrderType.BUY,
                bitfinexBuyOrder.price,
                bitfinexBuyOrder.amount,
                bitfinexBuyOrder.price.multiply(bitfinexBuyOrder.amount));
        buyOrders.add(buyOrder);
      }

      final List<MarketOrder> sellOrders = new ArrayList<>();
      for (BitfinexMarketOrder bitfinexSellOrder : orderBook.asks) {
        final MarketOrder sellOrder =
            new MarketOrderImpl(
                OrderType.SELL,
                bitfinexSellOrder.price,
                bitfinexSellOrder.amount,
                bitfinexSellOrder.price.multiply(bitfinexSellOrder.amount));
        sellOrders.add(sellOrder);
      }

      return new MarketOrderBookImpl(marketId, sellOrders, buyOrders);

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
    try {
      final ExchangeHttpResponse response = sendAuthenticatedRequestToExchange("orders", null);
      LOG.debug(() -> "Open Orders response: " + response);

      final BitfinexOpenOrders bitfinexOpenOrders =
          gson.fromJson(response.getPayload(), BitfinexOpenOrders.class);

      final List<OpenOrder> ordersToReturn = new ArrayList<>();
      for (final BitfinexOpenOrder bitfinexOpenOrder : bitfinexOpenOrders) {

        if (!marketId.equalsIgnoreCase(bitfinexOpenOrder.symbol)) {
          continue;
        }

        OrderType orderType;
        switch (bitfinexOpenOrder.side) {
          case "buy":
            orderType = OrderType.BUY;
            break;
          case "sell":
            orderType = OrderType.SELL;
            break;
          default:
            throw new TradingApiException(
                "Unrecognised order type received in getYourOpenOrders(). Value: "
                    + bitfinexOpenOrder.type);
        }

        final OpenOrder order =
            new OpenOrderImpl(
                Long.toString(bitfinexOpenOrder.id),
                // for some reason 'finex adds decimal point to long date value, e.g. "1442073766.0"
                //  - grrrr!
                Date.from(
                    Instant.ofEpochMilli(
                        Integer.parseInt(bitfinexOpenOrder.timestamp.split("\\.")[0]))),
                marketId,
                orderType,
                bitfinexOpenOrder.price,
                bitfinexOpenOrder.remainingAmount,
                bitfinexOpenOrder.originalAmount,
                bitfinexOpenOrder.price.multiply(
                    bitfinexOpenOrder.originalAmount) // total - not provided by finex :-(
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
  public String createOrder(
      String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final Map<String, Object> params = createRequestParamMap();

      params.put(SYMBOL, marketId);

      // note we need to limit amount and price to 8 decimal places else exchange will barf
      params.put(
          AMOUNT, new DecimalFormat("#.########", getDecimalFormatSymbols()).format(quantity));
      params.put(PRICE, new DecimalFormat("#.########", getDecimalFormatSymbols()).format(price));

      params.put(EXCHANGE, "bitfinex");

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

      // 'type' is either "market" / "limit" / "stop" / "trailing-stop" / "fill-or-kill" / "exchange
      // market" /
      // "exchange limit" / "exchange stop" / "exchange trailing-stop" / "exchange fill-or-kill".
      // (type starting by "exchange " are exchange orders, others are margin trading orders)

      // this adapter only supports 'exchange limit orders'
      params.put("type", "exchange limit");

      // This adapter does not currently support hidden orders.
      // Exchange API notes: "true if the order should be hidden. Default is false."
      // If you try and set "is_hidden" to false, the exchange barfs and sends a 401 back. Nice.
      // params.put("is_hidden", "false");

      final ExchangeHttpResponse response = sendAuthenticatedRequestToExchange("order/new", params);
      LOG.debug(() -> "Create Order response: " + response);

      final BitfinexNewOrderResponse createOrderResponse =
          gson.fromJson(response.getPayload(), BitfinexNewOrderResponse.class);
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

  /*
   * marketId is not needed for cancelling orders on this exchange.
   */
  @Override
  public boolean cancelOrder(String orderId, String marketIdNotNeeded)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final Map<String, Object> params = createRequestParamMap();
      params.put("order_id", Long.parseLong(orderId));

      final ExchangeHttpResponse response =
          sendAuthenticatedRequestToExchange("order/cancel", params);
      LOG.debug(() -> "Cancel Order response: " + response);

      // Exchange returns order id and other details if successful, a 400 HTTP Status if the order
      // id was not recognised.
      gson.fromJson(response.getPayload(), BitfinexCancelOrderResponse.class);
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
  public BigDecimal getLatestMarketPrice(String marketId)
      throws TradingApiException, ExchangeNetworkException {
    try {
      final ExchangeHttpResponse response = sendPublicRequestToExchange("pubticker/" + marketId);
      LOG.debug(() -> "Latest Market Price response: " + response);

      final BitfinexTicker ticker = gson.fromJson(response.getPayload(), BitfinexTicker.class);
      return ticker.lastPrice;

    } catch (ExchangeNetworkException | TradingApiException e) {
      throw e;

    } catch (Exception e) {
      LOG.error(UNEXPECTED_ERROR_MSG, e);
      throw new TradingApiException(UNEXPECTED_ERROR_MSG, e);
    }
  }

  @Override
  public BalanceInfo getBalanceInfo() throws TradingApiException, ExchangeNetworkException {
    try {
      final ExchangeHttpResponse response = sendAuthenticatedRequestToExchange("balances", null);
      LOG.debug(() -> "Balance Info response: " + response);

      final BitfinexBalances allAccountBalances =
          gson.fromJson(response.getPayload(), BitfinexBalances.class);
      final HashMap<String, BigDecimal> balancesAvailable = new HashMap<>();

      /*
       * The adapter only fetches the 'exchange' account balance details - this is the Bitfinex
       * 'exchange' account, i.e. the limit order trading account balance.
       */
      if (allAccountBalances != null) {
        allAccountBalances.stream()
            .filter(accountBalance -> accountBalance.type.equalsIgnoreCase(EXCHANGE))
            .forEach(
                accountBalance -> {
                  if (accountBalance.currency.equalsIgnoreCase("usd")) {
                    balancesAvailable.put("USD", accountBalance.available);
                  } else if (accountBalance.currency.equalsIgnoreCase("btc")) {
                    balancesAvailable.put("BTC", accountBalance.available);
                  }
                });
      }

      // 2nd arg of BalanceInfo constructor for reserved/on-hold balances is not pr