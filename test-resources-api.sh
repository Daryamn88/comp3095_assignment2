# Get all resources
curl -X GET http://localhost:8083/api/resources

# Get resources by category
curl -X GET http://localhost:8083/api/resources/category/LIBRARY

# Create new resource
curl -X POST http://localhost:8083/api/resources \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Online Learning Platform",
    "url": "https://learning.university.edu",
    "category": "ACADEMIC_SUPPORT",
    "description": "Access to online courses and materials"
  }'