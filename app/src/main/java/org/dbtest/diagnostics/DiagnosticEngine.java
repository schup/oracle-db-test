package org.dbtest.diagnostics;

import lombok.extern.slf4j.Slf4j;
import org.dbtest.config.ConnectionDefinition;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates diagnostic checks when a database connection fails.
 * Runs checks in sequence: DNS -> Port -> Oracle Error Analysis.
 */
@Slf4j
public class DiagnosticEngine {
    
    private final DnsChecker dnsChecker = new DnsChecker();
    private final PortChecker portChecker = new PortChecker();
    private final OracleErrorAnalyzer errorAnalyzer = new OracleErrorAnalyzer();
    
    /**
     * Performs comprehensive diagnostics for a failed connection.
     */
    public DiagnosticResult diagnose(ConnectionDefinition conn, SQLException exception) {
        log.info("Running diagnostics for connection: {}", conn.getName());
        
        DiagnosticResult.DiagnosticResultBuilder builder = DiagnosticResult.builder();
        List<String> allRecommendations = new ArrayList<>();
        
        // Step 1: DNS Check
        DiagnosticResult.DnsCheckResult dnsResult = dnsChecker.check(conn.getHost());
        builder.dnsCheck(dnsResult);
        
        if (!dnsResult.isSuccess()) {
            // DNS failed - stop here
            builder.errorType("DNS_FAILURE");
            builder.analysis("DNS resolution failed for hostname: " + conn.getHost());
            allRecommendations.add("Verify the hostname is correct");
            allRecommendations.add("Check DNS configuration");
            allRecommendations.add("Try using IP address instead of hostname");
            builder.recommendations(allRecommendations);
            return builder.build();
        }
        
        // Step 2: Port Check
        DiagnosticResult.PortCheckResult portResult = portChecker.check(conn.getHost(), conn.getPort());
        builder.portCheck(portResult);
        
        if (!portResult.isPortOpen()) {
            // Port not reachable
            builder.errorType("CONNECTION_REFUSED");
            builder.analysis(String.format("Cannot connect to %s:%d - %s", 
                conn.getHost(), conn.getPort(), 
                portResult.getErrorMessage() != null ? portResult.getErrorMessage() : "port is not open"));
            allRecommendations.add("Verify the port number is correct (default Oracle: 1521)");
            allRecommendations.add("Check if Oracle listener is running on the target host");
            allRecommendations.add("Verify firewall rules allow connections to this port");
            allRecommendations.add("Contact network administrator if port is blocked");
            builder.recommendations(allRecommendations);
            return builder.build();
        }
        
        // Step 3: Oracle Error Analysis
        // DNS and port are OK, so analyze the specific Oracle error
        OracleErrorAnalyzer.AnalysisResult analysis = errorAnalyzer.analyze(
            exception, 
            conn.getServiceOrSid(), 
            conn.usesServiceName()
        );
        
        builder.errorType(analysis.getErrorType());
        builder.analysis(analysis.getAnalysis());
        builder.recommendations(analysis.getRecommendations());
        
        return builder.build();
    }
}
