package org.dbtest.diagnostics;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes Oracle error codes and provides human-readable interpretations
 * and recommendations.
 */
@Slf4j
public class OracleErrorAnalyzer {
    
    private static final Pattern ORA_CODE_PATTERN = Pattern.compile("ORA-(\\d{5})");
    
    /**
     * Analysis result containing error type, analysis, and recommendations.
     */
    @Value
    public static class AnalysisResult {
        String errorType;
        String analysis;
        List<String> recommendations;
    }
    
    /**
     * Analyzes a SQLException and returns interpretation and recommendations.
     */
    public AnalysisResult analyze(SQLException e, String serviceOrSid, boolean usesServiceName) {
        String message = e.getMessage();
        int vendorCode = e.getErrorCode();
        
        // Try to extract ORA code from message
        String oraCode = extractOraCode(message);
        if (oraCode == null && vendorCode != 0) {
            oraCode = String.format("%05d", vendorCode);
        }
        
        log.debug("Analyzing Oracle error: ORA-{}", oraCode);
        
        if (oraCode != null) {
            return analyzeByCode(oraCode, serviceOrSid, usesServiceName);
        }
        
        // Generic analysis for unknown errors
        return new AnalysisResult(
            "UNKNOWN",
            "Unable to determine specific cause from error message",
            List.of("Check Oracle listener logs for more details",
                   "Verify database is running and accessible",
                   "Contact DBA for assistance")
        );
    }
    
    private String extractOraCode(String message) {
        if (message == null) return null;
        
        Matcher matcher = ORA_CODE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private AnalysisResult analyzeByCode(String code, String serviceOrSid, boolean usesServiceName) {
        return switch (code) {
            case "12154" -> new AnalysisResult(
                "TNS_RESOLVE_FAILURE",
                "TNS could not resolve the connect identifier (service name or SID)",
                List.of(
                    "Verify the service name or SID is correct",
                    "Check if tnsnames.ora is properly configured",
                    "Ensure the TNS_ADMIN environment variable points to the correct directory"
                )
            );
            
            case "12514" -> {
                List<String> recs = new ArrayList<>();
                recs.add(String.format("Verify the %s '%s' is correct (case-sensitive)",
                    usesServiceName ? "service name" : "SID", serviceOrSid));
                if (usesServiceName) {
                    recs.add("Try using SID instead of service name if available");
                } else {
                    recs.add("Try using service name instead of SID");
                }
                recs.add("Check available services with: lsnrctl status");
                recs.add("Contact DBA to verify service registration");
                
                yield new AnalysisResult(
                    "SERVICE_NOT_FOUND",
                    String.format("TNS listener does not know of the requested %s '%s'",
                        usesServiceName ? "service" : "SID", serviceOrSid),
                    recs
                );
            }
            
            case "12541" -> new AnalysisResult(
                "NO_LISTENER",
                "No TNS listener found - the listener is not running",
                List.of(
                    "Verify the Oracle listener is running on the target host",
                    "Check if the port is correct (default: 1521)",
                    "Ask DBA to start the listener: lsnrctl start"
                )
            );
            
            case "12170" -> new AnalysisResult(
                "CONNECT_TIMEOUT",
                "Connection timed out - could not reach the database server",
                List.of(
                    "Check network connectivity to the database host",
                    "Verify firewall rules allow connections to the Oracle port",
                    "Ensure the database server is running"
                )
            );
            
            case "12535" -> new AnalysisResult(
                "OPERATION_TIMEOUT",
                "TNS operation timed out",
                List.of(
                    "Check network latency to the database host",
                    "Increase the connection timeout",
                    "Verify there are no network issues"
                )
            );
            
            case "01017" -> new AnalysisResult(
                "INVALID_CREDENTIALS",
                "Invalid username or password",
                List.of(
                    "Verify the username is correct",
                    "Check if the password is correct",
                    "Ensure the password environment variable is set correctly",
                    "Check if the account is not locked"
                )
            );
            
            case "28000" -> new AnalysisResult(
                "ACCOUNT_LOCKED",
                "The database account is locked",
                List.of(
                    "Contact DBA to unlock the account",
                    "Check if there were too many failed login attempts"
                )
            );
            
            case "01033" -> new AnalysisResult(
                "DB_INITIALIZING",
                "Oracle initialization or shutdown in progress",
                List.of(
                    "Wait for the database to complete startup or shutdown",
                    "Contact DBA to check database status"
                )
            );
            
            case "01034" -> new AnalysisResult(
                "DB_NOT_AVAILABLE",
                "Oracle database is not available",
                List.of(
                    "Verify the database instance is started",
                    "Contact DBA to start the database"
                )
            );
            
            case "12505" -> new AnalysisResult(
                "SID_NOT_FOUND",
                "TNS listener does not know of SID given in connect descriptor",
                List.of(
                    "Verify the SID is correct",
                    "Try using service name instead of SID",
                    "Check available services with: lsnrctl status"
                )
            );
            
            case "12560" -> new AnalysisResult(
                "PROTOCOL_ERROR",
                "TNS protocol adapter error",
                List.of(
                    "Check if Oracle client is properly installed",
                    "Verify ORACLE_HOME environment variable",
                    "Check network configuration"
                )
            );
            
            case "12543" -> new AnalysisResult(
                "DESTINATION_HOST_UNREACHABLE",
                "TNS destination host unreachable",
                List.of(
                    "Verify the hostname/IP address is correct",
                    "Check network connectivity",
                    "Verify DNS resolution is working"
                )
            );
            
            default -> new AnalysisResult(
                "ORACLE_ERROR_" + code,
                String.format("Oracle error ORA-%s occurred", code),
                List.of(
                    "Check Oracle documentation for ORA-" + code,
                    "Review database and listener logs",
                    "Contact DBA for assistance"
                )
            );
        };
    }
}
