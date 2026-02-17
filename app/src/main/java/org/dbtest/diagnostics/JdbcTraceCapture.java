package org.dbtest.diagnostics;

import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures and parses Oracle JDBC trace logs to extract connection details.
 * Configures logging programmatically - no external configuration files needed.
 */
@Slf4j
public class JdbcTraceCapture {
    
    // Loggers to capture
    private static final String ORACLE_NET_LOGGER = "oracle.net";
    private static final String ORACLE_JDBC_LOGGER = "oracle.jdbc";
    
    // Patterns to extract connection details
    // TcpNTAdapter: "Connect succeeded. host=X, port=Y, ... socket=Socket[addr=H/IP,port=P,localport=L], ... connectTime=N ms"
    private static final Pattern CONNECT_RESULT_PATTERN = Pattern.compile(
        "Connect (succeeded|failed)\\. host=([^,]+), port=(\\d+)");
    
    private static final Pattern SOCKET_PATTERN = Pattern.compile(
        "socket=Socket\\[addr=([^/]*)/([^,]*),port=(\\d+),localport=(\\d+)\\]");
    
    private static final Pattern CONNECT_TIME_PATTERN = Pattern.compile(
        "connectTime=(\\d+)\\s*ms");
    
    // ConnStrategy: "Trying connection. option=[host=X port=Y ..."
    private static final Pattern TRYING_CONNECTION_PATTERN = Pattern.compile(
        "Trying connection\\. option=\\[host=([^\\s]+) port=(\\d+)");
    
    // T4CConnection updateSessionProperties: contains AUTH_SC_SERVER_HOST=X and SERVER_HOST=X
    private static final Pattern SERVER_HOST_PATTERN = Pattern.compile(
        "SERVER_HOST=([^,}]+)");
    
    private static boolean loggingConfigured = false;
    
    private final List<String> capturedLogs = new ArrayList<>();
    private final CaptureHandler handler = new CaptureHandler();
    private final boolean verbose;
    
    // Extracted data
    private String listenerHost;
    private Integer listenerPort;
    private String dataHost;
    private Integer dataPort;
    private String dataIp;
    private Integer localPort;
    private String serverHost;
    private Long connectTimeMs;
    private Boolean connectSucceeded;
    
    public JdbcTraceCapture(boolean verbose) {
        this.verbose = verbose;
    }
    
    /**
     * Configures Oracle JDBC logging programmatically.
     * Must be called before any JDBC operations.
     */
    public static synchronized void configureLogging() {
        if (loggingConfigured) {
            return;
        }
        
        // Enable Oracle JDBC diagnostic logging
        System.setProperty("oracle.jdbc.diagnostic.enableLogging", "true");
        
        // Configure JUL loggers for Oracle packages
        Logger oracleNetLogger = Logger.getLogger(ORACLE_NET_LOGGER);
        Logger oracleJdbcLogger = Logger.getLogger(ORACLE_JDBC_LOGGER);
        
        // Set levels - INFO captures the key connection messages
        oracleNetLogger.setLevel(Level.INFO);
        oracleJdbcLogger.setLevel(Level.FINE);
        
        // Prevent logs from going to parent handlers (console)
        oracleNetLogger.setUseParentHandlers(false);
        oracleJdbcLogger.setUseParentHandlers(false);
        
        loggingConfigured = true;
        log.debug("Oracle JDBC trace logging configured");
    }
    
    /**
     * Starts capturing JDBC trace logs.
     */
    public void startCapture() {
        capturedLogs.clear();
        resetExtractedData();
        
        Logger oracleNetLogger = Logger.getLogger(ORACLE_NET_LOGGER);
        Logger oracleJdbcLogger = Logger.getLogger(ORACLE_JDBC_LOGGER);
        
        oracleNetLogger.addHandler(handler);
        oracleJdbcLogger.addHandler(handler);
    }
    
