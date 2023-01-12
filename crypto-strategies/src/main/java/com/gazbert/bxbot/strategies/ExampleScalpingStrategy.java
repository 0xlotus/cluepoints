
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

package com.gazbert.crypto.strategies;

import com.gazbert.crypto.strategy.api.StrategyConfig;
import com.gazbert.crypto.strategy.api.StrategyException;
import com.gazbert.crypto.strategy.api.TradingStrategy;
import com.gazbert.crypto.trading.api.ExchangeNetworkException;
import com.gazbert.crypto.trading.api.Market;
import com.gazbert.crypto.trading.api.MarketOrder;
import com.gazbert.crypto.trading.api.MarketOrderBook;
import com.gazbert.crypto.trading.api.OpenOrder;
import com.gazbert.crypto.trading.api.OrderType;
import com.gazbert.crypto.trading.api.TradingApi;
import com.gazbert.crypto.trading.api.TradingApiException;
import com.google.common.base.MoreObjects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * This is a very simple <a
 * href="http://www.investopedia.com/articles/trading/02/081902.asp">scalping strategy</a> to show
 * how to use the Trading API; you will want to code a much better algorithm! It trades using <a
 * href="http://www.investopedia.com/terms/l/limitorder.asp">limit orders</a> at the <a
 * href="http://www.investopedia.com/terms/s/spotprice.asp">spot price</a>.
 *
 * <p><strong> DISCLAIMER: This algorithm is provided as-is; it might have bugs in it and you could
 * lose money. Use it at our own risk! </strong>
 *
 * <p>It was originally written to trade on <a href="https://btc-e.com">BTC-e</a>, but should work
 * for any exchange. The algorithm will start by buying the base currency (BTC in this example)
 * using the counter currency (USD in this example), and then sell the base currency (BTC) at a
 * higher price to take profit from the spread. The algorithm expects you to have deposited
 * sufficient counter currency (USD) into your exchange wallet in order to buy the base currency
 * (BTC).
 *
 * <p>When it starts up, it places an order at the current BID price and uses x amount of counter
 * currency (USD) to 'buy' the base currency (BTC). The value of x comes from the sample
 * {project-root}/config/strategies.yaml 'counter-currency-buy-order-amount' config-item, currently
 * set to 20 USD. Make sure that the value you use for x is large enough to be able to meet the
 * minimum BTC order size for the exchange you are trading on, e.g. the Bitfinex min order size is
 * 0.01 BTC as of 3 May 2017. The algorithm then waits for the buy order to fill...
 *
 * <p>Once the buy order fills, it then waits until the ASK price is at least y % higher than the
 * previous buy fill price. The value of y comes from the sample
 * {project-root}/config/strategies.yaml 'minimum-percentage-gain' config-item, currently set to 1%.
 * Once the % gain has been achieved, the algorithm will place a sell order at the current ASK
 * price. It then waits for the sell order to fill... and the cycle repeats.
 *
 * <p>The algorithm does not factor in being outbid when placing buy orders, i.e. it does not cancel
 * the current order and place a new order at a higher price; it simply holds until the current BID
 * price falls again. Likewise, the algorithm does not factor in being undercut when placing sell
 * orders; it does not cancel the current order and place a new order at a lower price.
 *
 * <p>Chances are you will either get a stuck buy order if the market is going up, or a stuck sell
 * order if the market goes down. You could manually execute the trades on the exchange and restart
 * the bot to get going again... but a much better solution would be to modify this code to deal
 * with it: cancel your current buy order and place a new order matching the current BID price, or
 * cancel your current sell order and place a new order matching the current ASK price. The {@link
 * TradingApi} allows you to add this behaviour.
 *
 * <p>Remember to include the correct exchange fees (both buy and sell) in your buy/sell
 * calculations when you write your own algorithm. Otherwise, you'll end up bleeding fiat/crypto to
 * the exchange...
 *
 * <p>This demo algorithm relies on the {project-root}/config/strategies.yaml
 * 'minimum-percentage-gain' config-item value being high enough to make a profit and cover the
 * exchange fees. You could tweak the algo to call the {@link
 * com.gazbert.crypto.trading.api.TradingApi#getPercentageOfBuyOrderTakenForExchangeFee(String)} and
 * {@link
 * com.gazbert.crypto.trading.api.TradingApi#getPercentageOfSellOrderTakenForExchangeFee(String)}
 * when calculating the order to send to the exchange... See the sample
 * {project-root}/config/samples/{exchange}/exchange.yaml files for info on the different exchange
 * fees.
 *
 * <p>You configure the loading of your strategy using either a className OR a beanName in the
 * {project-root}/config/strategies.yaml config file. This example strategy is configured using the
 * bean-name and by setting the @Component("exampleScalpingStrategy") annotation - this results in
 * Spring injecting the bean - see <a
 * href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/stereotype/Component.html">
 * Spring docs</a> for more details. Alternatively, you can load your strategy using className -
 * this will use the bot's custom injection framework. The choice is yours, but beanName is the way
 * to go if you want to use other Spring features in your strategy, e.g. a <a
 * href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/stereotype/Repository.html">
 * Repository</a> to store your trade data.
 *
 * <p>The algorithm relies on config from the sample {project-root}/config/strategies.yaml and
 * {project-root}/config/markets.yaml files. You can pass additional configItems to your Strategy
 * using the {project-root}/config/strategies.yaml file - you access it from the {@link
 * #init(TradingApi, Market, StrategyConfig)} method via the StrategyConfigImpl argument.
 *
 * <p>This simple demo algorithm only manages 1 order at a time to keep things simple.
 *
 * <p>The Trading Engine will only send 1 thread through your strategy code at a time - you do not
 * have to code for concurrency.
 *
 * <p>This <a
 * href="http://www.investopedia.com/articles/active-trading/101014/basics-algorithmic-trading-concepts-and-examples.asp">
 * site</a> might give you a few ideas - the {@link TradingApi} provides a basic Ticker that you
 * might want to use. Check out the excellent [ta4j](https://github.com/ta4j/ta4j) project too.
 *
 * <p>Good luck!
 *
 * @author gazbert
 */
@Component("exampleScalpingStrategy") // used to load the strategy using Spring bean injection
public class ExampleScalpingStrategy implements TradingStrategy {

  private static final Logger LOG = LogManager.getLogger();

  /** The decimal format for the logs. */
  private static final String DECIMAL_FORMAT = "#.########";

  /** Reference to the main Trading API. */
  private TradingApi tradingApi;

  /** The market this strategy is trading on. */
  private Market market;

  /** The state of the order. */
  private OrderState lastOrder;

  /**
   * The counter currency amount to use when placing the buy order. This was loaded from the
   * strategy entry in the {project-root}/config/strategies.yaml config file.
   */
  private BigDecimal counterCurrencyBuyOrderAmount;

  /**
   * The minimum % gain was to achieve before placing a SELL oder. This was loaded from the strategy
   * entry in the {project-root}/config/strategies.yaml config file.
   */
  private BigDecimal minimumPercentageGain;

  /**
   * Initialises the Trading Strategy. Called once by the Trading Engine when the bot starts up;
   * it's a bit like a servlet init() method.
   *
   * @param tradingApi the Trading API. Use this to make trades and stuff.
   * @param market the market for this strategy. This is the market the strategy is currently
   *     running on - you wire this up in the markets.yaml and strategies.yaml files.
   * @param config configuration for the strategy. Contains any (optional) config you set up in the
   *     strategies.yaml file.
   */
  @Override
  public void init(TradingApi tradingApi, Market market, StrategyConfig config) {
    LOG.info(() -> "Initialising Trading Strategy...");
    this.tradingApi = tradingApi;
    this.market = market;
    getConfigForStrategy(config);
    LOG.info(() -> "Trading Strategy initialised successfully!");
  }

  /**
   * This is the main execution method of the Trading Strategy. It is where your algorithm lives.
   *
   * <p>It is called by the Trading Engine during each trade cycle, e.g. every 60s. The trade cycle
   * is configured in the {project-root}/config/engine.yaml file.
   *
   * @throws StrategyException if something unexpected occurs. This tells the Trading Engine to
   *     shutdown the bot immediately to help prevent unexpected losses.
   */
  @Override
  public void execute() throws StrategyException {
    LOG.info(() -> market.getName() + " Checking order status...");

    try {
      // Grab the latest order book for the market.
      final MarketOrderBook orderBook = tradingApi.getMarketOrders(market.getId());

      final List<MarketOrder> buyOrders = orderBook.getBuyOrders();
      if (buyOrders.isEmpty()) {
        LOG.warn(
            () ->
                "Exchange returned empty Buy Orders. Ignoring this trade window. OrderBook: "
                    + orderBook);
        return;
      }

      final List<MarketOrder> sellOrders = orderBook.getSellOrders();
      if (sellOrders.isEmpty()) {
        LOG.warn(
            () ->
                "Exchange returned empty Sell Orders. Ignoring this trade window. OrderBook: "
                    + orderBook);
        return;
      }

      // Get the current BID and ASK spot prices.
      final BigDecimal currentBidPrice = buyOrders.get(0).getPrice();
      final BigDecimal currentAskPrice = sellOrders.get(0).getPrice();

      LOG.info(
          () ->
              market.getName()
                  + " Current BID price="
                  + new DecimalFormat(DECIMAL_FORMAT).format(currentBidPrice));
      LOG.info(
          () ->
              market.getName()
                  + " Current ASK price="