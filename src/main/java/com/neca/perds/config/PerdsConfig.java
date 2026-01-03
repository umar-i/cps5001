package com.neca.perds.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for PERDS system parameters.
 * Provides defaults and allows loading from properties files.
 */
public record PerdsConfig(
        PredictionConfig prediction,
        PrepositioningConfig prepositioning
) {
    /** Default configuration with sensible defaults. */
    public static final PerdsConfig DEFAULT = new PerdsConfig(
            PredictionConfig.DEFAULT,
            PrepositioningConfig.DEFAULT
    );

    public PerdsConfig {
        Objects.requireNonNull(prediction, "prediction");
        Objects.requireNonNull(prepositioning, "prepositioning");
    }

    /**
     * Configuration for demand prediction algorithms.
     */
    public record PredictionConfig(
            double adaptiveLearningRate,
            Duration slidingWindowDuration,
            double exponentialSmoothingAlpha
    ) {
        /** Default learning rate for adaptive ensemble (Hedge algorithm). */
        public static final double DEFAULT_LEARNING_RATE = 0.25;
        /** Default sliding window duration for demand prediction. */
        public static final Duration DEFAULT_WINDOW_DURATION = Duration.ofHours(1);
        /** Default smoothing factor for exponential smoothing predictor. */
        public static final double DEFAULT_EXP_SMOOTHING_ALPHA = 0.3;

        public static final PredictionConfig DEFAULT = new PredictionConfig(
                DEFAULT_LEARNING_RATE,
                DEFAULT_WINDOW_DURATION,
                DEFAULT_EXP_SMOOTHING_ALPHA
        );

        public PredictionConfig {
            Objects.requireNonNull(slidingWindowDuration, "slidingWindowDuration");
            if (Double.isNaN(adaptiveLearningRate) || adaptiveLearningRate <= 0.0) {
                throw new IllegalArgumentException("adaptiveLearningRate must be > 0");
            }
            if (slidingWindowDuration.isNegative() || slidingWindowDuration.isZero()) {
                throw new IllegalArgumentException("slidingWindowDuration must be > 0");
            }
            if (Double.isNaN(exponentialSmoothingAlpha) || exponentialSmoothingAlpha <= 0.0 || exponentialSmoothingAlpha > 1.0) {
                throw new IllegalArgumentException("exponentialSmoothingAlpha must be in (0, 1]");
            }
        }
    }

    /**
     * Configuration for prepositioning strategy.
     */
    public record PrepositioningConfig(
            int maxMoves,
            int maxZones
    ) {
        /** Default maximum reposition moves per planning cycle. */
        public static final int DEFAULT_MAX_MOVES = 3;
        /** Default maximum zones to consider for prepositioning. */
        public static final int DEFAULT_MAX_ZONES = 3;

        public static final PrepositioningConfig DEFAULT = new PrepositioningConfig(
                DEFAULT_MAX_MOVES,
                DEFAULT_MAX_ZONES
        );

        public PrepositioningConfig {
            if (maxMoves < 0) {
                throw new IllegalArgumentException("maxMoves must be >= 0");
            }
            if (maxZones <= 0) {
                throw new IllegalArgumentException("maxZones must be > 0");
            }
        }
    }
}
