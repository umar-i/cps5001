package com.neca.perds.config;

/**
 * Exception thrown when configuration loading or validation fails.
 */
public final class ConfigurationException extends RuntimeException {
    
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
