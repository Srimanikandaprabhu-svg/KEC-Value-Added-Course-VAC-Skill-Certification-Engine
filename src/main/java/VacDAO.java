import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) for KEC Value-Added Courses and Skill Certification Engine.
 * Implements 100% parameterized PreparedStatements to prevent SQL injection.
 */
public class VacDAO {

    // --------------------------------------------------------------------------
    // Data Transfer Objects (DTOs)
    // --------------------------------------------------------------------------

    public static class Course {
        private final String courseCode;
        private final String courseName;
        private final String trackType;
        private final int credits;

        public Course(String courseCode, String courseName, String trackType, int credits) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.trackType = trackType;
            this.credits = credits;
        }

        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public String getTrackType() { return trackType; }
        public int getCredits() { return credits; }

        @Override
        public String toString() {
            return String.format("[%s] %s | Track: %s | Credits: %d", courseCode, courseName, trackType, credits);
        }
    }

    public static class StudentEnrollment {
        private final String studentId;
        private final String name;
        private final String email;
        private final String courseCode;
        private final double attendancePercent;

        public StudentEnrollment(String studentId, String name, String email, String courseCode, double attendancePercent) {
            this.studentId = studentId;
            this.name = name;
            this.email = email;
            this.courseCode = courseCode;
            this.attendancePercent = attendancePercent;
        }

        public String getStudentId() { return studentId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getCourseCode() { return courseCode; }
        public double getAttendancePercent() { return attendancePercent; }
    }

    public static class AssessmentData {
        private final int assessmentId;
        private final String studentId;
        private final String studentName;
        private final String courseCode;
        private final String courseName;
        private final String trackType;
        private final int credits;
        private final double attendancePercent;
        private final Double phase1Score;
        private final Double phase2Score;
        private final Double phase3Score;
        private final Double quizScore;

        public AssessmentData(int assessmentId, String studentId, String studentName, String courseCode,
                              String courseName, String trackType, int credits, double attendancePercent,
                              Double phase1Score, Double phase2Score, Double phase3Score, Double quizScore) {
            this.assessmentId = assessmentId;
            this.studentId = studentId;
            this.studentName = studentName;
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.trackType = trackType;
            this.credits = credits;
            this.attendancePercent = attendancePercent;
            this.phase1Score = phase1Score;
            this.phase2Score = phase2Score;
            this.phase3Score = phase3Score;
            this.quizScore = quizScore;
        }

        public int getAssessmentId() { return assessmentId; }
        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public String getTrackType() { return trackType; }
        public int getCredits() { return credits; }
        public double getAttendancePercent() { return attendancePercent; }
        public Double getPhase1Score() { return phase1Score; }
        public Double getPhase2Score() { return phase2Score; }
        public Double getPhase3Score() { return phase3Score; }
        public Double getQuizScore() { return quizScore; }
    }

    public static class StudentIndexItem {
        private final String studentId;
        private final String courseCode;
        private final int credits;
        private final String badge;
        private final String status;

        public StudentIndexItem(String studentId, String courseCode, int credits, String badge, String status) {
            this.studentId = studentId;
            this.courseCode = courseCode;
            this.credits = credits;
            this.badge = badge;
            this.status = status;
        }

        public String getStudentId() { return studentId; }
        public String getCourseCode() { return courseCode; }
        public int getCredits() { return credits; }
        public String getBadge() { return badge; }
        public String getStatus() { return status; }
    }

    // --------------------------------------------------------------------------
    // DAO Operations
    // --------------------------------------------------------------------------

    /**
     * Fetches all registered VAC courses.
     *
     * @return List of Course objects
     * @throws SQLException on database error
     */
    public List<Course> getAllCourses() throws SQLException {
        String sql = "SELECT course_code, course_name, track_type, credits FROM vac_courses ORDER BY course_code";
        List<Course> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new Course(
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getString("track_type"),
                        rs.getInt("credits")
                ));
            }
        }
        return list;
    }

    /**
     * Fetches all enrolled student records with cleaned name uppercase.
     *
     * @return List of StudentEnrollment
     * @throws SQLException on database error
     */
    public List<StudentEnrollment> getEnrolledStudents() throws SQLException {
        String sql = "SELECT student_id, name, email, course_code, attendance_percent FROM enrolled_students ORDER BY student_id, course_code";
        List<StudentEnrollment> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String rawName = rs.getString("name");
                String cleanName = rawName != null ? rawName.trim().toUpperCase() : "";
                list.add(new StudentEnrollment(
                        rs.getString("student_id"),
                        cleanName,
                        rs.getString("email"),
                        rs.getString("course_code"),
                        rs.getDouble("attendance_percent")
                ));
            }
        }
        return list;
    }

    /**
     * Updates or sets quiz score for an assessment record.
     *
     * @param studentId Student ID
     * @param courseCode Course Code
     * @param quizScore Quiz Score (0-100)
     * @return number of affected rows
     * @throws SQLException on database error
     */
    public int updateQuizScore(String studentId, String courseCode, double quizScore) throws SQLException {
        String sql = "UPDATE assessments SET quiz_score = ? WHERE student_id = ? AND course_code = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, quizScore);
            stmt.setString(2, studentId);
            stmt.setString(3, courseCode);

            return stmt.executeUpdate();
        }
    }

    /**
     * Saves or updates phased evaluation scores for a student.
     *
     * @param studentId Student ID
     * @param courseCode Course Code
     * @param phase1 Phase 1 score (or null)
     * @param phase2 Phase 2 score (or null)
     * @param phase3 Phase 3 score (or null)
     * @return number of affected rows
     * @throws SQLException on database error
     */
    public int saveOrUpdatePhaseScores(String studentId, String courseCode, Double phase1, Double phase2, Double phase3) throws SQLException {
        String sql = "INSERT INTO assessments (student_id, course_code, phase1_score, phase2_score, phase3_score) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "phase1_score = VALUES(phase1_score), " +
                     "phase2_score = VALUES(phase2_score), " +
                     "phase3_score = VALUES(phase3_score)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, studentId);
            stmt.setString(2, courseCode);

            if (phase1 != null) stmt.setDouble(3, phase1); else stmt.setNull(3, Types.DECIMAL);
            if (phase2 != null) stmt.setDouble(4, phase2); else stmt.setNull(4, Types.DECIMAL);
            if (phase3 != null) stmt.setDouble(5, phase3); else stmt.setNull(5, Types.DECIMAL);

            return stmt.executeUpdate();
        }
    }

    /**
     * Fetches comprehensive assessment dataset joined with student and course metadata.
     *
     * @return List of AssessmentData objects
     * @throws SQLException on database error
     */
    public List<AssessmentData> getAllAssessments() throws SQLException {
        String sql = "SELECT a.assessment_id, a.student_id, s.name, a.course_code, c.course_name, c.track_type, " +
                     "       c.credits, s.attendance_percent, a.phase1_score, a.phase2_score, a.phase3_score, a.quiz_score " +
                     "FROM assessments a " +
                     "JOIN enrolled_students s ON a.student_id = s.student_id AND a.course_code = s.course_code " +
                     "JOIN vac_courses c ON a.course_code = c.course_code " +
                     "ORDER BY a.student_id, a.course_code";

        List<AssessmentData> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Double p1 = rs.getObject("phase1_score") != null ? rs.getDouble("phase1_score") : null;
                Double p2 = rs.getObject("phase2_score") != null ? rs.getDouble("phase2_score") : null;
                Double p3 = rs.getObject("phase3_score") != null ? rs.getDouble("phase3_score") : null;
                Double qz = rs.getObject("quiz_score") != null ? rs.getDouble("quiz_score") : null;

                String rawName = rs.getString("name");
                String cleanName = rawName != null ? rawName.trim().toUpperCase() : "";

                list.add(new AssessmentData(
                        rs.getInt("assessment_id"),
                        rs.getString("student_id"),
                        cleanName,
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        rs.getString("track_type"),
                        rs.getInt("credits"),
                        rs.getDouble("attendance_percent"),
                        p1, p2, p3, qz
                ));
            }
        }
        return list;
    }

    /**
     * Records or updates a certificate entry in the database.
     *
     * @param studentId Student ID
     * @param courseCode Course Code
     * @param finalScore Final computed weighted score
     * @param badge Badge awarded (GOLD, SILVER, COMPLETED, NONE)
     * @param status Status (ISSUED, WITHHELD, FAILED, INCOMPLETE)
     * @param filePath File path of generated certificate (or null)
     * @return Generated certificate ID
     * @throws SQLException on database error
     */
    public int saveCertificateRecord(String studentId, String courseCode, double finalScore,
                                    String badge, String status, String filePath) throws SQLException {
        // Remove prior certificate for student/course if re-running
        String deleteSql = "DELETE FROM certificates WHERE student_id = ? AND course_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement delStmt = conn.prepareStatement(deleteSql)) {
            delStmt.setString(1, studentId);
            delStmt.setString(2, courseCode);
            delStmt.executeUpdate();
        }

        String insertSql = "INSERT INTO certificates (student_id, course_code, final_score, badge, status, file_path) " +
                           "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, studentId);
            stmt.setString(2, courseCode);
            stmt.setDouble(3, finalScore);
            stmt.setString(4, badge);
            stmt.setString(5, status);
            if (filePath != null) {
                stmt.setString(6, filePath);
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Updates the file path of an issued certificate.
     *
     * @param studentId Student ID
     * @param courseCode Course Code
     * @param filePath File path
     * @throws SQLException on database error
     */
    public void updateCertificateFilePath(String studentId, String courseCode, String filePath) throws SQLException {
        String sql = "UPDATE certificates SET file_path = ?, status = 'ISSUED' WHERE student_id = ? AND course_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filePath);
            stmt.setString(2, studentId);
            stmt.setString(3, courseCode);
            stmt.executeUpdate();
        }
    }

    /**
     * Fetches all enrolled VAC course items per student to compute the VAC Achievement Index.
     *
     * @return Map of Student ID -> List of StudentIndexItem
     * @throws SQLException on database error
     */
    public Map<String, List<StudentIndexItem>> getStudentEnrollmentsForIndex() throws SQLException {
        String sql = "SELECT s.student_id, s.course_code, c.credits, " +
                     "       COALESCE(cert.badge, 'PENDING') AS badge, " +
                     "       COALESCE(cert.status, 'PENDING') AS status " +
                     "FROM enrolled_students s " +
                     "JOIN vac_courses c ON s.course_code = c.course_code " +
                     "LEFT JOIN certificates cert ON s.student_id = cert.student_id AND s.course_code = cert.course_code " +
                     "ORDER BY s.student_id, s.course_code";

        Map<String, List<StudentIndexItem>> map = new HashMap<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String sId = rs.getString("student_id");
                StudentIndexItem item = new StudentIndexItem(
                        sId,
                        rs.getString("course_code"),
                        rs.getInt("credits"),
                        rs.getString("badge"),
                        rs.getString("status")
                );
                map.computeIfAbsent(sId, k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }
}
