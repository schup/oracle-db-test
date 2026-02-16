package org.dbtest.diagnostics;

import lombok.extern.slf4j.Slf4j;
import org.dbtest.ssh.SshException;
import org.dbtest.ssh.SshTunnelConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides diagnostic checks for SSH tunnel and SOCKS proxy failures.
 */
@Slf4j
public class SshDiagnostics {
    
    private final DnsChecker dnsChecker = new DnsChecker();
    private final PortChecker portChecker = new PortChecker();
    
    /**
     * Diagnoses an SSH connection failure.
     */
    public DiagnosticResult diagnose(String tunnelName, 
                                     Map<String, SshTunnelConfig> tunnelConfigs,
                                     SshException exception) {
        log.info("Running SSH diagnostics for tunnel: {}", tunnelName);
        
        DiagnosticResult.DiagnosticResultBuilder builder = DiagnosticResult.builder();
        List<String> recommendations = new ArrayList<>();
        
        SshTunnelConfig config = tunnelConfigs != null ? tunnelConfigs.get(tunnelName) : null;
        if (config == null) {
            builder.errorType("SSH_CONFIG_ERROR");
            builder.analysis("SSH tunnel configuration not found: " + tunnelName);
            recommendations.add("Verify the tunnel name is correct in the configuration");
            builder.recommendations(recommendations);
            return builder.build();
        }
        
        String errorMessage = exception.getMessage();
        
        // Analyze the error message to determine the failure type
        String errorType = analyzeError(errorMessage);
        builder.errorType(errorType);
        
        // Check SSH server connectivity
        DiagnosticResult.DnsCheckResult dnsResult = dnsChecker.check(config.getHost());
        builder.dnsCheck(dnsResult);
        
        if (!dnsResult.isSuccess()) {
            builder.analysis("Cannot resolve SSH server hostname: " + config.getHost());
            recommendations.add("Verify the SSH server hostname is correct");
            recommendations.add("Check DNS configuration");
            recommendations.add("Try using IP address instead of hostname");
            builder.recommendations(recommendations);
            return builder.build();
        }
        
        // Check SSH port
        DiagnosticResult.PortCheckResult portResult = portChecker.check(config.getHost(), config.getPort());
        builder.portCheck(portResult);
        
        if (!portResult.isPortOpen()) {
            builder.analysis(String.format("Cannot connect to SSH server %s:%d - port is not reachable",
                config.getHost(), config.getPort()));
            recommendations.add("Verify the SSH server is running");
            recommendations.add("Check firewall rules for port " + config.getPort());
            recommendations.add("Verify the SSH port number is correct (default: 22)");
            builder.recommendations(recommendations);
            return builder.build();
        }
        
        // SSH server is reachable, analyze specific error
        String analysis = buildAnalysis(errorType, errorMessage, config);
        builder.analysis(analysis);
        
        // Add specific recommendations based on error type
        addRecommendations(errorType, errorMessage, config, recommendations);
        builder.recommendations(recommendations);
        
        return builder.build();
    }
    
    private String analyzeError(String errorMessage) {
        if (errorMessage == null) return "SSH_UNKNOWN_ERROR";
        
        String lowerMessage = errorMessage.toLowerCase();
        
        if (lowerMessage.contains("auth fail") || lowerMessage.contains("authentication")) {
            return "SSH_AUTH_FAILURE";
        }
        if (lowerMessage.contains("unknownhost") || lowerMessage.contains("unknown host")) {
            return "SSH_UNKNOWN_HOST";
        }
        if (lowerMessage.contains("host key") || lowerMessage.contains("hostkey")) {
            return "SSH_HOST_KEY_ERROR";
        }
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return "SSH_TIMEOUT";
        }
        if (lowerMessage.contains("connection refused")) {
            return "SSH_CONNECTION_REFUSED";
        }
        if (lowerMessage.contains("no such file") || lowerMessage.contains("file not found")) {
            return "SSH_KEY_FILE_NOT_FOUND";
        }
        if (lowerMessage.contains("invalid privatekey") || lowerMessage.contains("invalid key")) {
            return "SSH_INVALID_KEY";
        }
        if (lowerMessage.contains("passphrase")) {
            return "SSH_PASSPHRASE_ERROR";
        }
        if (lowerMessage.contains("channel") || lowerMessage.contains("port forward")) {
            return "SSH_TUNNEL_ERROR";
        }
        
