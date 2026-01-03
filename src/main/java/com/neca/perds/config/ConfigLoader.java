package com.neca.perds.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads PERDS configuration from .properties files.
 * 
 * Supported properties:
 * <ul>
 *   <li>{@code prediction.adaptive.learningRate} - Learning rate for adaptive ensemble (default: 0.25)</li>
 *   <li>{@code prediction.slidingWindow.durationMinutes} - Sliding window duration in minutes (default: 60)</li>
 *   <li>{@code prediction.exponentialSmoothing.alpha} - Smoothing factor (default: 0.3)</li>
 *   <li>{@code prepositioning.maxMoves} - Maximum reposition moves per cycle (default: 3)</li>
 *   <li>{@code prepositioning.maxZones} - Maximum zones to consider (default: 3)</li>
 * </ul>
 */
public final class ConfigLoader {

    private ConfigLoader() {}

    /**
     * Loads configuration from the specified properties file path.
     *
     * @param path path to the properties file
     * @return the loaded configuration
     * @throws IOException if the file cannot be read
     * @throws ConfigurationException if the configuration is invalid
     */
    public static PerdsConfig loadFromPath(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream in = Files.newInputStream(path)) {
            return loadFromStream(in);
        }
    }

    /**
     * Loads configuration from the specified input stream.
     *
     * @param inputStream the input stream to read from
     * @return the loaded configuration
     * @throws IOException if the stream cannot be read
     * @throws ConfigurationException if the configuration is invalid
     */
    public static PerdsConfig loadFromStream(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        Properties props = new Properties();
        props.load(inputStream);
        return loadFromProperties(props);
    }

    /**
     * Loads configuration from a Properties object.
     *
     * @param props the properties to load from
     * @return the loaded configuration
     * @throws ConfigurationException if the configuration is invalid
     */
    public static PerdsConfig loadFromProperties(Properties props) {
        Objects.requireNonNull(props, "props");
        try {
            PerdsConfig.PredictionConfig prediction = loadPredictionConfig(props);
            PerdsConfig.PrepositioningConfig prepositioning = loadPrepositioningConfig(props);
            return new PerdsConfig(prediction, prepositioning);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid configuration: " + e.getMessage(), e);
        }
    }

    private static PerdsConfig.PredictionConfig loadPredictionConfig(Properties props) {
        double learningRate = getDouble(props, "prediction.adaptive.learningRate",
                PerdsConfig.PredictionConfig.DEFAULT_LEARNING_RATE);
        
        long windowMinutes = getLong(props, "prediction.slidingWindow.durationMinutes",
                PerdsConfig.PredictionConfig.DEFAULT_WINDOW_DURATION.toMinutes());
        Duration windowDuration = Duration.ofMinutes(windowMinutes);
        
        double alpha = getDouble(props, "prediction.exponentialSmoothing.alpha",
                PerdsConfig.PredictionConfig.DEFAULT_EXP_SMOOTHING_ALPHA);

        return new PerdsConfig.PredictionConfig(learningRate, windowDuration, alpha);
    }

    private static PerdsConfig.PrepositioningConfig loadPrepositioningConfig(Properties props) {
        int maxMoves = getInt(props, "prepositioning.maxMoves",
                PerdsConfig.PrepositioningConfig.DEFAULT_MAX_MOVES);
        int maxZones = getInt(props, "prepositioning.maxZones",
                PerdsConfig.PrepositioningConfig.DEFAULT_MAX_ZONES);

        return new PerdsConfig.PrepositioningConfig(maxMoves, maxZones);
    }

    private static double getDouble(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid double value for '" + key + "': " + value, e);
        }
    }

    private static long getLong(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid long value for '" + key + "': " + value, e);
        }
    }

    private static int getInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid int value for '" + key + "': " + value, e);
        }
    }

    /**
     * Generates a sample properties file content with all supported properties.
     *
     * @return sample properties content as a string
     */
    public static String generateSampleConfig() {
        return """
                # PERDS Configuration File
                # All values shown are defaults

                # Prediction Configuration
                # Learning rate for adaptive ensemble predictor (Hedge algorithm)
                prediction.adaptive.learningRate=0.25

                # Sliding window duration in minutes for demand prediction
                prediction.slidingWindow.durationMinutes=60

                # Smoothing factor for exponential smoothing predictor (0 < alpha <= 1)
                prediction.exponentialSmoothing.alpha=0.3

                # Prepositioning Configuration
                # Maximum number of unit repositioning moves per planning cycle
                prepositioning.maxMoves=3

                # Maximum number of high-demand zones to consider for prepositioning
                prepositioning.maxZones=3
                """;
    }
}
