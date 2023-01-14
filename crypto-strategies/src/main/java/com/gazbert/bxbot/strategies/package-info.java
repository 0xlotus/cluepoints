/**
 *
 *
 * <h2>Trading Strategies</h2>
 *
 * <p>You can write your own Trading Strategies and keep them here. Alternatively, you can package
 * them up in a separate jar and place it on BX-bot's runtime classpath. Your Trading Strategy must:
 *
 * <ol>
 *   <li>implement the {@link com.gazbert.crypto.strategy.api.TradingStrategy} interface.
 *   <li>be placed on the Trading Engine's runtime classpath: keep it here, or in a separate jar
 *       file.
 *   <li>include a configuration entry in the strategies.yaml file.
 * </ol>
 *
 * <p>You can pass configuration to your Strategy from the strategies.yaml file - you access it from
 * the {@link
 * com.gazbert.crypto.strategy.ap