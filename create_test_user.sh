#!/bin/bash

# Create test user via GraphQL mutation
# 
# USAGE:
#   ./create_test_user.sh [username] [email]
#   TEST_PASSWORD=mypass ./create_test_user.sh [username] [email]
#   echo 'mypass' | ./create_test_user.sh [username] [email]
#   cat password.txt | ./create_test_user.sh [username] [email]
#
# PASSWORD HANDLING (in order of preference):
#   1. TEST_PASSWORD environment variable (secure for scripts)
#   2. stdin (secure for pipes or redirected input)
#   3. Interactive prompt with silent input (secure for manual runs)
#
# SECURITY WARNING:
#   DO NOT pass passwords as positional arguments or in command line.
#   Passwords passed as arguments are visible in process listings (ps).
#   Always use environment variables, stdin, or interactive prompts.
#
# ENVIRONMENT VARIABLES:
#   TEST_USERNAME       - default: testuser
#   TEST_EMAIL          - default: testuser@example.com
#   TEST_PASSWORD       - (optional) read from here if set
#   INSECURE_TLS        - set to 'true' to disable TLS verification (not recommended)
#
# EXAMPLES:
#   ./create_test_user.sh
#   TEST_PASSWORD=SecurePass123! ./create_test_user.sh myuser myuser@example.com
#   echo 'SecurePass123!' | ./create_test_user.sh myuser myuser@example.com
#   ./create_test_user.sh myuser myuser@example.com < password.txt

set -euo pipefail

# Set defaults from env vars or command args
USERNAME="${1:-${TEST_USERNAME:-testuser}}"
EMAIL="${2:-${TEST_EMAIL:-testuser@example.com}}"

# Handle password securely (in priority order)
if [ -n "${TEST_PASSWORD:-}" ]; then
    # Use TEST_PASSWORD environment variable if set
    PASSWORD="$TEST_PASSWORD"
elif [ ! -t 0 ]; then
    # Read from stdin if available (not a TTY)
    read -r PASSWORD || true
    if [ -z "$PASSWORD" ]; then
        echo "Error: Password required but stdin is empty" >&2
        echo "Usage: echo 'password' | $0 [username] [email]" >&2
        exit 1
    fi
elif [ -t 1 ]; then
    # Interactive terminal: prompt with silent input (read -s)
    read -sp "Enter password for $USERNAME: " PASSWORD
    echo >&2  # newline after silent input
    if [ -z "$PASSWORD" ]; then
        echo "Error: Password is required" >&2
        exit 1
    fi
else
    # No TTY, no stdin, no env var
    echo "Error: No password provided" >&2
    echo "Usage:" >&2
    echo "  TEST_PASSWORD=mypass $0 [username] [email]" >&2
    echo "  echo 'mypass' | $0 [username] [email]" >&2
    echo "  $0 [username] [email]  # will prompt interactively" >&2
    exit 1
fi

# Validate required fields
if [ -z "$USERNAME" ] || [ -z "$EMAIL" ]; then
    echo "Error: Missing required fields" >&2
    echo "Usage: $0 [username] [email]" >&2
    echo "Set TEST_PASSWORD env var, pipe password via stdin, or enter interactively." >&2
    exit 1
fi

# Basic email validation
if ! [[ "$EMAIL" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
    echo "Error: Invalid email format: $EMAIL" >&2
    exit 1
fi

# Build curl arguments
CURL_ARGS=(-s -X POST https://localhost:8443/graphql -H "Content-Type: application/json")

# Handle TLS verification (default: strict verification)
if [ "${INSECURE_TLS:-false}" = "true" ]; then
    echo "WARNING: TLS verification is DISABLED (INSECURE_TLS=true)" >&2
    echo "WARNING: This should only be used in development/testing" >&2
    CURL_ARGS+=(-k)
fi

# Build GraphQL mutation payload safely using jq to prevent injection
QUERY='mutation CreateUser($input: CreateUserInput!) { createUser(input: $input) { id username email } }'
PAYLOAD=$(jq -n \
    --arg query "$QUERY" \
    --arg username "$USERNAME" \
    --arg email "$EMAIL" \
    --arg password "$PASSWORD" \
    '{query: $query, variables: {input: {username: $username, email: $email, password: $password}}}')

# Execute mutation and capture response
RESPONSE=$(curl "${CURL_ARGS[@]}" -d "$PAYLOAD")

# Check for GraphQL errors
if echo "$RESPONSE" | jq -e '.errors' &>/dev/null; then
    echo "Error: GraphQL mutation failed" >&2
    echo "$RESPONSE" | jq '.' >&2
    exit 1
fi

# Check if data is present and valid
if ! echo "$RESPONSE" | jq -e '.data.createUser.id' &>/dev/null; then
    echo "Error: User creation did not return expected response" >&2
    echo "$RESPONSE" | jq '.' >&2
    exit 1
fi

# Success: output the result
echo "User created successfully:"
echo "$RESPONSE" | jq '.data.createUser'
