package com.neca.perds.routing;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CongestionProfile}.
 */
final class CongestionProfileTest {

    @Test
    void returnsDefaultMultiplierWhenNoPeriods() {
        CongestionProfile profile = CongestionProfile.none();
        
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(8, 0)));
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(12, 0)));
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(18, 0)));
        assertFalse(profile.hasPeriods());
    }

    @Test
    void returnsMultiplierForTimeWithinPeriod() {
        CongestionProfile profile = CongestionProfile.builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .build();
        
        assertEquals(1.5, profile.multiplierAt(LocalTime.of(7, 0)));   // start boundary
        assertEquals(1.5, profile.multiplierAt(LocalTime.of(8, 0)));   // middle
        assertEquals(1.5, profile.multiplierAt(LocalTime.of(8, 59)));  // near end
        assertTrue(profile.hasPeriods());
    }

    @Test
    void returnsDefaultMultiplierOutsidePeriods() {
        CongestionProfile profile = CongestionProfile.builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .build();
        
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(6, 59)));  // before
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(9, 0)));   // end boundary (exclusive)
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(12, 0)));  // well after
    }

    @Test
    void handlesMultiplePeriods() {
        CongestionProfile profile = CongestionProfile.builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .period(LocalTime.of(17, 0), LocalTime.of(19, 0), 1.7)
                .build();
        
        assertEquals(1.5, profile.multiplierAt(LocalTime.of(8, 0)));   // morning rush
        assertEquals(1.7, profile.multiplierAt(LocalTime.of(18, 0)));  // evening rush
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(12, 0)));  // between rushes
    }

    @Test
    void standardRushHourProfile() {
        CongestionProfile profile = CongestionProfile.standardRushHour();
        
        // Morning rush (07:00-09:00): 1.5x
        assertEquals(1.5, profile.multiplierAt(LocalTime.of(7, 30)));
        
        // Evening rush (17:00-19:00): 1.7x
        assertEquals(1.7, profile.multiplierAt(LocalTime.of(18, 0)));
        
        // Off-peak
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(12, 0)));
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(22, 0)));
    }

    @Test
    void handlesOvernightPeriod() {
        CongestionProfile profile = CongestionProfile.builder()
                .period(LocalTime.of(23, 0), LocalTime.of(1, 0), 0.5)  // late night discount
                .build();
        
        assertEquals(0.5, profile.multiplierAt(LocalTime.of(23, 30)));
        assertEquals(0.5, profile.multiplierAt(LocalTime.of(0, 30)));
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(1, 0)));   // end boundary
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(12, 0)));
    }

    @Test
    void rejectsInvalidMultipliers() {
        assertThrows(IllegalArgumentException.class, () ->
                CongestionProfile.builder()
                        .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 0.05));  // below MIN
        
        assertThrows(IllegalArgumentException.class, () ->
                CongestionProfile.builder()
                        .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 11.0));  // above MAX
        
        assertThrows(IllegalArgumentException.class, () ->
                CongestionProfile.builder()
                        .period(LocalTime.of(7, 0), LocalTime.of(9, 0), Double.NaN));
        
        assertThrows(IllegalArgumentException.class, () ->
                CongestionProfile.builder()
                        .period(LocalTime.of(7, 0), LocalTime.of(9, 0), Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNullTime() {
        CongestionProfile profile = CongestionProfile.standardRushHour();
        assertThrows(NullPointerException.class, () -> profile.multiplierAt(null));
    }

    @Test
    void toStringShowsProfileDetails() {
        CongestionProfile empty = CongestionProfile.none();
        assertEquals("CongestionProfile{none}", empty.toString());
        
        CongestionProfile withPeriods = CongestionProfile.builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .build();
        assertTrue(withPeriods.toString().contains("07:00"));
        assertTrue(withPeriods.toString().contains("1.5"));
    }

    @Test
    void periodsAreImmutable() {
        CongestionProfile profile = CongestionProfile.builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .build();
        
        assertThrows(UnsupportedOperationException.class, () ->
                profile.periods().clear());
    }

    @Test
    void timePeriodContainsLogic() {
        // Normal period
        var normalPeriod = new CongestionProfile.TimePeriod(
                LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5);
        assertTrue(normalPeriod.contains(LocalTime.of(7, 0)));
        assertTrue(normalPeriod.contains(LocalTime.of(8, 30)));
        assertFalse(normalPeriod.contains(LocalTime.of(9, 0)));  // exclusive end
        assertFalse(normalPeriod.contains(LocalTime.of(6, 59)));
        
        // Overnight period
        var overnightPeriod = new CongestionProfile.TimePeriod(
                LocalTime.of(22, 0), LocalTime.of(2, 0), 0.8);
        assertTrue(overnightPeriod.contains(LocalTime.of(23, 0)));
        assertTrue(overnightPeriod.contains(LocalTime.of(0, 0)));
        assertTrue(overnightPeriod.contains(LocalTime.of(1, 59)));
        assertFalse(overnightPeriod.contains(LocalTime.of(2, 0)));  // exclusive end
        assertFalse(overnightPeriod.contains(LocalTime.of(12, 0)));
    }

    @Test
    void edgeCaseAtMidnight() {
        CongestionProfile profile = CongestionProfile.builder()
                .period(LocalTime.of(0, 0), LocalTime.of(5, 0), 0.7)  // early morning
                .build();
        
        assertEquals(0.7, profile.multiplierAt(LocalTime.of(0, 0)));
        assertEquals(0.7, profile.multiplierAt(LocalTime.of(3, 0)));
        assertEquals(1.0, profile.multiplierAt(LocalTime.of(5, 0)));
    }
}
