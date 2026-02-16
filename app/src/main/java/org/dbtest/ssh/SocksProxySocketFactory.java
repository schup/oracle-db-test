package org.dbtest.ssh;

import lombok.extern.slf4j.Slf4j;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * Custom socket factory that routes connections through a SOCKS proxy.
 * Used with Oracle JDBC to connect through an SSH dynamic port forward.
 */
@Slf4j
public class SocksProxySocketFactory extends SocketFactory {
    
    private final String socksHost;
    private final int socksPort;
    
    public SocksProxySocketFactory(String socksHost, int socksPort) {
        this.socksHost = socksHost;
        this.socksPort = socksPort;
    }
    
    @Override
    public Socket createSocket() throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
        log.debug("Creating socket through SOCKS proxy {}:{}", socksHost, socksPort);
        return new Socket(proxy);
    }
    
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = createSocket();
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }
    
    @Override
    public Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) 
            throws IOException {
        Socket socket = createSocket();
        socket.bind(new InetSocketAddress(localHost, localPort));
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }
    
    @Override
    public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
        Socket socket = createSocket();
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }
    
    @Override
    public Socket createSocket(java.net.InetAddress host, int port, 
                               java.net.InetAddress localHost, int localPort) throws IOException {
        Socket socket = createSocket();
        socket.bind(new InetSocketAddress(localHost, localPort));
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }
}
