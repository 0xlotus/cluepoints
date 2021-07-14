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
import java.security.NoSu