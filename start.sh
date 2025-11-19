#!/bin/bash

# SSF Application Quick Start Script
# Supports: Docker Compose (full stack) or Local development

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="ssf"

# Functions
print_header() {
    echo -e "\n${BLUE}═══════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Check Docker installation
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed"
        exit 1
    fi
    print_success "Docker found: $(docker --version)"
}

check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed"
        exit 1
    fi
    print_success "Docker Compose found: $(docker-compose --version)"
}

check_java() {
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed"
        exit 1
    fi
    print_success "Java found: $(java -version 2>&1 | head -n 1)"
}

check_gradle() {
    if ! command -v gradle &> /dev/null && [ ! -f "$SCRIPT_DIR/gradlew" ]; then
        print_error "Gradle is not installed and gradlew not found"
        exit 1
    fi
    print_success "Gradle ready"
}

build_project() {
    print_header "Building Spring Boot Application"
    
    if [ -f "$SCRIPT_DIR/gradlew" ]; then
        chmod +x "$SCRIPT_DIR/gradlew"
        "$SCRIPT_DIR/gradlew" clean bootJar -x test
    else
        gradle clean bootJar -x test
    fi
    
    print_success "Build complete"
}

start_docker_stack() {
    print_header "Starting Docker Compose Stack"
    
    # Check .env.docker exists
    if [ ! -f "$SCRIPT_DIR/.env.docker" ]; then
        print_error ".env.docker not found"
        exit 1
    fi
    
    # Start services
    docker-compose up -d
    
    print_info "Waiting for services to be healthy..."
    sleep 10
    
    # Check services
    print_header "Service Status"
    docker-compose ps
    
    print_success "All services started"
    print_info "Oracle Free: oracle-free (port 1521)"
    print_info "MinIO: minio-server (port 9000, console 9001)"
    print_info "Application: ssf-app (port 8443)"
}

start_local_dev() {
    print_header "Starting Local Development Environment"
    
    check_java
    check_gradle
    
    # Start only database and MinIO
    print_info "Starting Oracle and MinIO containers..."
    docker-compose -f docker-compose.yml up -d oracle minio
    
    print_info "Waiting for services..."
    sleep 15
    
    # Start Spring Boot app
    print_info "Starting Spring Boot application..."
    if [ -f "$SCRIPT_DIR/gradlew" ]; then
        chmod +x "$SCRIPT_DIR/gradlew"
        "$SCRIPT_DIR/gradlew" bootRun
    else
        gradle bootRun
    fi
}

display_endpoints() {
    print_header "Service Endpoints"
    
    echo "🔗 REST API:"
    echo "  - Login: POST https://localhost:8443/api/auth/login"
    echo "  - Validate: POST https://localhost:8443/api/auth/validate"
    echo ""
    echo "📊 GraphQL:"
    echo "  - Endpoint: https://localhost:8443/graphql"
    echo "  - IDE: https://localhost:8443/graphiql"
    echo ""
    echo "💾 Database & Storage:"
    echo "  - Oracle: localhost:1521 (admin/AdminPassword123)"
    echo "  - MinIO: http://localhost:9000 (minioadmin/minioadmin)"
    echo "  - MinIO Console: http://localhost:9001"
    echo ""
    echo "🏥 Health:"
    echo "  - Status: https://localhost:8443/actuator/health (ignore SSL warnings)"
    echo ""
}

test_endpoints() {
    print_header "Testing Endpoints"
    
    BASE_URL="https://localhost:8443"
    
    # Test login
    print_info "Testing login endpoint..."
    if RESPONSE=$(curl -s -X POST $BASE_URL/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin123"}' \
        -k 2>/dev/null); then
        
        if echo "$RESPONSE" | grep -q "token"; then
            print_success "Login endpoint working"
            TOKEN=$(echo "$RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
            echo "  Sample token: ${TOKEN:0:50}..."
        else
            print_error "Login endpoint returned invalid response"
        fi
    else
        print_error "Could not connect to login endpoint"
    fi
    
    # Test health
    print_info "Testing health endpoint..."
    if curl -s $BASE_URL/actuator/health -k | grep -q "UP"; then
        print_success "Health endpoint working"
    else
        print_info "Health endpoint not yet available (services may still be starting)"
    fi
}

show_logs() {
    print_header "Showing Docker Logs"
    docker-compose logs -f
}

show_help() {
    cat << EOF
${BLUE}SSF Application Quick Start${NC}

Usage: $0 [COMMAND]

Commands:
    ${GREEN}docker${NC}         Start full stack with Docker Compose
    ${GREEN}local${NC}          Start local development (containers for DB/MinIO only)
    ${GREEN}build${NC}          Build Spring Boot JAR
    ${GREEN}test${NC}           Test endpoints
    ${GREEN}logs${NC}           Show Docker logs
    ${GREEN}stop${NC}           Stop all containers
    ${GREEN}status${NC}         Show container status
    ${GREEN}clean${NC}          Stop and remove containers
    ${GREEN}help${NC}           Show this help message

Examples:
    # Start full stack with Docker Compose
    $0 docker

    # Start local development
    $0 local

    # View logs
    $0 logs

EOF
}

# Main Script
main() {
    case "${1:-help}" in
        docker)
            check_docker
            check_docker_compose
            build_project
            start_docker_stack
            display_endpoints
            test_endpoints
            ;;
        local)
            check_java
            check_gradle
            check_docker
            check_docker_compose
            start_local_dev
            ;;
        build)
            check_gradle
            build_project
            ;;
        test)
            test_endpoints
            ;;
        logs)
            show_logs
            ;;
        stop)
            print_info "Stopping containers..."
            docker-compose stop
            print_success "Containers stopped"
            ;;
        status)
            print_header "Container Status"
            docker-compose ps
            ;;
        clean)
            print_info "Stopping and removing containers..."
            docker-compose down
            print_success "Cleanup complete"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "Unknown command: $1"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
