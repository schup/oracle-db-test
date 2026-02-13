package org.dbtest;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.dbtest.cli.CommandLineArgs;
import org.dbtest.config.ConfigLoader;
import org.dbtest.config.ConfigLoader.ConfigurationException;
import org.dbtest.config.ConnectionDefinition;
import org.dbtest.config.DatabaseConfig;
import org.dbtest.connection.ConnectionResult;
import org.dbtest.connection.ConnectionTester;
import org.dbtest.diagnostics.DiagnosticEngine;
import org.dbtest.output.*;
import org.dbtest.password.PasswordProviderException;
import org.dbtest.password.PasswordProviderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main entry point for the Oracle JDBC Connectivity Test Tool.
 */
@Slf4j
public class Main {
    
    private static final String DEFAULT_CONFIG_FILE = "connections.yaml";
    
    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }
    
    /**
     * Main run method - returns exit code.
     */
    public static int run(String[] args) {
        // Parse command line arguments
        CommandLineArgs cliArgs = CommandLineArgs.parse(args);
        
        // Handle help and version
        if (cliArgs.isHelp()) {
            CommandLineArgs.printHelp();
            return 0;
        }
        
        if (cliArgs.isVersion()) {
            CommandLineArgs.printVersion();
            return 0;
        }
        
        // Configure logging level
        if (cliArgs.isVerbose()) {
            Configurator.setRootLevel(Level.DEBUG);
            log.debug("Verbose mode enabled");
        }
        
        try {
            return executeTests(cliArgs);
        } catch (ConfigurationException e) {
            System.err.println();
            System.err.println(e.getMessage());
            return 2;
        } catch (Exception e) {
            log.error("Unexpected error", e);
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
    
    private static int executeTests(CommandLineArgs cliArgs) throws ConfigurationException, PasswordProviderException {
        // Determine config file path
        String configPath = cliArgs.getConfigPath() != null 
            ? cliArgs.getConfigPath() 
            : DEFAULT_CONFIG_FILE;
        
        // Load and validate configuration
        ConfigLoader configLoader = new ConfigLoader();
        DatabaseConfig config = configLoader.load(configPath);
        
        log.info("Loaded {} connection(s) from configuration", config.getConnections().size());
        
        // Initialize password providers
        PasswordProviderFactory passwordProviderFactory = new PasswordProviderFactory();
        passwordProviderFactory.initializeProviders(config.getPasswordProviders());
        
        // Filter connections
        List<ConnectionDefinition> connections = filterConnections(
            config.getConnections(), 
            cliArgs.getTags(), 
            cliArgs.getOnlyNames()
        );
        
        if (connections.isEmpty()) {
            System.err.println("No connections match the specified filters.");
            return 2;
        }
        
        log.info("Testing {} connection(s) after filtering", connections.size());
        
        // Initialize components
        DiagnosticEngine diagnosticEngine = new DiagnosticEngine();
        ConnectionTester connectionTester = new ConnectionTester(passwordProviderFactory, diagnosticEngine);
        
        // Run tests
        long startTime = System.currentTimeMillis();
        List<ConnectionResult> results = new ArrayList<>();
        
        for (ConnectionDefinition conn : connections) {
            ConnectionResult result = connectionTester.test(conn);
            results.add(result);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Create test report
        TestReport report = TestReport.fromResults(results, duration);
        
        // Generate output
        List<Reporter> reporters = createReporters(cliArgs);
        for (Reporter reporter : reporters) {
            reporter.report(report);
        }
        
        // Return appropriate exit code
        return report.isAllPassed() ? 0 : 1;
    }
    
    /**
     * Filters connections based on tags and names.
     */
    private static List<ConnectionDefinition> filterConnections(
            List<ConnectionDefinition> connections,
            Set<String> filterTags,
            Set<String> filterNames) {
        
        return connections.stream()
            .filter(conn -> {
                // Filter by name if specified
                if (filterNames != null && !filterNames.isEmpty()) {
                    if (!filterNames.contains(conn.getName())) {
                        return false;
                    }
                }
                
                // Filter by tags if specified (AND logic - must have all tags)
                if (filterTags != null && !filterTags.isEmpty()) {
                    if (conn.getTags() == null || conn.getTags().isEmpty()) {
                        return false;
                    }
                    for (String tag : filterTags) {
                        if (!conn.getTags().contains(tag)) {
                            return false;
                        }
                    }
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Creates reporters based on CLI arguments.
     */
    private static List<Reporter> createReporters(CommandLineArgs cliArgs) {
        List<Reporter> reporters = new ArrayList<>();
        
        // Always add console reporter
        reporters.add(new ConsoleReporter(cliArgs.isVerbose()));
        
        // Add JSON reporter if specified
        if (cliArgs.getJsonOutput() != null && !cliArgs.getJsonOutput().isBlank()) {
            reporters.add(new JsonReporter(cliArgs.getJsonOutput()));
        }
        
        // Add JUnit XML reporter if specified
        if (cliArgs.getJunitXmlOutput() != null && !cliArgs.getJunitXmlOutput().isBlank()) {
            reporters.add(new JunitXmlReporter(cliArgs.getJunitXmlOutput()));
        }
        
        return reporters;
    }
}
