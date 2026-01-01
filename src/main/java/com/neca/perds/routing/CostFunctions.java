package com.neca.perds.routing;

import com.neca.perds.graph.EdgeStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * Factory methods for common edge cost functions.
 */
public final class CostFunctions {
    private CostFunctions() {}

    /**
     * Returns a cost function based on travel time in seconds.
     * Closed edges return {@link Double#POSITIVE_INFINITY}.
     */
    public static EdgeCostFunction travelTimeSeconds() {
        return edge -> edge.status() == EdgeStatus.CLOSED
                ? Double.POSITIVE_INFINITY
                : edge.weights().travelTime().toSeconds();
    }

    /**
     * Returns a cost function based on distance in kilometers.
     * Closed edges return {@link Double#POSITIVE_INFINITY}.
     */
    public static EdgeCostFunction distanceKm() {
        return edge -> edge.status() == EdgeStatus.CLOSED
                ? Double.POSITIVE_INFINITY
                : edge.weights().distanceKm();
    }

    /**
     * Returns a travel-time cost function with time-of-day congestion modeling.
     * The base travel time is multiplied by a congestion factor that varies by time of day.
     *
     * @param congestionProfile defines time-based multipliers (e.g., rush hour periods)
     * @param timeSupplier supplies the current simulation time
     * @return a time-aware cost function
     */
    public static EdgeCostFunction travelTimeWithCongestion(
            CongestionProfile congestionProfile,
            Supplier<Instant> timeSupplier
    ) {
        return new TimeAwareCostFunction(travelTimeSeconds(), congestionProfile, timeSupplier);
    }

    /**
     * Returns a travel-time cost function with time-of-day congestion modeling
     * using a specific timezone.
     *
     * @param congestionProfile defines time-based multipliers
     * @param timeSupplier supplies the current simulation time
     * @param zoneId the timezone for time-of-day calculations
     * @return a time-aware cost function
     */
    public static EdgeCostFunction travelTimeWithCongestion(
            CongestionProfile congestionProfile,
            Supplier<Instant> timeSupplier,
            ZoneId zoneId
    ) {
        return new TimeAwareCostFunction(travelTimeSeconds(), congestionProfile, timeSupplier, zoneId);
    }

    /**
     * Returns a distance-based cost function with time-of-day congestion modeling.
     *
     * @param congestionProfile defines time-based multipliers
     * @param timeSupplier supplies the current simulation time
     * @return a time-aware cost function
     */
    public static EdgeCostFunction distanceWithCongestion(
            CongestionProfile congestionProfile,
            Supplier<Instant> timeSupplier
    ) {
        return new TimeAwareCostFunction(distanceKm(), congestionProfile, timeSupplier);
    }
}

