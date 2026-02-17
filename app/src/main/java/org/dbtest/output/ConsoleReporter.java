package org.dbtest.output;

import org.dbtest.config.ConnectionDefinition;
import org.dbtest.connection.ConnectionResult;
import org.dbtest.diagnostics.DiagnosticResult;
import org.dbtest.diagnostics.JdbcTraceInfo;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Reports test results to the console with colored output.
 */
public class ConsoleReporter implements Reporter {
    
    private final boolean verbose;
    
    public ConsoleReporter(boolean verbose) {
        this.verbose = verbose;
        AnsiConsole.systemInstall();
    }
    
    @Override
    public void report(TestReport report) {
        printHeader();
        printResults(report);
        printSummary(report);
    }
    
    private void printHeader() {
        System.out.println();
        System.out.println(ansi().fg(Ansi.Color.CYAN).bold()
            .a("╔═══════════════════════════════════════════════════════════════════════╗").reset());
        System.out.println(ansi().fg(Ansi.Color.CYAN).bold()
            .a("║           Oracle JDBC Connectivity Test Results                       ║").reset());
        System.out.println(ansi().fg(Ansi.Color.CYAN).bold()
            .a("╚═══════════════════════════════════════════════════════════════════════╝").reset());
        System.out.println();
    }
    
    private void printResults(TestReport report) {
        System.out.println(String.format("Testing %d connection(s)...", report.getTotal()));
        System.out.println();
        
        for (ConnectionResult result : report.getResults()) {
            printSeparator();
            printResult(result);
        }
        
        printSeparator();
    }
    
    private void printSeparator() {
        System.out.println(ansi().fg(Ansi.Color.WHITE)
            .a("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").reset());
        System.out.println();
    }
    
    private void printResult(ConnectionResult result) {
        var conn = result.getConnection();
        String tags = conn.getTags() != null && !conn.getTags().isEmpty() 
            ? " (" + String.join(", ", conn.getTags()) + ")" 
            : "";
        
        switch (result.getStatus()) {
            case SUCCESS -> printSuccess(result, tags);
            case FAILED -> printFailure(result, tags);
            case SKIPPED -> printSkipped(result, tags);
        }
        
        System.out.println();
    }
    
    private void printSuccess(ConnectionResult result, String tags) {
        var conn = result.getConnection();
        
        System.out.println(ansi().fg(Ansi.Color.GREEN).bold()
            .a("[✓] ").a(conn.getName()).reset()
            .fg(Ansi.Color.WHITE).a(tags).reset());
        
        printConnectionInfo(result);
        
        for (String line : ConnectionInfoFormatter.formatSuccessInfo(result)) {
            System.out.println("    " + line);
        }
    }
    
    private void printFailure(ConnectionResult result, String tags) {
        var conn = result.getConnection();
        
        System.out.println(ansi().fg(Ansi.Color.RED).bold()
            .a("[✗] ").a(conn.getName()).reset()
            .fg(Ansi.Color.WHITE).a(tags).reset());
        
        printConnectionInfo(result);
        
        System.out.println(ansi().fg(Ansi.Color.RED)
            .a("    Error: ").a(result.getErrorMessage()).reset());
        
        // Print diagnostics
        DiagnosticResult diagnostics = result.getDiagnostics();
        if (diagnostics != null) {
            printDiagnostics(diagnostics);
        }
    }
    
    private void printDiagnostics(DiagnosticResult diagnostics) {
        System.out.println();
        System.out.println(ansi().fg(Ansi.Color.YELLOW).a("    Diagnostics:").reset());
        
        // DNS Check
        var dnsCheck = diagnostics.getDnsCheck();
        if (dnsCheck != null) {
            if (dnsCheck.isSuccess()) {
                System.out.println(ansi().fg(Ansi.Color.GREEN)
                    .a("    ├─ DNS Resolution: ✓ ")
                    .a(String.format("(%dms) → %s", dnsCheck.getResolutionTimeMs(), dnsCheck.getResolvedIp()))
                    .reset());
            } else {
                System.out.println(ansi().fg(Ansi.Color.RED)
                    .a("    ├─ DNS Resolution: ✗ ")
                    .a(dnsCheck.getErrorMessage())
                    .reset());
            }
        }
        
        // Port Check
        var portCheck = diagnostics.getPortCheck();
        if (portCheck != null) {
            if (portCheck.isPortOpen()) {
                System.out.println(ansi().fg(Ansi.Color.GREEN)
                    .a("    ├─ Port Reachability: ✓ Port is open")
                    .reset());
            } else {
                System.out.println(ansi().fg(Ansi.Color.RED)
                    .a("    ├─ Port Reachability: ✗ ")
                    .a(portCheck.getErrorMessage() != null ? portCheck.getErrorMessage() : "Port not reachable")
                    .reset());
            }
        }
        
        // Analysis
        if (diagnostics.getAnalysis() != null) {
            System.out.println(ansi().fg(Ansi.Color.YELLOW)
                .a("    └─ Analysis: ").a(diagnostics.getAnalysis())
                .reset());
        }
        
        // Recommendations
        List<String> recommendations = diagnostics.getRecommendations();
        if (recommendations != null && !recommendations.isEmpty()) {
            System.out.println();
            System.out.println(ansi().fg(Ansi.Color.CYAN).a("    Recommendations:").reset());
            for (String rec : recommendations) {
                System.out.println(ansi().fg(Ansi.Color.WHITE)
                    .a("    • ").a(rec).reset());
            }
        }
    }
    
    private void printSkipped(ConnectionResult result, String tags) {
        var conn = result.getConnection();
        
        System.out.println(ansi().fg(Ansi.Color.YELLOW).bold()
            .a("[⊘] ").a(conn.getName()).a(" (SKIPPED)").reset()
            .fg(Ansi.Color.WHITE).a(tags).reset());
        
        System.out.println("    Connection is disabled in configuration");
    }
    
    private void printSummary(TestReport report) {
        System.out.println();
        System.out.println(ansi().bold().a("Summary:").reset());
        
        Ansi.Color statusColor = report.getFailed() > 0 ? Ansi.Color.RED : Ansi.Color.GREEN;
        
        System.out.println(ansi()
            .a("  Total: ").bold().a(report.getTotal()).reset()
            .a(" | ")
            .fg(Ansi.Color.GREEN).a("Passed: ").bold().a(report.getPassed()).reset()
            .a(" | ")
            .fg(Ansi.Color.RED).a("Failed: ").bold().a(report.getFailed()).reset()
            .a(" | ")
            .fg(Ansi.Color.YELLOW).a("Skipped: ").bold().a(report.getSkipped()).reset());
        
        System.out.println(String.format("  Duration: %dms", report.getDurationMs()));
        System.out.println();
        
        int exitCode = report.getFailed() > 0 ? 1 : 0;
        System.out.println(ansi().fg(statusColor)
            .a("Exit Code: ").bold().a(exitCode).reset());
        
        System.out.println();
    }
    
    private void printConnectionInfo(ConnectionResult result) {
        for (String line : ConnectionInfoFormatter.formatConnectionInfo(result)) {
            System.out.println("    " + line);
        }
    }
}
