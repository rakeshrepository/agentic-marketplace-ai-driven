#!/bin/bash
#
# Launch script for Kafka MCP Server (STDIO mode for VS Code)
#
# This script starts the MCP server in STDIO mode, which allows VS Code extensions
# (like Claude Code, Cline, etc.) to communicate with it via standard input/output.
#
# Usage:
#   ./run-mcp-stdio.sh
#
# Environment Variables:
#   KAFKA_BOOTSTRAP_SERVERS - Kafka broker address (default: localhost:9092)
#   KAFKA_ADMIN_TIMEOUT     - Request timeout in ms (default: 10000)
#

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Navigate to the mcp-server directory
MCP_SERVER_DIR="$PROJECT_ROOT/mcp-server/kafka-mcp-server"

# Check if JAR exists
JAR_FILE="$MCP_SERVER_DIR/target/kafka-mcp-server-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Building project first..."
    cd "$PROJECT_ROOT" && ./mvnw clean package -DskipTests
    
    if [ ! -f "$JAR_FILE" ]; then
        echo "❌ Build failed or JAR still not found"
        exit 1
    fi
fi

# Set default environment variables if not already set
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
export KAFKA_ADMIN_TIMEOUT=${KAFKA_ADMIN_TIMEOUT:-10000}

# Log to stderr so it doesn't interfere with JSON-RPC on stdout
echo "🚀 Starting Kafka MCP Server (STDIO mode)" >&2
echo "📡 Kafka Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS" >&2
echo "⏱️  Request Timeout: ${KAFKA_ADMIN_TIMEOUT}ms" >&2
echo "" >&2

# Run the STDIO MCP server
# Note: All logging goes to stderr, JSON-RPC communication uses stdout/stdin
exec java -cp "$JAR_FILE" com.agentic.marketplace.mcp.StdioKafkaMcpServer
