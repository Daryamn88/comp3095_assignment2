# Get all assignments
curl -X GET http://localhost:8082/api/assignments

# Get assignments by course
curl -X GET http://localhost:8082/api/assignments/course/CS101

# Create new assignment
curl -X POST http://localhost:8082/api/assignments \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Programming Project 1",
    "dueDate": "2024-12-30T23:59:00",
    "courseCode": "CS101",
    "description": "Build a simple calculator application"
  }'

# Mark assignment as completed
curl -X PATCH http://localhost:8082/api/assignments/{id}/complete