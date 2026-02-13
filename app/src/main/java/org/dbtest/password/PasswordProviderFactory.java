package org.dbtest.password;

import lombok.extern.slf4j.Slf4j;
import org.dbtest.config.ConnectionDefinition;
import org.dbtest.config.PasswordProviderConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating and managing password providers.
 * Determines the appropriate provider based on connection configuration.
 */
@Slf4j
public class PasswordProviderFactory {
    
    private final Map<String, PasswordProvider> namedProviders = new HashMap<>();
    private final DirectPasswordProvider directProvider = new DirectPasswordProvider();
    private final EnvironmentVariablePasswordProvider envVarProvider = new EnvironmentVariablePasswordProvider();
    
    /**
     * Initializes named password providers from configuration.
     */
    public void initializeProviders(Map<String, PasswordProviderConfig> providerConfigs) 
            throws PasswordProviderException {
        
        if (providerConfigs == null || providerConfigs.isEmpty()) {
            log.debug("No custom password providers configured");
            return;
        }
        
        for (Map.Entry<String, PasswordProviderConfig> entry : providerConfigs.entrySet()) {
            String name = entry.getKey();
            PasswordProviderConfig config = entry.getValue();
            
            try {
                PasswordProvider provider = createProvider(config);
                provider.initialize(config.getConfig());
                namedProviders.put(name, provider);
                log.info("Initialized password provider: {}", name);
            } catch (Exception e) {
                throw new PasswordProviderException(
                    "Failed to initialize password provider '" + name + "': " + e.getMessage(), e);
            }
        }
    }
    
    private PasswordProvider createProvider(PasswordProviderConfig config) 
            throws PasswordProviderException {
        
        String type = config.getType();
        
        if ("custom".equals(type)) {
            return createCustomProvider(config.getClassName());
        }
        
        // For now, we only support built-in providers
        // Vault and other providers would be added here
        throw new PasswordProviderException("Unknown password provider type: " + type);
    }
    
    private PasswordProvider createCustomProvider(String className) throws PasswordProviderException {
        if (className == null || className.isBlank()) {
            throw new PasswordProviderException("Custom provider requires 'class' to be specified");
        }
        
        try {
            log.info("Loading custom password provider: {}", className);
            Class<?> clazz = Class.forName(className);
            return (PasswordProvider) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new PasswordProviderException(
                "Custom provider class not found: " + className, e);
        } catch (Exception e) {
            throw new PasswordProviderException(
                "Failed to instantiate custom provider: " + className, e);
        }
    }
    
    /**
     * Retrieves the password for a connection using the appropriate provider.
     * 
     * Resolution priority:
     * 1. If password_provider is specified, use that named provider
     * 2. If password contains ${...}, use environment variable provider
     * 3. Otherwise, use direct password provider
     */
    public String getPassword(ConnectionDefinition conn) throws PasswordProviderException {
        PasswordContext context = PasswordContext.fromConnection(conn);
        
        // 1. Check for named password provider
        if (conn.getPasswordProvider() != null && !conn.getPasswordProvider().isBlank()) {
            String providerName = conn.getPasswordProvider();
            PasswordProvider provider = namedProviders.get(providerName);
            
            if (provider == null) {
                throw new PasswordProviderException(
                    "Password provider not found: " + providerName);
            }
            
            log.debug("Using named password provider '{}' for connection: {}", 
                providerName, conn.getName());
            return provider.getPassword(context);
        }
        
        // 2. Check for environment variable reference
        String password = conn.getPassword();
        if (EnvironmentVariablePasswordProvider.containsEnvVarReference(password)) {
            log.debug("Using environment variable provider for connection: {}", conn.getName());
            return envVarProvider.getPassword(context);
        }
        
        // 3. Use direct password
        log.debug("Using direct password for connection: {}", conn.getName());
        return directProvider.getPassword(context);
    }
}
