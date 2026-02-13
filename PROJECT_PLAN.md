# Oracle JDBC Connectivity Test Tool - Project Plan

## Project Overview
A standalone Java-based tool to test Oracle database connectivity with comprehensive diagnostics, configurable connection definitions, and human-readable output.

## Technical Stack
- **Language**: Java 17+ (LTS)
- **Build Tool**: Gradle with Wrapper
- **Build Output**: Shadow JAR (single executable JAR)
- **JDBC Driver**: Oracle ojdbc8-thin or ojdbc11-thin
- **Minimal Dependencies**:
  - Oracle JDBC Driver (required)
  - SnakeYAML (for YAML parsing)
  - JANSI (for cross-platform colored console output)
  - Gson or Jackson (for JSON output)
  - JUnit XML generation (custom implementation, no external dependency needed)
  - **Lombok** (for reducing boilerplate code - @Slf4j, @Data, @Builder, etc.)
  - **Log4j2** (SLF4J implementation for logging)
  - **Optional** password provider libraries:
    - HashiCorp Vault client (only if using Vault provider)
    - Custom provider JARs (user-supplied)

## Project Structure
```
oracle-jdbc-test/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/dbtest/
│   │   │       ├── Main.java
│   │   │       ├── config/
│   │   │       │   ├── ConfigLoader.java
│   │   │       │   ├── DatabaseConfig.java
│   │   │       │   └── ConnectionDefinition.java
│   │   │       ├── password/
│   │   │       │   ├── PasswordProvider.java (interface)
│   │   │       │   ├── PasswordProviderFactory.java
│   │   │       │   ├── DirectPasswordProvider.java
│   │   │       │   ├── EnvironmentVariablePasswordProvider.java
│   │   │       │   └── providers/
│   │   │       │       ├── VaultPasswordProvider.java (optional)
│   │   │       ├── connection/
│   │   │       │   ├── ConnectionTester.java
│   │   │       │   └── ConnectionResult.java
│   │   │       ├── diagnostics/
│   │   │       │   ├── DiagnosticEngine.java
│   │   │       │   ├── DnsChecker.java
│   │   │       │   ├── PortChecker.java
│   │   │       │   ├── TnsListenerChecker.java
│   │   │       │   └── OracleDiagnostics.java
│   │   │       ├── output/
│   │   │       │   ├── ConsoleReporter.java
│   │   │       │   ├── JsonReporter.java
│   │   │       │   ├── JunitXmlReporter.java
│   │   │       │   └── TestReport.java
│   │   │       └── util/
│   │   │           ├── EnvironmentVariableResolver.java
│   │   │           └── ColoredOutput.java
│   │   └── resources/
│   │   │       ├── log4j2.xml
│   │   │       └── banner.txt
│   └── test/
│       └── java/
│           └── com/dbtest/
│               └── (unit tests)
├── connections.yaml (example)
├── README.md
└── .gitignore
```

## YAML Configuration Format

```yaml
# connections.yaml
connections:
  - name: prod-db-primary
    host: prod-oracle-01.example.com
    port: 1521
    service: PRODDB  # Service Name (or use 'sid' for SID-based connections)
    username: app_user
    password: ${PROD_DB_PASSWORD}  # Environment variable reference
    tags:
      - production
      - primary
    groups:
      - prod-cluster
    test_query: "SELECT 1 FROM DUAL"  # Optional custom test query
    
  - name: dev-db
    host: dev-oracle.example.com
    port: 1521
    sid: DEVDB  # SID-based connection (alternative to service)
    username: dev_user
    password: ${DEV_DB_PASSWORD}
    tags:
      - development
    groups:
      - dev-env
    enabled: true  # Optional: disable without removing from config

  - name: test-db-local
    host: localhost
    port: 1521
    service: XEPDB1
    username: system
    password: DevPassword123  # Direct password (not recommended for prod)
    tags:
      - local
      - testing
    timeout: 5  # Optional: connection timeout in seconds (default: 10)
    
  - name: vault-protected-db
    host: vault-oracle.example.com
    port: 1521
    service: VAULTDB
    username: vault_user
    password_provider: vault  # Use pluggable password provider
    tags:
      - production
      - vault

# Password Provider Configuration (optional)
password_providers:
  # HashiCorp Vault provider
  vault:
    type: vault
    config:
      address: https://vault.example.com:8200
      token: ${VAULT_TOKEN}  # Or use other Vault auth methods
      secret_path: secret/data/oracle/credentials
      username_field: username
      password_field: password
  
  # Custom provider (user-implemented)
  custom:
    type: custom
    class: com.example.MyPasswordProvider
    config:
      api_endpoint: https://api.example.com/credentials
      api_key: ${CUSTOM_API_KEY}
```

## YAML Validation Rules

The tool performs **fail-fast validation** on startup before attempting any connections. Any validation error results in immediate exit with code 2.

### Required Fields Per Connection
- `name` - Must be unique (case-insensitive), non-empty
- `host` - Must be non-empty
- `port` - Must be integer between 1-65535
- `username` - Must be non-empty
- Either `service` OR `sid` - Exactly one must be specified (mutually exclusive)
- Password must be provided via ONE of:
  - `password` field (direct or `${ENV_VAR}` syntax)
  - `password_provider` field (references configured provider)

### Validation Rules

**Connection Names:**
- Must be unique across all connections (case-insensitive comparison)
- Allowed characters: alphanumeric, dash (-), underscore (_), dot (.)
- Cannot be empty or only whitespace
- Maximum length: 100 characters

**Service vs SID:**
- MUST specify exactly one of `service` OR `sid`
- Specifying both → **VALIDATION ERROR**
- Specifying neither → **VALIDATION ERROR**

**Password Resolution Priority:**
1. If `password_provider` specified → Use that provider
2. Else if `password` field present → Use it (resolve `${VAR}` if needed)
3. Else → **VALIDATION ERROR** (no password source)

Note: If BOTH `password` and `password_provider` are specified, `password_provider` takes precedence and `password` is ignored (warning logged).

**Port Validation:**
- Must be integer
- Range: 1 to 65535
- Default: 1521 if not specified

**Timeout Validation:**
- Must be positive integer (seconds)
- Range: 1 to 300 (5 minutes max)
- Default: 10 seconds if not specified

**Tags and Groups:**
- Optional fields
- If present, must be arrays of strings
- Empty arrays are valid
- Duplicate tags/groups within a connection are allowed but deduplicated

**Password Provider Validation:**
- If `password_provider` specified, must reference a provider in `password_providers` section
- Provider name must exist in config
- Provider config must have `type` field
- Provider config must have `config` section (can be empty)

