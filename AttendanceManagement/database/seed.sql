-- ============================================
-- SEED DATA - Sample records for testing
-- Run AFTER schema.sql
-- ============================================
USE attendance_db;

-- Default users (login password: admin123, stored as PBKDF2 hashes)
INSERT INTO users (username, password, email, role) VALUES
('admin',    'pbkdf2$65536$m76hGTFTx66WsKROwpxGHg==$SVGUqGwyeYiIaLLilsAxcQRcb0wiS9LP5CP1/R7NiIY=',   'admin@school.edu',    'admin'),
('teacher1', 'pbkdf2$65536$HNTUG91MIJBFSr7lVmMwsQ==$YTGfwqZtd6BO1hiCGzxFq+hS5nbx/bT+/Pl/JYporxo=',   'juan@school.edu',     'teacher'),
('student1', 'pbkdf2$65536$NUrRx3nRF6tsAppUNTHOcQ==$3YSDvDNtprR/ZEG1yl+B2mi2bVqqQMycgA+NkwJqcyw=',   'maria@student.edu',   'student');

-- Sample teacher
INSERT INTO teachers (user_id, full_name, email, phone, department, subject) VALUES
(2, 'Juan dela Cruz', 'juan@school.edu', '09171234567', 'Computer Science', 'Programming');

-- Sample student
INSERT INTO students (user_id, student_id, full_name, email, course, year_level, section) VALUES
(3, '2024-0001', 'Maria Santos', 'maria@student.edu', 'BSCS', 2, 'A');

-- Sample subject
INSERT INTO subjects (code, name, course, year_level, section, teacher_id, units) VALUES
('CS101', 'Introduction to Programming', 'BSCS', 2, 'A', 1, 3),
('CS102', 'Data Structures', 'BSCS', 2, 'A', 1, 3),
('MATH101', 'Calculus', 'BSCS', 2, 'A', 1, 3);

-- Sample timetable
INSERT INTO timetable (subject_id, day_of_week, start_time, end_time, room) VALUES
(1, 'Monday', '08:00:00', '09:30:00', 'Room 101'),
(1, 'Wednesday', '08:00:00', '09:30:00', 'Room 101'),
(2, 'Tuesday', '10:00:00', '11:30:00', 'Room 102'),
(3, 'Thursday', '13:00:00', '14:30:00', 'Room 103');

-- Sample enrollment
INSERT INTO enrollments (student_id, subject_id) VALUES (1, 1), (1, 2), (1, 3);

-- Sample attendance
INSERT INTO attendance (student_id, subject_id, date, time_in, status, method, marked_by) VALUES
(1, 1, CURDATE(), '08:05:00', 'present', 'manual', 2),
(1, 2, CURDATE(), NULL, 'absent', 'manual', 2);

-- Sample marks
INSERT INTO marks (student_id, subject_id, exam_type, score, max_score, date) VALUES
(1, 1, 'quiz', 18, 20, CURDATE()),
(1, 1, 'midterm', 85, 100, CURDATE()),
(1, 2, 'quiz', 15, 20, CURDATE());
