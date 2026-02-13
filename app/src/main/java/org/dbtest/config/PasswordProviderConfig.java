package org.dbtest.config;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a password provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordProviderConfig {
    
    private String type;
    
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();
    
    // For custom providers
    private String className;
}
