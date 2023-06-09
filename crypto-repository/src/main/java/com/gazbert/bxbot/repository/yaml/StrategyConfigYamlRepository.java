
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

import com.gazbert.crypto.datastore.yaml.ConfigurationManager;
import com.gazbert.crypto.datastore.yaml.strategy.StrategiesType;
import com.gazbert.crypto.domain.strategy.StrategyConfig;
import com.gazbert.crypto.repository.StrategyConfigRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Strategy config repo that uses a YAML backed datastore.
 *
 * @author gazbert
 */
@Repository("strategyConfigYamlRepository")
@Transactional
public class StrategyConfigYamlRepository implements StrategyConfigRepository {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public List<StrategyConfig> findAll() {
    LOG.info(() -> "Fetching all Strategy configs...");
    return ConfigurationManager.loadConfig(StrategiesType.class, STRATEGIES_CONFIG_YAML_FILENAME)
        .getStrategies();
  }

  @Override
  public StrategyConfig findById(String id) {
    LOG.info(() -> "Fetching config for Strategy id: " + id);

    final StrategiesType strategiesType =
        ConfigurationManager.loadConfig(StrategiesType.class, STRATEGIES_CONFIG_YAML_FILENAME);

    return adaptInternalToExternalConfig(
        strategiesType.getStrategies().stream()
            .filter(item -> item.getId().equals(id))
            .distinct()
            .collect(Collectors.toList()));
  }

  @Override
  public StrategyConfig save(StrategyConfig config) {
    final StrategiesType strategiesType =
        ConfigurationManager.loadConfig(StrategiesType.class, STRATEGIES_CONFIG_YAML_FILENAME);

    final List<StrategyConfig> strategyConfigs =
        strategiesType.getStrategies().stream()
            .filter(item -> item.getId().equals(config.getId()))
            .distinct()
            .collect(Collectors.toList());

    if (config.getId() == null || config.getId().isEmpty()) {
      LOG.info(() -> "About to create StrategyConfig: " + config);
      if (strategyConfigs.isEmpty()) {

        final StrategyConfig newStrategyConfig = new StrategyConfig(config);
        newStrategyConfig.setId(generateUuid());

        strategiesType.getStrategies().add(newStrategyConfig);
        ConfigurationManager.saveConfig(
            StrategiesType.class, strategiesType, STRATEGIES_CONFIG_YAML_FILENAME);

        final StrategiesType updatedInternalStrategiesConfig =
            ConfigurationManager.loadConfig(StrategiesType.class, STRATEGIES_CONFIG_YAML_FILENAME);

        return adaptInternalToExternalConfig(
            updatedInternalStrategiesConfig.getStrategies().stream()
                .filter(item -> item.getId().equals(newStrategyConfig.getId()))
                .distinct()
                .collect(Collectors.toList()));
      } else {
        throw new IllegalStateException(
            "Trying to create new StrategyConfig but null/empty id already exists. "
                + "StrategyConfig: "
                + config
                + " Existing StrategyConfigs: "
                + strategiesType.getStrategies());
      }
    } else {
      LOG.info(() -> "About to update StrategyConfig: " + config);

      if (!strategyConfigs.isEmpty()) {
        strategiesType
            .getStrategies()
            .remove(strategyConfigs.get(0)); // will only be 1 unique strat
        strategiesType.getStrategies().add(config);
        ConfigurationManager.saveConfig(
            StrategiesType.class, strategiesType, STRATEGIES_CONFIG_YAML_FILENAME);

        final StrategiesType updatedStrategiesType =
            ConfigurationManager.loadConfig(StrategiesType.class, STRATEGIES_CONFIG_YAML_FILENAME);

        return adaptInternalToExternalConfig(
            updatedStrategiesType.getStrategies().stream()
                .filter(item -> item.getId().equals(config.getId()))
                .distinct()
                .collect(Collectors.toList()));
      } else {
        LOG.warn(
            () ->
            "Trying to update StrategyConfig but id does not exist StrategyConfig: "
                + config
                + " Existing StrategyConfig: "
                + strategiesType.getStrategies());
        return null;
      }
    }
  }

  @Override
  public StrategyConfig delete(String id) {
    LOG.info(() -> "Deleting Strategy config for id: " + id);

    final StrategiesType strategiesType =
        ConfigurationManager.loadConfig(StrategiesType.class, STRATEGIES_CONFIG_YAML_FILENAME);

    final List<StrategyConfig> strategyConfigs =
        strategiesType.getStrategies().stream()
            .filter(item -> item.getId().equals(id))
            .distinct()
            .collect(Collectors.toList());

    if (!strategyConfigs.isEmpty()) {
      final StrategyConfig strategyToRemove = strategyConfigs.get(0); // will only be 1 unique strat
      strategiesType.getStrategies().remove(strategyToRemove);
      ConfigurationManager.saveConfig(
          StrategiesType.class, strategiesType, STRATEGIES_CONFIG_YAML_FILENAME);
      return adaptInternalToExternalConfig(Collections.singletonList(strategyToRemove));
    } else {
      LOG.warn(
          () ->
          "Trying to delete StrategyConfig but id does not exist. StrategyConfig id: "
              + id
              + " Existing StrategyConfig: "
              + strategiesType.getStrategies());
      return null;
    }
  }

  // --------------------------------------------------------------------------
  // Adapter methods
  // --------------------------------------------------------------------------

  private static StrategyConfig adaptInternalToExternalConfig(
      List<StrategyConfig> internalStrategyConfigItems) {
    if (!internalStrategyConfigItems.isEmpty()) {
      // Should only ever be 1 unique Strategy id
      return internalStrategyConfigItems.get(0);
    }
    return null;
  }

  // --------------------------------------------------------------------------
  // Util methods
  // --------------------------------------------------------------------------

  private String generateUuid() {
    return UUID.randomUUID().toString();
  }
}