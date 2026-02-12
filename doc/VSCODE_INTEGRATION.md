# VS Code MCP Integration Guide

This guide explains how to integrate the Kafka MCP Server with Visual Studio Code using the Model Context Protocol (MCP).

## 📋 Prerequisites

### Required

1. **Visual Studio Code** - [Download latest version](https://code.visualstudio.com/download)
2. **GitHub Copilot Extension** - [Install from VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=GitHub.copilot)
   - Requires active GitHub Copilot subscription
   - MCP server support is **built into GitHub Copilot extension** (no additional plugin needed)
3. **Java 17+** - Required to run the MCP server
   ```bash
   java -version  # Should show version 17 or higher
   ```
4. **Apache Kafka** - Running instance (default: `localhost:9092`)
   ```bash
   # Verify Kafka is running
   kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

### Important Notes

- ✅ **No additional MCP plugin needed** - MCP support is built into GitHub Copilot extension
- ✅ The `mcp.json` configuration file is automatically detected by VS Code
- ✅ MCP servers appear in the Extensions view under "MCP SERVERS"

## 🚀 Quick Start

### Step 0: Install GitHub Copilot Extension

If you haven't already:

1. Open VS Code
2. Go to Extensions view (`Cmd+Shift+X` or `Ctrl+Shift+X`)
3. Search for "**GitHub Copilot**"
4. Click **Install**
5. Sign in with your GitHub account (requires Copilot subscription)

> **Note:** MCP server support is built into the GitHub Copilot extension - no separate MCP plugin needed!

### Step 1: Build the Project

```bash
# From the project root
./mvnw clean package -DskipTests
```

### Step 2: Make the Launch Script Executable

```bash
chmod +x run-mcp-stdio.sh
```

### Step 3: Configure VS Code

The project includes a pre-configured `.vscode/mcp.json` file that VS Code will automatically detect.

**Configuration location:** `.vscode/mcp.json`

```json
{
  "mcpServers": {
    "kafkaAdmin": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "args": [],
      "env": {
        "KAFKA_BOOTSTRAP_SERVERS": "${input:kafkaBootstrapServers}",
        "KAFKA_ADMIN_TIMEOUT": "10000"
      }
    }
  },
  "inputs": [
    {
      "id": "kafkaBootstrapServers",
      "type": "promptString",
      "description": "Kafka Bootstrap Servers (e.g., localhost:9092)",
      "default": "localhost:9092"
    }
  ]
}
```

### Step 4: Start the MCP Server in VS Code

1. Open the **Command Palette** (`Cmd+Shift+P` on macOS, `Ctrl+Shift+P` on Windows/Linux)
2. Run: `MCP: List Servers`
3. Select `kafkaAdmin`
4. Choose `Start Server`
5. When prompted, enter your Kafka bootstrap servers (default: `localhost:9092`)
6. Click **Trust** when asked to trust the MCP server

## 🎯 Using Kafka Tools in Copilot Chat

Once the server is running, you can use Kafka admin tools in GitHub Copilot Chat:

### Available Tools

1. **list_topics** - List all Kafka topics
2. **describe_topic** - Get detailed information about a topic
3. **create_topic** - Create a new Kafka topic
4. **update_topic** - Update topic configuration (partitions)
5. **delete_topic** - Delete a Kafka topic

### Example Prompts

**List topics:**
```
@workspace List all Kafka topics
```

**Create a topic:**
```
@workspace Create a Kafka topic called "user-events" with 5 partitions and replication factor 3
```

**Get topic details:**
```
@workspace Describe the "orders-prod" topic
```

**Update partitions:**
```
@workspace Increase partitions for "user-events" topic to 10
```

**Delete a topic:**
```
@workspace Delete the "test-topic" topic
```

## 🔧 Advanced Configuration

### Using Different Kafka Environments

You can configure multiple MCP server instances for different Kafka environments:

```json
{
  "mcpServers": {
    "kafkaLocal": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "env": {
        "KAFKA_BOOTSTRAP_SERVERS": "localhost:9092"
      }
    },
    "kafkaStaging": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "env": {
        "KAFKA_BOOTSTRAP_SERVERS": "staging-kafka.example.com:9092"
      }
    },
    "kafkaProd": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "env": {
        "KAFKA_BOOTSTRAP_SERVERS": "prod-kafka.example.com:9092"
      }
    }
  }
}
```

### Using Environment Files

Instead of hardcoding values, you can use an `.env` file:

```json
{
  "mcpServers": {
    "kafkaAdmin": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "envFile": "${workspaceFolder}/.env"
    }
  }
}
```

Create a `.env` file in the project root:

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_ADMIN_TIMEOUT=10000
```

