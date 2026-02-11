#!/bin/bash

# Kafka MCP - Quick Start Testing Script
# This script helps you quickly start all services and begin testing

set -e

echo "🚀 Kafka MCP - Quick Start"
echo "=========================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Ollama is running
echo -e "${YELLOW}[1/6] Checking Ollama...${NC}"
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Ollama is running${NC}"
    
    # Check if llama3 model is available
    if ollama list | grep -q llama3; then
        echo -e "${GREEN}✓ llama3 model is installed${NC}"
    else
        echo -e "${YELLOW}⚠ llama3 model not found. Pulling...${NC}"
        ollama pull llama3
    fi
else
    echo -e "${RED}✗ Ollama is not running${NC}"
    echo "  Please start Ollama: ollama serve"
    echo "  Then run this script again"
    exit 1
fi

echo ""

# Check Docker
echo -e "${YELLOW}[2/6] Checking Docker...${NC}"
if command -v docker &> /dev/null && docker info > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Docker is running${NC}"
else
    echo -e "${RED}✗ Docker is not running${NC}"
    echo "  Please start Docker Desktop"
    exit 1
fi

echo ""

# Start Docker services
echo -e "${YELLOW}[3/6] Starting Docker services...${NC}"
docker-compose up -d

echo "  Waiting for services to be healthy (30 seconds)..."
sleep 30

echo ""

# Verify Kafka MCP Server
echo -e "${YELLOW}[4/6] Verifying Kafka MCP Server...${NC}"
if curl -s http://localhost:8081/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Kafka MCP Server is healthy${NC}"
    
    # Test MCP tools endpoint
    TOOLS=$(curl -s -X POST http://localhost:8081/mcp/message \
      -H "Content-Type: application/json" \
      -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | grep -o '"name"' | wc -l)
    
    if [ "$TOOLS" -gt 0 ]; then
        echo -e "${GREEN}✓ MCP Server has $TOOLS tools available${NC}"
    fi
else
    echo -e "${RED}✗ Kafka MCP Server not responding${NC}"
    echo "  Check logs: docker-compose logs kafka-mcp-server"
fi

echo ""

# Install web app dependencies
echo -e "${YELLOW}[5/6] Installing web app dependencies...${NC}"
cd web-app
if [ ! -d "node_modules" ]; then
    npm install
    echo -e "${GREEN}✓ Dependencies installed${NC}"
else
    echo -e "${GREEN}✓ Dependencies already installed${NC}"
fi

echo ""

# Start web app
echo -e "${YELLOW}[6/6] Starting web application...${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ All services started successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "📊 Service URLs:"
echo "  • Web App:           http://localhost:5173"
echo "  • Kafka UI:          http://localhost:8088"
echo "  • Kafka MCP Server:  http://localhost:8081"
echo "  • Agent Registry:    http://localhost:8090"
echo "  • Ollama:            http://localhost:11434"
echo ""
echo "🧪 Testing Instructions:"
echo "  1. Open http://localhost:5173 in your browser"
echo "  2. Click the '⚡ MCP Kafka Chat' button"
echo "  3. Try: 'Create a topic called test-topic with 3 partitions'"
echo "  4. Try: 'List all Kafka topics'"
echo "  5. Try: 'Show cluster health'"
echo ""
echo "📝 Full guide: See TESTING_GUIDE.md"
echo ""
echo "Starting Vite dev server..."
echo ""

npm run dev
