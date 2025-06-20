#!/bin/bash

# Circuit Breaker Interactive Demo Script

ASSIGNMENT_SERVICE="http://localhost:8082"
RESOURCE_SERVICE="http://localhost:8083"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "🎯 Interactive Circuit Breaker Demo"
echo "=================================="

# Get admin token
get_token() {
    TOKEN_RESPONSE=$(curl -s -X POST \
        "http://localhost:8180/auth/realms/GBC_Realm/protocol/openid-connect/token" \
        -d "username=admin&password=admin123&grant_type=password&client_id=api-gateway&client_secret=gateway-secret-key")

    ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')

    if [ "$ACCESS_TOKEN" != "null" ] && [ "$ACCESS_TOKEN" != "" ]; then
        echo -e "${GREEN}✅ Admin token obtained${NC}"
    else
        echo -e "${RED}❌ Failed to get token${NC}"
        exit 1
    fi
}

# Function to show circuit breaker status
show_cb_status() {
    echo ""
    echo -e "${BLUE}📊 Current Circuit Breaker Status${NC}"
    echo "--------------------------------"

    echo "Assignment Service:"
    curl -s -X GET "$ASSIGNMENT_SERVICE/api/circuit-breaker/status/courseService" \
        -H "Authorization: Bearer $ACCESS_TOKEN" | jq '.state, .failureRate, .numberOfFailedCalls, .numberOfSuccessfulCalls'

    echo ""
    echo "Resource Service (check actuator):"
    curl -s -X GET "$RESOURCE_SERVICE/actuator/circuitbreakers" \
        -H "Authorization: Bearer $ACCESS_TOKEN" | jq '.circuitBreakers.courseServiceForResources.state // "Unknown"'
}

# Function to manually control circuit breaker
control_circuit_breaker() {
    local action=$1
    local cb_name="courseService"

    echo -e "${YELLOW}🔧 ${action^}ing circuit breaker: $cb_name${NC}"

    curl -s -X POST "$ASSIGNMENT_SERVICE/api/circuit-breaker/$action/$cb_name" \
        -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
}

# Function to trigger failures
trigger_failures() {
    echo -e "${YELLOW}💥 Triggering failures to open circuit breaker${NC}"

    # Stop course service
    docker stop course-service > /dev/null 2>&1

    echo "Making failing calls..."
    for i in {1..6}; do
        echo -n "Call $i: "

        RESPONSE=$(curl -s -w "%{http_code}" -X POST \
            "http://localhost:8080/api/assignments" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -H "Content-Type: application/json" \
            -d "{\"title\":\"Test $i\",\"dueDate\":\"2024-12-31T23:59:00\",\"courseCode\":\"TEST$i\",\"description\":\"Failure test\"}")

        HTTP_CODE=$(echo "$RESPONSE" | tail -c 4)
        echo "HTTP $HTTP_CODE"

        sleep 2
    done
}

# Function to test recovery
test_recovery() {
    echo -e "${BLUE}🔄 Testing recovery${NC}"

    # Restart course service
    docker start course-service > /dev/null 2>&1

    echo "Waiting for course service to be ready..."
    sleep 20

    echo "Making recovery calls..."
    for i in {1..3}; do
        echo -n "Recovery call $i: "

        RESPONSE=$(curl -s -w "%{http_code}" -X GET \
            "http://localhost:8080/api/courses/departments" \
            -H "Authorization: Bearer $ACCESS_TOKEN")

        HTTP_CODE=$(echo "$RESPONSE" | tail -c 4)
        echo "HTTP $HTTP_CODE"

        sleep 2
    done
}

# Interactive menu
show_menu() {
    echo ""
    echo -e "${BLUE}🎛️ Circuit Breaker Demo Menu${NC}"
    echo "1. Show circuit breaker status"
    echo "2. Manually open circuit breaker"
    echo "3. Manually close circuit breaker"
    echo "4. Reset circuit breaker"
    echo "5. Trigger failures (opens circuit breaker)"
    echo "6. Test recovery scenario"
    echo "7. Show real-time logs"
    echo "8. Exit"
    echo ""
    echo -n "Select option (1-8): "
}

# Main interactive loop
main() {
    get_token

    echo ""
    echo -e "${GREEN}🎯 Circuit Breaker Demo Ready!${NC}"
    echo "This demo allows you to:"
    echo "  • Monitor circuit breaker states"
    echo "  • Manually control circuit breakers"
    echo "  • Trigger failures and test recovery"
    echo "  • View real-time logs"

    while true; do
        show_menu
        read -r choice

        case $choice in
            1)
                show_cb_status
                ;;
            2)
                control_circuit_breaker "open"
                ;;
            3)
                control_circuit_breaker "close"
                ;;
            4)
                control_circuit_breaker "reset"
                ;;
            5)
                trigger_failures
                show_cb_status
                ;;
            6)
                test_recovery
                show_cb_status
                ;;
            7)
                echo -e "${BLUE}📝 Real-time circuit breaker logs (Ctrl+C to stop):${NC}"
                docker logs -f assignment-service | grep -i "circuit\|resilience" &
                LOG_PID=$!
                echo "Press Enter to stop logs..."
                read -r
                kill $LOG_PID 2>/dev/null
                ;;
            8)
                echo -e "${GREEN}👋 Demo completed!${NC}"
                break
                ;;
            *)
                echo -e "${RED}❌ Invalid option. Please select 1-8.${NC}"
                ;;
        esac

        echo ""
        echo "Press Enter to continue..."
        read -r
    done
}

# Run the demo
main "$@"