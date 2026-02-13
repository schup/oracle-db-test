# Oracle Database XE - Docker Development Setup

## Prerequisites

- Docker and Docker Compose installed
- Accept Oracle's terms when pulling the image (you'll be prompted)

## Quick Start

1. Start the database:
   ```bash
   docker-compose up -d
   ```

2. Wait for initialization (first startup takes 5-10 minutes):
   ```bash
   docker-compose logs -f oracle-xe
   ```
   
   Look for "DATABASE IS READY TO USE!" message

3. Check health status:
   ```bash
   docker-compose ps
   ```

## Connection Details

**Default Credentials:**
- **SYS/SYSTEM Password:** DevPassword123
- **Host:** localhost
- **Port:** 1521
- **Service Name (CDB):** XE
- **Service Name (PDB):** XEPDB1

**Connection Strings:**
```
# For the Container Database (CDB)
system/DevPassword123@localhost:1521/XE

# For the Pluggable Database (PDB) - recommended for development
system/DevPassword123@localhost:1521/XEPDB1
```

**JDBC URL:**
```
jdbc:oracle:thin:@localhost:1521/XEPDB1
```

## Accessing the Database

### Using SQL*Plus (inside container)
```bash
docker exec -it oracle-xe-dev sqlplus system/DevPassword123@XEPDB1
```

### Using SQL*Plus (from host, if installed)
```bash
sqlplus system/DevPassword123@localhost:1521/XEPDB1
```

### Enterprise Manager Express
Access via browser: http://localhost:5500/em

## Managing the Container

```bash
# Start
docker-compose up -d

# Stop
docker-compose down

# Stop and remove data
docker-compose down -v

# View logs
docker-compose logs -f

# Restart
docker-compose restart
```

## Creating Users

Connect and run:
```sql
-- Connect to PDB
ALTER SESSION SET CONTAINER = XEPDB1;

-- Create a new user
CREATE USER myapp IDENTIFIED BY MyAppPassword123;

-- Grant privileges
GRANT CONNECT, RESOURCE TO myapp;
GRANT UNLIMITED TABLESPACE TO myapp;
```

## Initialization Scripts

To run SQL scripts on first startup:

1. Create a directory: `mkdir -p init-scripts`
2. Place your `.sql` scripts in `init-scripts/`
3. Uncomment the init scripts volume mount in docker-compose.yml
4. Start the container

Scripts will execute in alphabetical order on first startup only.

## Data Persistence

Database files are stored in the `oracle-data` Docker volume. To completely reset:

```bash
docker-compose down -v
docker-compose up -d
```

## Troubleshooting

**Container won't start:**
- Ensure you have enough disk space (at least 10GB free)
- Check Docker resources (needs at least 2GB RAM)

**Connection refused:**
- Wait for initialization to complete (check logs)
- Verify the container is healthy: `docker-compose ps`

**Performance issues:**
- Increase shared memory if needed (adjust `shm_size` in docker-compose.yml)
- Allocate more resources in Docker Desktop settings

## Oracle XE Limitations

- Maximum 12GB of user data
- Maximum 2GB RAM usage
- Maximum 2 CPU threads
- Single instance only

These limitations are fine for development but not for production use.

## Notes

- Change the default password in production environments
- The container takes several minutes to initialize on first startup
- Data persists across container restarts in the `oracle-data` volume
- Use XEPDB1 (Pluggable Database) for most development work, not the root container
