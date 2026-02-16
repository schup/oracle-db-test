package org.dbtest.ssh;

/**
 * Exception thrown when SSH tunnel or SOCKS proxy operations fail.
 */
public class SshException extends Exception {
    
    public SshException(String message) {
        super(message);
    }
    
    public SshException(String message, Throwable cause) {
        super(message, cause);
    }
}
