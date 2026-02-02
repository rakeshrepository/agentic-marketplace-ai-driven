#!/bin/bash

# Agentic Marketplace AI-Driven - Start Script
# This script builds and starts the entire stack

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Agentic Marketplace AI-Driven${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker and try again.${NC}"
    exit 1
fi

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}Error: docker-compose is not installed.${NC}"
    exit 1
fi

# Use docker compose (v2) if available, otherwise docker-compose
DOCKER_COMPOSE="docker compose"
if ! docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker-compose"
fi

# Configuration
OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.2}"

echo -e "${YELLOW}Configuration:${NC}"
echo -e "  LLM Model: ${OLLAMA_MODEL}"
echo ""

# Parse command line arguments
ACTION="${1:-start}"

case $ACTION in
    start)
        echo -e "${GREEN}Starting the Agentic Marketplace...${NC}"
        echo ""

        # Build and start services
        echo -e "${BLUE}Step 1: Building Docker images...${NC}"
        OLLAMA_MODEL=$OLLAMA_MODEL $DOCKER_COMPOSE build

        echo ""
        echo -e "${BLUE}Step 2: Starting services...${NC}"
        OLLAMA_MODEL=$OLLAMA_MODEL $DOCKER_COMPOSE up -d

        echo ""
        echo -e "${BLUE}Step 3: Waiting for services to be healthy...${NC}"
        sleep 10

        # Pull the Ollama model
        echo ""
        echo -e "${BLUE}Step 4: Pulling Ollama model (${OLLAMA_MODEL})...${NC}"
        echo -e "${YELLOW}This may take a few minutes on first run...${NC}"
        docker exec ollama ollama pull $OLLAMA_MODEL || true

        echo ""
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}  Agentic Marketplace is ready!${NC}"
        echo -e "${GREEN}========================================${NC}"
        echo ""
        echo -e "Access the application at:"
        echo -e "  ${BLUE}Web App:${NC}     http://localhost:3000"
        echo -e "  ${BLUE}Agent API:${NC}   http://localhost:8080/api/agent/health"
        echo -e "  ${BLUE}MCP Server:${NC}  http://localhost:8081/api/health"
        echo -e "  ${BLUE}Kafka:${NC}       localhost:9092"
        echo -e "  ${BLUE}Ollama:${NC}      http://localhost:11434"
        echo ""
        echo -e "${YELLOW}Tip: Use './start.sh logs' to view service logs${NC}"
        echo -e "${YELLOW}Tip: Use './start.sh stop' to stop all services${NC}"
        ;;

    stop)
        echo -e "${YELLOW}Stopping the Agentic Marketplace...${NC}"
        $DOCKER_COMPOSE down
        echo -e "${GREEN}All services stopped.${NC}"
        ;;

    restart)
        echo -e "${YELLOW}Restarting the Agentic Marketplace...${NC}"
        $DOCKER_COMPOSE restart
        echo -e "${GREEN}All services restarted.${NC}"
        ;;

    logs)
        SERVICE="${2:-}"
        if [ -z "$SERVICE" ]; then
            $DOCKER_COMPOSE logs -f
        else
            $DOCKER_COMPOSE logs -f $SERVICE
        fi
        ;;

    status)
        echo -e "${BLUE}Service Status:${NC}"
        $DOCKER_COMPOSE ps
        ;;

    clean)
        echo -e "${RED}Cleaning up all containers, images, and volumes...${NC}"
        $DOCKER_COMPOSE down -v --rmi all
        echo -e "${GREEN}Cleanup complete.${NC}"
        ;;

    build)
        echo -e "${BLUE}Building Docker images...${NC}"
        $DOCKER_COMPOSE build --no-cache
        echo -e "${GREEN}Build complete.${NC}"
        ;;

    pull-model)
        MODEL="${2:-$OLLAMA_MODEL}"
        echo -e "${BLUE}Pulling Ollama model: ${MODEL}...${NC}"
        docker exec ollama ollama pull $MODEL
        echo -e "${GREEN}Model pulled successfully.${NC}"
        ;;

    *)
        echo "Usage: $0 {start|stop|restart|logs|status|clean|build|pull-model}"
        echo ""
        echo "Commands:"
        echo "  start       - Build and start all services"
        echo "  stop        - Stop all services"
        echo "  restart     - Restart all services"
        echo "  logs [svc]  - View logs (optionally for a specific service)"
        echo "  status      - Show status of all services"
        echo "  clean       - Remove all containers, images, and volumes"
        echo "  build       - Rebuild all Docker images"
        echo "  pull-model  - Pull/update the Ollama model"
        echo ""
        echo "Environment Variables:"
        echo "  OLLAMA_MODEL  - LLM model to use (default: llama3.2)"
        exit 1
        ;;
esac
