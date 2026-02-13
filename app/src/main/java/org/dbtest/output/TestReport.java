package org.dbtest.output;

import lombok.Builder;
import lombok.Value;
import org.dbtest.connection.ConnectionResult;

import java.time.Instant;
import java.util.List;

/**
 * Represents the complete test report with all results and summary.
 */
@Value
@Builder
public class TestReport {
    
    /**
     * Timestamp when the test run started.
     */
    @Builder.Default
    Instant timestamp = Instant.now();
    
    /**
     * Individual test results.
     */
    List<ConnectionResult> results;
    
    /**
     * Total number of connections tested.
     */
    int total;
    
    /**
     * Number of successful connections.
     */
    int passed;
    
    /**
     * Number of failed connections.
     */
    int failed;
    
    /**
     * Number of skipped connections (disabled).
     */
    int skipped;
    
    /**
     * Total duration of all tests in milliseconds.
     */
    long durationMs;
    
    /**
     * Creates a TestReport from a list of results.
     */
    public static TestReport fromResults(List<ConnectionResult> results, long durationMs) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        
        for (ConnectionResult result : results) {
            switch (result.getStatus()) {
                case SUCCESS -> passed++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }
        
        return TestReport.builder()
            .results(results)
            .total(results.size())
            .passed(passed)
            .failed(failed)
            .skipped(skipped)
            .durationMs(durationMs)
            .build();
    }
    
    /**
     * Returns true if all tests passed (no failures).
     */
    public boolean isAllPassed() {
        return failed == 0;
    }
}
