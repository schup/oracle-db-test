package org.dbtest.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    
    private final ConfigLoader loader = new ConfigLoader();
    
    private InputStream toStream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
    
    @Test
    @DisplayName("Should load valid configuration with service name")
    void loadValidConfigWithService() throws Exception {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                port: 1521
                service: TESTDB
                username: testuser
                password: testpass
                tags:
                  - test
            """;
        
        DatabaseConfig config = loader.loadFromStream(toStream(yaml));
        
        assertEquals(1, config.getConnections().size());
        ConnectionDefinition conn = config.getConnections().get(0);
        assertEquals("test-db", conn.getName());
        assertEquals("localhost", conn.getHost());
        assertEquals(1521, conn.getPort());
        assertEquals("TESTDB", conn.getService());
        assertNull(conn.getSid());
        assertEquals("testuser", conn.getUsername());
        assertTrue(conn.usesServiceName());
    }
    
    @Test
    @DisplayName("Should load valid configuration with SID")
    void loadValidConfigWithSid() throws Exception {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                port: 1521
                sid: ORCL
                username: testuser
                password: testpass
            """;
        
        DatabaseConfig config = loader.loadFromStream(toStream(yaml));
        
        ConnectionDefinition conn = config.getConnections().get(0);
        assertEquals("ORCL", conn.getSid());
        assertNull(conn.getService());
        assertFalse(conn.usesServiceName());
    }
    
    @Test
    @DisplayName("Should use default port when not specified")
    void defaultPort() throws Exception {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                service: TESTDB
                username: testuser
                password: testpass
            """;
        
        DatabaseConfig config = loader.loadFromStream(toStream(yaml));
        
        assertEquals(1521, config.getConnections().get(0).getPort());
    }
    
    @Test
    @DisplayName("Should fail when both service and sid are specified")
    void failOnBothServiceAndSid() {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                service: TESTDB
                sid: ORCL
                username: testuser
                password: testpass
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("Both 'service' and 'sid' are specified"));
    }
    
    @Test
    @DisplayName("Should fail when neither service nor sid is specified")
    void failOnMissingServiceAndSid() {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                username: testuser
                password: testpass
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("Either 'service' or 'sid' must be specified"));
    }
    
    @Test
    @DisplayName("Should fail on duplicate connection names (case-insensitive)")
    void failOnDuplicateNames() {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                service: DB1
                username: user1
                password: pass1
              - name: TEST-DB
                host: localhost
                service: DB2
                username: user2
                password: pass2
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("Duplicate connection name"));
    }
    
    @Test
    @DisplayName("Should fail when password is missing")
    void failOnMissingPassword() {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                service: TESTDB
                username: testuser
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("No password source specified"));
    }
    
    @Test
    @DisplayName("Should fail on invalid port number")
    void failOnInvalidPort() {
        String yaml = """
            connections:
              - name: test-db
                host: localhost
                port: 99999
                service: TESTDB
                username: testuser
                password: testpass
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("Port must be between"));
    }
    
    @Test
    @DisplayName("Should collect multiple validation errors")
    void collectMultipleErrors() {
        String yaml = """
            connections:
              - name: test-db
                service: TESTDB
                sid: ORCL
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        // Should have errors for: host, username, password, both service and sid
        String message = ex.getMessage();
        assertTrue(message.contains("Host is required"));
        assertTrue(message.contains("Username is required"));
        assertTrue(message.contains("Both 'service' and 'sid'"));
    }
    
    @Test
    @DisplayName("Should fail on empty configuration")
    void failOnEmptyConfig() {
        String yaml = "";
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("empty"));
    }
    
    @Test
    @DisplayName("Should fail on no connections defined")
    void failOnNoConnections() {
        String yaml = """
            connections: []
            """;
        
        ConfigLoader.ConfigurationException ex = assertThrows(
            ConfigLoader.ConfigurationException.class,
            () -> loader.loadFromStream(toStream(yaml))
        );
        
        assertTrue(ex.getMessage().contains("No connections defined"));
    }
}
