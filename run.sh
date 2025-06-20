#!/bin/bash

echo "Starting Academic Planner Platform..."

# Make build script executable and run it
chmod +x build.sh
./build.sh

# Start all services with Docker Compose
docker-compose up

echo "All services are starting up..."
echo "Course Service: http://localhost:8081"
echo "Assignment Service: http://localhost:8082"
echo "Resource Service: http://localhost:8083"
echo ""
echo "Check service status with: docker-compose ps"
echo "View logs with: docker-compose logs -f [service-name]"
echo "Stop all services with: docker-compose down"