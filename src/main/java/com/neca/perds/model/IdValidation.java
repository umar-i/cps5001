package com.neca.perds.model;

import java.util.regex.Pattern;

/**
 * Utility class for validating ID formats across the system.
 * Provides consistent validation patterns for different ID types.
 */
public final class IdValidation {
    
    /**
     * Pattern for general alphanumeric IDs.
     * Allows letters, digits, underscores, and hyphens.
     * Must start with a letter or underscore.
     */
    private static final Pattern ALPHANUMERIC_ID_PATTERN = 
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]*$");
    
    /**
     * Pattern for unit IDs (e.g., U1, U2, F1, AMB1).
     * Allows letter prefix followed by optional alphanumeric suffix.
     */
    private static final Pattern UNIT_ID_PATTERN = 
            Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");
    
    /**
     * Pattern for incident IDs (e.g., I1, I2, INC001).
     * Allows letter prefix followed by optional alphanumeric suffix.
     */
    private static final Pattern INCIDENT_ID_PATTERN = 
            Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");
    
    private IdValidation() {
        // Utility class
    }
    
    /**
     * Validates a general alphanumeric ID format.
     * @param value the ID value to validate
     * @param idTypeName the name of the ID type for error messages
     * @throws IllegalArgumentException if the format is invalid
     */
    public static void validateAlphanumericId(String value, String idTypeName) {
        if (!ALPHANUMERIC_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    idTypeName + " must be alphanumeric (letters, digits, underscores, hyphens), " +
                    "starting with a letter or underscore: " + value);
        }
    }
    
    /**
     * Validates a unit ID format.
     * @param value the ID value to validate
     * @throws IllegalArgumentException if the format is invalid
     */
    public static void validateUnitId(String value) {
        if (!UNIT_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "UnitId must start with a letter and contain only alphanumeric characters, " +
                    "underscores, or hyphens: " + value);
        }
    }
    
    /**
     * Validates an incident ID format.
     * @param value the ID value to validate
     * @throws IllegalArgumentException if the format is invalid
     */
    public static void validateIncidentId(String value) {
        if (!INCIDENT_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "IncidentId must start with a letter and contain only alphanumeric characters, " +
                    "underscores, or hyphens: " + value);
        }
    }
}
