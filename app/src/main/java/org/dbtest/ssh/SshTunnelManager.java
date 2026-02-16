package org.dbtest.ssh;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.*;

/**
 * Manages SSH tunnel and SOCKS proxy lifecycle using JSch.
 * Supports single-hop and multi-hop (jump host) configurations.
 */
@Slf4j
public class SshTunnelManager implements Closeable {
    
    private final Map<String, SshTunnelConfig> tunnelConfigs;
    private final Map<String, SocksProxyConfig> socksConfigs;
    private final List<Session> activeSessions = new ArrayList<>();
    private final Map<String, ActiveTunnel> activeTunnels = new HashMap<>();
    private final Map<String, ActiveSocksProxy> activeSocksProxies = new HashMap<>();
    private final boolean verbose;
    
    public SshTunnelManager(Map<String, SshTunnelConfig> tunnelConfigs,
                           Map<String, SocksProxyConfig> socksConfigs,
                           boolean verbose) {
        this.tunnelConfigs = tunnelConfigs != null ? tunnelConfigs : Map.of();
        this.socksConfigs = socksConfigs != null ? socksConfigs : Map.of();
        this.verbose = verbose;
    }
    
    /**
     * Establishes an SSH tunnel for the given tunnel name.
     * Returns the local port to connect to.
     */
    public ActiveTunnel establishTunnel(String tunnelName, String remoteHost, int remotePort) 
            throws SshException {
        
        // Check if already established
        String tunnelKey = tunnelName + ":" + remoteHost + ":" + remotePort;
        if (activeTunnels.containsKey(tunnelKey)) {
            return activeTunnels.get(tunnelKey);
        }
        
        SshTunnelConfig config = tunnelConfigs.get(tunnelName);
        if (config == null) {
            throw new SshException("SSH tunnel not found: " + tunnelName);
        }
        
        log.info("Establishing SSH tunnel '{}' to {}:{}", tunnelName, remoteHost, remotePort);
        
        try {
            // Build session chain for jump hosts
            Session session = createSessionChain(config, tunnelName);
            
            // Set up local port forwarding
            int localPort = config.getLocalPort() != null ? config.getLocalPort() : 0;
            int assignedPort = session.setPortForwardingL(localPort, remoteHost, remotePort);
            
            log.info("Tunnel established: localhost:{} -> {}:{} via {}", 
                assignedPort, remoteHost, remotePort, config.getHost());
            
            ActiveTunnel tunnel = new ActiveTunnel(tunnelName, assignedPort, remoteHost, remotePort, session);
            activeTunnels.put(tunnelKey, tunnel);
            
            return tunnel;
            
        } catch (JSchException e) {
            throw new SshException("Failed to establish tunnel '" + tunnelName + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Establishes a SOCKS proxy session for the given proxy name.
     * Note: This creates an SSH session that can be used for port forwarding.
     * The actual SOCKS functionality is handled by routing through the SSH connection.
     */
    public ActiveSocksProxy establishSocksProxy(String proxyName) throws SshException {
        // Check if already established
        if (activeSocksProxies.containsKey(proxyName)) {
            return activeSocksProxies.get(proxyName);
        }
        
        SocksProxyConfig config = socksConfigs.get(proxyName);
        if (config == null) {
            throw new SshException("SOCKS proxy not found: " + proxyName);
        }
        
        log.info("Establishing SOCKS proxy session '{}'", proxyName);
        
        try {
            // Build session (with jump host support if configured)
            Session session = createSocksSessionChain(config, proxyName);
            
            int localPort = config.getLocalPort() != null ? config.getLocalPort() : 1080;
            
            log.info("SOCKS proxy session established via {}", config.getHost());
            
            ActiveSocksProxy proxy = new ActiveSocksProxy(proxyName, localPort, session);
            activeSocksProxies.put(proxyName, proxy);
            
            return proxy;
            
        } catch (JSchException e) {
            throw new SshException("Failed to establish SOCKS proxy '" + proxyName + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates a local port forward through a SOCKS proxy session to reach a remote host.
     * This is used when a connection has a socks_proxy configured.
     */
    public int createSocksProxyForward(String proxyName, String remoteHost, int remotePort) throws SshException {
        ActiveSocksProxy proxy = establishSocksProxy(proxyName);
        
        try {
            // Create local port forward through the proxy session
            int localPort = proxy.session().setPortForwardingL(0, remoteHost, remotePort);
            log.info("SOCKS proxy forward: localhost:{} -> {}:{} via {}", 
                localPort, remoteHost, remotePort, proxyName);
            return localPort;
        } catch (JSchException e) {
            throw new SshException("Failed to create forward through SOCKS proxy: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates an SSH session chain, handling jump hosts recursively.
     */
    private Session createSessionChain(SshTunnelConfig config, String tunnelName) throws JSchException, SshException {
        JSch jsch = new JSch();
        configureJSch(jsch, config);
        
        Session session;
        
        if (config.hasJumpHost()) {
            // Get jump host config
            SshTunnelConfig jumpConfig = tunnelConfigs.get(config.getJumpHost());
            if (jumpConfig == null) {
                throw new SshException("Jump host not found: " + config.getJumpHost());
            }
            
            // Recursively create jump host session
            log.debug("Creating jump host session for: {}", config.getJumpHost());
            Session jumpSession = createSessionChain(jumpConfig, config.getJumpHost());
            
            // Create port forward through jump host to reach target SSH server
            int proxyPort = jumpSession.setPortForwardingL(0, config.getHost(), config.getPort());
            log.debug("Jump host port forward: localhost:{} -> {}:{}", 
                proxyPort, config.getHost(), config.getPort());
            
            // Connect to target through the forwarded port
            session = jsch.getSession(config.getUsername(), "127.0.0.1", proxyPort);
        } else {
            // Direct connection
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        }
        
        configureSession(session, config);
        
        log.debug("Connecting to SSH server: {}@{}:{}", 
            config.getUsername(), config.getHost(), config.getPort());
        session.connect(config.getTimeout() * 1000);
        
        activeSessions.add(session);
        log.info("SSH session established: {}@{}", config.getUsername(), config.getHost());
        
        return session;
    }
    
    /**
     * Creates an SSH session chain for SOCKS proxy.
     */
    private Session createSocksSessionChain(SocksProxyConfig config, String proxyName) 
            throws JSchException, SshException {
        JSch jsch = new JSch();
        configureSocksJSch(jsch, config);
        
        Session session;
        
        if (config.hasJumpHost()) {
            // Get jump host config from tunnel configs
            SshTunnelConfig jumpConfig = tunnelConfigs.get(config.getJumpHost());
            if (jumpConfig == null) {
                throw new SshException("Jump host not found: " + config.getJumpHost());
            }
            
            // Create jump host session
            Session jumpSession = createSessionChain(jumpConfig, config.getJumpHost());
            
            // Create port forward through jump host
            int proxyPort = jumpSession.setPortForwardingL(0, config.getHost(), config.getPort());
            
            // Connect through the forwarded port
            session = jsch.getSession(config.getUsername(), "127.0.0.1", proxyPort);
        } else {
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        }
        
        configureSocksSession(session, config);
        session.connect(config.getTimeout() * 1000);
        
        activeSessions.add(session);
        return session;
    }
    
    private void configureJSch(JSch jsch, SshTunnelConfig config) throws JSchException {
        if (config.usesPrivateKey()) {
            String keyPath = config.getExpandedPrivateKeyPath();
            if (config.getPrivateKeyPassphrase() != null) {
                jsch.addIdentity(keyPath, config.getPrivateKeyPassphrase());
            } else {
                jsch.addIdentity(keyPath);
            }
            log.debug("Using private key: {}", keyPath);
        }
        
        if (config.getKnownHosts() != null) {
            jsch.setKnownHosts(config.getExpandedKnownHostsPath());
        }
        
        if (verbose) {
            JSch.setLogger(new JSchSlf4jLogger());
        }
    }
    
    private void configureSocksJSch(JSch jsch, SocksProxyConfig config) throws JSchException {
        if (config.usesPrivateKey()) {
            String keyPath = config.getExpandedPrivateKeyPath();
            if (config.getPrivateKeyPassphrase() != null) {
                jsch.addIdentity(keyPath, config.getPrivateKeyPassphrase());
            } else {
                jsch.addIdentity(keyPath);
            }
        }
        
        if (config.getKnownHosts() != null) {
            jsch.setKnownHosts(config.getExpandedKnownHostsPath());
        }
        
        if (verbose) {
            JSch.setLogger(new JSchSlf4jLogger());
        }
    }
    
    private void configureSession(Session session, SshTunnelConfig config) {
        if (config.getPassword() != null) {
            session.setPassword(config.getPassword());
        }
        
        // Host key checking - use known_hosts if provided, otherwise disable
        if (config.getKnownHosts() == null) {
            session.setConfig("StrictHostKeyChecking", "no");
        }
        
        session.setConfig("PreferredAuthentications", "publickey,password");
    }
    
    private void configureSocksSession(Session session, SocksProxyConfig config) {
        if (config.getPassword() != null) {
            session.setPassword(config.getPassword());
        }
        
        if (config.getKnownHosts() == null) {
            session.setConfig("StrictHostKeyChecking", "no");
        }
        
        session.setConfig("PreferredAuthentications", "publickey,password");
    }
    
    /**
     * Closes all active SSH sessions and tunnels.
     */
    @Override
    public void close() {
        log.debug("Closing {} active SSH sessions", activeSessions.size());
        
        // Close in reverse order (most recent first)
        for (int i = activeSessions.size() - 1; i >= 0; i--) {
            Session session = activeSessions.get(i);
            if (session.isConnected()) {
                try {
                    session.disconnect();
                } catch (Exception e) {
                    log.warn("Error disconnecting session: {}", e.getMessage());
                }
            }
        }
        
        activeSessions.clear();
        activeTunnels.clear();
        activeSocksProxies.clear();
    }
    
    /**
     * Represents an active SSH tunnel.
     */
    public record ActiveTunnel(
        String name,
        int localPort,
        String remoteHost,
        int remotePort,
        Session session
    ) {}
    
    /**
     * Represents an active SOCKS proxy.
     */
    public record ActiveSocksProxy(
        String name,
        int localPort,
        Session session
    ) {}
    
    /**
     * JSch logger that bridges to SLF4J.
     */
    private static class JSchSlf4jLogger implements Logger {
        @Override
        public boolean isEnabled(int level) {
            return level >= INFO;
        }
        
        @Override
        public void log(int level, String message) {
            switch (level) {
                case DEBUG -> log.debug("[JSch] {}", message);
                case INFO -> log.info("[JSch] {}", message);
                case WARN -> log.warn("[JSch] {}", message);
                case ERROR, FATAL -> log.error("[JSch] {}", message);
            }
        }
    }
}
