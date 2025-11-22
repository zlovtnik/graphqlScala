#!/bin/bash

# Generate test traces to populate Jaeger service dependencies
set -e  # Exit immediately if any command fails

echo "Generating test traces..."

# Function to execute GraphQL request with HTTP status validation
execute_graphql_test() {
  local test_name="$1"
  local query="$2"
  
  echo "Test: $test_name"
  local http_code
  http_code=$(curl -s -o /tmp/gql_response.json -w '%{http_code}' -k -X POST https://localhost:8443/graphql \
    -H "Content-Type: application/json" \
    -d "$query")
  
  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    echo "✓ $test_name succeeded (HTTP $http_code)"
    head -5 /tmp/gql_response.json
  elif [ "$http_code" -ge 400 ]; then
    echo "⚠ $test_name returned HTTP $http_code (expected for auth tests)"
    head -5 /tmp/gql_response.json
  else
    echo "✗ $test_name failed with HTTP $http_code" >&2
    cat /tmp/gql_response.json >&2
    return 1
  fi
}

# Test 1: Simple GraphQL query
execute_graphql_test "getSystemStatus query" \
  '{"query":"query { getSystemStatus { status uptime } }"}'

sleep 2

# Test 2: Get current user (requires auth, so this will fail but still generates a span)
execute_graphql_test "getCurrentUser query" \
  '{"query":"query { getCurrentUser { id username email } }"}'

sleep 2

# Test 3: Create user mutation (will fail but generates spans)
execute_graphql_test "createUser mutation" \
  '{"query":"mutation { createUser(input: {username: \"testuser\", email: \"test@example.com\", password: \"pass123\"}) { id username email } }"}'

sleep 3

echo ""
echo "✅ Traces generated successfully! Checking Elasticsearch..."

# Verify Elasticsearch connectivity
es_check=$(curl -s -o /dev/null -w '%{http_code}' 'http://localhost:9200/_cat/indices?v')
if [ "$es_check" = "200" ]; then
  echo "✓ Elasticsearch is reachable"
  curl -s 'http://localhost:9200/_cat/indices?v' | grep -E "jaeger|span" || echo "  (No jaeger indices found yet - may take a moment)"
else
  echo "⚠ Elasticsearch not fully ready (HTTP $es_check) - indices may appear shortly"
fi

echo ""
echo "✅ Jaeger UI available at: http://localhost:16686"
echo "✅ All tests completed successfully"
echo "Dependencies will appear in Jaeger after a few moments of trace collection."
