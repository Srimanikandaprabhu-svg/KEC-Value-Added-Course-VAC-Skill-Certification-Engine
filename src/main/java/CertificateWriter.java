import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CertificateWriter generates digital text certificates asynchronously for eligible students.
 * Certificates are saved to output/certificates/ using BufferedWriter and try-with-resources.
 */
public class CertificateWriter {

    private static final String CERT_OUTPUT_DIR = "output/certificates";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMMM-yyyy");

    private final VacDAO dao;

    public CertificateWriter(VacDAO dao) {
        this.dao = dao;
    }

    /**
     * Shuts down any resources if needed.
     */
    public void shutdown() {
        // No-op as per-batch pools terminate automatically
    }

    /**
     * Issues certificates asynchronously for a list of grading outcomes.
     * Skips students who are FAILED, INCOMPLETE, or PENDING.
     *
     * @param results List of GradingResult objects
     * @return CompletableFuture resolving to list of generated certificate file paths
     */
    public CompletableFuture<List<String>> generateCertificatesAsync(List<GradingWorker.GradingResult> results) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Ensure output directory exists
        File dir = new File(CERT_OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Reset thread counter per batch: CertWriter-1..N
        java.util.concurrent.atomic.AtomicInteger certThreadCounter = new java.util.concurrent.atomic.AtomicInteger(1);
        ExecutorService batchPool = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setName("CertWriter-" + certThreadCounter.getAndIncrement());
            return t;
        });

        for (GradingWorker.GradingResult res : results) {
            // Requirement: No certificate for INCOMPLETE, FAILED, or PENDING students
            if (!res.isEligibleForCertificate()) {
                GradingWorker.logProof(String.format("[-] Skipping certificate for Student %s in %s (Status: %s, Badge: %s)",
                        res.getStudentId(), res.getCourseCode(), res.getStatus(), res.getBadge()));
                continue;
            }

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                return generateSingleCertificate(res);
            }, batchPool);

            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<String> paths = new ArrayList<>();
                    for (CompletableFuture<String> f : futures) {
                        try {
                            String p = f.join();
                            if (p != null) paths.add(p);
                        } catch (Exception ex) {
                            System.err.println("[CertificateWriter] Error obtaining generated path: " + ex.getMessage());
                        }
                    }
                    batchPool.shutdown();
                    return paths;
                });
    }

    /**
     * Generates a single formatted .txt certificate file.
     *
     * @param res GradingResult
     * @return Absolute or relative file path of generated certificate
     */
    public String generateSingleCertificate(GradingWorker.GradingResult res) {
        String filename = String.format("CERT_%s_%s.txt", res.getStudentId(), res.getCourseCode());
        File certFile = new File(CERT_OUTPUT_DIR, filename);
        String issueDate = LocalDate.now().format(DATE_FORMATTER);

        String border       = "================================================================================";
        String doubleBorder = "################################################################################";
        String separator    = "--------------------------------------------------------------------------------";

        String certContent = String.format(
            "%s\n" +
            "                    KONGU ENGINEERING COLLEGE (AUTONOMOUS)                     \n" +
            "                   Perundurai, Erode - 638060, Tamil Nadu, India               \n" +
            "                 CENTRE FOR VALUE-ADDED & SKILL CERTIFICATION                  \n" +
            "%s\n" +
            "                                                                                \n" +
            "                    OFFICIAL VALUE-ADDED COURSE CERTIFICATE                     \n" +
            "                                                                                \n" +
            "    This is to proudly certify that                                             \n" +
            "                                                                                \n" +
            "                        %s                                                      \n" +
            "                        (Student Roll No: %s)                                   \n" +
            "                                                                                \n" +
            "    has successfully completed the prescribed Value-Added Course:               \n" +
            "                                                                                \n" +
            "         Course Code   : %-52s \n" +
            "         Course Title  : %-52s \n" +
            "         Track Domain  : %-52s \n" +
            "         Earned Credits: %-52d \n" +
            "                                                                                \n" +
            "%s\n" +
            "                            EVALUATION PERFORMANCE                             \n" +
            "%s\n" +
            "         Overall Score : %-52s \n" +
            "         Awarded Badge : %-52s \n" +
            "         Status        : %-52s \n" +
            "         Attendance    : %-52s \n" +
            "                                                                                \n" +
            "%s\n" +
            "    Date of Issuance : %-25s Verified by: Dean / VAC Cell\n" +
            "    Certificate Auth : KEC-VAC-VERIFIED-SECURE-HASH-2026                        \n" +
            "%s\n",
            doubleBorder,
            border,
            padCenter(res.getStudentName(), 70),
            res.getStudentId(),
            res.getCourseCode(),
            res.getCourseName(),
            res.getTrackType() + " CERTIFICATION",
            res.getCredits(),
            separator,
            separator,
            String.format("%.2f%%", res.getWeightedScore()),
            res.getBadge(),
            res.getStatus(),
            String.format("%.2f%%", res.getAttendance()),
            border,
            issueDate,
            doubleBorder
        );

        // Write using BufferedWriter with try-with-resources
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(certFile, StandardCharsets.UTF_8))) {
            writer.write(certContent);
            writer.flush();
        } catch (IOException e) {
            System.err.println("[CertificateWriter] IO Error writing certificate for " + res.getStudentId() + ": " + e.getMessage());
            return null;
        }

        String pathStr = certFile.getPath().replace('\\', '/');

        // Record file path in database
        if (dao != null) {
            try {
                dao.updateCertificateFilePath(res.getStudentId(), res.getCourseCode(), pathStr);
            } catch (SQLException e) {
                System.err.println("[CertificateWriter] Error updating certificate DB record: " + e.getMessage());
            }
        }

        GradingWorker.logProof(String.format("[+] Certificate Generated: [%s] -> %s", Thread.currentThread().getName(), pathStr));
        return pathStr;
    }

    private static String padCenter(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text;
        int padding = (width - text.length()) / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padding; i++) sb.append(" ");
        sb.append(text);
        return sb.toString();
    }
}
