-- Sample data for courses
INSERT INTO courses (course_code, title, description, department, credits, created_at, updated_at)
VALUES ('CS101', 'Introduction to Programming', 'Basic programming concepts using Java', 'Computer Science', 3, NOW(),
        NOW()),
       ('CS201', 'Data Structures', 'Advanced data structures and algorithms', 'Computer Science', 4, NOW(), NOW()),
       ('MATH101', 'Calculus I', 'Differential and integral calculus', 'Mathematics', 4, NOW(), NOW()),
       ('MATH201', 'Linear Algebra', 'Matrices, vectors, and linear transformations', 'Mathematics', 3, NOW(), NOW()),
       ('ENG101', 'English Composition', 'Academic writing and critical thinking', 'English', 3, NOW(), NOW()),
       ('HIST101', 'World History', 'Survey of world civilizations', 'History', 3, NOW(), NOW()),
       ('PHYS101', 'General Physics I', 'Mechanics and thermodynamics', 'Physics', 4, NOW(), NOW()),
       ('CHEM101', 'General Chemistry', 'Atomic structure and chemical bonding', 'Chemistry', 4, NOW(), NOW());
