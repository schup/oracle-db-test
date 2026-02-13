package org.dbtest.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class OracleErrorAnalyzerTest {
    
    private final OracleErrorAnalyzer analyzer = new OracleErrorAnalyzer();
    
    @Test
    @DisplayName("Should analyze ORA-12514 (Service not found)")
    void analyzeServiceNotFound() {
        SQLException ex = new SQLException("ORA-12514: TNS:listener does not currently know of service requested", "72000", 12514);
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "TESTDB", true);
        
        assertEquals("SERVICE_NOT_FOUND", result.getErrorType());
        assertTrue(result.getAnalysis().contains("does not know"));
        assertTrue(result.getRecommendations().stream()
            .anyMatch(r -> r.contains("service name")));
    }
    
    @Test
    @DisplayName("Should analyze ORA-12541 (No listener)")
    void analyzeNoListener() {
        SQLException ex = new SQLException("ORA-12541: TNS:no listener", "72000", 12541);
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "TESTDB", true);
        
        assertEquals("NO_LISTENER", result.getErrorType());
        assertTrue(result.getAnalysis().contains("listener"));
        assertTrue(result.getRecommendations().stream()
            .anyMatch(r -> r.contains("listener")));
    }
    
    @Test
    @DisplayName("Should analyze ORA-01017 (Invalid credentials)")
    void analyzeInvalidCredentials() {
        SQLException ex = new SQLException("ORA-01017: invalid username/password; logon denied", "72000", 1017);
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "TESTDB", true);
        
        assertEquals("INVALID_CREDENTIALS", result.getErrorType());
        assertTrue(result.getAnalysis().contains("password"));
    }
    
    @Test
    @DisplayName("Should analyze ORA-28000 (Account locked)")
    void analyzeAccountLocked() {
        SQLException ex = new SQLException("ORA-28000: the account is locked", "72000", 28000);
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "TESTDB", true);
        
        assertEquals("ACCOUNT_LOCKED", result.getErrorType());
        assertTrue(result.getRecommendations().stream()
            .anyMatch(r -> r.contains("unlock")));
    }
    
    @Test
    @DisplayName("Should extract ORA code from message")
    void extractOraCodeFromMessage() {
        // Test with vendor code = 0 but ORA code in message
        SQLException ex = new SQLException("Connection failed: ORA-12170: TNS:Connect timeout occurred");
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "TESTDB", true);
        
        assertEquals("CONNECT_TIMEOUT", result.getErrorType());
    }
    
    @Test
    @DisplayName("Should handle unknown errors")
    void handleUnknownError() {
        SQLException ex = new SQLException("Some unknown error");
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "TESTDB", true);
        
        assertEquals("UNKNOWN", result.getErrorType());
        assertFalse(result.getRecommendations().isEmpty());
    }
    
    @Test
    @DisplayName("Should provide SID-specific recommendations for SID connection")
    void sidSpecificRecommendations() {
        SQLException ex = new SQLException("ORA-12514: TNS:listener does not currently know of service requested", "72000", 12514);
        
        OracleErrorAnalyzer.AnalysisResult result = analyzer.analyze(ex, "ORCL", false);
        
        assertTrue(result.getRecommendations().stream()
            .anyMatch(r -> r.contains("SID")));
        assertTrue(result.getRecommendations().stream()
            .anyMatch(r -> r.contains("service name instead")));
    }
}
