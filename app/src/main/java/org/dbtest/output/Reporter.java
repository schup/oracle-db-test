package org.dbtest.output;

/**
 * Interface for test result reporters.
 */
public interface Reporter {
    
    /**
     * Reports the test results.
     */
    void report(TestReport report);
}
