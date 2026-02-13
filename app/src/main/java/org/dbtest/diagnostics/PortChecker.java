package org.dbtest.diagnostics;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Checks TCP port reachability.
 */
@Slf4j
public class PortChecker {
    
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    
    /**
     * Attempts to connect to a host:port to verify it's reachable.
     */
    public DiagnosticResult.PortCheckResult check(String host, int port) {
        return check(host, port, DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * Attempts to connect to a host:port with custom timeout.
     */
    public DiagnosticResult.PortCheckResult check(String host, int port, int timeoutMs) {
        log.debug("Checking port reachability: {}:{}", host, port);
        
        long startTime = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long elapsed = System.currentTimeMillis() - startTime;
            
            log.debug("Port {}:{} is open ({}ms)", host, port, elapsed);
            return DiagnosticResult.PortCheckResult.success(elapsed);
            
        } catch (SocketTimeoutException e) {
            log.debug("Connection to {}:{} timed out", host, port);
            return DiagnosticResult.PortCheckResult.portClosed(
                String.format("Connection timed out after %dms - port may be filtered or host unreachable", 
                    timeoutMs));
            
        } catch (IOException e) {
            log.debug("Port check failed for {}:{}: {}", host, port, e.getMessage());
            
            String message = e.getMessage();
            if (message != null && message.contains("Connection refused")) {
                return DiagnosticResult.PortCheckResult.portClosed(
                    "Connection refused - no service listening on port " + port);
            }
            
            return DiagnosticResult.PortCheckResult.failure(
                "Port check failed: " + e.getMessage());
        }
    }
}
