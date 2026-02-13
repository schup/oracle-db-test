package org.dbtest.password;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Password provider that resolves passwords from environment variables.
 * Supports ${VAR_NAME} syntax in password fields.
 */
@Slf4j
public class EnvironmentVariablePasswordProvider implements PasswordProvider {
    
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    
    @Override
    public void initialize(Map<String, Object> config) throws PasswordProviderException {
        // No initialization needed
        log.debug("EnvironmentVariablePasswordProvider initialized");
    }
    
    @Override
    public String getPassword(PasswordContext context) throws PasswordProviderException {
        String passwordTemplate = context.getPassword();
        
        if (passwordTemplate == null || passwordTemplate.isBlank()) {
            throw new PasswordProviderException(
                "No password configured for connection: " + context.getConnectionName());
        }
        
        return resolveEnvironmentVariables(passwordTemplate, context.getConnectionName());
    }
    
    /**
     * Resolves environment variables in the given string.
     * Supports ${VAR_NAME} syntax.
     */
    public String resolveEnvironmentVariables(String value, String connectionName) 
            throws PasswordProviderException {
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String envValue = System.getenv(varName);
            
            if (envValue == null) {
                throw new PasswordProviderException(
                    String.format("Environment variable '%s' not set for connection '%s'", 
                        varName, connectionName));
            }
            
            log.debug("Resolved environment variable '{}' for connection: {}", 
                varName, connectionName);
            // NEVER log the actual value!
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Checks if a string contains environment variable references.
     */
    public static boolean containsEnvVarReference(String value) {
        return value != null && ENV_VAR_PATTERN.matcher(value).find();
    }
}