**Enabled Flag:**
- Optional boolean field
- Default: `true` if not specified
- Disabled connections (`enabled: false`) are skipped but still validated

### Validation Error Examples

```yaml
# ERROR: Both service and sid specified
connections:
  - name: bad-db
    host: example.com
    port: 1521
    service: PRODDB
    sid: ORCL        # ERROR: Cannot specify both
    username: user
    password: pass

# ERROR: Neither service nor sid specified  
connections:
  - name: bad-db2
    host: example.com
    port: 1521
    # Missing service or sid
    username: user
    password: pass

# ERROR: Duplicate connection names
connections:
  - name: my-db
    host: host1.com
    service: DB1
  - name: MY-DB      # ERROR: Duplicate (case-insensitive)
    host: host2.com
    service: DB2

# ERROR: No password source
connections:
  - name: no-pass-db
    host: example.com
    service: TESTDB
    username: user
    # Missing both 'password' and 'password_provider'

# ERROR: Password provider not found
connections:
  - name: vault-db
    password_provider: vault  # ERROR: 'vault' not in password_providers section
    
# ERROR: Invalid port
connections:
  - name: bad-port
    port: 99999      # ERROR: Port must be 1-65535
```

### Validation Process

```
1. Parse YAML file
   ↓
2. Validate global structure (connections array exists, password_providers if used)
   ↓
3. For each connection:
   - Validate required fields present
   - Validate field types and ranges
   - Validate service XOR sid
   - Validate password source
   - Validate connection name uniqueness
   ↓
4. Validate password providers referenced actually exist
   ↓
5. Check for circular dependencies or invalid references
   ↓
6. If ANY validation fails → Print ALL errors and exit code 2
   If validation passes → Proceed with testing
```

**Fail-Fast Philosophy:**
- All validation errors are collected and reported together
- User sees all problems at once, not one at a time
- No partial execution - fix config, then run again
- Clear, actionable error messages

**Example Validation Output:**
```
Configuration Validation Failed:

ERROR [connection: prod-db-1]:
  - Both 'service' and 'sid' are specified. Use only one.
  
ERROR [connection: dev-db]:
  - No password source specified. Provide 'password' or 'password_provider'.
  
ERROR [connection: MY-DB]:
  - Duplicate connection name 'my-db' (case-insensitive).
  
ERROR [connection: vault-db]:
  - Password provider 'vault' not found in password_providers configuration.

Found 4 validation errors. Please fix configuration and try again.
Exit code: 2
```

## Configuration File Location

**Default Behavior:**
- If `--config` option is not specified, the tool looks for `connections.yaml` in the current working directory
- Location: `./connections.yaml`

**Explicit Path:**
- Use `--config` to specify a different location
- Supports absolute and relative paths
- Example: `--config=/etc/oracle-jdbc-test/prod-connections.yaml`

**File Not Found:**
- If the config file is not found, the tool exits with error code 2
- Clear error message: "Configuration file not found: ./connections.yaml"
- Suggestion to create the file or specify correct path with --config

**Example Directory Structure:**
```
project/
├── oracle-jdbc-test.jar
├── connections.yaml          # Default location
├── configs/
│   ├── prod-connections.yaml
│   ├── dev-connections.yaml
│   └── test-connections.yaml
└── logs/
```

**Usage:**
```bash
# Uses ./connections.yaml (default)
java -jar oracle-jdbc-test.jar

# Specify different config
java -jar oracle-jdbc-test.jar --config=configs/prod-connections.yaml
```

## Command Line Interface

### Basic Usage
```bash
# Test all connections (uses ./connections.yaml by default)
java -jar oracle-jdbc-test.jar

# Specify config file
java -jar oracle-jdbc-test.jar --config=/path/to/connections.yaml

# Filter by tag
java -jar oracle-jdbc-test.jar --tag=production
java -jar oracle-jdbc-test.jar --tag=production,primary  # Multiple tags (AND logic)

# Filter by connection name
java -jar oracle-jdbc-test.jar --only=prod-db-primary,dev-db

# Verbose output with detailed diagnostics
java -jar oracle-jdbc-test.jar --verbose

# Write JSON output to file
java -jar oracle-jdbc-test.jar --json-output=/path/to/results.json

# Write JUnit XML output to file (for CI/CD integration)
java -jar oracle-jdbc-test.jar --junit-xml=/path/to/test-results.xml

# Combine options
java -jar oracle-jdbc-test.jar --tag=production --verbose --junit-xml=results.xml
```

### Command Line Options
| Option | Short | Description | Example |
|--------|-------|-------------|---------|
| `--config` | `-c` | Path to YAML config file (default: `./connections.yaml`) | `--config=./connections.yaml` |
| `--tag` | `-t` | Filter by tag(s) - comma-separated | `--tag=prod,primary` |
| `--only` | `-o` | Test only specified connections | `--only=db1,db2` |
| `--verbose` | `-v` | Enable verbose/debug output | `--verbose` |
| `--json-output` | `-j` | Write JSON results to file | `--json-output=results.json` |
| `--junit-xml` | `-x` | Write JUnit XML results to file | `--junit-xml=test-results.xml` |
| `--help` | `-h` | Show help message | `--help` |
| `--version` | | Show version info | `--version` |

### Exit Codes
- **0**: All tests passed
- **1**: One or more tests failed
- **2**: Configuration error or runtime error

## Core Features

### 0. Password Provider Plugin System
A flexible, pluggable system for retrieving database passwords from various sources.

#### Password Provider Interface
```java
public interface PasswordProvider {
    /**
     * Retrieve password for a database connection
     * 
     * @param context Connection context with user, host, service/sid information
     * @return The password to use for the connection
     * @throws PasswordProviderException if password cannot be retrieved
     */
    String getPassword(PasswordContext context) throws PasswordProviderException;
    
    /**
     * Initialize the provider with configuration
     */
    void initialize(Map<String, Object> config) throws PasswordProviderException;
}

public class PasswordContext {
    private final String username;
    private final String host;
    private final Integer port;
    private final String service;  // null if using SID
    private final String sid;      // null if using service
    private final String connectionName;
    
    // Getters...
}
```

#### Built-in Providers

**1. Direct Password Provider**
- Password specified directly in YAML
- No transformation needed
```yaml
password: "mypassword123"
```

**2. Environment Variable Provider**
- Resolves `${VAR_NAME}` syntax
- Default provider for backward compatibility
```yaml
password: ${PROD_DB_PASSWORD}
```

