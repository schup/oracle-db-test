package org.dbtest.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionDefinitionTest {
    
    @Test
    @DisplayName("Should detect service name usage")
    void detectServiceName() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .host("localhost")
            .service("TESTDB")
            .username("user")
            .password("pass")
            .build();
        
        assertTrue(conn.usesServiceName());
        assertEquals("TESTDB", conn.getServiceOrSid());
    }
    
    @Test
    @DisplayName("Should detect SID usage")
    void detectSid() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .host("localhost")
            .sid("ORCL")
            .username("user")
            .password("pass")
            .build();
        
        assertFalse(conn.usesServiceName());
        assertEquals("ORCL", conn.getServiceOrSid());
    }
    
    @Test
    @DisplayName("Should use default test query")
    void defaultTestQuery() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .build();
        
        assertEquals("SELECT 1 FROM DUAL", conn.getEffectiveTestQuery());
    }
    
    @Test
    @DisplayName("Should use custom test query when specified")
    void customTestQuery() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .testQuery("SELECT * FROM users")
            .build();
        
        assertEquals("SELECT * FROM users", conn.getEffectiveTestQuery());
    }
    
    @Test
    @DisplayName("Should return primary tag")
    void primaryTag() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .tags(List.of("production", "primary"))
            .build();
        
        assertEquals("production", conn.getPrimaryTag());
    }
    
    @Test
    @DisplayName("Should return untagged when no tags")
    void untaggedWhenNoTags() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .build();
        
        assertEquals("untagged", conn.getPrimaryTag());
    }
    
    @Test
    @DisplayName("Should use default port")
    void defaultPort() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .build();
        
        assertEquals(1521, conn.getPort());
    }
    
    @Test
    @DisplayName("Should use default timeout")
    void defaultTimeout() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .build();
        
        assertEquals(10, conn.getTimeout());
    }
    
    @Test
    @DisplayName("Should be enabled by default")
    void enabledByDefault() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .build();
        
        assertTrue(conn.getEnabled());
    }
    
    @Test
    @DisplayName("ToString should not contain password")
    void toStringExcludesPassword() {
        ConnectionDefinition conn = ConnectionDefinition.builder()
            .name("test")
            .host("localhost")
            .service("TESTDB")
            .username("user")
            .password("supersecret")
            .passwordProvider("vault")
            .build();
        
        String str = conn.toString();
        assertFalse(str.contains("supersecret"));
        assertFalse(str.contains("vault"));
    }
}