    /**
     * Stops capturing and returns extracted trace info.
     */
    public JdbcTraceInfo stopCapture() {
        Logger oracleNetLogger = Logger.getLogger(ORACLE_NET_LOGGER);
        Logger oracleJdbcLogger = Logger.getLogger(ORACLE_JDBC_LOGGER);
        
        oracleNetLogger.removeHandler(handler);
        oracleJdbcLogger.removeHandler(handler);
        
        log.debug("Captured {} Oracle JDBC log entries", capturedLogs.size());
        
        // Parse captured logs
        parseCaptureLogs();
        
        return JdbcTraceInfo.builder()
            .listenerHost(listenerHost)
            .listenerPort(listenerPort)
            .dataHost(dataHost)
            .dataPort(dataPort)
            .dataIp(dataIp)
            .localPort(localPort)
            .serverHost(serverHost)
            .connectTimeMs(connectTimeMs)
            .connectSucceeded(connectSucceeded)
            .rawLogs(verbose ? new ArrayList<>(capturedLogs) : null)
            .build();
    }
    
    private void resetExtractedData() {
        listenerHost = null;
        listenerPort = null;
        dataHost = null;
        dataPort = null;
        dataIp = null;
        localPort = null;
        serverHost = null;
        connectTimeMs = null;
        connectSucceeded = null;
    }
    
    private void parseCaptureLogs() {
        for (String msg : capturedLogs) {
            // Check for "Trying connection" (listener details)
            Matcher tryingMatcher = TRYING_CONNECTION_PATTERN.matcher(msg);
            if (tryingMatcher.find()) {
                listenerHost = tryingMatcher.group(1);
                listenerPort = Integer.parseInt(tryingMatcher.group(2));
            }
            
            // Check for "Connect succeeded/failed" (data connection details)
            Matcher connectMatcher = CONNECT_RESULT_PATTERN.matcher(msg);
            if (connectMatcher.find()) {
                connectSucceeded = "succeeded".equals(connectMatcher.group(1));
                dataHost = connectMatcher.group(2);
                dataPort = Integer.parseInt(connectMatcher.group(3));
                
                // Extract socket details
                Matcher socketMatcher = SOCKET_PATTERN.matcher(msg);
                if (socketMatcher.find()) {
                    // addr=hostname/ip format
                    String addrPart = socketMatcher.group(2);
                    if (!addrPart.isEmpty()) {
                        dataIp = addrPart;
                    }
                    localPort = Integer.parseInt(socketMatcher.group(4));
                }
                
                // Extract connect time
                Matcher timeMatcher = CONNECT_TIME_PATTERN.matcher(msg);
                if (timeMatcher.find()) {
                    connectTimeMs = Long.parseLong(timeMatcher.group(1));
                }
            }
            
            // Check for SERVER_HOST in session properties
            if (msg.contains("session Properties=") || msg.contains("SESSION_")) {
                Matcher serverMatcher = SERVER_HOST_PATTERN.matcher(msg);
                if (serverMatcher.find()) {
                    serverHost = serverMatcher.group(1);
                }
            }
        }
    }
    
    /**
     * Custom handler that captures log records to a list.
     */
    private class CaptureHandler extends Handler {
        
        public CaptureHandler() {
            setLevel(Level.ALL);
        }
        
        @Override
        public void publish(LogRecord record) {
            if (record == null || record.getMessage() == null) {
                return;
            }
            
            String loggerName = record.getLoggerName();
            
            // Only capture relevant Oracle JDBC/NET logs
            if (loggerName != null && 
                (loggerName.startsWith("oracle.net") || loggerName.startsWith("oracle.jdbc"))) {
                // Format the message with parameters
                String message = formatMessage(record);
                capturedLogs.add(message);
            }
        }
        
        private String formatMessage(LogRecord record) {
            String pattern = record.getMessage();
            Object[] params = record.getParameters();
            
            if (params == null || params.length == 0) {
                return pattern;
            }
            
            try {
                return MessageFormat.format(pattern, params);
            } catch (Exception e) {
                // If formatting fails, return raw message
                return pattern;
            }
        }
        
        @Override
        public void flush() {
            // No-op
        }
        
        @Override
        public void close() throws SecurityException {
            // No-op
        }
    }
}