**3. Vault Provider (Optional)**
- Retrieves passwords from HashiCorp Vault
- Requires vault client library (optional dependency)
```yaml
password_provider: vault
```
Implementation:
- Connects to Vault using configured token/auth
- Looks up secret at: `{secret_path}/{host}/{service_or_sid}`
- Returns password from specified field

**4. Custom Provider**
- User implements `PasswordProvider` interface
- Loaded via reflection from classpath
```yaml
password_provider: custom
```

#### Provider Resolution Logic
```
1. If password_provider specified:
   → Look up provider by name in password_providers config
   → Initialize provider with its config
   → Call provider.getPassword(context)
   
2. Else if password contains ${...}:
   → Use EnvironmentVariablePasswordProvider
   → Resolve from environment variables
   
3. Else:
   → Use DirectPasswordProvider
   → Return password as-is
```

#### Provider Factory
```java
public class PasswordProviderFactory {
    private static final Map<String, Class<? extends PasswordProvider>> BUILTIN_PROVIDERS = Map.of(
        "vault", VaultPasswordProvider.class
    );
    
    public static PasswordProvider createProvider(String providerName, 
                                                   Map<String, Object> providerConfig,
                                                   Map<String, Object> globalConfig) {
        // 1. Check built-in providers
        // 2. Check for custom class in config
        // 3. Load via reflection
        // 4. Initialize with config
        // 5. Return instance
    }
}
```

#### Security Considerations
- Passwords never logged or printed (even in verbose mode)
- Provider errors don't expose password values
- Vault tokens loaded from environment variables
- Custom providers run in same JVM (trust boundary)

#### Password Security - Critical Rules

**PASSWORDS MUST NEVER APPEAR IN:**
1. **Console output** - Any mode (normal, verbose, debug)
2. **Log files** - Any log level (ERROR, WARN, INFO, DEBUG, TRACE)
3. **Error messages** - Even in exceptions or diagnostics
4. **JSON output** - Password fields excluded entirely
5. **JUnit XML output** - Password fields excluded entirely
6. **Stack traces** - Scrubbed from exception messages
7. **JDBC URLs in logs** - Connection strings sanitized

**Implementation Requirements:**
- Passwords stored in memory only during connection attempt
- Cleared/nulled after use where possible
- Never passed to logging frameworks
- Exception messages sanitized before logging
- JDBC connection strings sanitized in error output
- Password fields marked as `@ToString.Exclude` in Lombok classes
- Custom toString() methods for classes containing passwords

**Example of Safe Error Handling:**
```java
// BAD - exposes password
log.error("Connection failed: " + jdbcUrl);  // jdbc:oracle:thin:user/PASSWORD@host...

// GOOD - sanitized
log.error("Connection failed: " + sanitizeJdbcUrl(jdbcUrl));  // jdbc:oracle:thin:user/***@host...
```

**Sanitization Helper:**
```java
private String sanitizeJdbcUrl(String jdbcUrl) {
    // Replace password in JDBC URL with ***
    return jdbcUrl.replaceAll("/(.*?)@", "/***@");
}

private String sanitizeException(Exception e) {
    // Remove passwords from exception messages
    String message = e.getMessage();
    if (message != null) {
        message = message.replaceAll("password[=:][^\\s,;]+", "password=***");
    }
    return message;
}
```

#### Example Usage Scenarios

**Scenario 1: Development (direct password)**
```yaml
connections:
  - name: local-dev
    username: dev_user
    password: dev123
```

**Scenario 2: CI/CD (environment variables)**
```yaml
connections:
  - name: prod-db
    username: prod_user
    password: ${PROD_DB_PASSWORD}
```

**Scenario 3: Production (Vault)**
```yaml
password_providers:
  vault:
    type: vault
    config:
      address: https://vault.company.com
      token: ${VAULT_TOKEN}
      secret_path: secret/data/oracle

connections:
  - name: prod-primary
    host: prod-oracle-01.example.com
    service: PRODDB
    username: app_user
    password_provider: vault  # Uses Vault provider
```

**Scenario 4: Custom Provider**
```yaml
password_providers:
  custom:
    type: custom
    class: com.company.security.OraclePasswordProvider
    config:
      api_url: https://secrets.company.com
      api_key: ${SECRETS_API_KEY}

connections:
  - name: enterprise-db
    password_provider: custom
```

### 1. Configuration Management
- **YAML Parsing**: Load and validate connection definitions
- **Fail-Fast Validation**: Validate entire configuration before any connection attempts
  - All validation errors collected and reported together
  - Exit code 2 on any validation failure
  - No partial execution - configuration must be 100% valid
- **Environment Variable Resolution**: Replace `${VAR_NAME}` with environment values
- **Password Provider Resolution**: Initialize and use pluggable password providers
- **Validation**: Ensure required fields are present, unique names, service XOR sid, valid ranges
- **Filtering**: Support tag and name-based filtering (applied after validation)

### 2. Connection Testing
Sequential testing with these steps for each connection:
1. Resolve environment variables in connection string
2. Retrieve password using appropriate provider (direct, env var, vault, custom, etc.)
3. Build JDBC URL (support both Service Name and SID)
4. Attempt database connection
5. Retrieve Oracle version information
6. Execute test query (default: `SELECT 1 FROM DUAL` or custom)
7. Record connection time and results

### 3. Comprehensive Diagnostics
When a connection fails, perform analysis in this order:

#### Step 1: DNS Resolution
- Resolve hostname to IP address
- Check if DNS lookup succeeds
- Report: DNS resolution time, resolved IP(s)

#### Step 2: Network Reachability
- Test if host:port is reachable (TCP socket connection)
- Timeout: 5 seconds
- Report: Port open/closed/filtered

#### Step 3: TNS Listener Check
- Connect to Oracle TNS Listener on port 1521 (or configured port)
- Send TNS CONNECT packet to verify listener is responding
- Parse listener response for service availability
- Report: Listener version, available services

#### Step 4: Oracle-Specific Diagnostics
- Analyze SQLException error codes:
  - **ORA-12154**: TNS could not resolve service name
  - **ORA-12514**: TNS listener does not know of service
  - **ORA-12541**: TNS no listener
  - **ORA-01017**: Invalid username/password
  - **ORA-28000**: Account locked
  - **ORA-01033**: Oracle initialization or shutdown in progress
  - Common network errors (ORA-12170, ORA-12535, etc.)
- Report: Specific error interpretation and recommended action

#### Step 5: Connection String Validation
- Verify JDBC URL format
- Check if service name vs SID might be the issue
- Suggest alternative connection formats

