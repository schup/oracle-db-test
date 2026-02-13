package org.dbtest.password;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Password provider that returns the password directly from the configuration.
 * Used when the password is specified as a plain string (not an environment variable).
 */
@Slf4j
public class DirectPasswordProvider implements PasswordProvider {
    
    @Override
    public void initialize(Map<String, Object> config) throws PasswordProviderException {
        // No initialization needed for direct passwords
        log.debug("DirectPasswordProvider initialized");
    }
    
    @Override
    public String getPassword(PasswordContext context) throws PasswordProviderException {
        String password = context.getPassword();
        
        if (password == null || password.isBlank()) {
            throw new PasswordProviderException(
                "No password configured for connection: " + context.getConnectionName());
        }
        
        log.debug("Retrieved direct password for connection: {}", context.getConnectionName());
        // NEVER log the actual password!
        
        return password;
    }
}
