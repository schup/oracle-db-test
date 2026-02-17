package org.dbtest.connection;

import lombok.Builder;
import lombok.Value;
import org.dbtest.config.ConnectionDefinition;
import org.dbtest.diagnostics.DiagnosticResult;
import org.dbtest.diagnostics.JdbcTraceInfo;

import java.time.Instant;

/**
 * Represents the result of testing a single database connection.
 */
@Value
@Builder
public class ConnectionResult {
    
    /**
     * Status of the connection test.
     */
    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED
    }
    
    /**
     * The connection definition that was tested.
     */
    ConnectionDefinition connection;
    
    /**
     * Overall status of the test.
     */
    Status status;
    
    /**
     * Timestamp when the test was performed.
     */
    @Builder.Default
    Instant timestamp = Instant.now();
    
    /**
     * Oracle database version string (if connection succeeded).
     */
    String databaseVersion;
    
    /**
     * Oracle version number (e.g., "19.0.0.0.0").
     */
    String versionNumber;
    
    /**
     * Time taken to establish the connection in milliseconds.
     */
    Long connectionTimeMs;
    
    /**
     * Time taken to execute the test query in milliseconds.
     */
    Long testQueryTimeMs;
    
    /**
     * Error message if connection failed.
     */
    String errorMessage;
    
    /**
     * Oracle error code (e.g., "ORA-12514").
     */
    String errorCode;
    
    /**
     * Categorized error type.
     */
    String errorType;
    
    /**
     * Diagnostic results from failure analysis.
     */
    DiagnosticResult diagnostics;
    
    /**
     * JDBC trace information extracted from Oracle driver logs.
     */
    JdbcTraceInfo jdbcTrace;
    
    /**
     * Returns true if the test was successful.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Creates a successful result.
     */
    public static ConnectionResult success(ConnectionDefinition conn, 
                                           String databaseVersion,
                                           String versionNumber,
                                           long connectionTimeMs, 
                                           long testQueryTimeMs,
                                           JdbcTraceInfo jdbcTrace) {
        return ConnectionResult.builder()
            .connection(conn)
            .status(Status.SUCCESS)
            .databaseVersion(databaseVersion)
            .versionNumber(versionNumber)
            .connectionTimeMs(connectionTimeMs)
            .testQueryTimeMs(testQueryTimeMs)
            .jdbcTrace(jdbcTrace)
            .build();
    }
    
    /**
     * Creates a failed result.
     */
    public static ConnectionResult failure(ConnectionDefinition conn,
                                          String errorMessage,
                                          String errorCode,
                                          String errorType,
                                          DiagnosticResult diagnostics,
                                          JdbcTraceInfo jdbcTrace) {
        return ConnectionResult.builder()
            .connection(conn)
            .status(Status.FAILED)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .errorType(errorType)
            .diagnostics(diagnostics)
            .jdbcTrace(jdbcTrace)
            .build();
    }
    
    /**
     * Creates a skipped result (for disabled connections).
     */
    public static ConnectionResult skipped(ConnectionDefinition conn) {
        return ConnectionResult.builder()
            .connection(conn)
            .status(Status.SKIPPED)
            .build();
    }
}
