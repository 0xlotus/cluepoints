
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

import static com.gazbert.crypto.datastore.yaml.FileLocations.ENGINE_CONFIG_YAML_FILENAME;

import com.gazbert.crypto.datastore.yaml.ConfigurationManager;
import com.gazbert.crypto.datastore.yaml.engine.EngineType;
import com.gazbert.crypto.domain.engine.EngineConfig;
import com.gazbert.crypto.repository.EngineConfigRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * An Engine config repo that uses a YAML backed datastore.
 *
 * @author gazbert
 */
@Repository("engineConfigYamlRepository")
@Transactional
public class EngineConfigYamlRepository implements EngineConfigRepository {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public EngineConfig get() {
    LOG.info(() -> "Fetching EngineConfig...");
    return ConfigurationManager.loadConfig(EngineType.class, ENGINE_CONFIG_YAML_FILENAME)
        .getEngine();
  }

  @Override
  public EngineConfig save(EngineConfig config) {
    LOG.info(() -> "About to save EngineConfig: " + config);

    final EngineType engineType = new EngineType();
    engineType.setEngine(config);
    ConfigurationManager.saveConfig(EngineType.class, engineType, ENGINE_CONFIG_YAML_FILENAME);

    return ConfigurationManager.loadConfig(EngineType.class, ENGINE_CONFIG_YAML_FILENAME)
        .getEngine();
  }
}