#!/bin/bash

# Resilience4j Monitoring Script

echo "📊 Resilience4j Monitoring Dashboard"
echo "===================================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Function to get metrics
get_metrics() {
    local service_name=$1
    local service_port=$2

    echo -e "${BLUE}📈 $service_name Metrics${NC}"
    echo "$(date '+%Y-%m-%d %H:%M:%S')"
    echo "------------------------------"

    # Circuit Breaker metrics
    echo "🔴 Circuit Breakers:"
    curl -s "http://localhost:$service_port/actuator/circuitbreakers" | jq -r '
        .circuitBreakers | to_entries[] |
        "  \(.key): \(.value.state) (Failure Rate: \(.value.metrics.failureRate // "N/A")%)"'

    # Retry metrics
    echo ""
    echo "🔄 Retries:"
    curl -s "http://localhost:$service_port/actuator/retries" | jq -r '
        .retries | to_entries[] |
        "  \(.key): \(.value.metrics.numberOfSuccessfulCallsWithRetryAttempt // 0) successful, \(.value.metrics.numberOfFailedCallsWithRetryAttempt // 0) failed"'

    # Health
    echo ""
    echo "💚 Health:"
    HEALTH=$(curl -s "http://localhost:$service_port/actuator/health" | jq -r '.status')
    echo "  Status: $HEALTH"

    echo ""
}

# Function to monitor continuously
monitor_continuously() {
    while true; do
        clear
        echo "📊 Real-time Resilience4j Monitoring"
        echo "====================================="
        echo "Press Ctrl+C to stop"
        echo ""

        get_metrics "Assignment Service" "8082"
        get_metrics "Resource Service" "8083"

        echo -e "${YELLOW}🔄 Refreshing in 5 seconds...${NC}"
        sleep 5
    done
}

# Function to show detailed circuit breaker info
show_detailed_cb_info() {
    echo -e "${BLUE}🔍 Detailed Circuit Breaker Information${NC}"
    echo "======================================="

    # Get admin token for detailed info
    TOKEN_RESPONSE=$(curl -s -X POST \
        "http://localhost:8180/auth/realms/GBC_Realm/protocol/openid-connect/token" \
        -d "username=admin&password=admin123&grant_type=password&client_id=api-gateway&client_secret=gateway-secret-key")

    ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')

    if [ "$ACCESS_TOKEN" != "null" ] && [ "$ACCESS_TOKEN" != "" ]; then
        echo ""
        echo "Assignment Service Circuit Breaker Details:"
        curl -s -X GET "http://localhost:8082/api/circuit-breaker/status" \
            -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
    fi
}

# Main menu
echo ""
echo "Select monitoring option:"
echo "1. One-time metrics snapshot"
echo "2. Continuous monitoring (refreshes every 5s)"
echo "3. Detailed circuit breaker information"
echo "4. Export metrics to file"
echo ""
echo -n "Choose option (1-4): "
read -r choice

case $choice in
    1)
        get_metrics "Assignment Service" "8082"
        get_metrics "Resource Service" "8083"
        ;;
    2)
        monitor_continuously
        ;;
    3)
        show_detailed_cb_info
        ;;
    4)
        TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
        FILENAME="resilience_metrics_$TIMESTAMP.json"

        echo "📁 Exporting metrics to $FILENAME..."

        {
            echo "{"
            echo "  \"timestamp\": \"$(date -Iseconds)\","
            echo "  \"assignment_service\": {"
            echo "    \"circuitbreakers\": $(curl -s http://localhost:8082/actuator/circuitbreakers),"
            echo "    \"retries\": $(curl -s http://localhost:8082/actuator/retries),"
            echo "    \"health\": $(curl -s http://localhost:8082/actuator/health)"
            echo "  },"
            echo "  \"resource_service\": {"
            echo "    \"circuitbreakers\": $(curl -s http://localhost:8083/actuator/circuitbreakers),"
            echo "    \"retries\": $(curl -s http://localhost:8083/actuator/retries),"
            echo "    \"health\": $(curl -s http://localhost:8083/actuator/health)"
            echo "  }"
            echo "}"
        } > "$FILENAME"

        echo -e "${GREEN}✅ Metrics exported to $FILENAME${NC}"
        ;;
    *)
        echo "Invalid option"
        ;;
esac