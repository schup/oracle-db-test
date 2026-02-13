package org.dbtest.password;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentVariablePasswordProviderTest {
    
    @Test
    @DisplayName("Should detect environment variable reference")
    void detectEnvVarReference() {
        assertTrue(EnvironmentVariablePasswordProvider.containsEnvVarReference("${MY_VAR}"));
        assertTrue(EnvironmentVariablePasswordProvider.containsEnvVarReference("prefix${MY_VAR}suffix"));
        assertFalse(EnvironmentVariablePasswordProvider.containsEnvVarReference("plain_text"));
        assertFalse(EnvironmentVariablePasswordProvider.containsEnvVarReference(null));
        assertFalse(EnvironmentVariablePasswordProvider.containsEnvVarReference(""));
    }
    
    @Test
    @DisplayName("Should resolve existing environment variable")
    void resolveExistingEnvVar() throws Exception {
        // PATH is always set
        EnvironmentVariablePasswordProvider provider = new EnvironmentVariablePasswordProvider();
        
        String result = provider.resolveEnvironmentVariables("${PATH}", "test-conn");
        
        assertNotNull(result);
        assertFalse(result.contains("${"));
        assertEquals(System.getenv("PATH"), result);
    }
    
    @Test
    @DisplayName("Should throw on missing environment variable")
    void throwOnMissingEnvVar() {
        EnvironmentVariablePasswordProvider provider = new EnvironmentVariablePasswordProvider();
        
        PasswordProviderException ex = assertThrows(
            PasswordProviderException.class,
            () -> provider.resolveEnvironmentVariables("${DEFINITELY_NOT_SET_12345}", "test-conn")
        );
        
        assertTrue(ex.getMessage().contains("DEFINITELY_NOT_SET_12345"));
        assertTrue(ex.getMessage().contains("not set"));
    }
    
    @Test
    @DisplayName("Should preserve text around environment variable")
    void preserveSurroundingText() throws Exception {
        EnvironmentVariablePasswordProvider provider = new EnvironmentVariablePasswordProvider();
        
        // Use HOME which is commonly set
        String home = System.getenv("HOME");
        if (home != null) {
            String result = provider.resolveEnvironmentVariables("prefix${HOME}suffix", "test-conn");
            assertEquals("prefix" + home + "suffix", result);
        }
    }
    
    @Test
    @DisplayName("Should return text as-is if no env var reference")
    void returnTextAsIs() throws Exception {
        EnvironmentVariablePasswordProvider provider = new EnvironmentVariablePasswordProvider();
        
        String result = provider.resolveEnvironmentVariables("plain_password", "test-conn");
        
        assertEquals("plain_password", result);
    }
}
