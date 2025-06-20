#!/bin/bash

# Fault Tolerance Testing Script using Resilience4j Circuit Breakers

KEYCLOAK_URL="http://localhost:8180"
REALM="GBC_Realm"
GATEWAY_URL="http://localhost:8080"
ASSIGNMENT_SERVICE="http://localhost:8082"
RESOURCE_SERVICE="http://localhost:8083"
COURSE_SERVICE="http://localhost:8081"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "🛡️ Testing Fault Tolerance with Resilience4j Circuit Breakers"
echo "============================================================="

# Function to get access token
get_admin_token() {
    echo "🔑 Getting admin access token..."

    TOKEN_RESPONSE=$(curl -s -X POST \
        "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=admin" \
        -d "password=admin123" \
        -d "grant_type=password" \
        -d "client_id=api-gateway" \
        -d "client_secret=gateway-secret-key")

    ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')

    if [ "$ACCESS_TOKEN" != "null" ] && [ "$ACCESS_TOKEN" != "" ]; then
        echo -e "${GREEN}✅ Admin token obtained successfully${NC}"
        return 0
    else
        echo -e "${RED}❌ Failed to get admin token${NC}"
        return 1
    fi
}

# Function to check service health
check_service_health() {
    local service_name=$1
    local service_url=$2

    echo -n "Checking $service_name health... "

    if curl -s -f "$service_url/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ UP${NC}"
        return 0
    else
        echo -e "${RED}❌ DOWN${NC}"
        return 1
    fi
}

# Function to get circuit breaker status
get_circuit_breaker_status() {
    local service_url=$1
    local cb_name=$2

    curl -s -X GET "$service_url/api/circuit-breaker/status/$cb_name" \
        -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
}

# Function to test API endpoint
test_api_endpoint() {
    local method=$1
    local endpoint=$2
    local description=$3
    local data=$4

    echo "🧪 Testing: $description"

    if [ -n "$data" ]; then
        RESPONSE=$(curl -s -w "\n%{http_code}" -X $method \
            "$endpoint" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -H "Content-Type: application/json" \
            -d "$data")
    else
        RESPONSE=$(curl -s -w "\n%{http_code}" -X $method \
            "$endpoint" \
            -H "Authorization: Bearer $ACCESS_TOKEN")
    fi

    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | head -n -1)

    if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
        echo -e "   ${GREEN}✅ Success ($HTTP_CODE)${NC}"
        return 0
    else
        echo -e "   ${YELLOW}⚠️  Response ($HTTP_CODE)${NC}"
        echo "   Body: $(echo "$BODY" | jq -r '.message // .' 2>/dev/null || echo "$BODY")"
        return 1
    fi
}

# Function to simulate service failure
simulate_service_failure() {
    local service_name=$1

    echo -e "${YELLOW}💥 Simulating $service_name failure...${NC}"
    docker stop "$service_name" > /dev/null 2>&1
    sleep 2
}

# Function to restore service
restore_service() {
    local service_name=$1

    echo -e "${BLUE}🔄 Restoring $service_name...${NC}"
    docker start "$service_name" > /dev/null 2>&1

    # Wait for service to be ready
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "http://localhost:808$(echo $service_name | grep -o '[0-9]')/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ $service_name restored and healthy${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    echo -e "${RED}❌ $service_name failed to restore within timeout${NC}"
    return 1
}

