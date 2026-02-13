package org.dbtest.config;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single database connection definition from YAML configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionDefinition {
    
    private String name;
    private String host;
    
    @Builder.Default
    private Integer port = 1521;
    
    private String service;
    private String sid;
    private String username;
    
    @ToString.Exclude
    private String password;
    
    @ToString.Exclude
    private String passwordProvider;
    
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    @Builder.Default
    private List<String> groups = new ArrayList<>();
    
    private String testQuery;
    
    @Builder.Default
    private Integer timeout = 10;
    
    @Builder.Default
    private Boolean enabled = true;
    
    /**
     * Returns the service name or SID, whichever is defined.
     */
    public String getServiceOrSid() {
        return service != null ? service : sid;
    }
    
    /**
     * Returns true if this connection uses a service name (vs SID).
     */
    public boolean usesServiceName() {
        return service != null && !service.isBlank();
    }
    
    /**
     * Returns the test query, defaulting to SELECT 1 FROM DUAL if not specified.
     */
    public String getEffectiveTestQuery() {
        return testQuery != null && !testQuery.isBlank() ? testQuery : "SELECT 1 FROM DUAL";
    }
    
    /**
     * Returns the primary tag (first tag) or "untagged" if no tags.
     */
    public String getPrimaryTag() {
        return tags != null && !tags.isEmpty() ? tags.get(0) : "untagged";
    }
}