### Tool Selection

Control which tools are available in Copilot Chat:

1. Click the **Tools** button in the Chat view
2. Expand the **kafkaAdmin** server section
3. Toggle individual tools on/off

## 🛠️ Managing the MCP Server

### Start Server

- **Command Palette:** `MCP: List Servers` → Select server → `Start Server`
- **Extensions View:** Right-click server → `Start Server`

### Stop Server

- **Command Palette:** `MCP: List Servers` → Select server → `Stop Server`
- **Extensions View:** Right-click server → `Stop Server`

### View Server Logs

- **Command Palette:** `MCP: List Servers` → Select server → `Show Output`
- **Extensions View:** Right-click server → `Show Output`

### Restart Server

- **Command Palette:** `MCP: List Servers` → Select server → `Restart Server`

### Clear Cached Tools

If tools aren't showing up after changes:

```
Command Palette → MCP: Reset Cached Tools
```

## 🔍 Troubleshooting

### Server Won't Start

1. **Check build:**
   ```bash
   ./mvnw clean package -DskipTests
   ls -la mcp-server/kafka-mcp-server/target/kafka-mcp-server-*.jar
   ```

2. **Check script permissions:**
   ```bash
   chmod +x run-mcp-stdio.sh
   ```

3. **Test script manually:**
   ```bash
   ./run-mcp-stdio.sh
   ```
   Type: `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}`
   Press Enter. You should see a JSON response.
   Press `Ctrl+C` to exit.

4. **View server logs:**
   - Command Palette → `MCP: List Servers` → Select server → `Show Output`

### Kafka Connection Issues

1. **Verify Kafka is running:**
   ```bash
   # Using Kafka CLI tools
   kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

2. **Check bootstrap servers:**
   - Command Palette → `MCP: Open Workspace Configuration`
   - Verify `KAFKA_BOOTSTRAP_SERVERS` is correct

3. **Check firewall/network:**
   - Ensure port 9092 (or your Kafka port) is accessible

### Tools Not Appearing in Chat

1. **Restart server:**
   - Command Palette → `MCP: List Servers` → Restart Server

2. **Clear tool cache:**
   - Command Palette → `MCP: Reset Cached Tools`

3. **Check tool selection:**
   - Click Tools button in Chat view
   - Ensure Kafka tools are enabled

### Error: "Cannot have more than 128 tools per request"

Disable some tools or servers in the tool picker:
1. Click **Tools** button in Chat view
2. Deselect unused servers or individual tools

## 🔐 Security Considerations

### MCP Server Trust

VS Code will prompt you to trust the MCP server on first start. Review the configuration before accepting:

- ✅ `command`: Points to your project's script
- ✅ `env`: Contains only Kafka connection details
- ✅ No suspicious network calls or file system access

### Reset Trust

If you need to re-evaluate server trust:

```
Command Palette → MCP: Reset Trust
```

### Network Security

- Use **localhost** for local development
- Use **VPN or SSH tunnels** for remote Kafka clusters
- Consider **authentication** (SASL/SSL) for production environments

## 📊 Development Mode

Enable development mode to automatically restart the server when code changes:

```json
{
  "mcpServers": {
    "kafkaAdmin": {
      "type": "stdio",
      "command": "${workspaceFolder}/run-mcp-stdio.sh",
      "dev": {
        "watch": "${workspaceFolder}/mcp-server/kafka-mcp-server/target/**/*.jar"
      }
    }
  }
}
```

Now the server will restart when you rebuild:

```bash
./mvnw clean package -DskipTests
```

## 🌐 Alternative: HTTP/SSE Transport

If you prefer HTTP transport instead of STDIO (for debugging or web-based clients):

1. Start the HTTP server:
   ```bash
   java -jar mcp-server/kafka-mcp-server/target/kafka-mcp-server-*.jar
   ```

2. Configure VS Code for HTTP:
   ```json
   {
     "mcpServers": {
       "kafkaAdmin": {
         "type": "http",
         "url": "http://localhost:8080/mcp"
       }
     }
   }
   ```

**Note:** STDIO transport is recommended for VS Code as it's more efficient and doesn't require managing server lifecycle separately.

## 📚 Additional Resources

- [VS Code MCP Documentation](https://code.visualstudio.com/docs/copilot/customization/mcp-servers)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [GitHub Copilot Documentation](https://docs.github.com/copilot)
- [Kafka Admin API](https://kafka.apache.org/documentation/#adminapi)

## 🤝 Contributing

Found an issue or have a suggestion? Please open an issue or pull request in the project repository.

---

**Last Updated:** February 12, 2026
