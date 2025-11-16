#!/bin/bash

# Create test user via GraphQL mutation
# Usage: create_test_user.sh [username] [email] [password]
# Environment variables can be used:
#   TEST_USERNAME - default: testuser
#   TEST_EMAIL - default: testuser@example.com
#   TEST_PASSWORD - default: TestPassword123!
# 
# Example:
#   ./create_test_user.sh
#   ./create_test_user.sh myuser myuser@example.com MyPassword123!
#   TEST_USERNAME=customuser TEST_EMAIL=custom@example.com ./create_test_user.sh

# Set defaults from env vars or command args
USERNAME="${1:-${TEST_USERNAME:-testuser}}"
EMAIL="${2:-${TEST_EMAIL:-testuser@example.com}}"
PASSWORD="${3:-${TEST_PASSWORD:-TestPassword123!}}"

# Validate required fields
if [ -z "$USERNAME" ] || [ -z "$EMAIL" ] || [ -z "$PASSWORD" ]; then
    echo "Error: Missing required fields"
    echo "Usage: $0 [username] [email] [password]"
    echo "Or set environment variables: TEST_USERNAME, TEST_EMAIL, TEST_PASSWORD"
    exit 1
fi

# Basic email validation
if ! [[ "$EMAIL" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
    echo "Error: Invalid email format: $EMAIL"
    exit 1
fi

# Create mutation payload with user-provided values
curl -s -X POST https://localhost:8443/graphql \
  -H "Content-Type: application/json" \
  -k \
  -d "{
    \"query\": \"mutation CreateUser(\$input: CreateUserInput!) { createUser(input: \$input) { id username email } }\",
    \"variables\": {
      \"input\": {
        \"username\": \"$USERNAME\",
        \"email\": \"$EMAIL\",
        \"password\": \"$PASSWORD\"
      }
    }
  }" | jq .
