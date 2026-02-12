#!/bin/bash
# Test MCP Server JSON-RPC Response

REQUEST='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

echo "Sending initialize request..."
echo "$REQUEST" | ./run-mcp-stdio.sh 2>/tmp/mcp-stderr.log &
PID=$!

# Wait for response (max 3 seconds)
for i in {1..30}; do
    sleep 0.1
    if ! kill -0 $PID 2>/dev/null; then
        break
    fi
done

# Kill if still running
kill $PID 2>/dev/null || true
wait $PID 2>/dev/null || true

echo ""
echo "=== STDERR LOG ==="
tail -20 /tmp/mcp-stderr.log
