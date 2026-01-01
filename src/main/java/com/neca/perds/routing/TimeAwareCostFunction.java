package com.neca.perds.routing;

import com.neca.perds.graph.Edge;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An EdgeCostFunction that applies time-of-day congestion multipliers to a base cost function.
 * 
 * <p>This class wraps a base cost function and multiplies its result by a congestion factor
 * that varies based on the current time of day. The time is obtained from a supplied clock,
 * allowing for integration with simulation time.
 * 
 * <p>Example usage:
 * <pre>{@code
 * CongestionProfile profile = CongestionProfile.standardRushHour();
 * SimulationClock clock = ...;
 * EdgeCostFunction baseCost = CostFunctions.travelTimeSeconds();
 * EdgeCostFunction congestionAware = new TimeAwareCostFunction(baseCost, profile, clock::now);
 * }</pre>
 * 
 * <p>The effective cost is: {@code baseCost(edge) * congestionProfile.multiplierAt(timeOfDay)}
 */
public final class TimeAwareCostFunction implements EdgeCostFunction {
    private final EdgeCostFunction baseCostFunction;
    private final CongestionProfile congestionProfile;
    private final Supplier<Instant> timeSupplier;
    private final ZoneId zoneId;

    /**
     * Creates a time-aware cost function using the system default timezone.
     *
     * @param baseCostFunction the underlying cost function to apply multipliers to
     * @param congestionProfile the congestion profile defining time-based multipliers
     * @param timeSupplier supplies the current simulation time
     */
    public TimeAwareCostFunction(
            EdgeCostFunction baseCostFunction,
            CongestionProfile congestionProfile,
            Supplier<Instant> timeSupplier
    ) {
        this(baseCostFunction, congestionProfile, timeSupplier, ZoneId.systemDefault());
    }

    /**
     * Creates a time-aware cost function with a specific timezone.
     *
     * @param baseCostFunction the underlying cost function to apply multipliers to
     * @param congestionProfile the congestion profile defining time-based multipliers
     * @param timeSupplier supplies the current simulation time
     * @param zoneId the timezone to use for converting Instant to LocalTime
     */
    public TimeAwareCostFunction(
            EdgeCostFunction baseCostFunction,
            CongestionProfile congestionProfile,
            Supplier<Instant> timeSupplier,
            ZoneId zoneId
    ) {
        this.baseCostFunction = Objects.requireNonNull(baseCostFunction, "baseCostFunction");
        this.congestionProfile = Objects.requireNonNull(congestionProfile, "congestionProfile");
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
    }

    @Override
    public double cost(Edge edge) {
        double baseCost = baseCostFunction.cost(edge);
        
        // Handle special cases (infinity for closed edges, etc.)
        if (Double.isInfinite(baseCost) || Double.isNaN(baseCost)) {
            return baseCost;
        }
        
        Instant now = timeSupplier.get();
        LocalTime timeOfDay = now.atZone(zoneId).toLocalTime();
        double multiplier = congestionProfile.multiplierAt(timeOfDay);
        
        return baseCost * multiplier;
    }

    /**
     * Returns the congestion profile used by this cost function.
     */
    public CongestionProfile congestionProfile() {
        return congestionProfile;
    }

    /**
     * Returns the base cost function.
     */
    public EdgeCostFunction baseCostFunction() {
        return baseCostFunction;
    }

    /**
     * Returns the current congestion multiplier based on the current time.
     */
    public double currentMultiplier() {
        Instant now = timeSupplier.get();
        LocalTime timeOfDay = now.atZone(zoneId).toLocalTime();
        return congestionProfile.multiplierAt(timeOfDay);
    }
}
