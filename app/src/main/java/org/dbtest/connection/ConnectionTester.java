package org.dbtest.connection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dbtest.config.ConnectionDefinition;
import org.dbtest.diagnostics.DiagnosticEngine;
import org.dbtest.diagnostics.DiagnosticResult;
import org.dbtest.password.PasswordProviderException;
import org.dbtest.password.PasswordProviderFactory;
import org.dbtest.ssh.SshException;
import org.dbtest.ssh.SshTunnelManager;

import java.sql.*;
import java.util.Properties;

/**
 * Tests database connections and gathers diagnostic information on failure.
 */
@Slf4j
@RequiredArgsConstructor
public class ConnectionTester {
    
    private static final String VERSION_QUERY = "SELECT banner FROM v$version WHERE banner LIKE 'Oracle%' AND ROWNUM = 1";
    private static final String VERSION_NUMBER_QUERY = "SELECT version FROM v$instance";
    
    private final PasswordProviderFactory passwordProviderFactory;
    private final DiagnosticEngine diagnosticEngine;
    private final SshTunnelManager sshTunnelManager;
    
    /**
     * Tests a single database connection.
     */
    public ConnectionResult test(ConnectionDefinition conn) {
        log.info("Testing connection: {}", conn.getName());
        
        // Skip disabled connections
        if (conn.getEnabled() != null && !conn.getEnabled()) {
            log.info("Skipping disabled connection: {}", conn.getName());
            return ConnectionResult.skipped(conn);
        }
        
        // Resolve password
        String password;
        try {
            password = passwordProviderFactory.getPassword(conn);
        } catch (PasswordProviderException e) {
            log.error("Password retrieval failed for {}: {}", conn.getName(), e.getMessage());
            return ConnectionResult.failure(conn, 
                "Password retrieval failed: " + e.getMessage(),
                null,
                "PASSWORD_ERROR",
                null);
        }
        
        // Determine effective host/port (may be modified by SSH tunnel or SOCKS proxy)
        String effectiveHost = conn.getHost();
        int effectivePort = conn.getPort();
        
        // Set up SSH tunnel if configured
        if (conn.getSshTunnel() != null && !conn.getSshTunnel().isBlank() && sshTunnelManager != null) {
            try {
                log.info("Establishing SSH tunnel '{}' for connection {}", conn.getSshTunnel(), conn.getName());
                SshTunnelManager.ActiveTunnel tunnel = sshTunnelManager.establishTunnel(
                    conn.getSshTunnel(), conn.getHost(), conn.getPort());
                effectiveHost = "localhost";
                effectivePort = tunnel.localPort();
                log.info("Tunnel established: localhost:{} -> {}:{}", 
                    effectivePort, conn.getHost(), conn.getPort());
            } catch (SshException e) {
                log.error("SSH tunnel setup failed for {}: {}", conn.getName(), e.getMessage());
                DiagnosticResult diagnostics = diagnosticEngine.diagnoseSsh(conn, e);
                return ConnectionResult.failure(conn,
                    "SSH tunnel failed: " + e.getMessage(),
                    null,
                    "SSH_TUNNEL_ERROR",
                    diagnostics);
            }
        }
        
        // Set up SOCKS proxy if configured (uses SSH port forwarding under the hood)
        if (conn.getSocksProxy() != null && !conn.getSocksProxy().isBlank() && sshTunnelManager != null) {
            try {
                log.info("Setting up connection via SOCKS proxy '{}' for {}", conn.getSocksProxy(), conn.getName());
                // Use local port forwarding through the SOCKS proxy session
                int forwardedPort = sshTunnelManager.createSocksProxyForward(
                    conn.getSocksProxy(), conn.getHost(), conn.getPort());
                effectiveHost = "localhost";
                effectivePort = forwardedPort;
                log.info("SOCKS proxy forward established: localhost:{} -> {}:{}", 
                    forwardedPort, conn.getHost(), conn.getPort());
            } catch (SshException e) {
                log.error("SOCKS proxy setup failed for {}: {}", conn.getName(), e.getMessage());
                DiagnosticResult diagnostics = diagnosticEngine.diagnoseSsh(conn, e);
                return ConnectionResult.failure(conn,
                    "SOCKS proxy failed: " + e.getMessage(),
                    null,
                    "SOCKS_PROXY_ERROR",
                    diagnostics);
            }
        }
        
        // Build JDBC URL with effective host/port
        String jdbcUrl = buildJdbcUrl(effectiveHost, effectivePort, conn);
        log.debug("Connecting to: {}", sanitizeJdbcUrl(jdbcUrl));
        
        // Attempt connection
        long startTime = System.currentTimeMillis();
        
        try {
            Properties props = new Properties();
            props.setProperty("user", conn.getUsername());
            props.setProperty("password", password);
            props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(conn.getTimeout() * 1000));
            props.setProperty("oracle.jdbc.ReadTimeout", "30000");
            
            try (Connection connection = DriverManager.getConnection(jdbcUrl, props)) {
                long connectionTime = System.currentTimeMillis() - startTime;
                log.info("Connection successful: {} ({}ms)", conn.getName(), connectionTime);
                
                // Get database version
                String[] versionInfo = getDatabaseVersion(connection);
                String databaseVersion = versionInfo[0];
                String versionNumber = versionInfo[1];
                
                // Execute test query
                long queryStartTime = System.currentTimeMillis();
                executeTestQuery(connection, conn.getEffectiveTestQuery());
                long queryTime = System.currentTimeMillis() - queryStartTime;
                
                log.debug("Test query completed in {}ms", queryTime);
                
                return ConnectionResult.success(conn, databaseVersion, versionNumber, 
                    connectionTime, queryTime);
            }
            
        } catch (SQLException e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.error("Connection failed for {}: {} (after {}ms)", 
                conn.getName(), sanitizeException(e), elapsedTime);
            
            // Run diagnostics
            DiagnosticResult diagnostics = diagnosticEngine.diagnose(conn, e);
            
            // Extract Oracle error code
            String errorCode = extractOracleErrorCode(e);
            String errorType = diagnostics != null ? diagnostics.getErrorType() : "UNKNOWN";
            
            return ConnectionResult.failure(conn, 
                sanitizeException(e),
                errorCode,
                errorType,
                diagnostics);
        }
    }
    
    /**
     * Builds the JDBC URL for the connection using effective host/port.
     */
    public String buildJdbcUrl(String host, int port, ConnectionDefinition conn) {
        if (conn.usesServiceName()) {
            // Service Name format: jdbc:oracle:thin:@//host:port/service
            return String.format("jdbc:oracle:thin:@//%s:%d/%s",
                host, port, conn.getService());
        } else {
            // SID format: jdbc:oracle:thin:@host:port:sid
            return String.format("jdbc:oracle:thin:@%s:%d:%s",
                host, port, conn.getSid());
        }
    }
    
    /**
     * Builds the JDBC URL for the connection (convenience overload).
     */
    public String buildJdbcUrl(ConnectionDefinition conn) {
        return buildJdbcUrl(conn.getHost(), conn.getPort(), conn);
    }
    
    /**
     * Retrieves the Oracle database version.
     * Returns [banner, version_number].
     */
    private String[] getDatabaseVersion(Connection connection) {
        String banner = "Unknown";
        String versionNumber = "Unknown";
        
        try (Statement stmt = connection.createStatement()) {
            // Get full banner
            try (ResultSet rs = stmt.executeQuery(VERSION_QUERY)) {
                if (rs.next()) {
                    banner = rs.getString(1);
                }
            }
            
            // Get version number
            try (ResultSet rs = stmt.executeQuery(VERSION_NUMBER_QUERY)) {
                if (rs.next()) {
                    versionNumber = rs.getString(1);
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to retrieve database version: {}", e.getMessage());
        }
        
        return new String[]{banner, versionNumber};
    }
    
    /**
     * Executes the test query.
     */
    private void executeTestQuery(Connection connection, String query) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            // Just verify the query executes without error
            if (rs.next()) {
                log.debug("Test query returned result");
            }
        }
    }
    
    /**
     * Extracts Oracle error code from SQLException (e.g., "ORA-12514").
     */
    private String extractOracleErrorCode(SQLException e) {
        String message = e.getMessage();
        if (message != null) {
            // Look for ORA-XXXXX pattern
            int oraIndex = message.indexOf("ORA-");
            if (oraIndex >= 0) {
                int endIndex = oraIndex + 9; // ORA-XXXXX is 9 characters
                if (endIndex <= message.length()) {
                    return message.substring(oraIndex, endIndex);
                }
            }
        }
        
        // Fall back to vendor error code
        int vendorCode = e.getErrorCode();
        if (vendorCode != 0) {
            return "ORA-" + String.format("%05d", vendorCode);
        }
        
        return null;
    }
    
    /**
     * Sanitizes JDBC URL by removing password.
     */
    private String sanitizeJdbcUrl(String jdbcUrl) {
        // JDBC URLs typically don't contain passwords in URL form for Oracle,
        // but sanitize just in case
        return jdbcUrl.replaceAll("/(.*?)@", "/***@");
    }
    
    /**
     * Sanitizes exception message by removing potential password information.
     */
    private String sanitizeException(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return "No error message";
        
        // Remove passwords from error messages
        msg = msg.replaceAll("password[=:][^\\s,;)]+", "password=***");
        msg = msg.replaceAll("/[^@]+@", "/***@");
        msg = msg.replaceAll("PWD=[^;]+", "PWD=***");
        
        return msg;
    }
}