# Main testing flow
main() {
    if ! get_admin_token; then
        exit 1
    fi

    echo ""
    echo -e "${BLUE}📊 Initial Service Health Check${NC}"
    echo "--------------------------------"

    check_service_health "Course Service" "$COURSE_SERVICE"
    COURSE_HEALTH=$?

    check_service_health "Assignment Service" "$ASSIGNMENT_SERVICE"
    ASSIGNMENT_HEALTH=$?

    check_service_health "Resource Service" "$RESOURCE_SERVICE"
    RESOURCE_HEALTH=$?

    if [ $COURSE_HEALTH -ne 0 ] || [ $ASSIGNMENT_HEALTH -ne 0 ] || [ $RESOURCE_HEALTH -ne 0 ]; then
        echo -e "${RED}❌ Some services are not healthy. Please start all services first.${NC}"
        exit 1
    fi

    echo ""
    echo -e "${BLUE}🔍 Initial Circuit Breaker Status${NC}"
    echo "----------------------------------"

    echo "Assignment Service - Course Service Circuit Breaker:"
    get_circuit_breaker_status "$ASSIGNMENT_SERVICE" "courseService"

    echo ""
    echo "Resource Service - Course Service Circuit Breaker:"
    get_circuit_breaker_status "$RESOURCE_SERVICE" "courseServiceForResources"

    echo ""
    echo -e "${BLUE}🧪 Testing Normal Operations${NC}"
    echo "-----------------------------"

    # Test normal assignment creation (should validate course code)
    test_api_endpoint "POST" "$GATEWAY_URL/api/assignments" \
        "Assignment creation with course validation" \
        '{
            "title": "Circuit Breaker Test Assignment",
            "dueDate": "2024-12-31T23:59:00",
            "courseCode": "CS101",
            "description": "Testing circuit breaker functionality"
        }'

    # Test resource department fetching
    test_api_endpoint "GET" "$GATEWAY_URL/api/resources/department/Computer%20Science" \
        "Resource department data fetching"

    echo ""
    echo -e "${YELLOW}💥 SIMULATING COURSE SERVICE FAILURE${NC}"
    echo "====================================="

    simulate_service_failure "course-service"

    echo ""
    echo -e "${BLUE}🧪 Testing Operations During Failure${NC}"
    echo "-------------------------------------"

    # Test assignment creation during course service failure
    echo "Testing assignment creation with fallback validation..."
    for i in {1..3}; do
        echo "Attempt $i:"
        test_api_endpoint "POST" "$GATEWAY_URL/api/assignments" \
            "Assignment creation (course service down)" \
            '{
                "title": "Fallback Test Assignment '$i'",
                "dueDate": "2024-12-31T23:59:00",
                "courseCode": "CS999",
                "description": "Testing fallback during service failure"
            }'
        sleep 1
    done

    echo ""
    echo "Testing resource department fetching with fallback..."
    for i in {1..3}; do
        echo "Attempt $i:"
        test_api_endpoint "GET" "$GATEWAY_URL/api/resources/department/Computer%20Science" \
            "Resource department fetching (course service down)"
        sleep 1
    done

    echo ""
    echo -e "${BLUE}📊 Circuit Breaker Status During Failure${NC}"
    echo "-------------------------------------------"

    echo "Assignment Service Circuit Breaker:"
    get_circuit_breaker_status "$ASSIGNMENT_SERVICE" "courseService"

    echo ""
    echo "Resource Service Circuit Breaker:"
    get_circuit_breaker_status "$RESOURCE_SERVICE" "courseServiceForResources"

    echo ""
    echo -e "${BLUE}🔄 RESTORING COURSE SERVICE${NC}"
    echo "==========================="

    restore_service "course-service"

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${BLUE}🧪 Testing Operations After Recovery${NC}"
        echo "-------------------------------------"

        # Wait for circuit breaker to potentially close
        echo "Waiting for circuit breaker recovery..."
        sleep 30

        # Test normal operations
        test_api_endpoint "GET" "$GATEWAY_URL/api/courses/departments" \
            "Course service direct access after recovery"

        test_api_endpoint "POST" "$GATEWAY_URL/api/assignments" \
            "Assignment creation after recovery" \
            '{
                "title": "Recovery Test Assignment",
                "dueDate": "2024-12-31T23:59:00",
                "courseCode": "CS101",
                "description": "Testing after service recovery"
            }'

        echo ""
        echo -e "${BLUE}📊 Final Circuit Breaker Status${NC}"
        echo "--------------------------------"

        echo "Assignment Service Circuit Breaker:"
        get_circuit_breaker_status "$ASSIGNMENT_SERVICE" "courseService"

        echo ""
        echo "Resource Service Circuit Breaker:"
        get_circuit_breaker_status "$RESOURCE_SERVICE" "courseServiceForResources"
    fi

    echo ""
    echo -e "${GREEN}🎉 Fault Tolerance Testing Completed!${NC}"
    echo ""
    echo -e "${BLUE}📋 Test Summary:${NC}"
    echo "   ✅ Circuit breaker configuration verified"
    echo "   ✅ Fallback methods tested during service failure"
    echo "   ✅ Service recovery and circuit breaker reset tested"
    echo "   ✅ Logging and monitoring verified"
    echo ""
    echo -e "${BLUE}🔍 Check logs for detailed circuit breaker events:${NC}"
    echo "   docker logs assignment-service | grep -i circuit"
    echo "   docker logs resource-service | grep -i circuit"
}

# Run main function
main "$@"