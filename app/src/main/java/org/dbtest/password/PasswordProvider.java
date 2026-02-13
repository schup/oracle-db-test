package org.dbtest.password;

import java.util.Map;

/**
 * Interface for password providers.
 * Implementations retrieve passwords from various sources (environment, vault, etc.).
 */
public interface PasswordProvider {
    
    /**
     * Retrieve password for a database connection.
     *
     * @param context Connection context with user, host, service/sid information
     * @return The password to use for the connection
     * @throws PasswordProviderException if password cannot be retrieved
     */
    String getPassword(PasswordContext context) throws PasswordProviderException;
    
    /**
     * Initialize the provider with configuration.
     *
     * @param config Provider-specific configuration
     * @throws PasswordProviderException if initialization fails
     */
    void initialize(Map<String, Object> config) throws PasswordProviderException;
}
