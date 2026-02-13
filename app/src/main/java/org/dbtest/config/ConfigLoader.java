package org.dbtest.config;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads and validates database configuration from YAML files.
 * Implements fail-fast validation - collects all errors before reporting.
 */
@Slf4j
public class ConfigLoader {
    
    private static final String DEFAULT_CONFIG_FILE = "connections.yaml";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int MIN_TIMEOUT = 1;
    private static final int MAX_TIMEOUT = 300;
    
    /**
     * Loads configuration from the default file (connections.yaml).
     */
    public DatabaseConfig load() throws ConfigurationException {
        return load(DEFAULT_CONFIG_FILE);
    }
    
    /**
     * Loads configuration from the specified file path.
     */
    public DatabaseConfig load(String configPath) throws ConfigurationException {
        Path path = Path.of(configPath);
        
        if (!Files.exists(path)) {
            throw new ConfigurationException(
                String.format("Configuration file not found: %s%n%n" +
                    "Please ensure the file exists in the current directory, or specify the path:%n" +
                    "  java -jar oracle-jdbc-test.jar --config=/path/to/connections.yaml",
                    configPath));
        }
        
        log.info("Loading configuration from: {}", configPath);
        
        try (InputStream input = new FileInputStream(path.toFile())) {
            return loadFromStream(input);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("Configuration file not found: " + configPath, e);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read configuration file: " + configPath, e);
        }
    }
    
    /**
     * Loads configuration from an input stream.
     */
    @SuppressWarnings("unchecked")
    public DatabaseConfig loadFromStream(InputStream input) throws ConfigurationException {
        Yaml yaml = new Yaml(new LoaderOptions());
        
        Map<String, Object> yamlData;
        try {
            yamlData = yaml.load(input);
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse YAML: " + e.getMessage(), e);
        }
        
        if (yamlData == null) {
            throw new ConfigurationException("Configuration file is empty");
        }
        
        DatabaseConfig config = parseConfig(yamlData);
        
        // Validate and collect all errors
        List<ValidationError> errors = validate(config);
        if (!errors.isEmpty()) {
            throw new ConfigurationException(formatValidationErrors(errors));
        }
        
        return config;
    }
    
    @SuppressWarnings("unchecked")
    private DatabaseConfig parseConfig(Map<String, Object> yamlData) throws ConfigurationException {
        DatabaseConfig.DatabaseConfigBuilder builder = DatabaseConfig.builder();
        
        // Parse connections
        List<Map<String, Object>> connectionsList = (List<Map<String, Object>>) yamlData.get("connections");
        if (connectionsList != null) {
            List<ConnectionDefinition> connections = new ArrayList<>();
            for (Map<String, Object> connMap : connectionsList) {
                connections.add(parseConnection(connMap));
            }
            builder.connections(connections);
        }
        
        // Parse password providers
        Map<String, Map<String, Object>> providersMap = 
            (Map<String, Map<String, Object>>) yamlData.get("password_providers");
        if (providersMap != null) {
            Map<String, PasswordProviderConfig> providers = new HashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : providersMap.entrySet()) {
                providers.put(entry.getKey(), parsePasswordProvider(entry.getValue()));
            }
            builder.passwordProviders(providers);
        }
        