### 4. Output Formatting

#### Console Output (Color-Coded)
```
╔═══════════════════════════════════════════════════════════════════════╗
║           Oracle JDBC Connectivity Test Results                       ║
╚═══════════════════════════════════════════════════════════════════════╝

Testing 3 connections...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[✓] prod-db-primary (production, primary)
    Host: prod-oracle-01.example.com:1521
    Service: PRODDB
    Version: Oracle Database 19c Enterprise Edition Release 19.0.0.0.0
    Connection Time: 234ms
    Test Query: OK (2ms)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[✗] dev-db (development)
    Host: dev-oracle.example.com:1521
    SID: DEVDB
    Error: ORA-12514: TNS:listener does not currently know of service requested
    
    Diagnostics:
    ├─ DNS Resolution: ✓ (23ms) → 10.0.1.45
    ├─ Port Reachability: ✓ Port 1521 is open
    ├─ TNS Listener: ✓ Listener responding
    │  └─ Available Services: PRODDB, TESTDB
    └─ Analysis: The SID 'DEVDB' is not registered with the listener
       
    Recommendations:
    • Verify the SID name is correct (case-sensitive)
    • Check if you meant to use a Service Name instead of SID
    • Available services on this listener: PRODDB, TESTDB
    • Contact DBA to verify service registration

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[✓] test-db-local (local, testing)
    Host: localhost:1521
    Service: XEPDB1
    Version: Oracle Database 21c Express Edition Release 21.0.0.0.0
    Connection Time: 156ms
    Test Query: OK (1ms)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary:
  Total: 3 | Passed: 2 | Failed: 1
  
Exit Code: 1
```

**Color Scheme**:
- Green (✓): Successful connections
- Red (✗): Failed connections
- Yellow (⚠): Warnings or partial success
- Cyan: Informational headers
- White: Regular text

#### JSON Output Format
```json
{
  "timestamp": "2026-02-13T10:30:45Z",
  "summary": {
    "total": 3,
    "passed": 2,
    "failed": 1,
    "duration_ms": 2450
  },
  "results": [
    {
      "name": "prod-db-primary",
      "tags": ["production", "primary"],
      "groups": ["prod-cluster"],
      "status": "SUCCESS",
      "connection": {
        "host": "prod-oracle-01.example.com",
        "port": 1521,
        "service": "PRODDB",
        "username": "app_user"
      },
      "database_info": {
        "version": "Oracle Database 19c Enterprise Edition Release 19.0.0.0.0",
        "version_number": "19.0.0.0.0"
      },
      "metrics": {
        "connection_time_ms": 234,
        "test_query_time_ms": 2
      },
      "test_query_result": "SUCCESS"
    },
    {
      "name": "dev-db",
      "tags": ["development"],
      "groups": ["dev-env"],
      "status": "FAILED",
      "connection": {
        "host": "dev-oracle.example.com",
        "port": 1521,
        "sid": "DEVDB",
        "username": "dev_user"
      },
      "error": {
        "message": "ORA-12514: TNS:listener does not currently know of service requested",
        "code": "ORA-12514",
        "type": "SERVICE_NOT_FOUND"
      },
      "diagnostics": {
        "dns_resolution": {
          "status": "SUCCESS",
          "resolved_ip": "10.0.1.45",
          "resolution_time_ms": 23
        },
        "port_reachability": {
          "status": "SUCCESS",
          "port_open": true
        },
        "tns_listener": {
          "status": "SUCCESS",
          "listener_responding": true,
          "available_services": ["PRODDB", "TESTDB"]
        },
        "analysis": {
          "issue": "SID not registered with listener",
          "recommendations": [
            "Verify the SID name is correct (case-sensitive)",
            "Check if you meant to use a Service Name instead of SID",
            "Available services on this listener: PRODDB, TESTDB",
            "Contact DBA to verify service registration"
          ]
        }
      }
    },
    {
      "name": "test-db-local",
      "tags": ["local", "testing"],
      "groups": [],
      "status": "SUCCESS",
      "connection": {
        "host": "localhost",
        "port": 1521,
        "service": "XEPDB1",
        "username": "system"
      },
      "database_info": {
        "version": "Oracle Database 21c Express Edition Release 21.0.0.0.0",
        "version_number": "21.0.0.0.0"
      },
      "metrics": {
        "connection_time_ms": 156,
        "test_query_time_ms": 1
      },
      "test_query_result": "SUCCESS"
    }
  ]
}
```

#### JUnit XML Output Format
JUnit XML format allows CI/CD servers (Jenkins, GitLab CI, GitHub Actions, etc.) to parse and display test results with built-in reporters.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="Oracle JDBC Connectivity Tests" 
            tests="3" 
            failures="1" 
            errors="0" 
            skipped="0" 
            time="2.450">
  
  <testsuite name="production" 
             tests="1" 
             failures="0" 
             errors="0" 
             skipped="0" 
             time="0.234" 
             timestamp="2026-02-13T10:30:45Z">
    <testcase name="prod-db-primary" 
              classname="oracle.connectivity.production" 
              time="0.234">
      <system-out>
Host: prod-oracle-01.example.com:1521
Service: PRODDB
Version: Oracle Database 19c Enterprise Edition Release 19.0.0.0.0
Connection Time: 234ms
Test Query: OK (2ms)
Tags: production, primary
      </system-out>
    </testcase>
  </testsuite>
  
  <testsuite name="development" 
             tests="1" 
             failures="1" 
             errors="0" 
             skipped="0" 
             time="0.560" 
             timestamp="2026-02-13T10:30:45Z">
    <testcase name="dev-db" 
              classname="oracle.connectivity.development" 
              time="0.560">
      <failure message="ORA-12514: TNS:listener does not currently know of service requested" 
               type="SERVICE_NOT_FOUND">
ORA-12514: TNS:listener does not currently know of service requested

Diagnostics:
- DNS Resolution: SUCCESS (23ms) → 10.0.1.45
- Port Reachability: SUCCESS - Port 1521 is open
- TNS Listener: SUCCESS - Listener responding
  Available Services: PRODDB, TESTDB
- Analysis: The SID 'DEVDB' is not registered with the listener

Recommendations:
• Verify the SID name is correct (case-sensitive)
• Check if you meant to use a Service Name instead of SID
• Available services on this listener: PRODDB, TESTDB
• Contact DBA to verify service registration
      </failure>
      <system-out>
