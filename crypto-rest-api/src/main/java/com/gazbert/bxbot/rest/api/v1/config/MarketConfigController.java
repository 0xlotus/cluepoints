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

package com.gazbert.crypto.rest.api.v1.config;

import static com.gazbert.crypto.rest.api.v1.EndpointLocations.CONFIG_ENDPOINT_BASE_URI;

import com.gazbert.crypto.domain.market.MarketConfig;
import com.gazbert.crypto.services.config.MarketConfigService;
import io.swagger.annotations.Api;
import java.security.Principal;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

/**
 * Controller for directing Market config requests.
 *
 * @author gazbert
 * @since 1.0
 */
@Api(tags = {"Market Configuration"})
@RestController
@RequestMapping(CONFIG_ENDPOINT_BASE_URI)
public class MarketConfigController {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MARKETS_RESOURCE_PATH = "/markets";
  private final MarketConfigService marketConfigService;

  @Autowired
  public MarketConfigController(MarketConfigService marketConfigService) {
    this.marketConfigService = marketConfigService;
  }

  /**
   * Returns all of the Market configuration for the bot.
   *
   * @param principal the authenticated user.
   * @return all the Market configurations.
   */
  @PreAuthorize("hasRole('USER')")
  @GetMapping(value = MARKETS_RESOURCE_PATH)
  public List<MarketConfig> getAllMarkets(@ApiIgnore Principal principal) {

    LOG.info(
        () ->
            "GET " + MARKETS_RE