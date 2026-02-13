package org.dbtest.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dbtest.connection.ConnectionResult;
import org.dbtest.diagnostics.DiagnosticResult;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports test results to a JSON file.
 */
@Slf4j
@RequiredArgsConstructor
public class JsonReporter implements Reporter {
    
    private final String outputPath;
    
    @Override
    public void report(TestReport report) {
        Map<String, Object> jsonReport = buildJsonReport(report);
        
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
        
        try (FileWriter writer = new FileWriter(outputPath)) {
            gson.toJson(jsonReport, writer);
            log.info("JSON report written to: {}", outputPath);
        } catch (IOException e) {
            log.error("Failed to write JSON report to {}: {}", outputPath, e.getMessage());
        }
    }
    
    private Map<String, Object> buildJsonReport(TestReport report) {
        Map<String, Object> json = new HashMap<>();
        
        json.put("timestamp", report.getTimestamp().toString());
        
        // Summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", report.getTotal());
        summary.put("passed", report.getPassed());
        summary.put("failed", report.getFailed());
        summary.put("skipped", report.getSkipped());
        summary.put("duration_ms", report.getDurationMs());
        json.put("summary", summary);
        
        // Results
        List<Map<String, Object>> results = new ArrayList<>();
        for (ConnectionResult result : report.getResults()) {
            results.add(buildResultJson(result));
        }
        json.put("results", results);
        
        return json;
    }
    
    private Map<String, Object> buildResultJson(ConnectionResult result) {
        Map<String, Object> json = new HashMap<>();
        var conn = result.getConnection();
        
        json.put("name", conn.getName());
        json.put("tags", conn.getTags());
        json.put("groups", conn.getGroups());
        json.put("status", result.getStatus().name());
        
        // Connection info (never include password)
        Map<String, Object> connection = new HashMap<>();
        connection.put("host", conn.getHost());
        connection.put("port", conn.getPort());
        if (conn.usesServiceName()) {
            connection.put("service", conn.getService());
        } else {
            connection.put("sid", conn.getSid());
        }
        connection.put("username", conn.getUsername());
        json.put("connection", connection);
        
        // Success info
        if (result.isSuccess()) {
            Map<String, Object> dbInfo = new HashMap<>();
            dbInfo.put("version", result.getDatabaseVersion());
            dbInfo.put("version_number", result.getVersionNumber());
            json.put("database_info", dbInfo);
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("connection_time_ms", result.getConnectionTimeMs());
            metrics.put("test_query_time_ms", result.getTestQueryTimeMs());
            json.put("metrics", metrics);
            
            json.put("test_query_result", "SUCCESS");
        }
        
        // Failure info
        if (result.getStatus() == ConnectionResult.Status.FAILED) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", result.getErrorMessage());
            error.put("code", result.getErrorCode());
            error.put("type", result.getErrorType());
            json.put("error", error);
            
            // Diagnostics
            DiagnosticResult diagnostics = result.getDiagnostics();
            if (diagnostics != null) {
                json.put("diagnostics", buildDiagnosticsJson(diagnostics));
            }
        }
        
        return json;
    }
    
    private Map<String, Object> buildDiagnosticsJson(DiagnosticResult diagnostics) {
        Map<String, Object> json = new HashMap<>();
        
        // DNS Check
        var dnsCheck = diagnostics.getDnsCheck();
        if (dnsCheck != null) {
            Map<String, Object> dns = new HashMap<>();
            dns.put("status", dnsCheck.isSuccess() ? "SUCCESS" : "FAILED");
            dns.put("resolved_ip", dnsCheck.getResolvedIp());
            dns.put("resolution_time_ms", dnsCheck.getResolutionTimeMs());
            if (!dnsCheck.isSuccess()) {
                dns.put("error_message", dnsCheck.getErrorMessage());
            }
            json.put("dns_resolution", dns);
        }
        
        // Port Check
        var portCheck = diagnostics.getPortCheck();
        if (portCheck != null) {
            Map<String, Object> port = new HashMap<>();
            port.put("status", portCheck.isSuccess() ? "SUCCESS" : "FAILED");
            port.put("port_open", portCheck.isPortOpen());
            port.put("check_time_ms", portCheck.getCheckTimeMs());
            if (!portCheck.isPortOpen()) {
                port.put("error_message", portCheck.getErrorMessage());
            }
            json.put("port_reachability", port);
        }
        
        // Analysis
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("issue", diagnostics.getAnalysis());
        analysis.put("recommendations", diagnostics.getRecommendations());
        json.put("analysis", analysis);
        
        return json;
    }
}