Host: dev-oracle.example.com:1521
SID: DEVDB
Tags: development
      </system-out>
    </testcase>
  </testsuite>
  
  <testsuite name="local" 
             tests="1" 
             failures="0" 
             errors="0" 
             skipped="0" 
             time="0.156" 
             timestamp="2026-02-13T10:30:46Z">
    <testcase name="test-db-local" 
              classname="oracle.connectivity.local" 
              time="0.156">
      <system-out>
Host: localhost:1521
Service: XEPDB1
Version: Oracle Database 21c Express Edition Release 21.0.0.0.0
Connection Time: 156ms
Test Query: OK (1ms)
Tags: local, testing
      </system-out>
    </testcase>
  </testsuite>
  
</testsuites>
```

**JUnit XML Structure:**
- **testsuites**: Root element with summary statistics
  - `tests`: Total number of test cases
  - `failures`: Number of failed tests
  - `errors`: Number of tests with errors (configuration issues)
  - `time`: Total execution time in seconds
  
- **testsuite**: Group by primary tag (or "untagged" if no tags)
  - Each connection test is a `testcase`
  - `classname`: Uses format `oracle.connectivity.<primary_tag>`
  - `name`: Connection name from YAML
  
- **testcase**: Individual connection test
  - `time`: Connection time in seconds
  - `<failure>`: Present if connection failed (includes diagnostics)
  - `<error>`: Present if configuration error occurred
  - `<system-out>`: Connection details and success info

**Jenkins Integration:**
```groovy
// Jenkinsfile
stage('Database Connectivity Test') {
    steps {
        sh '''
            export PROD_DB_PASSWORD=${VAULT_PROD_PASSWORD}
            java -jar oracle-jdbc-test.jar \
                --config=connections.yaml \
                --junit-xml=test-results.xml
        '''
    }
    post {
        always {
            junit 'test-results.xml'
        }
    }
}
```

**GitLab CI Integration:**
```yaml
# .gitlab-ci.yml
db-connectivity-test:
  stage: test
  script:
    - export PROD_DB_PASSWORD=$VAULT_PROD_PASSWORD
    - java -jar oracle-jdbc-test.jar --junit-xml=test-results.xml
  artifacts:
    reports:
      junit: test-results.xml
    when: always
```

**GitHub Actions Integration:**
```yaml
# .github/workflows/db-test.yml
- name: Test Database Connectivity
  run: |
    export PROD_DB_PASSWORD=${{ secrets.PROD_DB_PASSWORD }}
    java -jar oracle-jdbc-test.jar --junit-xml=test-results.xml
    
- name: Publish Test Results
  uses: dorny/test-reporter@v1
  if: always()
  with:
    name: Database Connectivity Tests
    path: test-results.xml
    reporter: java-junit
```

## Implementation Details

### Architecture Decision: JUnit XML Output vs JUnit Framework

**Decision:** Generate JUnit XML format output WITHOUT using JUnit as the test framework.

**Rationale:**
- **Minimal dependencies**: Avoid JUnit platform/engine dependencies (~2-3 MB)
- **Full control**: Custom test execution flow, filtering, and output formatting
- **Better CLI experience**: Direct command-line control without JUnit Console Launcher complexity
- **Ops-focused tool**: Tests defined at runtime in YAML, not compile-time Java code
- **Simpler architecture**: Standalone tool with pluggable reporters (Console, JSON, JUnit XML)

JUnit XML is just an output format specification - we can generate compliant XML without the framework overhead. This keeps the tool lightweight and purpose-built for connectivity testing.

### 1. Main Application Flow
```java
@Slf4j  // Lombok annotation for logging
public class Main {
    private static final String DEFAULT_CONFIG_FILE = "connections.yaml";
    
    public static void main(String[] args) {
        // 1. Parse command line arguments
        CommandLineArgs cliArgs = parseArgs(args);
        
        // 2. Determine config file location (default: ./connections.yaml)
        String configPath = cliArgs.getConfig() != null 
            ? cliArgs.getConfig() 
            : DEFAULT_CONFIG_FILE;
        
        // 3. Load YAML configuration
        DatabaseConfig config = loadConfig(configPath);
        
        // 4. VALIDATE configuration (fail-fast - collect ALL errors)
        List<ValidationError> errors = validateConfig(config);
        if (!errors.isEmpty()) {
            printValidationErrors(errors);
            System.exit(2);  // Configuration error
        }
        
        // 5. Initialize password providers from config
        Map<String, PasswordProvider> providers = initializeProviders(config);
        
        // 6. Apply filters (tags, names) to validated connections
        List<ConnectionDefinition> filteredConnections = applyFilters(
            config.getConnections(), 
            cliArgs.getTags(), 
            cliArgs.getOnlyNames()
        );
        
        // 7. Initialize reporters (console, JSON, JUnit XML)
        List<Reporter> reporters = initializeReporters(cliArgs);
        
        // 8. Test each connection sequentially
        List<TestResult> results = testConnections(filteredConnections, providers);
        
        // 9. Generate output (console + JSON/JUnit XML if specified)
        for (Reporter reporter : reporters) {
            reporter.report(results);
        }
        
        // 10. Exit with appropriate code (0=success, 1=failures, 2=config error)
        int exitCode = results.stream().anyMatch(r -> !r.isSuccess()) ? 1 : 0;
        System.exit(exitCode);
    }
}
```

### 2. Password Provider Implementation Example

```java
// Interface all providers must implement
public interface PasswordProvider {
    String getPassword(PasswordContext context) throws PasswordProviderException;
    void initialize(Map<String, Object> config) throws PasswordProviderException;
}

// Context object passed to providers - using Lombok
@Data
@Builder
@ToString(exclude = {"password"})  // NEVER include password in toString()
public class PasswordContext {
    private final String username;
    private final String host;
    private final Integer port;
    private final String service;
    private final String sid;
    private final String connectionName;
    
    public static PasswordContext fromConnection(ConnectionDefinition conn) {
        return PasswordContext.builder()
            .username(conn.getUsername())
            .host(conn.getHost())
            .port(conn.getPort())
            .service(conn.getService())
            .sid(conn.getSid())
            .connectionName(conn.getName())
            .build();
    }
    
    public String getServiceOrSid() {
        return service != null ? service : sid;
    }
}

// Connection definition with Lombok
@Data
@Builder
@ToString(exclude = {"password", "passwordProvider"})  // NEVER log passwords
public class ConnectionDefinition {
    private String name;
    private String host;
    private Integer port;
    private String service;
    private String sid;
    private String username;
    
    @ToString.Exclude  // Critical: exclude from toString
    private String password;
    
