import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

/**
 * GradingWorker evaluates one student enrollment assessment concurrently.
 * Runs inside an ExecutorService fixed thread pool, enforcing track-specific rules,
 * phased entry checks, minimum key-component thresholds, and badge assignments.
 */
public class GradingWorker implements Callable<GradingWorker.GradingResult> {

    private static String logFilePath = "logs/execution_proofs.log";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOG_LOCK = new Object();

    public static void setLogFilePath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            logFilePath = path.trim();
        }
    }

    public static String getLogFilePath() {
        if (DBConnection.getConfigPath() != null && DBConnection.getConfigPath().contains("remote") 
                && "logs/execution_proofs.log".equals(logFilePath)) {
            return "logs/execution_proofs_remote.log";
        }
        return logFilePath;
    }

    // Track types
    public static final String TRACK_INDUSTRY = "INDUSTRY";
    public static final String TRACK_SKILL = "SKILL";
    public static final String TRACK_SOFT = "SOFT";

    // Badges
    public static final String BADGE_GOLD = "GOLD";
    public static final String BADGE_SILVER = "SILVER";
    public static final String BADGE_COMPLETED = "COMPLETED";
    public static final String BADGE_FAILED = "FAILED";
    public static final String BADGE_INCOMPLETE = "INCOMPLETE";
    public static final String BADGE_PENDING = "PENDING";
    public static final String BADGE_NONE = "NONE";

    // Statuses
    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_WITHHELD = "WITHHELD";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";
    public static final String STATUS_PENDING = "PENDING";

    private final VacDAO.AssessmentData assessment;
    private final VacDAO dao;

    public GradingWorker(VacDAO.AssessmentData assessment, VacDAO dao) {
        this.assessment = assessment;
        this.dao = dao;
    }

    /**
     * Value class representing the outcome of the grading evaluation.
     */
    public static class GradingResult {
        private final String studentId;
        private final String studentName;
        private final String courseCode;
        private final String courseName;
        private final String trackType;
        private final int credits;
        private final double attendance;
        private final double weightedScore;
        private final String badge;
        private final String status;
        private final String remarks;
        private final boolean eligibleForCertificate;
        private final String threadName;
        private final String timestamp;

        public GradingResult(String studentId, String studentName, String courseCode, String courseName,
                             String trackType, int credits, double attendance, double weightedScore,
                             String badge, String status, String remarks, boolean eligibleForCertificate,
                             String threadName, String timestamp) {
            this.studentId = studentId;
            this.studentName = studentName;
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.trackType = trackType;
            this.credits = credits;
            this.attendance = attendance;
            this.weightedScore = weightedScore;
            this.badge = badge;
            this.status = status;
            this.remarks = remarks;
            this.eligibleForCertificate = eligibleForCertificate;
            this.threadName = threadName;
            this.timestamp = timestamp;
        }

        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getCourseCode() { return courseCode; }
        public String getCourseName() { return courseName; }
        public String getTrackType() { return trackType; }
        public int getCredits() { return credits; }
        public double getAttendance() { return attendance; }
        public double getWeightedScore() { return weightedScore; }
        public String getBadge() { return badge; }
        public String getStatus() { return status; }
        public String getRemarks() { return remarks; }
        public boolean isEligibleForCertificate() { return eligibleForCertificate; }
        public String getThreadName() { return threadName; }
        public String getTimestamp() { return timestamp; }
    }

    @Override
    public GradingResult call() {
        String threadName = Thread.currentThread().getName();
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);

        String studentId = assessment.getStudentId();
        String studentName = assessment.getStudentName();
        String courseCode = assessment.getCourseCode();
        String courseName = assessment.getCourseName();
        String track = assessment.getTrackType().toUpperCase();
        int credits = assessment.getCredits();
        double attendance = assessment.getAttendancePercent();

        Double p1 = assessment.getPhase1Score();
        Double p2 = assessment.getPhase2Score();
        Double p3 = assessment.getPhase3Score();
        Double quiz = assessment.getQuizScore();

        // ----------------------------------------------------------------------
        // Phased Entry Validation Check
        // ----------------------------------------------------------------------
        boolean phasedDataComplete = true;
        String missingPhaseReason = "";

        if (TRACK_INDUSTRY.equals(track)) {
            // Requires Project (P2), Exam (P3), Quiz
            if (p2 == null || p3 == null || quiz == null) {
                phasedDataComplete = false;
                missingPhaseReason = String.format("Missing required phases for INDUSTRY track (P2=%s, P3=%s, Quiz=%s)",
                        p2, p3, quiz);
            }
        } else if (TRACK_SKILL.equals(track)) {
            // Requires Labs (P1), Project (P2)
            if (p1 == null || p2 == null) {
                phasedDataComplete = false;
                missingPhaseReason = String.format("Missing required phases for SKILL track (P1=%s, P2=%s)", p1, p2);
            }
        } else if (TRACK_SOFT.equals(track)) {
            // Requires CA (P1), Presentation (P3)
            if (p1 == null || p3 == null) {
                phasedDataComplete = false;
                missingPhaseReason = String.format("Missing required phases for SOFT track (P1=%s, P3=%s)", p1, p3);
            }
        }

        if (!phasedDataComplete) {
            String logMsg = String.format("[%s] [%-20s] Student: %-9s Course: %-8s Track: %-8s Score: %7s Badge: %-10s Status: %-10s | SKIPPED: %s",
                    timestamp, threadName, studentId, courseCode, track, "N/A", BADGE_PENDING, STATUS_PENDING, missingPhaseReason);
            logProof(logMsg);

            GradingResult skippedResult = new GradingResult(
                    studentId, studentName, courseCode, courseName, track, credits, attendance,
                    0.0, BADGE_PENDING, STATUS_PENDING, missingPhaseReason, false, threadName, timestamp
            );
            saveToDb(skippedResult);
            return skippedResult;
        }

        // ----------------------------------------------------------------------
        // Calculate Track Weighted Total & Key-Component Minimums
        // ----------------------------------------------------------------------
        double weightedScore = 0.0;
        double keyComponentScore = 0.0;
        double passTotal = 0.0;
        String keyComponentName = "";

        if (TRACK_INDUSTRY.equals(track)) {
            // Project 50% + Final Exam 30% + Quizzes 20%
            // Exam minimum 50%. Pass total 60%.
            weightedScore = (p2 * 0.50) + (p3 * 0.30) + (quiz * 0.20);
            keyComponentScore = p3;
            keyComponentName = "Final Exam (P3)";
            passTotal = 60.0;
        } else if (TRACK_SKILL.equals(track)) {
            // Hands-on Labs 60% + Attendance 20% + Project 20%
            // Lab minimum 50%. Pass total 55%.
            weightedScore = (p1 * 0.60) + (attendance * 0.20) + (p2 * 0.20);
            keyComponentScore = p1;
            keyComponentName = "Hands-on Labs (P1)";
            passTotal = 55.0;
        } else if (TRACK_SOFT.equals(track)) {
            // Continuous Assessment 70% + Presentation 30%
            // Presentation minimum 50%. Pass total 50%.
            weightedScore = (p1 * 0.70) + (p3 * 0.30);
            keyComponentScore = p3;
            keyComponentName = "Presentation (P3)";
            passTotal = 50.0;
        }

        // Round to 2 decimal places
        weightedScore = Math.round(weightedScore * 100.0) / 100.0;

        // ----------------------------------------------------------------------
        // Outcome Logic Hierarchy
        // ----------------------------------------------------------------------
        String badge = BADGE_FAILED;
        String status;
        String remarks;
        boolean eligibleForCert = false;

        // Rule 1: Attendance < 80% -> INCOMPLETE, certificate withheld
        if (attendance < 80.0) {
            status = STATUS_INCOMPLETE;
            badge = BADGE_INCOMPLETE;
            remarks = String.format("Attendance %.2f%% < 80.0%% requirement -> Certificate Withheld", attendance);
        }
        // Rule 2: Key-component minimum not met (< 50%) -> FAILED, even if weighted total passes
        else if (keyComponentScore < 50.0) {
            status = STATUS_FAILED;
            badge = BADGE_FAILED;
            remarks = String.format("Key component '%s' score %.2f < 50.0%% minimum (Total: %.2f%%)",
                    keyComponentName, keyComponentScore, weightedScore);
        }
        // Rule 3: Weighted total < pass total -> FAILED
        else if (weightedScore < passTotal) {
            status = STATUS_FAILED;
            badge = BADGE_FAILED;
            remarks = String.format("Weighted total %.2f%% < %.2f%% pass mark", weightedScore, passTotal);
        }
        // Rule 4: Successful completion -> Badge by score
        else {
            eligibleForCert = true;
            status = STATUS_ISSUED;
            if (weightedScore >= 90.0) {
                badge = BADGE_GOLD;
            } else if (weightedScore >= 75.0) {
                badge = BADGE_SILVER;
            } else {
                badge = BADGE_COMPLETED;
            }
            remarks = String.format("Passed successfully with %s badge", badge);
        }

        GradingResult result = new GradingResult(
                studentId, studentName, courseCode, courseName, track, credits, attendance,
                weightedScore, badge, status, remarks, eligibleForCert, threadName, timestamp
        );

        // Logging Proof of Concurrency
        String proofLine = String.format("[%s] [%-20s] Student: %-9s Course: %-8s Track: %-8s Score: %6.2f%% Badge: %-10s Status: %-10s | %s",
                timestamp, threadName, studentId, courseCode, track, weightedScore, badge, status, remarks);
        logProof(proofLine);

        // Persist to database
        saveToDb(result);

        return result;
    }

    private void saveToDb(GradingResult result) {
        if (dao != null) {
            try {
                dao.saveCertificateRecord(
                        result.getStudentId(),
                        result.getCourseCode(),
                        result.getWeightedScore(),
                        result.getBadge(),
                        result.getStatus(),
                        null // File path populated asynchronously upon certificate generation
                );
            } catch (SQLException e) {
                System.err.println("[GradingWorker] DB save error for " + result.getStudentId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Synchronized thread-safe logging to console and execution proofs log file.
     */
    public static void logProof(String message) {
        synchronized (LOG_LOCK) {
            System.out.println(message);
            String targetPath = getLogFilePath();
            File logFile = new File(targetPath);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(message);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("[GradingWorker] Error writing to " + targetPath + ": " + e.getMessage());
            }
        }
    }
}
