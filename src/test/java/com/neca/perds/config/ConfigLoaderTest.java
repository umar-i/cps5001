package com.neca.perds.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void loadFromProperties_emptyProperties_returnsDefaults() {
        Properties props = new Properties();

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(PerdsConfig.DEFAULT, config);
    }

    @Test
    void loadFromProperties_customLearningRate() {
        Properties props = new Properties();
        props.setProperty("prediction.adaptive.learningRate", "0.5");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(0.5, config.prediction().adaptiveLearningRate(), 0.001);
        assertEquals(PerdsConfig.PredictionConfig.DEFAULT_WINDOW_DURATION, config.prediction().slidingWindowDuration());
    }

    @Test
    void loadFromProperties_customWindowDuration() {
        Properties props = new Properties();
        props.setProperty("prediction.slidingWindow.durationMinutes", "120");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(Duration.ofMinutes(120), config.prediction().slidingWindowDuration());
    }

    @Test
    void loadFromProperties_customAlpha() {
        Properties props = new Properties();
        props.setProperty("prediction.exponentialSmoothing.alpha", "0.7");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(0.7, config.prediction().exponentialSmoothingAlpha(), 0.001);
    }

    @Test
    void loadFromProperties_customMaxMoves() {
        Properties props = new Properties();
        props.setProperty("prepositioning.maxMoves", "5");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(5, config.prepositioning().maxMoves());
    }

    @Test
    void loadFromProperties_customMaxZones() {
        Properties props = new Properties();
        props.setProperty("prepositioning.maxZones", "10");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(10, config.prepositioning().maxZones());
    }

    @Test
    void loadFromProperties_allCustomValues() {
        Properties props = new Properties();
        props.setProperty("prediction.adaptive.learningRate", "0.1");
        props.setProperty("prediction.slidingWindow.durationMinutes", "30");
        props.setProperty("prediction.exponentialSmoothing.alpha", "0.5");
        props.setProperty("prepositioning.maxMoves", "2");
        props.setProperty("prepositioning.maxZones", "4");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(0.1, config.prediction().adaptiveLearningRate(), 0.001);
        assertEquals(Duration.ofMinutes(30), config.prediction().slidingWindowDuration());
        assertEquals(0.5, config.prediction().exponentialSmoothingAlpha(), 0.001);
        assertEquals(2, config.prepositioning().maxMoves());
        assertEquals(4, config.prepositioning().maxZones());
    }

    @Test
    void loadFromProperties_invalidLearningRate_throws() {
        Properties props = new Properties();
        props.setProperty("prediction.adaptive.learningRate", "notanumber");

        assertThrows(ConfigurationException.class, () -> ConfigLoader.loadFromProperties(props));
    }

    @Test
    void loadFromProperties_negativeMaxMoves_throws() {
        Properties props = new Properties();
        props.setProperty("prepositioning.maxMoves", "-1");

        assertThrows(ConfigurationException.class, () -> ConfigLoader.loadFromProperties(props));
    }

    @Test
    void loadFromProperties_zeroMaxZones_throws() {
        Properties props = new Properties();
        props.setProperty("prepositioning.maxZones", "0");

        assertThrows(ConfigurationException.class, () -> ConfigLoader.loadFromProperties(props));
    }

    @Test
    void loadFromProperties_alphaOutOfRange_throws() {
        Properties props = new Properties();
        props.setProperty("prediction.exponentialSmoothing.alpha", "1.5");

        assertThrows(ConfigurationException.class, () -> ConfigLoader.loadFromProperties(props));
    }

    @Test
    void loadFromStream_validProperties() throws IOException {
        String content = """
                prediction.adaptive.learningRate=0.3
                prepositioning.maxMoves=4
                """;
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        PerdsConfig config = ConfigLoader.loadFromStream(in);

        assertEquals(0.3, config.prediction().adaptiveLearningRate(), 0.001);
        assertEquals(4, config.prepositioning().maxMoves());
    }

    @Test
    void loadFromProperties_whitespaceInValues_trimmed() {
        Properties props = new Properties();
        props.setProperty("prediction.adaptive.learningRate", "  0.4  ");
        props.setProperty("prepositioning.maxMoves", " 6 ");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(0.4, config.prediction().adaptiveLearningRate(), 0.001);
        assertEquals(6, config.prepositioning().maxMoves());
    }

    @Test
    void loadFromProperties_blankValues_useDefaults() {
        Properties props = new Properties();
        props.setProperty("prediction.adaptive.learningRate", "   ");
        props.setProperty("prepositioning.maxMoves", "");

        PerdsConfig config = ConfigLoader.loadFromProperties(props);

        assertEquals(PerdsConfig.PredictionConfig.DEFAULT_LEARNING_RATE, config.prediction().adaptiveLearningRate(), 0.001);
        assertEquals(PerdsConfig.PrepositioningConfig.DEFAULT_MAX_MOVES, config.prepositioning().maxMoves());
    }

    @Test
    void generateSampleConfig_containsAllProperties() {
        String sample = ConfigLoader.generateSampleConfig();

        assertTrue(sample.contains("prediction.adaptive.learningRate"));
        assertTrue(sample.contains("prediction.slidingWindow.durationMinutes"));
        assertTrue(sample.contains("prediction.exponentialSmoothing.alpha"));
        assertTrue(sample.contains("prepositioning.maxMoves"));
        assertTrue(sample.contains("prepositioning.maxZones"));
    }

    @Test
    void defaultConfig_hasExpectedValues() {
        PerdsConfig config = PerdsConfig.DEFAULT;

        assertEquals(0.25, config.prediction().adaptiveLearningRate(), 0.001);
        assertEquals(Duration.ofHours(1), config.prediction().slidingWindowDuration());
        assertEquals(0.3, config.prediction().exponentialSmoothingAlpha(), 0.001);
        assertEquals(3, config.prepositioning().maxMoves());
        assertEquals(3, config.prepositioning().maxZones());
    }
}
