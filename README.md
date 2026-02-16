# Oracle JDBC Connectivity Test Tool

A standalone Java-based tool to test Oracle database connectivity with comprehensive diagnostics, configurable connection definitions, and human-readable output.

## Features

- Test multiple Oracle database connections from a single YAML configuration
- Comprehensive diagnostics on connection failures:
  - DNS resolution check
  - Port reachability check
  - Oracle error code analysis with recommendations
- Color-coded console output
- JSON output for automation
- JUnit XML output for CI/CD integration
- Support for both Service Name and SID connections
- Environment variable substitution for passwords
- Tag and name-based filtering

## Building

```bash
# Build the shadow JAR (single executable)
./gradlew shadowJar

# The JAR will be in app/build/libs/oracle-jdbc-test-1.0.0.jar
```

## Usage

```bash
# Test all connections (uses ./connections.yaml by default)
java -jar oracle-jdbc-test-1.0.0.jar

# Specify config file
java -jar oracle-jdbc-test-1.0.0.jar --config=/path/to/connections.yaml

# Filter by tag
java -jar oracle-jdbc-test-1.0.0.jar --tag=production
java -jar oracle-jdbc-test-1.0.0.jar --tag=production,primary  # AND logic

# Filter by connection name
java -jar oracle-jdbc-test-1.0.0.jar --only=prod-db-primary,dev-db

# Verbose output
java -jar oracle-jdbc-test-1.0.0.jar --verbose

# Write JSON output to file
java -jar oracle-jdbc-test-1.0.0.jar --json-output=results.json

# Write JUnit XML output for CI/CD
java -jar oracle-jdbc-test-1.0.0.jar --junit-xml=test-results.xml

# Combine options
java -jar oracle-jdbc-test-1.0.0.jar --tag=production --verbose --junit-xml=results.xml
```

## Command Line Options

| Option | Short | Description |
|--------|-------|-------------|
| `--config` | `-c` | Path to YAML config file (default: `./connections.yaml`) |
| `--tag` | `-t` | Filter by tag(s) - comma-separated |
| `--only` | `-o` | Test only specified connections |
| `--verbose` | `-v` | Enable verbose/debug output |
| `--ssh-verbose` | | Enable verbose SSH debug output |
| `--json-output` | `-j` | Write JSON results to file |
| `--junit-xml` | `-x` | Write JUnit XML results to file |
| `--help` | `-h` | Show help message |
| `--version` | | Show version info |

## Exit Codes

- **0**: All tests passed
- **1**: One or more tests failed
- **2**: Configuration error or runtime error

## Configuration

Create a `connections.yaml` file:

```yaml
connections:
  - name: prod-db-primary
    host: prod-oracle-01.example.com
    port: 1521
    service: PRODDB  # Use 'service' or 'sid', not both
    username: app_user
    password: ${PROD_DB_PASSWORD}  # Environment variable
    tags:
      - production
      - primary
    groups:
      - prod-cluster
    timeout: 10  # Connection timeout in seconds
    enabled: true  # Set to false to skip

  - name: dev-db
    host: dev-oracle.example.com
    port: 1521
    sid: DEVDB  # SID-based connection
    username: dev_user
    password: DevPassword123  # Direct password (not recommended for prod)
    tags:
      - development
```

### Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique connection name |
| `host` | Yes | Database hostname or IP |
| `port` | No | Port number (default: 1521) |
| `service` | * | Oracle Service Name |
| `sid` | * | Oracle SID (* exactly one of service/sid required) |
| `username` | Yes | Database username |
| `password` | Yes | Password or `${ENV_VAR}` reference |
| `tags` | No | List of tags for filtering |
| `groups` | No | List of groups |
| `timeout` | No | Connection timeout in seconds (default: 10) |
| `enabled` | No | Set to false to skip (default: true) |
| `test_query` | No | Custom test query (default: SELECT 1 FROM DUAL) |
| `ssh_tunnel` | No | Reference to SSH tunnel configuration name |
| `socks_proxy` | No | Reference to SOCKS proxy configuration name |

## SSH Tunnels and SOCKS Proxies

For databases behind firewalls or bastion hosts, the tool supports SSH tunnels and SOCKS proxies.

### SSH Tunnel Configuration

Define SSH tunnels in a separate `ssh_tunnels` section and reference them from connections:

```yaml
ssh_tunnels:
  prod-bastion:
    host: bastion.example.com
    port: 22
    username: admin
    private_key: ~/.ssh/id_rsa
    private_key_passphrase: ${SSH_PASSPHRASE}  # optional
    # known_hosts: ~/.ssh/known_hosts  # optional

connections:
  - name: prod-db-via-tunnel
    host: internal-db.example.com  # target host (from bastion's perspective)
    port: 1521
    service: PRODDB
    username: app_user
    password: ${PROD_PASSWORD}
    ssh_tunnel: prod-bastion  # reference by name
```

### Multi-hop SSH Tunnels (Jump Hosts)

For databases accessible only through multiple jump servers:

