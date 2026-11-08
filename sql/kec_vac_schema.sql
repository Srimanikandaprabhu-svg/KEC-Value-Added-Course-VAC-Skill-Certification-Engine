-- ==============================================================================
-- KEC VAC & Skill Certification Engine Database Schema
-- Target: MySQL 8.0+
-- Database: kec_vac
-- ==============================================================================

DROP DATABASE IF EXISTS kec_vac;
CREATE DATABASE kec_vac CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kec_vac;

-- ------------------------------------------------------------------------------
-- 1. Table: vac_courses
-- Tracks: INDUSTRY, SKILL, SOFT
-- ------------------------------------------------------------------------------
CREATE TABLE vac_courses (
    course_code VARCHAR(10) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    track_type VARCHAR(20) NOT NULL,
    credits INT NOT NULL,
    CONSTRAINT pk_vac_courses PRIMARY KEY (course_code),
    CONSTRAINT chk_course_code_format CHECK (course_code REGEXP '^VAC-[0-9]{3}$'),
    CONSTRAINT chk_track_type CHECK (track_type IN ('INDUSTRY', 'SKILL', 'SOFT')),
    CONSTRAINT chk_credits CHECK (credits BETWEEN 1 AND 4)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 2. Table: enrolled_students
-- Enrolled student records with course association and attendance tracking
-- ------------------------------------------------------------------------------
CREATE TABLE enrolled_students (
    student_id VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    course_code VARCHAR(10) NOT NULL,
    attendance_percent DECIMAL(5,2) NOT NULL,
    CONSTRAINT pk_enrolled_students PRIMARY KEY (student_id, course_code),
    CONSTRAINT fk_student_course FOREIGN KEY (course_code) REFERENCES vac_courses(course_code) ON DELETE CASCADE,
    CONSTRAINT chk_attendance CHECK (attendance_percent BETWEEN 0.00 AND 100.00)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 3. Table: assessments
-- Phased evaluations: Phase 1 (Labs/CA), Phase 2 (Project), Phase 3 (Exam/Pres), Quiz
-- ------------------------------------------------------------------------------
CREATE TABLE assessments (
    assessment_id INT AUTO_INCREMENT,
    student_id VARCHAR(20) NOT NULL,
    course_code VARCHAR(10) NOT NULL,
    phase1_score DECIMAL(5,2) NULL,
    phase2_score DECIMAL(5,2) NULL,
    phase3_score DECIMAL(5,2) NULL,
    quiz_score DECIMAL(5,2) NULL,
    CONSTRAINT pk_assessments PRIMARY KEY (assessment_id),
    CONSTRAINT uq_student_course_assessment UNIQUE (student_id, course_code),
    CONSTRAINT fk_assessment_student FOREIGN KEY (student_id, course_code) REFERENCES enrolled_students(student_id, course_code) ON DELETE CASCADE,
    CONSTRAINT chk_phase1 CHECK (phase1_score IS NULL OR (phase1_score BETWEEN 0.00 AND 100.00)),
    CONSTRAINT chk_phase2 CHECK (phase2_score IS NULL OR (phase2_score BETWEEN 0.00 AND 100.00)),
    CONSTRAINT chk_phase3 CHECK (phase3_score IS NULL OR (phase3_score BETWEEN 0.00 AND 100.00)),
    CONSTRAINT chk_quiz CHECK (quiz_score IS NULL OR (quiz_score BETWEEN 0.00 AND 100.00))
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 4. Table: certificates
-- Issued credentials with badge, status, and generated file tracking
-- ------------------------------------------------------------------------------
CREATE TABLE certificates (
    certificate_id INT AUTO_INCREMENT,
    student_id VARCHAR(20) NOT NULL,
    course_code VARCHAR(10) NOT NULL,
    final_score DECIMAL(5,2) NOT NULL,
    badge VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    file_path VARCHAR(255) NULL,
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_certificates PRIMARY KEY (certificate_id),
    CONSTRAINT fk_cert_student FOREIGN KEY (student_id, course_code) REFERENCES enrolled_students(student_id, course_code) ON DELETE CASCADE,
    CONSTRAINT chk_cert_badge CHECK (badge IN ('GOLD', 'SILVER', 'COMPLETED', 'FAILED', 'INCOMPLETE', 'PENDING', 'NONE')),
    CONSTRAINT chk_cert_status CHECK (status IN ('ISSUED', 'WITHHELD', 'FAILED', 'INCOMPLETE', 'PENDING'))
) ENGINE=InnoDB;

-- ==============================================================================
-- SEED DATA
-- 3 Courses (One per Track) and 10 Sample Students covering all grading scenarios
-- ==============================================================================

-- 3 Courses (INDUSTRY, SKILL, SOFT)
INSERT INTO vac_courses (course_code, course_name, track_type, credits) VALUES
('VAC-101', 'Cloud Infrastructure & DevOps Engineering', 'INDUSTRY', 3),
('VAC-102', 'Full Stack Modern Web Application Dev', 'SKILL', 2),
('VAC-103', 'Corporate Communication & Leadership', 'SOFT', 1);

-- 10 Sample Students (with varied attendance and enrollments)
INSERT INTO enrolled_students (student_id, name, email, course_code, attendance_percent) VALUES
('25ITR001', 'KAVIN RAJ S', 'kavinraj@kec.ac.in', 'VAC-101', 95.00),
('25ITR002', 'PRIYA DHARSHINI M', 'priyadharshini@kec.ac.in', 'VAC-102', 90.00),
('25ITR003', 'ARUN KUMAR T', 'arunkumar@kec.ac.in', 'VAC-101', 88.00),
('25ITR004', 'SNEHA REDDY K', 'snehareddy@kec.ac.in', 'VAC-103', 92.00),
('25ITR005', 'VIKRAM CHANDRAN R', 'vikram@kec.ac.in', 'VAC-101', 72.00), -- Attendance < 80% (INCOMPLETE)
('25ITR006', 'DEEPAK BALAJI N', 'deepak@kec.ac.in', 'VAC-101', 94.00), -- High Total, Low Exam (FAILED min threshold)
('25ITR007', 'ANANYA IYER S', 'ananyaiyer@kec.ac.in', 'VAC-103', 96.00),
('25ITR008', 'MANIKANDAN P', 'manikandan@kec.ac.in', 'VAC-102', 85.00),
('25ITR009', 'DIVYA BHARATHI G', 'divyabharathi@kec.ac.in', 'VAC-102', 86.00), -- High Total, Low Lab (FAILED min threshold)
('25ITR010', 'HARISH KUMAR V', 'harish@kec.ac.in', 'VAC-101', 84.00), -- Missing Phase 3 (Skipped in grading)
('25ITR001', 'KAVIN RAJ S', 'kavinraj@kec.ac.in', 'VAC-103', 98.00); -- Dual enrollment for Index calculation

-- Assessments initial seed (Phase 1, Phase 2, Phase 3).
-- Note: Quiz scores will be populated/updated dynamically from data/quiz_marks.txt!
INSERT INTO assessments (student_id, course_code, phase1_score, phase2_score, phase3_score, quiz_score) VALUES
-- Student 1: Industry Track -> GOLD candidate (Project=94, Exam=92, Quiz to be imported=90)
('25ITR001', 'VAC-101', 90.00, 94.00, 92.00, 90.00),

-- Student 2: Skill Track -> SILVER candidate (Labs=85, Project=80, Attendance=90%)
('25ITR002', 'VAC-102', 85.00, 80.00, NULL, NULL),

-- Student 3: Industry Track -> COMPLETED candidate (Project=68, Exam=65, Quiz to be imported=70)
('25ITR003', 'VAC-101', 75.00, 68.00, 65.00, 70.00),

-- Student 4: Soft Skills Track -> FAILED candidate (CA=45, Presentation=48 < 50% min)
('25ITR004', 'VAC-103', 45.00, NULL, 48.00, NULL),

-- Student 5: Industry Track -> INCOMPLETE candidate (Attendance 72% < 80%)
('25ITR005', 'VAC-101', 85.00, 90.00, 88.00, 85.00),

-- Student 6: Industry Track -> High Total (79.5%) BUT Final Exam=45 (< 50% minimum threshold) -> FAILED
('25ITR006', 'VAC-101', 88.00, 96.00, 45.00, 90.00),

-- Student 7: Soft Skills Track -> GOLD candidate (CA=95, Presentation=92)
('25ITR007', 'VAC-103', 95.00, NULL, 92.00, NULL),

-- Student 8: Skill Track -> COMPLETED candidate (Labs=72, Project=70, Attendance=85%)
('25ITR008', 'VAC-102', 72.00, 70.00, NULL, NULL),

-- Student 9: Skill Track -> High Total (61%) BUT Lab=45 (< 50% minimum threshold) -> FAILED
('25ITR009', 'VAC-102', 45.00, 85.00, NULL, NULL),

-- Student 10: Industry Track -> Incomplete Phased Data (Phase 3 is NULL, skipped by worker)
('25ITR010', 'VAC-101', 80.00, 78.00, NULL, NULL),

-- Dual Enrollment for Student 1 in VAC-103: Soft Skills (CA=92, Presentation=90) -> GOLD
('25ITR001', 'VAC-103', 92.00, NULL, 90.00, NULL);
