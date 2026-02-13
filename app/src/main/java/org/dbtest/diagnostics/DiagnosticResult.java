package org.dbtest.diagnostics;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Contains the results of diagnostic checks performed after a connection failure.
 */
@Value
@Builder
public class DiagnosticResult {
    
    /**
     * DNS resolution check result.
     */
    DnsCheckResult dnsCheck;
    
    /**
     * Port reachability check result.
     */
    PortCheckResult portCheck;
    
    /**
     * Categorized error type (e.g., "DNS_FAILURE", "CONNECTION_REFUSED", "SERVICE_NOT_FOUND").
     */
    String errorType;
    
    /**
     * Human-readable analysis of the issue.
     */
    String analysis;
    
    /**
     * Actionable recommendations to resolve the issue.
     */
    List<String> recommendations;
    
    /**
     * Result of DNS resolution check.
     */
    @Value
    @Builder
    public static class DnsCheckResult {
        boolean success;
        String resolvedIp;
        Long resolutionTimeMs;
        String errorMessage;
        
        public static DnsCheckResult success(String resolvedIp, long timeMs) {
            return DnsCheckResult.builder()
                .success(true)
                .resolvedIp(resolvedIp)
                .resolutionTimeMs(timeMs)
                .build();
        }
        
        public static DnsCheckResult failure(String errorMessage) {
            return DnsCheckResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
        }
    }
    
    /**
     * Result of port reachability check.
     */
    @Value
    @Builder
    public static class PortCheckResult {
        boolean success;
        boolean portOpen;
        Long checkTimeMs;
        String errorMessage;
        
        public static PortCheckResult success(long timeMs) {
            return PortCheckResult.builder()
                .success(true)
                .portOpen(true)
                .checkTimeMs(timeMs)
                .build();
        }
        
        public static PortCheckResult portClosed(String errorMessage) {
            return PortCheckResult.builder()
                .success(true)
                .portOpen(false)
                .errorMessage(errorMessage)
                .build();
        }
        
        public static PortCheckResult failure(String errorMessage) {
            return PortCheckResult.builder()
                .success(false)
                .portOpen(false)
                .errorMessage(errorMessage)
                .build();
        }
    }
}
