package org.dbtest.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineArgsTest {
    
    @Test
    @DisplayName("Should parse help flag")
    void parseHelp() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--help"});
        assertTrue(args.isHelp());
        
        args = CommandLineArgs.parse(new String[]{"-h"});
        assertTrue(args.isHelp());
    }
    
    @Test
    @DisplayName("Should parse version flag")
    void parseVersion() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--version"});
        assertTrue(args.isVersion());
    }
    
    @Test
    @DisplayName("Should parse verbose flag")
    void parseVerbose() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--verbose"});
        assertTrue(args.isVerbose());
        
        args = CommandLineArgs.parse(new String[]{"-v"});
        assertTrue(args.isVerbose());
    }
    
    @Test
    @DisplayName("Should parse config path with equals")
    void parseConfigWithEquals() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--config=/path/to/config.yaml"});
        assertEquals("/path/to/config.yaml", args.getConfigPath());
        
        args = CommandLineArgs.parse(new String[]{"-c=config.yaml"});
        assertEquals("config.yaml", args.getConfigPath());
    }
    
    @Test
    @DisplayName("Should parse config path with space")
    void parseConfigWithSpace() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--config", "/path/to/config.yaml"});
        assertEquals("/path/to/config.yaml", args.getConfigPath());
        
        args = CommandLineArgs.parse(new String[]{"-c", "config.yaml"});
        assertEquals("config.yaml", args.getConfigPath());
    }
    
    @Test
    @DisplayName("Should parse single tag")
    void parseSingleTag() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--tag=production"});
        assertTrue(args.getTags().contains("production"));
        assertEquals(1, args.getTags().size());
    }
    
    @Test
    @DisplayName("Should parse multiple comma-separated tags")
    void parseMultipleTags() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--tag=production,primary"});
        assertTrue(args.getTags().contains("production"));
        assertTrue(args.getTags().contains("primary"));
        assertEquals(2, args.getTags().size());
    }
    
    @Test
    @DisplayName("Should parse only names")
    void parseOnlyNames() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--only=db1,db2"});
        assertTrue(args.getOnlyNames().contains("db1"));
        assertTrue(args.getOnlyNames().contains("db2"));
    }
    
    @Test
    @DisplayName("Should parse json output path")
    void parseJsonOutput() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--json-output=results.json"});
        assertEquals("results.json", args.getJsonOutput());
        
        args = CommandLineArgs.parse(new String[]{"-j", "output.json"});
        assertEquals("output.json", args.getJsonOutput());
    }
    
    @Test
    @DisplayName("Should parse junit xml output path")
    void parseJunitXmlOutput() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{"--junit-xml=test-results.xml"});
        assertEquals("test-results.xml", args.getJunitXmlOutput());
        
        args = CommandLineArgs.parse(new String[]{"-x", "results.xml"});
        assertEquals("results.xml", args.getJunitXmlOutput());
    }
    
    @Test
    @DisplayName("Should parse multiple options together")
    void parseMultipleOptions() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{
            "--config=test.yaml",
            "--tag=prod",
            "--verbose",
            "--json-output=out.json"
        });
        
        assertEquals("test.yaml", args.getConfigPath());
        assertTrue(args.getTags().contains("prod"));
        assertTrue(args.isVerbose());
        assertEquals("out.json", args.getJsonOutput());
    }
    
    @Test
    @DisplayName("Should handle empty args")
    void handleEmptyArgs() {
        CommandLineArgs args = CommandLineArgs.parse(new String[]{});
        
        assertNull(args.getConfigPath());
        assertTrue(args.getTags().isEmpty());
        assertTrue(args.getOnlyNames().isEmpty());
        assertFalse(args.isVerbose());
        assertFalse(args.isHelp());
    }
}
