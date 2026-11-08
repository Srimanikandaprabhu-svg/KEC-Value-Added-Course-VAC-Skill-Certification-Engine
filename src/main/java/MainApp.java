    import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainApp: Central Controller and Console User Interface for the
 * KEC Value-Added Course & Skill Certification Engine.
 */
public class MainApp {

    // Regex pattern requirement: VAC-[0-9]{3}
    public static final String COURSE_CODE_REGEX = "^VAC-[0-9]{3}$";
    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile(COURSE_CODE_REGEX);

    // Badge Points Constants for VAC Achievement Index calculation
    public static final int POINTS_GOLD = 10;
    public static final int POINTS_SILVER = 8;
    public static final int POINTS_COMPLETED = 6;
    public static final int POINTS_FAILED = 0;
    public static final int POINTS_INCOMPLETE = 0;
    public static final int POINTS_PENDING = 0;

    private static final String QUIZ_FILE_PATH = "data/quiz_marks.txt";

    private final VacDAO dao;
    private final CertificateWriter certWriter;
    private List<GradingWorker.GradingResult> lastGradingResults = new ArrayList<>();

    public MainApp() {
        this.dao = new VacDAO();
        this.certWriter = new CertificateWriter(dao);
    }

    public static void main(String[] args) {
        boolean isDemo = false;

        // Parse command line arguments for configuration and demo mode
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--remote".equalsIgnoreCase(arg) || "-r".equalsIgnoreCase(arg)) {
                DBConnection.setConfigPath("config/db-remote.properties");
            } else if ("--local".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                DBConnection.setConfigPath("config/db.properties");
            } else if ("--config".equalsIgnoreCase(arg) && i + 1 < args.length) {
                DBConnection.setConfigPath(args[++i]);
            } else if ("--demo".equalsIgnoreCase(arg) || "-d".equalsIgnoreCase(arg)) {
                isDemo = true;
            }
        }

        MainApp app = new MainApp();

        if (isDemo) {
            app.runFullDemo();
            System.exit(0);
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            printMainMenu();
            System.out.print("Enter your choice (1-9): ");
            String input = "";
            if (scanner.hasNextLine()) {
                input = scanner.nextLine().trim();
            } else {
                // If EOF or non-interactive stream, execute full demo and exit
                System.out.println("Non-interactive mode detected. Executing full automated demo...");
                app.runFullDemo();
                break;
            }

            switch (input) {
                case "1":
                    app.testDatabaseConnection();
                    break;
                case "2":
                    app.runRegexValidationSuite();
                    break;
                case "3":
                    app.importQuizMarks();
                    break;
                case "4":
                    app.runConcurrentGrading();
                    break;
                case "5":
                    app.generateCertificates();
                    break;
                case "6":
                    app.computeAndPrintAchievementIndex();
                    break;
                case "7":
                    app.printSummaryTable();
                    break;
                case "8":
                    app.runFullDemo();
                    break;
                case "9":
                    System.out.println("\nExiting KEC VAC Engine. Thank you!");
                    app.certWriter.shutdown();
                    System.exit(0);
                default:
                    System.out.println("Invalid selection. Please choose a valid option (1-9).");
            }
        }
        app.certWriter.shutdown();
    }

    private static void printMainMenu() {
        System.out.println("\n================================================================================");
        System.out.println("           KONGU ENGINEERING COLLEGE - VAC & SKILL CERTIFICATION ENGINE         ");
        System.out.println("================================================================================");
        System.out.printf("  Active Configuration : %s (Target Host: %s)\n", DBConnection.getConfigPath(), DBConnection.getJdbcUrl().split("\\?")[0]);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  1. Test Database Connection (Remote/Local MySQL Proof)");
        System.out.println("  2. Validate Course Codes with Regex (Pattern/Matcher Proof)");
        System.out.println("  3. Import Raw Quiz Marks from File (data/quiz_marks.txt)");
        System.out.println("  4. Run Multi-Threaded Batch Grading (ExecutorService)");
        System.out.println("  5. Generate Certificates Asynchronously (output/certificates/)");
        System.out.println("  6. Compute & Display VAC Achievement Index per Student");
        System.out.println("  7. Display Full Academic Assessment Summary Table");
        System.out.println("  8. [RUN FULL DEMONSTRATION] Execute Complete Workflow (1 -> 7)");
        System.out.println("  9. Exit Engine");
        System.out.println("================================================================================");
    }

    /**
     * Executes the complete end-to-end demo sequentially.
     */
    public void runFullDemo() {
        System.out.println("\n>>> STARTING FULL END-TO-END AUTOMATED DEMO <<<\n");
        GradingWorker.logProof("================================================================================");
        GradingWorker.logProof(">>> INITIATING KEC VAC ENGINE AUTOMATED EXECUTION RUN <<<");
        GradingWorker.logProof("================================================================================");

        // 1. Test DB Connection
        System.out.println("\n--- STEP 1: TEST DB CONNECTION ---");
        boolean dbOk = testDatabaseConnection();
        if (!dbOk) {
            System.err.println("Database connection failed! Aborting demo. Check config/db.properties.");
            return;
        }

        // 2. Validate Course Codes
        System.out.println("\n--- STEP 2: REGEX COURSE CODE VALIDATION SUITE ---");
        runRegexValidationSuite();

        // 3. Import Quiz Marks
        System.out.println("\n--- STEP 3: IMPORT QUIZ MARKS FROM DATA STREAM ---");
        importQuizMarks();

        // 4. Run Concurrent Grading
        System.out.println("\n--- STEP 4: CONCURRENT MULTI-THREADED GRADING ---");
        runConcurrentGrading();

        // 5. Generate Certificates
        System.out.println("\n--- STEP 5: ASYNCHRONOUS CERTIFICATE GENERATION ---");
        generateCertificates();

        // 6. Compute Achievement Index
        System.out.println("\n--- STEP 6: VAC ACHIEVEMENT INDEX CALCULATION ---");
        computeAndPrintAchievementIndex();

        // 7. Print Summary Table
        System.out.println("\n--- STEP 7: COMPREHENSIVE ACADEMIC SUMMARY ---");
        printSummaryTable();

        System.out.println("\n>>> FULL DEMO EXECUTION COMPLETE! CHECK output/certificates/ & logs/execution_proofs.log <<<");
    }

    /**
     * Step 1: Tests DB connection and logs proof metadata.
     */
    public boolean testDatabaseConnection() {
        boolean ok = DBConnection.testConnection();
        GradingWorker.logProof("[DB_PROOF] Test Connection Result: " + (ok ? "SUCCESSFUL" : "FAILED"));
        return ok;
    }

    /**
     * Step 2: String processing requirement - validate course codes with Pattern/Matcher.
     */
    public void runRegexValidationSuite() {
        System.out.println("Validating course codes against Regex Pattern: " + COURSE_CODE_REGEX);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-15s | %-12s | %-45s\n", "Course Code", "Regex Status", "Test Expectation");
        System.out.println("--------------------------------------------------------------------------------");

        String[][] testCases = {
            {"VAC-101", "Valid standard code"},
            {"VAC-205", "Valid standard code"},
            {"VAC-999", "Valid standard code"},
            {"VAC-12", "Invalid: only 2 digits, requires 3"},
            {"vac-101", "Invalid: lowercase prefix, requires uppercase"},
            {"VAC-1234", "Invalid: 4 digits, exceeds 3 digit specification"},
            {"ABC-101", "Invalid: prefix is ABC instead of VAC"},
            {"VAC-A01", "Invalid: non-numeric digit component"},
            {" VAC-101 ", "Invalid: untrimmed surrounding whitespace"},
            {"", "Invalid: empty string"}
        };

        for (String[] tc : testCases) {
            String code = tc[0];
            String note = tc[1];
            boolean isValid = validateCourseCode(code);
            String status = isValid ? "PASS [VALID]" : "FAIL [INVALID]";
            System.out.printf("%-15s | %-12s | %-45s\n", "'" + code + "'", status, note);
            GradingWorker.logProof(String.format("[REGEX_CHECK] Code: '%s' -> %s (%s)", code, status, note));
        }
        System.out.println("--------------------------------------------------------------------------------\n");
    }

    public static boolean validateCourseCode(String code) {
        if (code == null) return false;
        Matcher matcher = COURSE_CODE_PATTERN.matcher(code);
        return matcher.matches();
    }

    /**
     * Step 3: File I/O requirement - read quiz marks from data/quiz_marks.txt.
     */
    public void importQuizMarks() {
        File file = new File(QUIZ_FILE_PATH);
        if (!file.exists()) {
            System.err.println("[QuizImporter] Warning: File not found: " + file.getAbsolutePath());
            GradingWorker.logProof("[QUIZ_IMPORT] Skipped: File not found " + file.getAbsolutePath());
            return;
        }

        System.out.println("Reading and parsing quiz marks stream from: " + QUIZ_FILE_PATH);
        int successCount = 0;
        int errorCount = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip comments and blank lines
                }

                String[] tokens = line.split(",");
                if (tokens.length < 3) {
                    System.err.printf("[Line %2d] Malformed line (expected 3 tokens, found %d): '%s'\n", lineNumber, tokens.length, line);
                    GradingWorker.logProof(String.format("[QUIZ_ERROR] Line %d: Malformed tokens in '%s'", lineNumber, line));
                    errorCount++;
                    continue;
                }

                String rawStudentId = tokens[0].trim();
                String rawCourseCode = tokens[1].trim();
                String rawScore = tokens[2].trim();

                // Clean string values
                String studentId = rawStudentId.toUpperCase();
                String courseCode = rawCourseCode.toUpperCase();

                // Validate course code regex
                if (!validateCourseCode(courseCode)) {
                    System.err.printf("[Line %2d] Invalid course code format: '%s'\n", lineNumber, courseCode);
                    GradingWorker.logProof(String.format("[QUIZ_ERROR] Line %d: Invalid course code '%s'", lineNumber, courseCode));
                    errorCount++;
                    continue;
                }

                // Parse and validate score
                double score;
                try {
                    score = Double.parseDouble(rawScore);
                } catch (NumberFormatException nfe) {
                    System.err.printf("[Line %2d] Non-numeric quiz score: '%s'\n", lineNumber, rawScore);
                    GradingWorker.logProof(String.format("[QUIZ_ERROR] Line %d: Non-numeric score '%s'", lineNumber, rawScore));
                    errorCount++;
                    continue;
                }

                if (score < 0.0 || score > 100.0) {
                    System.err.printf("[Line %2d] Out-of-range score (%.2f must be between 0 and 100): '%s'\n", lineNumber, score, line);
                    GradingWorker.logProof(String.format("[QUIZ_ERROR] Line %d: Score out-of-range (%.2f)", lineNumber, score));
                    errorCount++;
                    continue;
                }

                // Update in database via DAO
                try {
                    int rows = dao.updateQuizScore(studentId, courseCode, score);
                    if (rows > 0) {
                        System.out.printf("[Line %2d] Successfully imported Quiz Score %.2f for %s in %s\n", lineNumber, score, studentId, courseCode);
                        GradingWorker.logProof(String.format("[QUIZ_SUCCESS] Imported score %.2f for %s (%s)", score, studentId, courseCode));
                        successCount++;
                    } else {
                        System.out.printf("[Line %2d] Warning: No assessment found to update for %s in %s\n", lineNumber, studentId, courseCode);
                        GradingWorker.logProof(String.format("[QUIZ_WARN] Assessment row not found for %s in %s", studentId, courseCode));
                    }
                } catch (SQLException sqle) {
                    System.err.printf("[Line %2d] Database update failed for %s: %s\n", lineNumber, studentId, sqle.getMessage());
                    errorCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("Fatal IO error while reading quiz marks: " + e.getMessage());
        }

        System.out.printf("\nQuiz Import Finished -> Successfully Updated: %d | Skipped/Errors: %d\n\n", successCount, errorCount);
    }

    /**
     * Step 4: Multi-threaded batch grading using fixed thread pool ExecutorService.
     */
    public void runConcurrentGrading() {
        System.out.println("Fetching all student assessment records from database...");
        List<VacDAO.AssessmentData> assessmentList;
        try {
            assessmentList = dao.getAllAssessments();
        } catch (SQLException e) {
            System.err.println("Failed to fetch assessments: " + e.getMessage());
            return;
        }

        if (assessmentList.isEmpty()) {
            System.out.println("No assessments found in database. Please run the schema seed script.");
            return;
        }

        int threadCount = Math.min(4, Runtime.getRuntime().availableProcessors());
        AtomicInteger gradingThreadCounter = new AtomicInteger(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setName("GradingPool-Worker-" + gradingThreadCounter.getAndIncrement());
            return t;
        });

        System.out.printf("Dispatching %d student assessments across ExecutorService fixed thread pool (%d workers)...\n",
                assessmentList.size(), threadCount);
        GradingWorker.logProof(String.format("[CONCURRENCY_START] Launching batch evaluation for %d items on %d threads",
                assessmentList.size(), threadCount));

        List<Future<GradingWorker.GradingResult>> futures = new ArrayList<>();
        for (VacDAO.AssessmentData item : assessmentList) {
            GradingWorker worker = new GradingWorker(item, dao);
            futures.add(executor.submit(worker));
        }

        lastGradingResults.clear();
        for (Future<GradingWorker.GradingResult> f : futures) {
            try {
                GradingWorker.GradingResult res = f.get();
                lastGradingResults.add(res);
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Grading execution error: " + e.getMessage());
            }
        }

        executor.shutdown();
        GradingWorker.logProof("[CONCURRENCY_END] Batch parallel evaluation finished successfully.");
        System.out.println("Batch parallel grading completed! Total evaluated: " + lastGradingResults.size());
    }

    /**
     * Step 5: Generates text certificates asynchronously into output/certificates/.
     */
    public void generateCertificates() {
        if (lastGradingResults.isEmpty()) {
            System.out.println("No grading results in memory. Running concurrent grading first...");
            runConcurrentGrading();
        }

        System.out.println("\nTriggering Asynchronous Certificate Generation...");
        try {
            List<String> certPaths = certWriter.generateCertificatesAsync(lastGradingResults).get();
            System.out.printf("\nSuccessfully generated %d digital certificates in 'output/certificates/':\n", certPaths.size());
            for (String p : certPaths) {
                System.out.println(" -> " + p);
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Asynchronous certificate generation failed: " + e.getMessage());
        }
    }

    /**
     * Step 6: Computes and prints VAC Achievement Index per student.
     * Formula: SUM(course_credits x badge_points) / total enrolled VAC courses for that student.
     */
    public void computeAndPrintAchievementIndex() {
        System.out.println("\n================================================================================");
        System.out.println("                   KEC VAC ACHIEVEMENT INDEX EVALUATION REPORT                  ");
        System.out.println("    Formula: SUM(Course Credits x Badge Points) / Total Enrolled VAC Courses    ");
        System.out.println("    Constants: GOLD=10 | SILVER=8 | COMPLETED=6 | FAILED=0 | INCOMPLETE=0 | PENDING=0");
        System.out.println("================================================================================");

        Map<String, List<VacDAO.StudentIndexItem>> studentMap;
        try {
            studentMap = dao.getStudentEnrollmentsForIndex();
        } catch (SQLException e) {
            System.err.println("Error fetching enrollment data for index calculation: " + e.getMessage());
            return;
        }

        if (studentMap.isEmpty()) {
            System.out.println("No enrolled student records found.");
            return;
        }

        System.out.printf("%-10s | %-14s | %-13s | %-22s | %-18s\n",
                "Student ID", "Enrolled VACs", "Total Credits", "Badges Breakdown", "Achievement Index");
        System.out.println("--------------------------------------------------------------------------------");

        List<String> studentIds = new ArrayList<>(studentMap.keySet());
        Collections.sort(studentIds);

        for (String sId : studentIds) {
            List<VacDAO.StudentIndexItem> enrollments = studentMap.get(sId);
            int totalCourses = enrollments.size();
            int totalCredits = 0;
            double weightedPointSum = 0.0;
            StringBuilder badgeBreakdown = new StringBuilder();

            for (VacDAO.StudentIndexItem item : enrollments) {
                int credits = item.getCredits();
                totalCredits += credits;
                String badge = item.getBadge() != null ? item.getBadge().toUpperCase() : "INCOMPLETE";
                int points = getPointsForBadge(badge);

                weightedPointSum += (credits * points);

                if (badgeBreakdown.length() > 0) badgeBreakdown.append(", ");
                badgeBreakdown.append(String.format("%s:%s", item.getCourseCode(), badge));
            }

            double index = totalCourses > 0 ? (weightedPointSum / totalCourses) : 0.0;
            index = Math.round(index * 100.0) / 100.0;

            System.out.printf("%-10s | %-14d | %-13d | %-22s | %6.2f pts\n",
                    sId, totalCourses, totalCredits, badgeBreakdown.toString(), index);

            GradingWorker.logProof(String.format("[ACHIEVEMENT_INDEX] Student: %-9s Courses: %d Credits: %d Breakdown: [%s] -> Index: %.2f",
                    sId, totalCourses, totalCredits, badgeBreakdown.toString(), index));
        }
        System.out.println("================================================================================\n");
    }

    public static int getPointsForBadge(String badge) {
        if (badge == null) return POINTS_INCOMPLETE;
        switch (badge.toUpperCase()) {
            case "GOLD":
                return POINTS_GOLD;
            case "SILVER":
                return POINTS_SILVER;
            case "COMPLETED":
                return POINTS_COMPLETED;
            case "FAILED":
                return POINTS_FAILED;
            case "INCOMPLETE":
                return POINTS_INCOMPLETE;
            case "PENDING":
                return POINTS_PENDING;
            case "NONE":
            default:
                return 0;
        }
    }

    /**
     * Step 7: Prints comprehensive academic summary table across all students and outcomes.
     */
    public void printSummaryTable() {
        System.out.println("\n=============================================================================================================");
        System.out.println("                                  KEC VAC BATCH GRADING SUMMARY TABLE                                        ");
        System.out.println("=============================================================================================================");
        System.out.printf("%-9s | %-20s | %-8s | %-8s | %-6s | %-7s | %-11s | %-10s | %-20s\n",
                "Roll No", "Student Name", "Course", "Track", "Attn %", "Score", "Badge", "Status", "Remarks");
        System.out.println("-------------------------------------------------------------------------------------------------------------");

        if (lastGradingResults.isEmpty()) {
            System.out.println("No graded results to display. Please run Option 4 or 8.");
        } else {
            for (GradingWorker.GradingResult r : lastGradingResults) {
                String scoreStr = (r.getStatus().equals("PENDING") || (r.getStatus().equals("INCOMPLETE") && r.getWeightedScore() == 0.0))
                        ? "N/A" : String.format("%.2f%%", r.getWeightedScore());
                System.out.printf("%-9s | %-20s | %-8s | %-8s | %5.1f%% | %-7s | %-11s | %-10s | %-20s\n",
                        r.getStudentId(),
                        truncate(r.getStudentName(), 20),
                        r.getCourseCode(),
                        r.getTrackType(),
                        r.getAttendance(),
                        scoreStr,
                        r.getBadge(),
                        r.getStatus(),
                        truncate(r.getRemarks(), 20));
            }
        }
        System.out.println("=============================================================================================================\n");
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return "";
        return val.length() > maxLen ? val.substring(0, maxLen - 3) + "..." : val;
    }
}
