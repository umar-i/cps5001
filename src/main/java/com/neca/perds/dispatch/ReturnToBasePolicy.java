package com.neca.perds.dispatch;

import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.DispatchCentreId;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.system.SystemSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes return-to-base routes for units that should return to their home dispatch centre.
 * 
 * <p>This policy determines whether a unit should return to its home base after completing
 * an incident, and computes the route if applicable. A unit is considered for return-to-base
 * when:
 * <ul>
 *   <li>The unit has a home dispatch centre assigned</li>
 *   <li>The unit is currently available (not assigned to an incident)</li>
 *   <li>The unit is not already at its home dispatch centre's location</li>
 *   <li>The distance to home exceeds a configurable threshold</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * ReturnToBasePolicy policy = ReturnToBasePolicy.builder()
 *     .router(new DijkstraRouter())
 *     .costFunction(CostFunctions.travelTimeSeconds())
 *     .minReturnDistanceKm(2.0)  // Only return if more than 2km away
 *     .build();
 * 
 * Optional<ReturnToBaseDecision> decision = policy.shouldReturnToBase(snapshot, unit);
 * }</pre>
 */
public final class ReturnToBasePolicy {
    /** Default minimum distance (km) before triggering return-to-base. */
    public static final double DEFAULT_MIN_RETURN_DISTANCE_KM = 0.0;

    private final Router router;
    private final EdgeCostFunction costFunction;
    private final double minReturnDistanceKm;

    private ReturnToBasePolicy(Router router, EdgeCostFunction costFunction, double minReturnDistanceKm) {
        this.router = Objects.requireNonNull(router, "router");
        this.costFunction = Objects.requireNonNull(costFunction, "costFunction");
        if (minReturnDistanceKm < 0) {
            throw new IllegalArgumentException("minReturnDistanceKm must be >= 0");
        }
        this.minReturnDistanceKm = minReturnDistanceKm;
    }

    /**
     * Determines if the given unit should return to its home dispatch centre.
     *
     * @param snapshot current system state
     * @param unit the unit to evaluate
     * @return a decision containing the route home, or empty if return is not applicable
     */
    public Optional<ReturnToBaseDecision> shouldReturnToBase(SystemSnapshot snapshot, ResponseUnit unit) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(unit, "unit");

        // Unit must have a home dispatch centre
        if (unit.homeDispatchCentreId().isEmpty()) {
            return Optional.empty();
        }

        // Unit must be available (not assigned)
        if (unit.assignedIncidentId().isPresent()) {
            return Optional.empty();
        }

        DispatchCentreId homeCentreId = unit.homeDispatchCentreId().get();
        Optional<DispatchCentre> homeCentre = findDispatchCentre(snapshot.dispatchCentres(), homeCentreId);
        if (homeCentre.isEmpty()) {
            return Optional.empty();
        }

        NodeId homeNodeId = homeCentre.get().nodeId();

        // Already at home?
        if (unit.currentNodeId().equals(homeNodeId)) {
            return Optional.empty();
        }

        // Compute route home
        Optional<Route> route = router.findRoute(
                snapshot.graph(),
                unit.currentNodeId(),
                homeNodeId,
                costFunction
        );

        if (route.isEmpty()) {
            return Optional.empty();
        }

        // Check minimum distance threshold
        if (route.get().totalDistanceKm() < minReturnDistanceKm) {
            return Optional.empty();
        }

        return Optional.of(new ReturnToBaseDecision(
                unit.id(),
                homeCentreId,
                homeNodeId,
                route.get(),
                "Returning to home dispatch centre: " + homeCentreId
        ));
    }

    /**
     * Finds all units that should return to their home dispatch centres.
     *
     * @param snapshot current system state
     * @return map of unit IDs to their return-to-base decisions
     */
    public Map<UnitId, ReturnToBaseDecision> findUnitsToReturn(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<UnitId, ReturnToBaseDecision> decisions = new HashMap<>();

        for (ResponseUnit unit : snapshot.units()) {
            shouldReturnToBase(snapshot, unit).ifPresent(decision ->
                    decisions.put(unit.id(), decision));
        }

        return Map.copyOf(decisions);
    }

    private static Optional<DispatchCentre> findDispatchCentre(
            Collection<DispatchCentre> centres, DispatchCentreId id) {
        for (DispatchCentre centre : centres) {
            if (centre.id().equals(id)) {
                return Optional.of(centre);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the router used by this policy.
     */
    public Router router() {
        return router;
    }

    /**
     * Returns the cost function used by this policy.
     */
    public EdgeCostFunction costFunction() {
        return costFunction;
    }

    /**
     * Returns the minimum distance threshold for return-to-base.
     */
    public double minReturnDistanceKm() {
        return minReturnDistanceKm;
    }

    /**
     * Creates a new builder for ReturnToBasePolicy.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating ReturnToBasePolicy instances.
     */
    public static final class Builder {
        private Router router;
        private EdgeCostFunction costFunction;
        private double minReturnDistanceKm = DEFAULT_MIN_RETURN_DISTANCE_KM;

        private Builder() {}

        /**
         * Sets the router for computing return routes.
         */
        public Builder router(Router router) {
            this.router = router;
            return this;
        }

        /**
         * Sets the cost function for route computation.
         */
        public Builder costFunction(EdgeCostFunction costFunction) {
            this.costFunction = costFunction;
            return this;
        }

        /**
         * Sets the minimum distance (km) before return-to-base is triggered.
         * Units closer to home than this threshold will not be asked to return.
         */
        public Builder minReturnDistanceKm(double minReturnDistanceKm) {
            this.minReturnDistanceKm = minReturnDistanceKm;
            return this;
        }

        /**
         * Builds the ReturnToBasePolicy.
         */
        public ReturnToBasePolicy build() {
            Objects.requireNonNull(router, "router must be set");
            Objects.requireNonNull(costFunction, "costFunction must be set");
            return new ReturnToBasePolicy(router, costFunction, minReturnDistanceKm);
        }
    }

    /**
     * Represents a decision for a unit to return to its home dispatch centre.
     *
     * @param unitId the unit that should return
     * @param dispatchCentreId the home dispatch centre
     * @param homeNodeId the node location of the dispatch centre
     * @param route the computed route home
     * @param reason human-readable explanation
     */
    public record ReturnToBaseDecision(
            UnitId unitId,
            DispatchCentreId dispatchCentreId,
            NodeId homeNodeId,
            Route route,
            String reason
    ) {
        public ReturnToBaseDecision {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(dispatchCentreId, "dispatchCentreId");
            Objects.requireNonNull(homeNodeId, "homeNodeId");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
