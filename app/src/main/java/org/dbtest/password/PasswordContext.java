package org.dbtest.password;

import lombok.Builder;
import lombok.Value;
import lombok.ToString;
import org.dbtest.config.ConnectionDefinition;

/**
 * Context object passed to password providers containing connection information.
 * Password fields are excluded from toString() for security.
 */
@Value
@Builder
public class PasswordContext {
    
    String username;
    String host;
    Integer port;
    String service;
    String sid;
    String connectionName;
    
    /**
     * The raw password value from configuration (may be ${VAR} reference).
     */
    @ToString.Exclude
    String password;
    
    /**
     * Creates a PasswordContext from a ConnectionDefinition.
     */
    public static PasswordContext fromConnection(ConnectionDefinition conn) {
        return PasswordContext.builder()
            .username(conn.getUsername())
            .host(conn.getHost())
            .port(conn.getPort())
            .service(conn.getService())
            .sid(conn.getSid())
            .connectionName(conn.getName())
            .password(conn.getPassword())
            .build();
    }
    
    /**
     * Returns the service name or SID, whichever is defined.
     */
    public String getServiceOrSid() {
        return service != null ? service : sid;
    }
}