    @ToString.Exclude  // Don't log provider reference either
    private String passwordProvider;
    
    private List<String> tags;
    private List<String> groups;
    private String testQuery;
    private Integer timeout;
    private Boolean enabled;
}

// Example: Vault provider implementation with Lombok
@Slf4j  // Lombok logging
public class VaultPasswordProvider implements PasswordProvider {
    private String vaultAddress;
    private String vaultToken;
    private String secretPath;
    private String passwordField = "password";
    
    @Override
    public void initialize(Map<String, Object> config) {
        this.vaultAddress = (String) config.get("address");
        this.vaultToken = resolveEnvVar((String) config.get("token"));
        this.secretPath = (String) config.get("secret_path");
        this.passwordField = (String) config.getOrDefault("password_field", "password");
        
        if (vaultAddress == null || vaultToken == null) {
            throw new PasswordProviderException("Vault address and token required");
        }
        
        log.info("Initialized Vault provider: {}", vaultAddress);
        // NEVER log token!
    }
    
    @Override
    public String getPassword(PasswordContext context) throws PasswordProviderException {
        try {
            String fullPath = String.format("%s/%s/%s", 
                secretPath, 
                context.getHost(), 
                context.getServiceOrSid());
            
            log.debug("Retrieving password from Vault: {}", fullPath);
            // NEVER log the actual password!
            
            VaultConfig vaultConfig = new VaultConfig()
                .address(vaultAddress)
                .token(vaultToken)
                .build();
            
            Vault vault = new Vault(vaultConfig);
            LogicalResponse response = vault.logical().read(fullPath);
            
            Map<String, String> data = response.getData();
            String password = data.get(passwordField);
            
            if (password == null) {
                throw new PasswordProviderException(
                    "Password field '" + passwordField + "' not found in Vault secret");
            }
            
            log.debug("Password retrieved successfully from Vault");
            // NEVER log the password value!
            
            return password;
            
        } catch (VaultException e) {
            log.error("Failed to retrieve password from Vault: {}", e.getMessage());
            throw new PasswordProviderException(
                "Failed to retrieve password from Vault: " + e.getMessage(), e);
        }
    }
    
    private String resolveEnvVar(String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            return System.getenv(varName);
        }
        return value;
    }
}

// Factory for creating providers - with Lombok
@Slf4j
public class PasswordProviderFactory {
    private static final Map<String, Class<? extends PasswordProvider>> PROVIDERS = new HashMap<>();
    
    static {
        PROVIDERS.put("vault", VaultPasswordProvider.class);
    }
    
    public static PasswordProvider create(String providerName, 
                                          Map<String, Object> providerConfigs) {
        try {
            Map<String, Object> config = (Map<String, Object>) providerConfigs.get(providerName);
            if (config == null) {
                throw new PasswordProviderException(
                    "No configuration found for provider: " + providerName);
            }
            
            String type = (String) config.get("type");
            
            // Handle custom providers
            if ("custom".equals(type)) {
                String className = (String) config.get("class");
                log.info("Loading custom password provider: {}", className);
                Class<?> clazz = Class.forName(className);
                PasswordProvider provider = (PasswordProvider) clazz.getDeclaredConstructor().newInstance();
                Map<String, Object> providerConfig = (Map<String, Object>) config.get("config");
                provider.initialize(providerConfig);
                return provider;
            }
            
            // Handle built-in providers
            Class<? extends PasswordProvider> providerClass = PROVIDERS.get(type);
            if (providerClass == null) {
                throw new PasswordProviderException("Unknown provider type: " + type);
            }
            
            log.info("Creating password provider: {}", type);
            PasswordProvider provider = providerClass.getDeclaredConstructor().newInstance();
            Map<String, Object> providerConfig = (Map<String, Object>) config.get("config");
            provider.initialize(providerConfig);
            return provider;
            
        } catch (Exception e) {
            log.error("Failed to create password provider: {}", providerName, e);
            throw new PasswordProviderException(
                "Failed to create password provider: " + providerName, e);
        }
    }
}

// Usage in ConnectionTester - with Lombok and password sanitization
@Slf4j
public class ConnectionTester {
    private final Map<String, PasswordProvider> passwordProviders;
    
    public TestResult test(ConnectionDefinition conn) {
        log.info("Testing connection: {}", conn.getName());
        
        try {
            // Get password using appropriate provider
            String password = resolvePassword(conn);
            // NEVER log the password!
            
            // Build JDBC URL and connect
            String jdbcUrl = buildJdbcUrl(conn);
            log.debug("Connecting to: {}", sanitizeJdbcUrl(jdbcUrl));  // Sanitized!
            
            Connection connection = DriverManager.getConnection(
                jdbcUrl, conn.getUsername(), password);
            
            log.info("Connection successful: {}", conn.getName());
            
            // ... rest of connection testing
            
        } catch (PasswordProviderException e) {
            log.error("Password retrieval failed for {}: {}", conn.getName(), e.getMessage());
            return TestResult.failure(conn, "Password retrieval failed: " + e.getMessage());
        } catch (SQLException e) {
            log.error("Connection failed for {}: {}", conn.getName(), sanitizeException(e));
            // Run diagnostics...
        }
    }
    
    private String resolvePassword(ConnectionDefinition conn) {
        // If password_provider specified, use it
        if (conn.getPasswordProvider() != null) {
            log.debug("Using password provider: {}", conn.getPasswordProvider());
            PasswordProvider provider = passwordProviders.get(conn.getPasswordProvider());
            if (provider == null) {
                throw new PasswordProviderException(
                    "Password provider not found: " + conn.getPasswordProvider());
            }
            PasswordContext context = PasswordContext.fromConnection(conn);
            return provider.getPassword(context);
        }
        
        // If password contains ${...}, resolve from environment
        String password = conn.getPassword();
        if (password != null && password.startsWith("${") && password.endsWith("}")) {
            String varName = password.substring(2, password.length() - 1);
            String resolved = System.getenv(varName);
            if (resolved == null) {
                throw new PasswordProviderException(
                    "Environment variable not set: " + varName);
            }
            log.debug("Resolved password from environment variable: {}", varName);
            return resolved;
        }
        
        // Return password as-is (direct password)
        log.debug("Using direct password from configuration");
        return password;
    }
    
    private String sanitizeJdbcUrl(String jdbcUrl) {
        // jdbc:oracle:thin:user/password@host:port/service
        return jdbcUrl.replaceAll("/(.*?)@", "/***@");
    }
    
