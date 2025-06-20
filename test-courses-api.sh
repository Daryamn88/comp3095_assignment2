# Get all courses
curl -X GET http://localhost:8081/api/courses

# Get course by ID
curl -X GET http://localhost:8081/api/courses/1

# Get courses by department
curl -X GET http://localhost:8081/api/courses/department/Computer%20Science

# Search courses
curl -X GET "http://localhost:8081/api/courses/search?keyword=programming"

# Create new course
curl -X POST http://localhost:8081/api/courses \
  -H "Content-Type: application/json" \
  -d '{
    "courseCode": "CS301",
    "title": "Advanced Programming",
    "description": "Advanced programming concepts",
    "department": "Computer Science",
    "credits": 4
  }'