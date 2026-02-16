# Documentation

## Architecture Diagrams

This folder contains architecture diagrams using [LikeC4](https://likec4.dev/).

**Note:** Dynamic views require LikeC4 v1.2.0 or later. Install with `npx likec4@latest` to ensure compatibility.

### SSH Tunnel Architecture

The `ssh-tunnel-architecture.c4` file describes the multi-hop SSH tunnel setup for connecting to databases through bastion hosts.

#### Viewing the Diagram

**Option 1: LikeC4 CLI**
```bash
# Install LikeC4
npm install -g likec4

# Start the dev server
likec4 serve docs/

# Or export to various formats
likec4 export docs/ssh-tunnel-architecture.c4 -o docs/diagrams/
```

**Option 2: VS Code Extension**
Install the [LikeC4 VS Code extension](https://marketplace.visualstudio.com/items?itemName=likec4.likec4-vscode) for live preview.

### Diagram Overview

The SSH tunnel architecture shows a multi-hop scenario:

```
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  Client Machine  │    │ Jump Server A    │    │ Server B        │    │ Oracle Database  │
│                  │    │ (DMZ)            │    │ (Internal)      │    │ (Secure Zone)    │
│  ┌────────────┐  │    │  ┌────────────┐  │    │  ┌────────────┐  │    │  ┌────────────┐  │
│  │ jdbc-test  │  │    │  │ sshd :22   │  │    │  │ sshd :22   │  │    │  │ TNS :1521  │  │
│  └──────┬─────┘  │    │  └──────┬─────┘  │    │  └──────┬─────┘  │    │  └──────┬─────┘  │
│  localhost:54321 │    │       │          │    │       │          │    │       │          │
└────────┼─────────┘    └───────┼──────────┘    └───────┼──────────┘    └───────┼──────────┘
         │                      │                      │                      │
         │    SSH Session 1     │    SSH Session 2     │    Port Forward      │
         │─────────────────────>│─────────────────────>│─────────────────────>│
         │   bastion:22         │   server-b:22        │   db:1521            │
         │   (via internet)     │   (via 1st tunnel)   │   (via 2nd tunnel)   │
         │                      │                      │                      │
         │<═══════════════════════════════════════════════════════════════════│
         │                 JDBC Connection (tunneled through chain)           │
```

**Views available:**
- `index` - Overview of all components and connections
- `tunnelDetail` - Detailed view showing port forwarding at each hop
- `tunnelSequence` - Dynamic view showing step-by-step tunnel establishment sequence

### Port Flow Detail

1. **Client binds ephemeral port** (e.g., `localhost:54321`)
2. **SSH Session 1**: Client → Jump Server A (port 22)
   - Establishes first tunnel
   - Creates local forward for next hop
3. **SSH Session 2**: Through tunnel to Server B (port 22)
   - Connection routed through first SSH session
   - Establishes second tunnel
4. **Port Forward**: Server B → Oracle Database (port 1521)
   - Final tunnel to database listener
5. **JDBC**: Connects to `localhost:54321`, traffic flows through entire chain

### Configuration Example

```yaml
ssh_tunnels:
  jump-server-a:
    host: jump-a.example.com      # Publicly accessible
    port: 22
    username: admin
    private_key: ~/.ssh/id_rsa
  
  internal-via-jump:
    host: server-b.internal       # Only reachable from jump-a
    port: 22
    username: dbadmin
    private_key: ~/.ssh/id_ed25519
    jump_host: jump-server-a      # Uses first tunnel

connections:
  - name: prod-db
    host: db.secure.internal      # Only reachable from server-b
    port: 1521
    service: PRODDB
    username: app_user
    password: ${DB_PASSWORD}
    ssh_tunnel: internal-via-jump # Uses multi-hop chain
```
