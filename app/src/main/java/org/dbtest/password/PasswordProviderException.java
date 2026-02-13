package org.dbtest.password;

/**
 * Exception thrown when a password provider fails to retrieve a password.
 */
public class PasswordProviderException extends Exception {
    
    public PasswordProviderException(String message) {
        super(message);
    }
    
    public PasswordProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
