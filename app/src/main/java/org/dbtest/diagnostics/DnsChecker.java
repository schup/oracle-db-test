package org.dbtest.diagnostics;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Checks DNS resolution for hostnames.
 */
@Slf4j
public class DnsChecker {
    
    /**
     * Attempts to resolve a hostname to an IP address.
     */
    public DiagnosticResult.DnsCheckResult check(String hostname) {
        log.debug("Checking DNS resolution for: {}", hostname);
        
        long startTime = System.currentTimeMillis();
        
        try {
            InetAddress address = InetAddress.getByName(hostname);
            long elapsed = System.currentTimeMillis() - startTime;
            
            String resolvedIp = address.getHostAddress();
            log.debug("DNS resolved {} -> {} in {}ms", hostname, resolvedIp, elapsed);
            
            return DiagnosticResult.DnsCheckResult.success(resolvedIp, elapsed);
            
        } catch (UnknownHostException e) {
            log.debug("DNS resolution failed for {}: {}", hostname, e.getMessage());
            return DiagnosticResult.DnsCheckResult.failure(
                "Unable to resolve hostname: " + e.getMessage());
        }
    }
}
