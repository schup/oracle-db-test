package org.dbtest.ssh;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Configuration for an SSH SOCKS proxy (dynamic port forwarding).
 * Creates a local SOCKS proxy via SSH that can route to any destination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocksProxyConfig {
    
    /** SSH server hostname */
    private String host;
    
    /** SSH server port (default: 22) */
    @Builder.Default
    private Integer port = 22;
    
    /** SSH username */
    private String username;
    
    /** Path to private key file (e.g., ~/.ssh/id_rsa) */
    private String privateKey;
    
    /** Passphrase for encrypted private key */
    @ToString.Exclude
    private String privateKeyPassphrase;
    
    /** SSH password (alternative to private key) */
    @ToString.Exclude
    private String password;
    
    /** Path to known_hosts file for host key verification */
    private String knownHosts;
    
    /** Local SOCKS proxy port to bind */
    @Builder.Default
    private Integer localPort = 1080;
    
    /** Reference to another tunnel config to use as jump host */
    private String jumpHost;
    
    /** Connection timeout in seconds */
    @Builder.Default
    private Integer timeout = 30;
    
    /**
     * Returns true if this proxy uses private key authentication.
     */
    public boolean usesPrivateKey() {
        return privateKey != null && !privateKey.isBlank();
    }
    
    /**
     * Returns true if this proxy has a jump host configured.
     */
    public boolean hasJumpHost() {
        return jumpHost != null && !jumpHost.isBlank();
    }
    
    /**
     * Expands ~ to user home directory in paths.
     */
    public String getExpandedPrivateKeyPath() {
        if (privateKey == null) return null;
        return privateKey.replaceFirst("^~", System.getProperty("user.home"));
    }
    
    /**
     * Expands ~ to user home directory in known_hosts path.
     */
    public String getExpandedKnownHostsPath() {
        if (knownHosts == null) return null;
        return knownHosts.replaceFirst("^~", System.getProperty("user.home"));
    }
}
