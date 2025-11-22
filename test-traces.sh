#!/bin/bash

# Generate test traces to populate Jaeger service dependencies

echo "Generating test traces..."

# Test 1: Simple GraphQL query
echo "Test 1: getSystemStatus query"
curl -s -k -X POST https://localhost:8443/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query { getSystemStatus { status uptime } }"}' 2>&1 | head -5

sleep 2

# Test 2: Get current user (requires auth, so this will fail but still generates a span)
echo -e "\nTest 2: getCurrentUser query"
curl -s -k -X POST https://localhost:8443/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query { getCurrentUser { id username email } }"}' 2>&1 | head -5

sleep 2

# Test 3: Create user mutation (will fail but generates spans)
echo -e "\nTest 3: createUser mutation"
curl -s -k -X POST https://localhost:8443/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { createUser(input: {username: \"testuser\", email: \"test@example.com\", password: \"pass123\"}) { id username email } }"}' 2>&1 | head -5

sleep 3

echo -e "\n✅ Traces generated! Checking Elasticsearch..."
curl -s 'http://localhost:9200/_cat/indices?v' | grep -E "jaeger|span"

echo -e "\n✅ Jaeger UI available at: http://localhost:16686"
echo "Dependencies will appear after a few moments of trace collection."