    private String sanitizeException(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "No error message";
        // Remove passwords from error messages
        return msg.replaceAll("password[=:][^\\s,;)]+", "password=***")
                  .replaceAll("/[^@]+@", "/***@");
    }
}
```

### 3. JDBC Connection Building
```java
// For Service Name:
jdbc:oracle:thin:@//<host>:<port>/<service_name>

// For SID:
jdbc:oracle:thin:@<host>:<port>:<sid>

// Connection properties:
- oracle.net.CONNECT_TIMEOUT: 10000ms (configurable)
- oracle.jdbc.ReadTimeout: 30000ms
```

### 4. Database Version Retrieval
```sql
SELECT banner FROM v$version WHERE banner LIKE 'Oracle%'
-- Or for more detail:
SELECT * FROM v$version
```

### 5. Test Query Execution
- Default: `SELECT 1 FROM DUAL`
- Custom: User-defined in YAML
- Validates: Read permissions, basic query execution

### 6. TNS Listener Check Implementation
- Open raw TCP socket to listener port
- Send TNS CONNECT packet (basic format)
- Parse response for service list
- Handle TNS protocol versions

### 7. Diagnostic Analysis Logic
```
IF password retrieval fails:
  → Report password provider error
  → Check environment variables if using env var provider
  → Do NOT proceed to database connection (no password available)
  → STOP with configuration error

IF connection fails:
  1. Check DNS (java.net.InetAddress.getByName)
     - SUCCESS: Continue to port check
     - FAILURE: Report DNS error, STOP
     
  2. Check Port (java.net.Socket with timeout)
     - SUCCESS: Continue to TNS check
     - FAILURE: Report port unreachable, STOP
     
  3. Check TNS Listener (custom TNS protocol)
     - SUCCESS: Get service list, continue
     - FAILURE: Report listener not responding
     
  4. Analyze SQLException
     - Map error code to known issues
     - Provide specific recommendations
     
  5. Suggest alternatives
     - Try SID if Service Name failed (or vice versa)
     - Check for typos in service/SID name
```

### 8. JUnit XML Generation
The JUnit XML reporter generates standards-compliant XML output:

**Implementation approach:**
- No external library needed - generate XML directly using StringBuilder or DOM
- Follow JUnit XML schema (commonly used by Jenkins, GitLab, GitHub Actions)
- Group test cases by primary tag into test suites
- Include comprehensive diagnostics in failure messages
- Proper XML escaping for special characters in error messages

**XML Structure Mapping:**
```
YAML Connection → JUnit Test Case
Tags (first tag) → Test Suite name
Connection name → Test Case name
Success → Test case with <system-out>
Failure → Test case with <failure> containing diagnostics
Config Error → Test case with <error>
```

**Key attributes to populate:**
- `time`: Test execution time in seconds (decimal)
- `timestamp`: ISO 8601 format (e.g., 2026-02-13T10:30:45Z)
- `message`: Brief error summary for failures
- `type`: Error classification (e.g., SERVICE_NOT_FOUND, CONNECTION_REFUSED)

**Special considerations:**
- Escape XML special characters: &, <, >, ", '
- Include full diagnostic output in failure message for debugging
- Handle connections without tags (use "untagged" suite)
- Ensure valid UTF-8 encoding for all text content

## Build Configuration (build.gradle)

```gradle
plugins {
    id 'java'
    id 'application'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
    id 'io.freefair.lombok' version '8.4'  // Lombok plugin
}

group = 'org.dbtest'
version = '1.0.0'
sourceCompatibility = '17'
targetCompatibility = '17'

repositories {
    mavenCentral()
}

dependencies {
    // Oracle JDBC Driver
    implementation 'com.oracle.database.jdbc:ojdbc11:23.3.0.23.09'
    
    // YAML parsing
    implementation 'org.yaml:snakeyaml:2.2'
    
    // JSON output
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Colored console output
    implementation 'org.fusesource.jansi:jansi:2.4.1'
    
    // Lombok (annotations for reducing boilerplate)
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
    testCompileOnly 'org.projectlombok:lombok:1.18.30'
    testAnnotationProcessor 'org.projectlombok:lombok:1.18.30'
    
    // Logging - SLF4J API + Log4j2 implementation
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'org.apache.logging.log4j:log4j-api:2.21.1'
    implementation 'org.apache.logging.log4j:log4j-core:2.21.1'
    implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.21.1'  // SLF4J to Log4j2 bridge
    
    // Optional: HashiCorp Vault password provider
    // Uncomment if using Vault provider
    // implementation 'com.bettercloud:vault-java-driver:5.1.0'
        
    // Testing
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
}

application {
    mainClass = 'org.dbtest.Main'
}

shadowJar {
    archiveBaseName.set('oracle-jdbc-test')
    archiveClassifier.set('')
    archiveVersion.set(project.version)
    manifest {
        attributes 'Main-Class': 'org.dbtest.Main'
    }
    // Merge Log4j2 plugin cache files
    mergeServiceFiles()
    append('META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat')
}

test {
    useJUnitPlatform()
}
```

## Log4j2 Configuration

**File: `src/main/resources/log4j2.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <!-- Console appender for normal output -->
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>
        
        <!-- File appender for verbose/debug mode -->
        <RollingFile name="FileLogger" fileName="logs/oracle-jdbc-test.log"
                     filePattern="logs/oracle-jdbc-test-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout>
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n</Pattern>
            </PatternLayout>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                <SizeBasedTriggeringPolicy size="10 MB"/>
            </Policies>
            <DefaultRolloverStrategy max="5"/>
        </RollingFile>
    </Appenders>
    
    <Loggers>
        <!-- Root logger - INFO level by default -->
        <Root level="info">
            <AppenderRef ref="Console"/>
        </Root>
        
        <!-- Application logger -->
        <Logger name="org.dbtest" level="info" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="FileLogger"/>
        </Logger>
        
        <!-- Verbose mode: set to DEBUG via system property -->
        <!-- java -Dlog4j2.level=debug -jar oracle-jdbc-test.jar -->
        
        <!-- Silence verbose third-party libraries -->
        <Logger name="org.yaml.snakeyaml" level="warn"/>
        <Logger name="com.google.gson" level="warn"/>
        <Logger name="oracle.jdbc" level="warn"/>
    </Loggers>