        return "SSH_UNKNOWN_ERROR";
    }
    
    private String buildAnalysis(String errorType, String errorMessage, SshTunnelConfig config) {
        return switch (errorType) {
            case "SSH_AUTH_FAILURE" -> String.format(
                "SSH authentication failed for %s@%s",
                config.getUsername(), config.getHost());
            case "SSH_UNKNOWN_HOST" -> String.format(
                "SSH server hostname unknown: %s",
                config.getHost());
            case "SSH_HOST_KEY_ERROR" -> String.format(
                "SSH host key verification failed for %s",
                config.getHost());
            case "SSH_TIMEOUT" -> String.format(
                "SSH connection timed out to %s:%d",
                config.getHost(), config.getPort());
            case "SSH_CONNECTION_REFUSED" -> String.format(
                "SSH connection refused by %s:%d",
                config.getHost(), config.getPort());
            case "SSH_KEY_FILE_NOT_FOUND" -> String.format(
                "SSH private key file not found: %s",
                config.getPrivateKey());
            case "SSH_INVALID_KEY" -> "SSH private key is invalid or corrupted";
            case "SSH_PASSPHRASE_ERROR" -> "SSH private key passphrase is incorrect or missing";
            case "SSH_TUNNEL_ERROR" -> "Failed to establish SSH port forwarding";
            default -> "SSH connection failed: " + errorMessage;
        };
    }
    
    private void addRecommendations(String errorType, String errorMessage, 
                                    SshTunnelConfig config, List<String> recommendations) {
        switch (errorType) {
            case "SSH_AUTH_FAILURE" -> {
                if (config.usesPrivateKey()) {
                    String keyPath = config.getExpandedPrivateKeyPath();
                    recommendations.add("Verify the private key is authorized on the server");
                    recommendations.add("Check that the public key is in ~/.ssh/authorized_keys on " + config.getHost());
                    
                    // Check if key file exists and has correct permissions
                    File keyFile = new File(keyPath);
                    if (!keyFile.exists()) {
                        recommendations.add("Private key file not found: " + keyPath);
                    } else if (!keyFile.canRead()) {
                        recommendations.add("Cannot read private key file - check permissions");
                    } else {
                        recommendations.add("Verify key file permissions are 600: chmod 600 " + keyPath);
                    }
                } else {
                    recommendations.add("Verify the password is correct");
                }
                recommendations.add("Confirm username '" + config.getUsername() + "' is correct");
            }
            case "SSH_HOST_KEY_ERROR" -> {
                recommendations.add("Add the server to known_hosts: ssh-keyscan " + config.getHost() + " >> ~/.ssh/known_hosts");
                recommendations.add("Or specify known_hosts path in configuration");
                recommendations.add("Or remove old entry if server key changed: ssh-keygen -R " + config.getHost());
            }
            case "SSH_KEY_FILE_NOT_FOUND" -> {
                recommendations.add("Verify the private key path is correct");
                recommendations.add("Check that ~ is properly expanded in the path");
                recommendations.add("Current path: " + config.getPrivateKey());
            }
            case "SSH_INVALID_KEY" -> {
                recommendations.add("Verify the private key file is not corrupted");
                recommendations.add("Check that the key format is supported (OpenSSH, PEM)");
                recommendations.add("Try regenerating the key pair");
            }
            case "SSH_PASSPHRASE_ERROR" -> {
                recommendations.add("Verify the private_key_passphrase is correct");
                recommendations.add("If the key has no passphrase, remove the passphrase setting");
            }
            case "SSH_TUNNEL_ERROR" -> {
                recommendations.add("Verify the remote host is reachable from the SSH server");
                recommendations.add("Check that port forwarding is allowed on the SSH server");
                recommendations.add("Verify AllowTcpForwarding is enabled in sshd_config");
            }
            case "SSH_TIMEOUT" -> {
                recommendations.add("Check network connectivity to the SSH server");
                recommendations.add("Increase the timeout value in configuration");
                recommendations.add("Verify no firewall is blocking the connection");
            }
            default -> {
                recommendations.add("Check SSH server logs for more details");
                recommendations.add("Try connecting manually: ssh " + config.getUsername() + "@" + config.getHost());
            }
        }
    }
}
