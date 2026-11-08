import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DBConnection manages JDBC connections for the KEC VAC & Skill Certification Engine.
 * Configured dynamically via config/db.properties to support both local and remote cloud VMs.
 */
public class DBConnection {

    private static String configPath = System.getProperty("db.config",
            System.getenv("DB_CONFIG") != null ? System.getenv("DB_CONFIG") : "config/db.properties");
    private static Properties props = new Properties();
    private static boolean isConfigLoaded = false;

    static {
        loadConfiguration();
    }

    /**
     * Sets the active configuration properties file path and reloads properties.
     *
     * @param newPath path to configuration file (e.g. config/db-remote.properties)
     */
    public static synchronized void setConfigPath(String newPath) {
        if (newPath != null && !newPath.trim().isEmpty()) {
            configPath = newPath.trim();
            isConfigLoaded = false;
            props.clear();
            loadConfiguration();
        }
    }

    /**
     * Retrieves the current configuration file path.
     *
     * @return active config path
     */
    public static String getConfigPath() {
        return configPath;
    }

    /**
     * Loads database configuration properties from file or classpath.
     */
    public static synchronized void loadConfiguration() {
        File file = new File(configPath);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                props.load(input);
                isConfigLoaded = true;
            } catch (IOException ex) {
                System.err.println("[DBConnection] Error reading configuration from " + configPath + ": " + ex.getMessage());
            }
        } else {
            // Fallback to classpath
            try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream(configPath)) {
                if (input != null) {
                    props.load(input);
                    isConfigLoaded = true;
                } else {
                    System.err.println("[DBConnection] Warning: " + configPath + " not found on disk or classpath.");
                }
            } catch (IOException ex) {
                System.err.println("[DBConnection] Error loading classpath resource: " + ex.getMessage());
            }
        }

        // Register JDBC driver
        try {
            String driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] MySQL JDBC Driver not found in classpath: " + e.getMessage());
        }
    }

    /**
     * Constructs and returns the JDBC connection URL.
     *
     * @return JDBC URL string
     */
    public static String getJdbcUrl() {
        String host = props.getProperty("db.host", "localhost");
        String port = props.getProperty("db.port", "3306");
        String dbName = props.getProperty("db.name", "kec_vac");
        String useSSL = props.getProperty("db.useSSL", "false");
        String allowPublicKey = props.getProperty("db.allowPublicKeyRetrieval", "true");
        String serverTimezone = props.getProperty("db.serverTimezone", "UTC");
        String connectTimeout = props.getProperty("db.connectTimeout", "5000");

        return String.format("jdbc:mysql://%s:%s/%s?useSSL=%s&allowPublicKeyRetrieval=%s&serverTimezone=%s&connectTimeout=%s",
                host, port, dbName, useSSL, allowPublicKey, serverTimezone, connectTimeout);
    }

    /**
     * Retrieves an active JDBC Connection.
     *
     * @return Connection object
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        if (!isConfigLoaded) {
            loadConfiguration();
        }
        String url = getJdbcUrl();
        String user = props.getProperty("db.user", "root");
        String pass = props.getProperty("db.password", "root");

        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            String host = props.getProperty("db.host", "localhost");
            String port = props.getProperty("db.port", "3306");
            System.err.println("\n========================================================");
            System.err.println("DATABASE CONNECTION FAILURE!");
            System.err.println("Target Host : " + host + ":" + port);
            System.err.println("Target DB   : " + props.getProperty("db.name", "kec_vac"));
            System.err.println("SQL State   : " + e.getSQLState());
            System.err.println("Error Code  : " + e.getErrorCode());
            System.err.println("Details     : " + e.getMessage());
            System.err.println("Troubleshooting Advice for Cloud VM Setup:");
            System.err.println(" 1. Check if MySQL server is running on " + host);
            System.err.println(" 2. Verify port " + port + " is open in cloud Security List / OS firewall");
            System.err.println(" 3. Verify user has remote access grants (e.g. 'user'@'%' or specific IP)");
            System.err.println(" 4. Verify bind-address in mysqld.cnf is 0.0.0.0");
            System.err.println("========================================================\n");
            throw e;
        }
    }

    /**
     * Tests connectivity to the database and prints remote server proofs.
     *
     * @return true if connection succeeded, false otherwise
     */
    public static boolean testConnection() {
        System.out.println("\n--------------------------------------------------------");
        System.out.println("TESTING DATABASE CONNECTIVITY (REMOTE/LOCAL PROOF)...");
        System.out.println("Active Config File   : " + getConfigPath());
        System.out.println("Target JDBC URL      : " + getJdbcUrl());
        System.out.println("Configured User      : " + props.getProperty("db.user", "root"));
        System.out.println("--------------------------------------------------------");

        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            System.out.println(">>> CONNECTION SUCCESSFUL! <<<");
            System.out.println("Connected Server Host: " + props.getProperty("db.host", "localhost") + ":" + props.getProperty("db.port", "3306"));
            System.out.println("Database Product Name: " + metaData.getDatabaseProductName());
            System.out.println("Database Server Ver  : " + metaData.getDatabaseProductVersion());
            System.out.println("JDBC Driver Name     : " + metaData.getDriverName());
            System.out.println("JDBC Driver Version  : " + metaData.getDriverVersion());
            System.out.println("Connection User      : " + metaData.getUserName());
            System.out.println("Auto-Commit Mode     : " + conn.getAutoCommit());
            System.out.println("--------------------------------------------------------\n");
            return true;
        } catch (SQLException e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Standalone main method to test DB connection independently.
     * Supports passing --remote or path to a custom properties file.
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            if ("--remote".equalsIgnoreCase(args[0])) {
                setConfigPath("config/db-remote.properties");
            } else if ("--local".equalsIgnoreCase(args[0])) {
                setConfigPath("config/db.properties");
            } else {
                setConfigPath(args[0]);
            }
        }
        System.out.println("KEC VAC Engine - DB Connection Self-Test");
        boolean ok = testConnection();
        System.exit(ok ? 0 : 1);
    }
}
