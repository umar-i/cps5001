package com.neca.perds.dispatch;

import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.DispatchCentreId;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.system.SystemSnapshot;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility for computing dispatch centre preferences when selecting units.
 * 
 * <p>When multiple units are equally good candidates for dispatch (same travel time/cost),
 * this utility helps prefer units that will end up closer to their home dispatch centre
 * after completing the incident, or units that are starting from their home base.
 * 
 * <p>This supports more realistic emergency response behavior where dispatchers try to
 * maintain coverage and minimize repositioning costs.
 */
public final class DispatchCentrePreference {
    
    private DispatchCentrePreference() {}

    /**
     * Computes a preference score for dispatching a unit to an incident location.
     * 
     * <p>Lower scores are better. The score considers:
     * <ul>
     *   <li>Distance from incident location back to unit's home base (if any)</li>
     *   <li>Whether the unit is currently at its home base (bonus for units not at home)</li>
     * </ul>
     * 
     * <p>Units without a home dispatch centre receive a neutral score of 0.0.
     *
     * @param snapshot current system state
     * @param unit the unit being considered
     * @param incidentLocation the incident location
     * @param router for computing return distance
     * @param costFunction for route computation
     * @return preference score (lower is better, 0.0 for units without home base)
     */
    public static double computePreferenceScore(
            SystemSnapshot snapshot,
            ResponseUnit unit,
            NodeId incidentLocation,
            Router router,
            EdgeCostFunction costFunction
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(incidentLocation, "incidentLocation");
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(costFunction, "costFunction");

        if (unit.homeDispatchCentreId().isEmpty()) {
            return 0.0; // Neutral score for units without home base
        }

        DispatchCentreId homeCentreId = unit.homeDispatchCentreId().get();
        Optional<DispatchCentre> homeCentre = findDispatchCentre(snapshot.dispatchCentres(), homeCentreId);
        if (homeCentre.isEmpty()) {
            return 0.0; // Home centre not found, neutral score
        }

        NodeId homeNodeId = homeCentre.get().nodeId();

        // Compute distance from incident location back to home
        Optional<Route> returnRoute = router.findRoute(
                snapshot.graph(),
                incidentLocation,
                homeNodeId,
                costFunction
        );

        double returnCost = returnRoute.map(Route::totalCost).orElse(Double.MAX_VALUE);

        // Bonus: prefer units NOT currently at home (they're already out, so use them)
        // This helps maintain home base coverage
        boolean atHome = unit.currentNodeId().equals(homeNodeId);
        double atHomePenalty = atHome ? 1000.0 : 0.0; // Large penalty for being at home

        return returnCost + atHomePenalty;
    }

    /**
     * Compares two units based on dispatch centre preference.
     * Returns negative if unit1 is preferred, positive if unit2 is preferred, 0 if equal.
     *
     * @param snapshot current system state
     * @param unit1 first unit
     * @param unit2 second unit
     * @param incidentLocation the incident location
     * @param router for computing return distance
     * @param costFunction for route computation
     * @return comparison result
     */
    public static int compareByPreference(
            SystemSnapshot snapshot,
            ResponseUnit unit1,
            ResponseUnit unit2,
            NodeId incidentLocation,
            Router router,
            EdgeCostFunction costFunction
    ) {
        double score1 = computePreferenceScore(snapshot, unit1, incidentLocation, router, costFunction);
        double score2 = computePreferenceScore(snapshot, unit2, incidentLocation, router, costFunction);
        return Double.compare(score1, score2);
    }

    /**
     * Returns true if the unit is currently at its home dispatch centre.
     */
    public static boolean isAtHomeBase(SystemSnapshot snapshot, ResponseUnit unit) {
        if (unit.homeDispatchCentreId().isEmpty()) {
            return false;
        }

        Optional<DispatchCentre> homeCentre = findDispatchCentre(
                snapshot.dispatchCentres(),
                unit.homeDispatchCentreId().get()
        );

        return homeCentre
                .map(centre -> unit.currentNodeId().equals(centre.nodeId()))
                .orElse(false);
    }

    /**
     * Returns the home node for a unit, if available.
     */
    public static Optional<NodeId> getHomeNode(SystemSnapshot snapshot, ResponseUnit unit) {
        if (unit.homeDispatchCentreId().isEmpty()) {
            return Optional.empty();
        }

        return findDispatchCentre(snapshot.dispatchCentres(), unit.homeDispatchCentreId().get())
                .map(DispatchCentre::nodeId);
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
}
