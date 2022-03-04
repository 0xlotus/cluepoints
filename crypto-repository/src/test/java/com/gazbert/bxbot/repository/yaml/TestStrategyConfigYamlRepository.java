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

package com.gazbert.crypto.repository.yaml;

import static com.gazbert.crypto.datastore.yaml.FileLocations.STRATEGIES_CONFIG_YAML_FILENAME;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import com.gazbert.crypto.datastore.yaml.ConfigurationManager;
import com.gazbert.crypto.datastore.yaml.strategy.StrategiesType;
import com.gazbert.crypto.domain.strategy.StrategyConfig;
import com.gazbert.crypto.repository.StrategyConfigRepository;
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
 * Tests YAML backed Strategy configuration repository behaves as expected.
 *
 * @author gazbert
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ConfigurationManager.class, StrategyConfigYamlRepository.class})
@PowerMockIgnore({
    "javax.crypto.*",
    "javax.management.*",
    "com.sun.org.apache.xerces.*",
    "javax.xml.parsers.*",
    "org.xml.sax.*",
    "org.w3c.dom.*"
})
public class TestStrategyConfigYamlRepository {

  private static final String MOCKED_GENERATE_UUID_METHOD = "generateUuid";

  private static final String UNKNOWN_STRAT_ID = "unknown-or-new-strat-id";
  private static final String GENERATED_STRAT_ID = "new-strat-id-123";

  private static final String STRAT_ID_1 = "macd-long-position";
  private static final String STRAT_NAME_1 = "MACD Long Position Algo";
  private static final String STRAT_DESCRIPTION_1 =
      "Uses MACD as indicator and takes long position in base currency.";
  private static final String STRAT_CLASSNAME_1 = "com.gazbert.nova.algos.MacdLongBase";
  private static final String STRAT_BEANAME_1 = "macdLongBase";

  private static final String STRAT_ID_2 = "long-scalper";
  private static final String STRAT_NAME_2 = "Long Position Scalper Algo";
  private static final String STRAT_DESCRIPTION_2 = "Scalps and goes long...";
  private static final String STRAT_CLASSNAME_2 = "com.gazbert.nova.algos.LongScalper";
  private static final String STRAT_BEANAME_2 = "longScalper";

  private static final String NEW_STRAT_NAME = "Short Position Scalper Algo";
  p