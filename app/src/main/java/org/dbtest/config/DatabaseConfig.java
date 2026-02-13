package org.dbtest.config;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root configuration class representing the entire YAML configuration file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConfig {
    
    @Builder.Default
    private List<ConnectionDefinition> connections = new ArrayList<>();
    
    @Builder.Default
    private Map<String, PasswordProviderConfig> passwordProviders = new HashMap<>();
}