</Configuration>
```

**Usage in Code with Lombok:**

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j  // Lombok generates: private static final Logger log = LoggerFactory.getLogger(...)
public class ConnectionTester {
    
    public TestResult test(ConnectionDefinition conn) {
        log.info("Testing connection: {}", conn.getName());
        
        try {
            String password = resolvePassword(conn);
            // NEVER log the password!
            
            String jdbcUrl = buildJdbcUrl(conn);
            log.debug("Connecting to: {}", sanitizeJdbcUrl(jdbcUrl));  // Sanitized!
            
            Connection connection = DriverManager.getConnection(jdbcUrl, conn.getUsername(), password);
            log.info("Connection successful: {}", conn.getName());
            
        } catch (SQLException e) {
            log.error("Connection failed for {}: {}", conn.getName(), sanitizeException(e));
        }
    }
    
    private String sanitizeJdbcUrl(String jdbcUrl) {
        // Remove password from JDBC URL
        return jdbcUrl.replaceAll("/(.*?)@", "/***@");
    }
}
```

**Verbose Mode:**
```bash
# Enable DEBUG logging
java -Dlog4j2.level=debug -jar oracle-jdbc-test.jar --verbose

# Or via command line option (tool sets system property)
java -jar oracle-jdbc-test.jar --verbose
```

**Log File Location:**
- Default: `./logs/oracle-jdbc-test.log`
- Rolls daily or at 10 MB
- Keeps last 5 log files
- Only written in verbose mode or when errors occur

## Error Handling Strategy

### Configuration Errors (Fail-Fast)
- **Config file not found** → Report file path, suggest creating file or using --config, exit code 2
- **YAML syntax errors** → Report error with line number, exit code 2
- **Missing required fields** → Collect ALL errors, report together, exit code 2
- **Invalid field values** → Collect ALL errors, report together, exit code 2
- **Duplicate connection names** → Report all duplicates, exit code 2
- **Service/SID validation** → Report conflicts, exit code 2
- **Password provider not found** → Report missing providers, exit code 2
- **Unresolved environment variables** → Report which variables missing, exit code 2

**Fail-Fast Philosophy:** All configuration errors are collected during validation phase and reported together. No partial execution. User must fix all errors before tool proceeds.

**Config File Not Found Example:**
```
ERROR: Configuration file not found: ./connections.yaml

Please ensure the file exists in the current directory, or specify the path:
  java -jar oracle-jdbc-test.jar --config=/path/to/connections.yaml

Exit code: 2
```

### Runtime Errors
- All exceptions caught and logged (with sanitized messages)
- Each connection test is isolated (one failure doesn't stop others)
- Detailed error messages with context (but NO passwords)
- Password provider failures → Skip connection, report error
- Network errors → Run diagnostics, report findings

### Database Connection Errors
- Catch SQLException and analyze error code
- Provide human-readable interpretation
- Run comprehensive diagnostics to pinpoint issue
- **Always sanitize error messages** - remove passwords, credentials

### Password Security in Error Handling
```java
@Slf4j
public class SafeErrorHandler {
    
    public void handleConnectionError(ConnectionDefinition conn, Exception e) {
        // NEVER include password in error message
        String safeMessage = sanitizeException(e);
        log.error("Connection failed for {}: {}", conn.getName(), safeMessage);
        
        // NEVER log full JDBC URL (contains password)
        String jdbcUrl = buildJdbcUrl(conn);
        log.debug("Failed URL: {}", sanitizeJdbcUrl(jdbcUrl));  // password masked
    }
    
    private String sanitizeException(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "No error message";
        
        // Remove passwords from various formats
        msg = msg.replaceAll("password[=:][^\\s,;)]+", "password=***");
        msg = msg.replaceAll("/[^@]+@", "/***@");  // JDBC URL passwords
        msg = msg.replaceAll("PWD=[^;]+", "PWD=***");  // Connection string passwords
        
        return msg;
    }
    
    private String sanitizeJdbcUrl(String url) {
        // jdbc:oracle:thin:user/password@host:port/service
        return url.replaceAll("/(.*?)@", "/***@");
    }
}
```

## Testing Strategy

### Unit Tests
- ConfigLoader: YAML parsing and validation
- EnvironmentVariableResolver: Variable substitution
- DiagnosticEngine: Mock network failures
- ConnectionTester: Mock JDBC connections

### Integration Tests
- Test against real Oracle XE container
- Verify all diagnostic paths
- Validate JSON output schema

## Usage Examples

### Example 1: Test all production databases
```bash
export PROD_DB_PASSWORD=secret123
java -jar oracle-jdbc-test.jar --tag=production
```

### Example 2: Test specific database with verbose output
```bash
java -jar oracle-jdbc-test.jar --only=prod-db-primary --verbose
```

### Example 3: Generate JSON report
```bash
java -jar oracle-jdbc-test.jar --json-output=weekly-report.json
```

### Example 4: Generate JUnit XML for CI/CD
```bash
java -jar oracle-jdbc-test.jar --junit-xml=test-results.xml
```

### Example 5: CI/CD Integration with JUnit XML
```bash
#!/bin/bash
# Test database connectivity before deployment

export PROD_DB_PASSWORD=${VAULT_PROD_PASSWORD}
export DEV_DB_PASSWORD=${VAULT_DEV_PASSWORD}

java -jar oracle-jdbc-test.jar \
  --config=./deployment/db-connections.yaml \
  --junit-xml=results.xml \
  --json-output=results.json

if [ $? -eq 0 ]; then
  echo "All database connections verified"
  exit 0
else
  echo "Database connectivity issues detected"
  cat results.json
  exit 1
fi
```

## Future Enhancements (Out of Scope for v1.0)
- Connection pooling tests
- Performance benchmarking
- Oracle RAC multi-node support
- Scheduled/automated testing
- Alerting integrations
- TLS/SSL connection testing
- Kerberos authentication
- Oracle Wallet support
- Multiple YAML file support
- Configuration inheritance/profiles

## Deliverables
1. Fully functional Java application
2. Gradle build configuration with Shadow JAR plugin
3. Example connections.yaml file
4. README.md with usage instructions
5. Gradle wrapper for easy building
6. Unit and integration tests
7. .gitignore for Java/Gradle projects

## Success Criteria
- ✓ Single executable JAR that runs on any Java 17+ JVM
- ✓ Comprehensive diagnostics for connection failures
- ✓ Color-coded, human-readable console output
- ✓ JSON output option for automation
- ✓ JUnit XML output option for CI/CD integration (Jenkins, GitLab, GitHub Actions)
- ✓ Environment variable support for passwords
- ✓ Tag and name-based filtering
- ✓ Oracle version detection on successful connections
- ✓ Sequential testing with clear progress indication
- ✓ Minimal dependencies (under 10 libraries)
- ✓ Exit codes for CI/CD integration
