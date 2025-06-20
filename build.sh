#!/bin/bash

echo "Building Academic Planner Platform - Phase 2..."

# Function to build service
build_service() {
    local service_name=$1
    local service_dir=$2

    echo "📦 Building $service_name..."

    if [ ! -d "$service_dir" ]; then
        echo "❌ $service_dir directory not found!"
        return 1
    fi

    cd "$service_dir"
    mvn clean

    if [ $? -eq 0 ]; then
        echo "✅ $service_name built successfully"
    else
        echo "❌ Failed to build $service_name"
        return 1
    fi

    cd ..
    return 0
}

# Build all services
build_service "Eureka Server" "eureka-server"
build_service "API Gateway" "api-gateway"
build_service "Course Service" "course-service"
build_service "Assignment Service" "assignment-service"
build_service "Resource Service" "resource-service"

echo ""
echo "🎉 All services built successfully!"
echo "Now you can run: docker-compose up --build -d"