```yaml
ssh_tunnels:
  # First hop - public bastion
  jump-server-a:
    host: jump-a.example.com
    port: 22
    username: admin
    private_key: ~/.ssh/id_rsa

  # Second hop - internal server (reached via first hop)
  internal-via-jump:
    host: server-b.internal      # address as seen from jump-server-a
    port: 22
    username: dbadmin
    private_key: ~/.ssh/id_ed25519
    jump_host: jump-server-a     # reference the first hop

connections:
  - name: secure-db
    host: db.secure.internal     # address as seen from server-b
    port: 1521
    service: SECUREDB
    username: secure_user
    password: ${SECURE_PASSWORD}
    ssh_tunnel: internal-via-jump
```

### SOCKS Proxy Configuration

For dynamic port forwarding (SSH SOCKS proxy):

```yaml
socks_proxies:
  dev-socks:
    host: dev-bastion.example.com
    port: 22
    username: ${USER}
    private_key: ~/.ssh/id_ed25519
    local_port: 1080  # local SOCKS port

connections:
  - name: dev-db-via-socks
    host: internal-dev.example.com
    port: 1521
    service: DEVDB
    username: dev_user
    password: ${DEV_PASSWORD}
    socks_proxy: dev-socks
```

### SSH Authentication Options

| Field | Description |
|-------|-------------|
| `host` | SSH server hostname |
| `port` | SSH port (default: 22) |
| `username` | SSH username |
| `private_key` | Path to private key file (supports ~ expansion) |
| `private_key_passphrase` | Passphrase for encrypted key |
| `password` | SSH password (alternative to key) |
| `known_hosts` | Path to known_hosts file |
| `jump_host` | Reference to another tunnel for multi-hop |
| `timeout` | Connection timeout in seconds (default: 30) |

### Troubleshooting SSH Connections

Use `--ssh-verbose` for detailed SSH debug output:

```bash
java -jar oracle-jdbc-test-1.0.0.jar --ssh-verbose
```

When SSH connections fail, the tool provides layered diagnostics:

```
[✗] prod-db-via-tunnel
    SSH Chain: bastion.example.com → internal-db.example.com:1521
    
    Hop 1 (prod-bastion): ✓ Connected (45ms)
    Error: SSH authentication failed for admin@bastion.example.com
    
    Recommendations:
    • Verify the private key is authorized on the server
    • Check key file permissions are 600: chmod 600 ~/.ssh/id_rsa
    • Confirm username 'admin' is correct
```

## Password Management

### Environment Variables

Use `${VAR_NAME}` syntax to reference environment variables:

```yaml
password: ${PROD_DB_PASSWORD}
```

Set the environment variable before running:

```bash
export PROD_DB_PASSWORD=secret123
java -jar oracle-jdbc-test-1.0.0.jar
```

### Direct Passwords

For development/testing only:

```yaml
password: MyDevPassword123
```

**Warning**: Never commit direct passwords to version control.

## CI/CD Integration

### Jenkins

```groovy
stage('Database Connectivity Test') {
    steps {
        sh '''
            export PROD_DB_PASSWORD=${VAULT_PROD_PASSWORD}
            java -jar oracle-jdbc-test-1.0.0.jar \
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

### GitLab CI

```yaml
db-connectivity-test:
  stage: test
  script:
    - export PROD_DB_PASSWORD=$VAULT_PROD_PASSWORD
    - java -jar oracle-jdbc-test-1.0.0.jar --junit-xml=test-results.xml
  artifacts:
    reports:
      junit: test-results.xml
    when: always
```

### GitHub Actions

```yaml
- name: Test Database Connectivity
  env:
    PROD_DB_PASSWORD: ${{ secrets.PROD_DB_PASSWORD }}
  run: java -jar oracle-jdbc-test-1.0.0.jar --junit-xml=test-results.xml
    
- name: Publish Test Results
  uses: dorny/test-reporter@v1
  if: always()
  with:
    name: Database Connectivity Tests
    path: test-results.xml
    reporter: java-junit
```

## Example Output

### Successful Connection

```
╔═══════════════════════════════════════════════════════════════════════╗
║           Oracle JDBC Connectivity Test Results                       ║
╚═══════════════════════════════════════════════════════════════════════╝

Testing 2 connection(s)...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[✓] prod-db-primary (production, primary)
    Host: prod-oracle-01.example.com:1521
    Service: PRODDB
    Version: Oracle Database 19c Enterprise Edition Release 19.0.0.0.0
    Connection Time: 234ms
    Test Query: OK (2ms)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Summary:
  Total: 2 | Passed: 2 | Failed: 0 | Skipped: 0
  Duration: 456ms

Exit Code: 0
```

### Failed Connection with Diagnostics

```
[✗] dev-db (development)
    Host: dev-oracle.example.com:1521
    SID: DEVDB
    Error: ORA-12514: TNS:listener does not currently know of service requested

    Diagnostics:
    ├─ DNS Resolution: ✓ (23ms) → 192.168.1.100
    ├─ Port Reachability: ✓ Port is open
    └─ Analysis: TNS listener does not know of the requested SID 'DEVDB'

    Recommendations:
    • Verify the SID 'DEVDB' is correct (case-sensitive)
    • Try using service name instead of SID
    • Check available services with: lsnrctl status
    • Contact DBA to verify service registration
```

## Requirements

- Java 21 or later
- Network access to Oracle database servers

## License

MIT License
