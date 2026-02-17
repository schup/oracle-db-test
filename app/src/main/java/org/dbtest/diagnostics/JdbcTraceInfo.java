package org.dbtest.diagnostics;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Contains connection details extracted from Oracle JDBC trace logs.
 * Useful for diagnosing issues in load-balanced or RAC environments
 * where the actual data connection may differ from the configured listener.
 */
@Value
@Builder
public class JdbcTraceInfo {
    
    /**
     * Configured listener host being connected to.
     */
    String listenerHost;
    
    /**
     * Configured listener port.
     */
    Integer listenerPort;
    
    /**
     * Actual data connection host (may differ from listener in RAC/load-balanced setups).
     */
    String dataHost;
    
    /**
     * Actual data connection port.
     */
    Integer dataPort;
    
    /**
     * Resolved IP address of the data connection.
     */
    String dataIp;
    
    /**
     * Local port used for the connection.
     */
    Integer localPort;
    
    /**
     * Server host identity from session properties (AUTH_SC_SERVER_HOST).
     */
    String serverHost;
    
    /**
     * TCP connect time in milliseconds.
     */
    Long connectTimeMs;
    
    /**
     * Whether the TCP connection succeeded.
     */
    Boolean connectSucceeded;
    
    /**
     * Raw captured log messages (for verbose output).
     */
    List<String> rawLogs;
    
    /**
     * Returns true if the actual data connection differs from the configured listener.
     */
    public boolean hasRedirect() {
        if (listenerHost == null || dataHost == null) {
            return false;
        }
        boolean hostDiffers = !listenerHost.equalsIgnoreCase(dataHost) 
            && (dataIp == null || !dataIp.equals(listenerHost));
        boolean portDiffers = listenerPort != null && dataPort != null 
            && !listenerPort.equals(dataPort);
        return hostDiffers || portDiffers;
    }
    
    /**
     * Returns a summary of the actual data connection endpoint.
     */
    public String getDataEndpoint() {
        if (dataHost == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(dataHost);
        if (dataIp != null && !dataIp.equals(dataHost)) {
            sb.append("/").append(dataIp);
        }
        if (dataPort != null) {
            sb.append(":").append(dataPort);
        }
        return sb.toString();
    }
}
