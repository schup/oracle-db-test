package org.dbtest.cli;

import lombok.Builder;
import lombok.Value;

import java.util.*;

/**
 * Parsed command line arguments.
 */
@Value
@Builder
public class CommandLineArgs {
    
    String configPath;
    Set<String> tags;
    Set<String> onlyNames;
    boolean verbose;
    String jsonOutput;
    String junitXmlOutput;
    boolean help;
    boolean version;
    
    /**
     * Parses command line arguments.
     */
    public static CommandLineArgs parse(String[] args) {
        CommandLineArgsBuilder builder = CommandLineArgs.builder();
        
        Set<String> tags = new HashSet<>();
        Set<String> onlyNames = new HashSet<>();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.equals("-h") || arg.equals("--help")) {
                builder.help(true);
            } else if (arg.equals("--version")) {
                builder.version(true);
            } else if (arg.equals("-v") || arg.equals("--verbose")) {
                builder.verbose(true);
            } else if (arg.startsWith("-c=") || arg.startsWith("--config=")) {
                builder.configPath(extractValue(arg));
            } else if ((arg.equals("-c") || arg.equals("--config")) && i + 1 < args.length) {
                builder.configPath(args[++i]);
            } else if (arg.startsWith("-t=") || arg.startsWith("--tag=")) {
                Collections.addAll(tags, extractValue(arg).split(","));
            } else if ((arg.equals("-t") || arg.equals("--tag")) && i + 1 < args.length) {
                Collections.addAll(tags, args[++i].split(","));
            } else if (arg.startsWith("-o=") || arg.startsWith("--only=")) {
                Collections.addAll(onlyNames, extractValue(arg).split(","));
            } else if ((arg.equals("-o") || arg.equals("--only")) && i + 1 < args.length) {
                Collections.addAll(onlyNames, args[++i].split(","));
            } else if (arg.startsWith("-j=") || arg.startsWith("--json-output=")) {
                builder.jsonOutput(extractValue(arg));
            } else if ((arg.equals("-j") || arg.equals("--json-output")) && i + 1 < args.length) {
                builder.jsonOutput(args[++i]);
            } else if (arg.startsWith("-x=") || arg.startsWith("--junit-xml=")) {
                builder.junitXmlOutput(extractValue(arg));
            } else if ((arg.equals("-x") || arg.equals("--junit-xml")) && i + 1 < args.length) {
                builder.junitXmlOutput(args[++i]);
            }
        }
        
        builder.tags(tags);
        builder.onlyNames(onlyNames);
        
        return builder.build();
    }
    
    private static String extractValue(String arg) {
        int idx = arg.indexOf('=');
        return idx >= 0 ? arg.substring(idx + 1) : "";
    }
    
    /**
     * Prints help message.
     */
    public static void printHelp() {
        System.out.println("Oracle JDBC Connectivity Test Tool");
        System.out.println();
        System.out.println("Usage: java -jar oracle-jdbc-test.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c, --config <path>      Path to YAML config file (default: ./connections.yaml)");
        System.out.println("  -t, --tag <tags>         Filter by tag(s) - comma-separated");
        System.out.println("  -o, --only <names>       Test only specified connections - comma-separated");
        System.out.println("  -v, --verbose            Enable verbose/debug output");
        System.out.println("  -j, --json-output <path> Write JSON results to file");
        System.out.println("  -x, --junit-xml <path>   Write JUnit XML results to file");
        System.out.println("  -h, --help               Show this help message");
        System.out.println("      --version            Show version info");
        System.out.println();
        System.out.println("Exit Codes:");
        System.out.println("  0  All tests passed");
        System.out.println("  1  One or more tests failed");
        System.out.println("  2  Configuration error or runtime error");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar oracle-jdbc-test.jar");
        System.out.println("  java -jar oracle-jdbc-test.jar --config=./prod-connections.yaml");
        System.out.println("  java -jar oracle-jdbc-test.jar --tag=production");
        System.out.println("  java -jar oracle-jdbc-test.jar --only=db1,db2 --verbose");
        System.out.println("  java -jar oracle-jdbc-test.jar --junit-xml=results.xml");
    }
    
    /**
     * Prints version information.
     */
    public static void printVersion() {
        System.out.println("Oracle JDBC Connectivity Test Tool v1.0.0");
    }
}
