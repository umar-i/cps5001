package com.neca.perds.routing;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Defines time-of-day congestion multipliers for rush hour simulation.
 * 
 * <p>A congestion profile consists of multiple time periods, each with a start time,
 * end time, and multiplier. Outside defined periods, a default multiplier of 1.0 is used.
 * 
 * <p>Example usage:
 * <pre>{@code
 * CongestionProfile rushHour = CongestionProfile.builder()
 *     .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)   // morning rush
 *     .period(LocalTime.of(17, 0), LocalTime.of(19, 0), 1.7) // evening rush
 *     .build();
 * }</pre>
 */
public final class CongestionProfile {
    /** Default multiplier when time is outside any defined period. */
    public static final double DEFAULT_MULTIPLIER = 1.0;

    /** Minimum valid multiplier value. */
    public static final double MIN_MULTIPLIER = 0.1;

    /** Maximum valid multiplier value. */
    public static final double MAX_MULTIPLIER = 10.0;

    private final List<TimePeriod> periods;

    private CongestionProfile(List<TimePeriod> periods) {
        this.periods = List.copyOf(periods);
    }

    /**
     * Returns the congestion multiplier for the given time of day.
     * If the time falls within a defined period, returns that period's multiplier.
     * Otherwise returns {@link #DEFAULT_MULTIPLIER}.
     *
     * @param time the time of day
     * @return the congestion multiplier (>= {@link #MIN_MULTIPLIER})
     */
    public double multiplierAt(LocalTime time) {
        Objects.requireNonNull(time, "time");
        for (TimePeriod period : periods) {
            if (period.contains(time)) {
                return period.multiplier();
            }
        }
        return DEFAULT_MULTIPLIER;
    }

    /**
     * Returns true if this profile has any defined congestion periods.
     */
    public boolean hasPeriods() {
        return !periods.isEmpty();
    }

    /**
     * Returns the list of time periods in this profile.
     */
    public List<TimePeriod> periods() {
        return periods;
    }

    /**
     * Creates a new builder for constructing a CongestionProfile.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a profile with no congestion periods (always returns 1.0).
     */
    public static CongestionProfile none() {
        return new CongestionProfile(List.of());
    }

    /**
     * Returns a standard rush hour profile with typical morning and evening peaks.
     * <ul>
     *   <li>Morning rush (07:00-09:00): 1.5x multiplier</li>
     *   <li>Evening rush (17:00-19:00): 1.7x multiplier</li>
     * </ul>
     */
    public static CongestionProfile standardRushHour() {
        return builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .period(LocalTime.of(17, 0), LocalTime.of(19, 0), 1.7)
                .build();
    }

    /**
     * Represents a time period with an associated congestion multiplier.
     *
     * @param start the start time (inclusive)
     * @param end the end time (exclusive, or wraps to next day if before start)
     * @param multiplier the congestion multiplier for this period
     */
    public record TimePeriod(LocalTime start, LocalTime end, double multiplier) {
        public TimePeriod {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
                throw new IllegalArgumentException(
                        "multiplier must be between " + MIN_MULTIPLIER + " and " + MAX_MULTIPLIER);
            }
            if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
                throw new IllegalArgumentException("multiplier must be a finite number");
            }
        }

        /**
         * Returns true if the given time falls within this period.
         * Handles periods that span midnight (e.g., 23:00 to 01:00).
         */
        public boolean contains(LocalTime time) {
            if (start.isBefore(end) || start.equals(end)) {
                // Normal period (e.g., 07:00-09:00)
                return !time.isBefore(start) && time.isBefore(end);
            } else {
                // Overnight period (e.g., 23:00-01:00)
                return !time.isBefore(start) || time.isBefore(end);
            }
        }
    }

    /**
     * Builder for constructing CongestionProfile instances.
     */
    public static final class Builder {
        private final java.util.ArrayList<TimePeriod> periods = new java.util.ArrayList<>();

        private Builder() {}

        /**
         * Adds a congestion period.
         *
         * @param start start time (inclusive)
         * @param end end time (exclusive)
         * @param multiplier the congestion multiplier (must be between 0.1 and 10.0)
         * @return this builder
         */
        public Builder period(LocalTime start, LocalTime end, double multiplier) {
            periods.add(new TimePeriod(start, end, multiplier));
            return this;
        }

        /**
         * Builds the CongestionProfile.
         */
        public CongestionProfile build() {
            return new CongestionProfile(periods);
        }
    }

    @Override
    public String toString() {
        if (periods.isEmpty()) {
            return "CongestionProfile{none}";
        }
        StringBuilder sb = new StringBuilder("CongestionProfile{");
        for (int i = 0; i < periods.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            TimePeriod p = periods.get(i);
            sb.append(p.start()).append("-").append(p.end()).append(": ").append(p.multiplier()).append("x");
        }
        sb.append("}");
        return sb.toString();
    }
}
