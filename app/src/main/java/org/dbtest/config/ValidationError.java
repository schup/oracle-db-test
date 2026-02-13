package org.dbtest.config;

import lombok.Value;

/**
 * Represents a single validation error found during configuration validation.
 */
@Value
public class ValidationError {
    
    /**
     * The connection name this error relates to, or null for global errors.
     */
    String connectionName;
    
    /**
     * The field name that failed validation, or null for connection-level errors.
     */
    String fieldName;
    
    /**
     * Human-readable error message.
     */
    String message;
    
    /**
     * Creates a validation error for a specific connection.
     */
    public static ValidationError forConnection(String connectionName, String message) {
        return new ValidationError(connectionName, null, message);
    }
    
    /**
     * Creates a validation error for a specific field in a connection.
     */
    public static ValidationError forField(String connectionName, String fieldName, String message) {
        return new ValidationError(connectionName, fieldName, message);
    }
    
    /**
     * Creates a global validation error (not related to a specific connection).
     */
    public static ValidationError global(String message) {
        return new ValidationError(null, null, message);
    }
    
    /**
     * Returns a formatted error message for display.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (connectionName != null) {
            sb.append("ERROR [connection: ").append(connectionName).append("]");
            if (fieldName != null) {
                sb.append(" [field: ").append(fieldName).append("]");
            }
            sb.append(":\n  - ").append(message);
        } else {
            sb.append("ERROR: ").append(message);
        }
        return sb.toString();
    }
}
