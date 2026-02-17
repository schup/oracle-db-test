package org.dbtest.output;

import org.dbtest.config.ConnectionDefinition;
import org.dbtest.connection.ConnectionResult;
import org.dbtest.diagnostics.DiagnosticResult;
import org.dbtest.diagnostics.JdbcTraceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats connection information consistently across different reporters.
 */
public class ConnectionInfoFormatter {
    
    /**
     * Formats the basic connection info line.
     */
    public static String formatConnectionLine(ConnectionDefinition conn) {
        String serviceOrSid = conn.usesServiceName() 
            ? "Service: " + conn.getService() 
            : "SID: " + conn.getSid();
        return String.format("Host: %s:%d  %s  User: %s", 
            conn.getHost(), conn.getPort(), serviceOrSid, conn.getUsername());
    }
    
    /**
     * Formats the actual data connection line from JDBC trace.
     */
    public static String formatConnectedTo(JdbcTraceInfo trace) {
        if (trace == null || trace.getDataHost() == null) {
            return null;
        }
        
        String dataEndpoint = trace.getDataEndpoint();
        String connectStatus = "";
        if (trace.getConnectTimeMs() != null) {
            String symbol = Boolean.TRUE.equals(trace.getConnectSucceeded()) ? "✓" : "✗";
            connectStatus = String.format(" [%s %dms]", symbol, trace.getConnectTimeMs());
        }
        return String.format("Connected to: %s%s", dataEndpoint, connectStatus);
    }
    
    /**
     * Builds the JDBC URL for the connection.
     */
    public static String buildJdbcUrl(ConnectionDefinition conn) {
        if (conn.usesServiceName()) {
            return String.format("jdbc:oracle:thin:@//%s:%d/%s",
                conn.getHost(), conn.getPort(), conn.getService());
        } else {
            return String.format("jdbc:oracle:thin:@%s:%d:%s",
                conn.getHost(), conn.getPort(), conn.getSid());
        }
    }
    
    /**
     * Formats all connection info lines.
     */
    public static List<String> formatConnectionInfo(ConnectionResult result) {
        List<String> lines = new ArrayList<>();
        var conn = result.getConnection();
        var trace = result.getJdbcTrace();
        
        lines.add(formatConnectionLine(conn));
        
        String connectedTo = formatConnectedTo(trace);
        if (connectedTo != null) {
            lines.add(connectedTo);
        }
        
        lines.add("JDBC URL: " + buildJdbcUrl(conn));
        
        return lines;
    }
    
    /**
     * Formats success-specific info.
     */
    public static List<String> formatSuccessInfo(ConnectionResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("Version: " + result.getDatabaseVersion());
        lines.add(String.format("Connection Time: %dms", result.getConnectionTimeMs()));
        lines.add(String.format("Test Query: OK (%dms)", result.getTestQueryTimeMs()));
        return lines;
    }
    
    /**
     * Formats diagnostic info as plain text lines.
     */
    public static List<String> formatDiagnostics(DiagnosticResult diagnostics) {
        List<String> lines = new ArrayList<>();
        if (diagnostics == null) {
            return lines;
        }
        
        lines.add("Diagnostics:");
        
        var dnsCheck = diagnostics.getDnsCheck();
        if (dnsCheck != null) {
            if (dnsCheck.isSuccess()) {
                lines.add(String.format("- DNS Resolution: SUCCESS (%dms) → %s",
                    dnsCheck.getResolutionTimeMs(), dnsCheck.getResolvedIp()));
            } else {
                lines.add("- DNS Resolution: FAILED - " + dnsCheck.getErrorMessage());
            }
        }
        
        var portCheck = diagnostics.getPortCheck();
        if (portCheck != null) {
            if (portCheck.isPortOpen()) {
                lines.add("- Port Reachability: SUCCESS - Port is open");
            } else {
                lines.add("- Port Reachability: FAILED - " + 
                    (portCheck.getErrorMessage() != null ? portCheck.getErrorMessage() : "Port not reachable"));
            }
        }
        
        if (diagnostics.getAnalysis() != null) {
            lines.add("- Analysis: " + diagnostics.getAnalysis());
        }
        
        var recommendations = diagnostics.getRecommendations();
        if (recommendations != null && !recommendations.isEmpty()) {
            lines.add("");
            lines.add("Recommendations:");
            for (String rec : recommendations) {
                lines.add("• " + rec);
            }
        }
        
        return lines;
    }
}
