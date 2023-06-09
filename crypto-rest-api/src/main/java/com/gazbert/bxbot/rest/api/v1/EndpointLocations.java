
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

package com.gazbert.crypto.rest.api.v1;

/**
 * Base paths for BX-bot v1 REST endpoints.
 *
 * @author gazbert
 */
public final class EndpointLocations {

  /** Base path for entire REST API. */
  private static final String API_ENDPOINT_BASE_URI = "/api/v1";

  /** Base path for configuration REST endpoints. */
  public static final String CONFIG_ENDPOINT_BASE_URI = API_ENDPOINT_BASE_URI + "/config";

  /** Base path for runtime REST endpoints. */
  public static final String RUNTIME_ENDPOINT_BASE_URI = API_ENDPOINT_BASE_URI + "/runtime";

  private EndpointLocations() {
  }
}