        return builder.build();
    }
    
    @SuppressWarnings("unchecked")
    private ConnectionDefinition parseConnection(Map<String, Object> connMap) {
        ConnectionDefinition.ConnectionDefinitionBuilder builder = ConnectionDefinition.builder();
        
        builder.name((String) connMap.get("name"));
        builder.host((String) connMap.get("host"));
        
        Object portObj = connMap.get("port");
        if (portObj != null) {
            builder.port(((Number) portObj).intValue());
        }
        
        builder.service((String) connMap.get("service"));
        builder.sid((String) connMap.get("sid"));
        builder.username((String) connMap.get("username"));
        builder.password((String) connMap.get("password"));
        builder.passwordProvider((String) connMap.get("password_provider"));
        
        List<String> tags = (List<String>) connMap.get("tags");
        if (tags != null) {
            builder.tags(new ArrayList<>(tags));
        }
        
        List<String> groups = (List<String>) connMap.get("groups");
        if (groups != null) {
            builder.groups(new ArrayList<>(groups));
        }
        
        builder.testQuery((String) connMap.get("test_query"));
        
        Object timeoutObj = connMap.get("timeout");
        if (timeoutObj != null) {
            builder.timeout(((Number) timeoutObj).intValue());
        }
        
        Object enabledObj = connMap.get("enabled");
        if (enabledObj != null) {
            builder.enabled((Boolean) enabledObj);
        }
        
        return builder.build();
    }
    
    @SuppressWarnings("unchecked")
    private PasswordProviderConfig parsePasswordProvider(Map<String, Object> providerMap) {
        PasswordProviderConfig.PasswordProviderConfigBuilder builder = PasswordProviderConfig.builder();
        
        builder.type((String) providerMap.get("type"));
        builder.className((String) providerMap.get("class"));
        
        Map<String, Object> config = (Map<String, Object>) providerMap.get("config");
        if (config != null) {
            builder.config(new HashMap<>(config));
        }
        
        return builder.build();
    }
    
    /**
     * Validates the configuration and returns all errors found.
     */
    public List<ValidationError> validate(DatabaseConfig config) {
        List<ValidationError> errors = new ArrayList<>();
        
        if (config.getConnections() == null || config.getConnections().isEmpty()) {
            errors.add(ValidationError.global("No connections defined in configuration"));
            return errors;
        }
        
        // Track connection names for duplicate detection (case-insensitive)
        Map<String, String> namesSeen = new HashMap<>();
        
        for (ConnectionDefinition conn : config.getConnections()) {
            validateConnection(conn, namesSeen, config.getPasswordProviders(), errors);
        }
        
        return errors;
    }
    
    private void validateConnection(ConnectionDefinition conn, 
                                    Map<String, String> namesSeen,
                                    Map<String, PasswordProviderConfig> providers,
                                    List<ValidationError> errors) {
        String connName = conn.getName();
        
        // Validate name
        if (connName == null || connName.isBlank()) {
            errors.add(ValidationError.forField(connName, "name", "Connection name is required"));
            connName = "<unnamed>";
        } else {
            // Check name format
            if (connName.length() > MAX_NAME_LENGTH) {
                errors.add(ValidationError.forField(connName, "name", 
                    String.format("Connection name must be at most %d characters", MAX_NAME_LENGTH)));
            }
            if (!NAME_PATTERN.matcher(connName).matches()) {
                errors.add(ValidationError.forField(connName, "name", 
                    "Connection name can only contain alphanumeric characters, dash, underscore, and dot"));
            }
            
            // Check for duplicates (case-insensitive)
            String lowerName = connName.toLowerCase();
            if (namesSeen.containsKey(lowerName)) {
                errors.add(ValidationError.forConnection(connName, 
                    String.format("Duplicate connection name '%s' (case-insensitive match with '%s')", 
                        connName, namesSeen.get(lowerName))));
            } else {
                namesSeen.put(lowerName, connName);
            }
        }
        
        // Validate host
        if (conn.getHost() == null || conn.getHost().isBlank()) {
            errors.add(ValidationError.forField(connName, "host", "Host is required"));
        }
        
        // Validate port
        if (conn.getPort() != null) {
            if (conn.getPort() < MIN_PORT || conn.getPort() > MAX_PORT) {
                errors.add(ValidationError.forField(connName, "port", 
                    String.format("Port must be between %d and %d", MIN_PORT, MAX_PORT)));
            }
        }
        
        // Validate username
        if (conn.getUsername() == null || conn.getUsername().isBlank()) {
            errors.add(ValidationError.forField(connName, "username", "Username is required"));
        }
        
        // Validate service XOR sid
        boolean hasService = conn.getService() != null && !conn.getService().isBlank();
        boolean hasSid = conn.getSid() != null && !conn.getSid().isBlank();
        
        if (hasService && hasSid) {
            errors.add(ValidationError.forConnection(connName, 
                "Both 'service' and 'sid' are specified. Use only one."));
        } else if (!hasService && !hasSid) {
            errors.add(ValidationError.forConnection(connName, 
                "Either 'service' or 'sid' must be specified."));
        }
        
        // Validate password source
        boolean hasPassword = conn.getPassword() != null && !conn.getPassword().isBlank();
        boolean hasPasswordProvider = conn.getPasswordProvider() != null && !conn.getPasswordProvider().isBlank();
        
        if (!hasPassword && !hasPasswordProvider) {
            errors.add(ValidationError.forConnection(connName, 
                "No password source specified. Provide 'password' or 'password_provider'."));
        }
        
        // If password provider is specified, check it exists
        if (hasPasswordProvider && providers != null) {
            if (!providers.containsKey(conn.getPasswordProvider())) {
                errors.add(ValidationError.forConnection(connName, 
                    String.format("Password provider '%s' not found in password_providers configuration.", 
                        conn.getPasswordProvider())));
            }
        }
        
        // Validate timeout
        if (conn.getTimeout() != null) {
            if (conn.getTimeout() < MIN_TIMEOUT || conn.getTimeout() > MAX_TIMEOUT) {
                errors.add(ValidationError.forField(connName, "timeout", 
                    String.format("Timeout must be between %d and %d seconds", MIN_TIMEOUT, MAX_TIMEOUT)));
            }
        }
    }
    
    private String formatValidationErrors(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Validation Failed:\n\n");
        
        for (ValidationError error : errors) {
            sb.append(error.format()).append("\n\n");
        }
        
        sb.append(String.format("Found %d validation error(s). Please fix configuration and try again.", 
            errors.size()));
        
        return sb.toString();
    }
    
    /**
     * Exception thrown when configuration loading or validation fails.
     */
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
        
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
