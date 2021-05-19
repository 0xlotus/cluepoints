
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015. Gareth Jon Lynch
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

package com.gazbert.crypto.core.util.adapters;

import com.gazbert.crypto.trading.api.BalanceInfo;
import com.gazbert.crypto.trading.api.MarketOrderBook;
import com.gazbert.crypto.trading.api.OpenOrder;
import com.gazbert.crypto.trading.api.OrderType;
import com.gazbert.crypto.trading.api.TradingApi;
import java.math.BigDecimal;
import java.util.List;

/*
 * An invalid (and useless!) Exchange Adapter for unit testing.
 * Invalid because it does not implement the ExchangeAdapter interface.
 */
public class InvalidExchangeAdapterNotImplExchangeAdapter implements TradingApi {

  @Override
  public String getImplName() {
    return null;
  }

  @Override
  public MarketOrderBook getMarketOrders(String marketId) {
    return null;
  }

  @Override
  public List<OpenOrder> getYourOpenOrders(String marketId) {
    return null;
  }

  @Override
  public String createOrder(
      String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price) {
    return null;
  }

  @Override
  public boolean cancelOrder(String orderId, String marketId) {
    return false;
  }

  @Override
  public BigDecimal getLatestMarketPrice(String marketId) {
    return null;
  }

  @Override
  public BalanceInfo getBalanceInfo() {
    return null;
  }

  @Override
  public BigDecimal getPercentageOfBuyOrderTakenForExchangeFee(String marketId) {
    return null;
  }

  @Override
  public BigDecimal getPercentageOfSellOrderTakenForExchangeFee(String marketId) {
    return null;
  }
}