package org.dbtest.output;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dbtest.connection.ConnectionResult;
import org.dbtest.diagnostics.DiagnosticResult;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reports test results in JUnit XML format for CI/CD integration.
 * Compatible with Jenkins, GitLab CI, GitHub Actions, etc.
 */
@Slf4j
@RequiredArgsConstructor
public class JunitXmlReporter implements Reporter {
    
    private final String outputPath;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    @Override
    public void report(TestReport report) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writeXml(writer, report);
            log.info("JUnit XML report written to: {}", outputPath);
        } catch (IOException e) {
            log.error("Failed to write JUnit XML report to {}: {}", outputPath, e.getMessage());
        }
    }
    
    private void writeXml(PrintWriter writer, TestReport report) {
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        
        // Group results by primary tag for test suites
        Map<String, List<ConnectionResult>> byTag = report.getResults().stream()
            .collect(Collectors.groupingBy(r -> r.getConnection().getPrimaryTag()));
        
        // Calculate totals
        long failures = report.getResults().stream()
            .filter(r -> r.getStatus() == ConnectionResult.Status.FAILED)
            .count();
        long skipped = report.getResults().stream()
            .filter(r -> r.getStatus() == ConnectionResult.Status.SKIPPED)
            .count();
        
        writer.printf("<testsuites name=\"Oracle JDBC Connectivity Tests\" tests=\"%d\" failures=\"%d\" errors=\"0\" skipped=\"%d\" time=\"%.3f\">%n",
            report.getTotal(), failures, skipped, report.getDurationMs() / 1000.0);
        
        // Write each test suite (grouped by tag)
        for (Map.Entry<String, List<ConnectionResult>> entry : byTag.entrySet()) {
            writeTestSuite(writer, entry.getKey(), entry.getValue(), report);
        }
        
        writer.println("</testsuites>");
    }
    
    private void writeTestSuite(PrintWriter writer, String suiteName, List<ConnectionResult> results, TestReport report) {
        long suiteFailures = results.stream()
            .filter(r -> r.getStatus() == ConnectionResult.Status.FAILED)
            .count();
        long suiteSkipped = results.stream()
            .filter(r -> r.getStatus() == ConnectionResult.Status.SKIPPED)
            .count();
        
        // Calculate suite duration
        double suiteDuration = results.stream()
            .mapToLong(r -> r.getConnectionTimeMs() != null ? r.getConnectionTimeMs() : 0)
            .sum() / 1000.0;
        
        writer.printf("  <testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"0\" skipped=\"%d\" time=\"%.3f\" timestamp=\"%s\">%n",
            escapeXml(suiteName),
            results.size(),
            suiteFailures,
            suiteSkipped,
            suiteDuration,
            ISO_FORMATTER.format(report.getTimestamp()));
        
        for (ConnectionResult result : results) {
            writeTestCase(writer, result, suiteName);
        }
        
        writer.println("  </testsuite>");
    }
    
    private void writeTestCase(PrintWriter writer, ConnectionResult result, String suiteName) {
        var conn = result.getConnection();
        double testTime = (result.getConnectionTimeMs() != null ? result.getConnectionTimeMs() : 0) / 1000.0;
        
        writer.printf("    <testcase name=\"%s\" classname=\"oracle.connectivity.%s\" time=\"%.3f\">%n",
            escapeXml(conn.getName()),
            escapeXml(suiteName),
            testTime);
        
        switch (result.getStatus()) {
            case FAILED -> writeFailure(writer, result);
            case SKIPPED -> writeSkipped(writer, result);
            case SUCCESS -> {} // No additional elements needed
        }
        
        // Write system-out with connection details
        writeSystemOut(writer, result);
        
        writer.println("    </testcase>");
    }
    
    private void writeFailure(PrintWriter writer, ConnectionResult result) {
        String message = result.getErrorMessage() != null ? result.getErrorMessage() : "Connection failed";
        String type = result.getErrorType() != null ? result.getErrorType() : "UNKNOWN";
        
        writer.printf("      <failure message=\"%s\" type=\"%s\">%n",
            escapeXml(message),
            escapeXml(type));
        
        // Write detailed failure content
        writer.println(escapeXml(message));
        writer.println();
        
        DiagnosticResult diagnostics = result.getDiagnostics();
        if (diagnostics != null) {
            writer.println("Diagnostics:");
            
            var dnsCheck = diagnostics.getDnsCheck();
            if (dnsCheck != null) {
                if (dnsCheck.isSuccess()) {
                    writer.printf("- DNS Resolution: SUCCESS (%dms) → %s%n",
                        dnsCheck.getResolutionTimeMs(), dnsCheck.getResolvedIp());
                } else {
                    writer.printf("- DNS Resolution: FAILED - %s%n", dnsCheck.getErrorMessage());
                }
            }
            
            var portCheck = diagnostics.getPortCheck();
            if (portCheck != null) {
                if (portCheck.isPortOpen()) {
                    writer.println("- Port Reachability: SUCCESS - Port is open");
                } else {
                    writer.printf("- Port Reachability: FAILED - %s%n", 
                        portCheck.getErrorMessage() != null ? portCheck.getErrorMessage() : "Port not reachable");
                }
            }
            
            if (diagnostics.getAnalysis() != null) {
                writer.printf("- Analysis: %s%n", diagnostics.getAnalysis());
            }
            
            var recommendations = diagnostics.getRecommendations();
            if (recommendations != null && !recommendations.isEmpty()) {
                writer.println();
                writer.println("Recommendations:");
                for (String rec : recommendations) {
                    writer.printf("• %s%n", rec);
                }
            }
        }
        
        writer.println("      </failure>");
    }
    
    private void writeSkipped(PrintWriter writer, ConnectionResult result) {
        writer.println("      <skipped message=\"Connection disabled in configuration\"/>");
    }
    
    private void writeSystemOut(PrintWriter writer, ConnectionResult result) {
        var conn = result.getConnection();
        
        writer.println("      <system-out>");
        writer.printf("Host: %s:%d%n", conn.getHost(), conn.getPort());
        
        if (conn.usesServiceName()) {
            writer.printf("Service: %s%n", conn.getService());
        } else {
            writer.printf("SID: %s%n", conn.getSid());
        }
        
        if (result.isSuccess()) {
            writer.printf("Version: %s%n", result.getDatabaseVersion());
            writer.printf("Connection Time: %dms%n", result.getConnectionTimeMs());
            writer.printf("Test Query: OK (%dms)%n", result.getTestQueryTimeMs());
        }
        
        if (conn.getTags() != null && !conn.getTags().isEmpty()) {
            writer.printf("Tags: %s%n", String.join(", ", conn.getTags()));
        }
        
        writer.println("      </system-out>");
    }
    
    /**
     * Escapes special XML characters.
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
