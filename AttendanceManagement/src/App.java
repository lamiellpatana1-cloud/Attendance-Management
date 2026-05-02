import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class App {

    static final String DB_URL;
    static final String DB_USER;
    static final String DB_PASS;
    static final String MAIL_FROM;
    static final String MAIL_PASS;
    static final String MAIL_HOST;
    static final String MAIL_FROM_NAME;
    static final int MAIL_PORT;
    static final boolean MAIL_ENABLED;
    static final int SERVER_PORT;
    static final String PASSWORD_SCHEME = "pbkdf2";
    static final int PASSWORD_ITERATIONS = 65536;
    static final int PASSWORD_KEY_LENGTH = 256;
    static final long OTP_VALIDITY_MS = 3L * 60L * 1000L;
    static final int SESSION_VALIDITY_HOURS = 24;
    static final int MAX_EMAIL_USAGE_COUNT = ValidationUtils.MAX_EMAIL_USAGE_COUNT;
    static final SecureRandom SECURE_RANDOM = new SecureRandom();
    static volatile boolean DATABASE_READY = false;
    static volatile String DATABASE_STATUS = "starting";

    static Connection conn;

    static synchronized Connection ensureConnection() throws SQLException {
        try {
            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                return conn;
            }
        } catch (SQLException ignored) {
        }
        conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        return conn;
    }

    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    static String propertyOrEnv(Properties props, String propertyName, String defaultValue, String... envNames) {
        for (String envName : envNames) {
            String value = System.getenv(envName);
            if (hasText(value)) return value.trim();
        }
        return props.getProperty(propertyName, defaultValue);
    }

    static int parseIntSetting(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static void loadPropertiesIfExists(Properties props, Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (InputStream input = Files.newInputStream(path)) {
            props.load(input);
        }
    }

    static {
        Properties props = new Properties();
        try {
            loadPropertiesIfExists(props, Paths.get("config.properties"));
            loadPropertiesIfExists(props, Paths.get("config.local.properties"));

            String dbHost = propertyOrEnv(props, "db.host", "localhost", "DB_HOST", "MYSQLHOST");
            String dbPort = propertyOrEnv(props, "db.port", "3306", "DB_PORT", "MYSQLPORT");
            String dbName = propertyOrEnv(props, "db.name", "attendance_db", "DB_NAME", "MYSQLDATABASE");
            String dbSsl = propertyOrEnv(props, "db.ssl", "false", "DB_SSL");
            String dbTimezone = propertyOrEnv(props, "db.timezone", "UTC", "DB_TIMEZONE");

            DB_URL = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=%s&serverTimezone=%s&allowPublicKeyRetrieval=true",
                dbHost,
                dbPort,
                dbName,
                dbSsl,
                dbTimezone
            );
            DB_USER = propertyOrEnv(props, "db.user", "root", "DB_USER", "MYSQLUSER");
            DB_PASS = propertyOrEnv(props, "db.password", "root", "DB_PASSWORD", "MYSQLPASSWORD");
            MAIL_HOST = propertyOrEnv(props, "mail.host", "smtp.gmail.com", "MAIL_HOST");
            MAIL_PORT = parseIntSetting(propertyOrEnv(props, "mail.port", "587", "MAIL_PORT"), 587);
            MAIL_FROM = propertyOrEnv(props, "mail.from", "your_gmail@gmail.com", "MAIL_FROM");
            MAIL_PASS = propertyOrEnv(props, "mail.password", "xxxx xxxx xxxx xxxx", "MAIL_PASSWORD", "MAIL_PASS");
            MAIL_FROM_NAME = propertyOrEnv(props, "mail.from_name", "AttendEase", "MAIL_FROM_NAME");
            MAIL_ENABLED = Boolean.parseBoolean(propertyOrEnv(props, "mail.enabled", "false", "MAIL_ENABLED"));
            SERVER_PORT = parseIntSetting(firstNonBlank(System.getenv("PORT"), props.getProperty("server.port"), "8080"), 8080);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("  AttendEase - Attendance Management System  ");
        System.out.println("==============================================");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("[ERROR] MySQL JDBC driver not found in lib/");
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", SERVER_PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        server.createContext("/", new StaticHandler());
        server.createContext("/api/health", new HealthHandler());

        server.createContext("/api/login",          new LoginHandler());
        server.createContext("/api/register",       new RegisterHandler());
        server.createContext("/api/logout",         new LogoutHandler());
        server.createContext("/api/reset-password", new ResetPasswordHandler());
        server.createContext("/api/change-password", new ChangePasswordHandler());
        server.createContext("/api/profile",        new ProfileHandler());
        server.createContext("/api/send-otp",       new SendOtpHandler());
        server.createContext("/api/verify-otp",     new VerifyOtpHandler());

        server.createContext("/api/students",   new StudentsHandler());
        server.createContext("/api/teachers",   new TeachersHandler());
        server.createContext("/api/subjects",   new SubjectsHandler());
        server.createContext("/api/attendance", new AttendanceHandler());
        server.createContext("/api/marks",      new MarksHandler());
        server.createContext("/api/timetable",  new TimetableHandler());
        server.createContext("/api/reports",    new ReportsHandler());
        server.createContext("/api/dashboard",  new DashboardHandler());
        server.createContext("/api/qr",         new QRHandler());
        server.createContext("/api/notify",     new NotifyHandler());
        server.start();
        System.out.println("[SERVER] Running on 0.0.0.0:" + SERVER_PORT);
        System.out.println("[SERVER] Press Ctrl+C to stop.");

        Thread dbInitializer = new Thread(App::initializeDatabaseWithRetry, "database-initializer");
        dbInitializer.setDaemon(true);
        dbInitializer.start();
    }

    static void initializeDatabaseWithRetry() {
        int attempt = 1;
        while (!DATABASE_READY) {
            try {
                System.out.println("[DB] Connecting to MySQL... attempt " + attempt);
                ensureConnection();
                System.out.println("[DB] Connected successfully!");
                initializeDatabaseIfNeeded();
                ensureDatabaseCompatibility();
                migrateLegacyPasswords();
                DATABASE_READY = true;
                DATABASE_STATUS = "ready";
                System.out.println("[DB] Database is ready.");
            } catch (Exception e) {
                DATABASE_STATUS = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                System.out.println("[WARN] Database is not ready yet: " + DATABASE_STATUS);
                System.out.println("       The app will keep retrying. Check DB_HOST, DB_PORT, DB_NAME, DB_USER, and DB_PASSWORD on Railway.");
                try {
                    Thread.sleep(Math.min(30000, 3000L + (attempt * 1000L)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                attempt++;
            }
        }
    }

    // ── DB helpers ─────────────────────────────────────────────
    static void initializeDatabaseIfNeeded() throws SQLException, IOException {
        if (tableExists("users")) return;

        System.out.println("[DB] No schema found. Importing database/schema.sql...");
        runSqlScript(Paths.get("database", "schema.sql"));

        if (tableIsEmpty("users") && Files.exists(Paths.get("database", "seed.sql"))) {
            System.out.println("[DB] Importing database/seed.sql...");
            runSqlScript(Paths.get("database", "seed.sql"));
        }
    }

    static boolean tableExists(String tableName) throws SQLException {
        Connection db = ensureConnection();
        DatabaseMetaData metaData = db.getMetaData();
        try (ResultSet rs = metaData.getTables(db.getCatalog(), null, tableName, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    static boolean tableIsEmpty(String tableName) throws SQLException {
        if (!tableExists(tableName)) return true;
        try (ResultSet rs = query("SELECT COUNT(*) AS total FROM " + tableName)) {
            return rs.next() && rs.getInt("total") == 0;
        }
    }

    static void runSqlScript(Path scriptPath) throws SQLException, IOException {
        if (!Files.exists(scriptPath)) {
            throw new IOException("Missing SQL file: " + scriptPath);
        }

        try (Statement stmt = ensureConnection().createStatement()) {
            StringBuilder sql = new StringBuilder();
            for (String rawLine : Files.readAllLines(scriptPath, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) continue;

                sql.append(rawLine).append('\n');
                if (line.endsWith(";")) {
                    String statement = sql.toString().trim();
                    statement = statement.substring(0, statement.length() - 1).trim();
                    if (!statement.isEmpty()) stmt.execute(statement);
                    sql.setLength(0);
                }
            }

            String statement = sql.toString().trim();
            if (!statement.isEmpty()) stmt.execute(statement);
        }
    }

    static ResultSet query(String sql, Object... p) throws SQLException {
        PreparedStatement ps = ensureConnection().prepareStatement(sql);
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
        return ps.executeQuery();
    }

    static int update(String sql, Object... p) throws SQLException {
        PreparedStatement ps = ensureConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
        ps.executeUpdate();
        ResultSet k = ps.getGeneratedKeys();
        return k.next() ? k.getInt(1) : -1;
    }

    // ── HTTP helpers ───────────────────────────────────────────
    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type",                 "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.getResponseBody().close();
    }

    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), "UTF-8");
    }

    static Map<String, String> parseJson(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        String json = raw.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}"))   json = json.substring(0, json.length() - 1);
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"", "");
                String v = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(k, v);
            }
        }
        return map;
    }

    static String resultToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) sb.append(",");
            sb.append("{");
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(",");
                sb.append("\"").append(meta.getColumnLabel(i)).append("\":");
                Object val = rs.getObject(i);
                if (val == null) sb.append("null");
                else sb.append("\"").append(val.toString().replace("\\","\\\\").replace("\"","\\\"")).append("\"");
            }
            sb.append("}");
            first = false;
        }
        return sb.append("]").toString();
    }

    // ── Token store ────────────────────────────────────────────
    static final Map<String, Map<String, String>> tokenStore = new ConcurrentHashMap<>();
    static final Map<String, Long> verifiedOtpStore = new ConcurrentHashMap<>();

    static Map<String, String> validateToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        String token = auth.substring(7);
        Map<String, String> cached = tokenStore.get(token);
        if (cached != null) return cached;
        try {
            Map<String, String> restored = loadSessionUser(token);
            if (restored != null) tokenStore.put(token, restored);
            return restored;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to validate session token", e);
        }
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return map;
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    static boolean hasRole(Map<String, String> user, String... roles) {
        if (user == null) return false;
        String role = user.get("role");
        for (String allowed : roles) {
            if (allowed.equals(role)) return true;
        }
        return false;
    }

    static boolean isTruthy(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes");
    }

    static String currentDate() {
        return new java.sql.Date(System.currentTimeMillis()).toString();
    }

    static String currentTime() {
        return new java.sql.Time(System.currentTimeMillis()).toString();
    }

    static String[] resolveDashboardDateRange(Map<String, String> qs) {
        LocalDate today = LocalDate.now();
        String defaultFrom = today.withDayOfMonth(1).toString();
        String defaultTo = today.toString();

        String from = normalize(qs.get("from"));
        String to = normalize(qs.get("to"));

        if (from.isEmpty()) from = defaultFrom;
        if (to.isEmpty()) to = defaultTo;

        require(isValidDate(from), "From date must use YYYY-MM-DD format");
        require(isValidDate(to), "To date must use YYYY-MM-DD format");
        require(!LocalDate.parse(from).isAfter(LocalDate.parse(to)), "From date must not be later than To date");
        return new String[] { from, to };
    }

    static void persistSession(String userId, String token) throws SQLException {
        update("DELETE FROM sessions WHERE user_id=? OR expires_at <= NOW()", userId);
        update("INSERT INTO sessions(user_id,token,expires_at) VALUES(?,?,DATE_ADD(NOW(), INTERVAL " + SESSION_VALIDITY_HOURS + " HOUR))",
            userId, token);
    }

    static void clearSession(String token) throws SQLException {
        tokenStore.remove(token);
        update("DELETE FROM sessions WHERE token=?", token);
    }

    static Map<String, String> loadSessionUser(String token) throws SQLException {
        ResultSet rs = query(
            "SELECT u.id, u.username, u.role," +
            " COALESCE(t.full_name, s.full_name, u.username) AS full_name," +
            " s.id AS student_profile_id, t.id AS teacher_profile_id, s.student_id AS student_number" +
            " FROM sessions se" +
            " JOIN users u ON u.id=se.user_id" +
            " LEFT JOIN teachers t ON t.user_id=u.id AND COALESCE(t.is_archived,FALSE)=FALSE" +
            " LEFT JOIN students s ON s.user_id=u.id AND COALESCE(s.is_archived,FALSE)=FALSE" +
            " WHERE se.token=? AND se.expires_at > NOW() AND COALESCE(u.is_archived,FALSE)=FALSE LIMIT 1",
            token
        );
        if (!rs.next()) {
            update("DELETE FROM sessions WHERE token=? AND expires_at <= NOW()", token);
            return null;
        }
        String role = rs.getString("role");
        if (("student".equals(role) && isBlank(rs.getString("student_profile_id"))) ||
            ("teacher".equals(role) && isBlank(rs.getString("teacher_profile_id")))) {
            clearSession(token);
            return null;
        }

        Map<String, String> user = new HashMap<>();
        user.put("id", rs.getString("id"));
        user.put("username", rs.getString("username"));
        user.put("role", role);
        user.put("full_name", rs.getString("full_name"));
        String profileId = role.equals("student")
            ? rs.getString("student_profile_id")
            : role.equals("teacher")
                ? rs.getString("teacher_profile_id")
                : rs.getString("id");
        user.put("profile_id", profileId == null ? "" : profileId);
        user.put("student_number", rs.getString("student_number") == null ? "" : rs.getString("student_number"));
        return user;
    }

    static String normalize(String value) { return ValidationUtils.normalize(value); }

    static String normalizeEmail(String value) { return ValidationUtils.normalizeEmail(value); }

    static String normalizeCourse(String value) { return ValidationUtils.normalizeCourse(value); }

    static String normalizeSection(String value) { return ValidationUtils.normalizeSection(value); }

    static String normalizeSubjectCode(String value) { return ValidationUtils.normalizeSubjectCode(value); }

    static String normalizeAddressComponent(String value) { return ValidationUtils.normalizeAddressComponent(value); }

    static boolean isBlank(String value) { return ValidationUtils.isBlank(value); }

    static String toNameCase(String value) { return ValidationUtils.toNameCase(value); }

    static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    static void require(boolean condition, String message) { ValidationUtils.require(condition, message); }

    static String getEmailValidationError(String email) { return ValidationUtils.getEmailValidationError(email); }

    static boolean isValidEmail(String email) { return ValidationUtils.isValidEmail(email); }

    static void requireValidEmail(String email) {
        String error = getEmailValidationError(email);
        if (error != null) throw new IllegalArgumentException(error);
    }

    static boolean isValidUsername(String username) { return ValidationUtils.isValidUsername(username); }

    static boolean isValidFullName(String fullName) { return ValidationUtils.isValidFullName(fullName); }

    static boolean isValidPhone(String phone) { return ValidationUtils.isValidPhone(phone); }

    static boolean isValidCourse(String course) { return ValidationUtils.isValidCourse(course); }

    static boolean isValidSection(String section) { return ValidationUtils.isValidSection(section); }

    static boolean isValidYearLevel(String yearLevel) { return ValidationUtils.isValidYearLevel(yearLevel); }

    static boolean isValidDate(String date) { return ValidationUtils.isValidDate(date); }

    static String getBirthDateValidationError(String birthDate) { return ValidationUtils.getBirthDateValidationError(birthDate); }

    static boolean isValidBirthDate(String birthDate) { return ValidationUtils.isValidBirthDate(birthDate); }

    static int calculateAgeFromBirthDate(String birthDate) { return ValidationUtils.calculateAgeFromBirthDate(birthDate); }

    static boolean isLagunaProvince(String province) { return ValidationUtils.isLagunaProvince(province); }

    static String getAddressComponentError(String label, String value, int maxLength) {
        return ValidationUtils.getAddressComponentError(label, value, maxLength);
    }

    static boolean isPositiveInteger(String value) { return ValidationUtils.isPositiveInteger(value); }

    static boolean isValidStudentNumber(String studentNumber) { return ValidationUtils.isValidStudentNumber(studentNumber); }

    static boolean isValidSubjectCode(String subjectCode) { return ValidationUtils.isValidSubjectCode(subjectCode); }

    static boolean isValidSubjectName(String subjectName) { return ValidationUtils.isValidSubjectName(subjectName); }

    static boolean isValidOtpPurpose(String purpose) { return ValidationUtils.isValidOtpPurpose(purpose); }

    static boolean isAllowedSelfRegisterRole(String role) { return ValidationUtils.isAllowedSelfRegisterRole(role); }

    static boolean isAllowedAttendanceStatus(String status) { return ValidationUtils.isAllowedAttendanceStatus(status); }

    static boolean isAllowedAttendanceMethod(String method) { return ValidationUtils.isAllowedAttendanceMethod(method); }

    static boolean isAllowedExamType(String examType) { return ValidationUtils.isAllowedExamType(examType); }

    static int parseUnits(String value) { return ValidationUtils.parseUnits(value); }

    static boolean recordExists(String sql, Object... params) throws SQLException { return ValidationUtils.recordExists(sql, params); }

    static boolean usernameExists(String username, String exceptUserId) throws SQLException {
        return ValidationUtils.usernameExists(username, exceptUserId);
    }

    static boolean emailExists(String email, String exceptUserId) throws SQLException {
        return ValidationUtils.emailExists(email, exceptUserId);
    }

    static int getEmailUsageCount(String email, String exceptUserId) throws SQLException {
        return ValidationUtils.getEmailUsageCount(email, exceptUserId);
    }

    static boolean hasReachedEmailUsageLimit(String email, String exceptUserId) throws SQLException {
        return ValidationUtils.hasReachedEmailUsageLimit(email, exceptUserId);
    }

    static String getEmailUsageLimitMessage() {
        return MAX_EMAIL_USAGE_COUNT <= 1
            ? "Email is already registered"
            : "This email can only be used for up to " + MAX_EMAIL_USAGE_COUNT + " accounts";
    }

    static void ensureDatabaseCompatibility() throws SQLException {
        ensureColumnExists("users", "is_archived", "BOOLEAN NOT NULL DEFAULT FALSE AFTER role");
        ensureColumnExists("users", "archived_at", "TIMESTAMP NULL DEFAULT NULL AFTER is_archived");
        ensureColumnExists("students", "is_archived", "BOOLEAN NOT NULL DEFAULT FALSE AFTER qr_code");
        ensureColumnExists("students", "archived_at", "TIMESTAMP NULL DEFAULT NULL AFTER is_archived");
        ensureColumnExists("students", "date_of_birth", "DATE NULL AFTER phone");
        ensureColumnExists("students", "age", "INT NULL AFTER date_of_birth");
        ensureColumnExists("students", "province", "VARCHAR(60) NULL AFTER age");
        ensureColumnExists("students", "municipality", "VARCHAR(80) NULL AFTER province");
        ensureColumnExists("students", "barangay", "VARCHAR(100) NULL AFTER municipality");
        ensureColumnExists("teachers", "is_archived", "BOOLEAN NOT NULL DEFAULT FALSE AFTER profile_picture");
        ensureColumnExists("teachers", "archived_at", "TIMESTAMP NULL DEFAULT NULL AFTER is_archived");
        ensureColumnExists("teachers", "date_of_birth", "DATE NULL AFTER phone");
        ensureColumnExists("teachers", "age", "INT NULL AFTER date_of_birth");
        ensureColumnExists("teachers", "province", "VARCHAR(60) NULL AFTER age");
        ensureColumnExists("teachers", "municipality", "VARCHAR(80) NULL AFTER province");
        ensureColumnExists("teachers", "barangay", "VARCHAR(100) NULL AFTER municipality");
        ensureColumnExists("subjects", "is_archived", "BOOLEAN NOT NULL DEFAULT FALSE AFTER units");
        ensureColumnExists("subjects", "archived_at", "TIMESTAMP NULL DEFAULT NULL AFTER is_archived");
        ensureColumnExists("subjects", "course", "VARCHAR(30) NULL AFTER name");
        ensureColumnExists("subjects", "year_level", "INT NULL AFTER course");
        ensureColumnExists("subjects", "section", "VARCHAR(20) NULL AFTER year_level");
        ensureSessionsTable();
        backfillSeedSubjectTargets();
    }

    static void ensureSessionsTable() throws SQLException {
        try (Statement stmt = ensureConnection().createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS sessions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "token VARCHAR(50) UNIQUE NOT NULL," +
                "expires_at TIMESTAMP NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)"
            );
        }
    }

    static void backfillSeedSubjectTargets() throws SQLException {
        update(
            "UPDATE subjects SET course='BSCS', year_level=2, section='A' " +
            "WHERE code IN ('CS101','CS102','MATH101') " +
            "AND (course IS NULL OR year_level IS NULL OR section IS NULL)"
        );
    }

    static void ensureColumnExists(String tableName, String columnName, String columnDefinition) throws SQLException {
        Connection db = ensureConnection();
        DatabaseMetaData metaData = db.getMetaData();
        try (ResultSet rs = metaData.getColumns(db.getCatalog(), null, tableName, columnName)) {
            if (rs.next()) return;
        }

        try (Statement stmt = db.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
            System.out.println("[DB] Added missing column " + tableName + "." + columnName);
        }
    }

    static String hashPassword(String rawPassword) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = derivePasswordHash(rawPassword.toCharArray(), salt, PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
        return PASSWORD_SCHEME + "$" + PASSWORD_ITERATIONS + "$" +
            Base64.getEncoder().encodeToString(salt) + "$" +
            Base64.getEncoder().encodeToString(hash);
    }

    static byte[] derivePasswordHash(char[] rawPassword, byte[] salt, int iterations, int keyLength) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(rawPassword, salt, iterations, keyLength);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static boolean isHashedPassword(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith(PASSWORD_SCHEME + "$");
    }

    static boolean verifyPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        if (!isHashedPassword(storedPassword)) return storedPassword.equals(rawPassword);
        try {
            String[] parts = storedPassword.split("\\$");
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derivePasswordHash(rawPassword.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    static void migrateLegacyPasswords() throws Exception {
        ResultSet rs = query("SELECT id, password FROM users");
        List<String[]> toUpgrade = new ArrayList<>();
        while (rs.next()) {
            String storedPassword = rs.getString("password");
            if (!isBlank(storedPassword) && !isHashedPassword(storedPassword)) {
                toUpgrade.add(new String[] { rs.getString("id"), storedPassword });
            }
        }
        for (String[] item : toUpgrade) {
            update("UPDATE users SET password=? WHERE id=?", hashPassword(item[1]), item[0]);
        }
        if (!toUpgrade.isEmpty()) {
            System.out.println("[AUTH] Migrated " + toUpgrade.size() + " legacy plaintext password(s) to hashed storage.");
        }
    }

    static void upgradeLegacyPasswordIfNeeded(String userId, String rawPassword, String storedPassword) throws Exception {
        if (!isHashedPassword(storedPassword)) {
            update("UPDATE users SET password=? WHERE id=?", hashPassword(rawPassword), userId);
        }
    }

    static String otpVerificationKey(String email, String purpose) {
        return normalize(purpose).toLowerCase(Locale.ROOT) + ":" + normalizeEmail(email);
    }

    static void rememberVerifiedOtp(String email, String purpose) {
        verifiedOtpStore.put(otpVerificationKey(email, purpose), System.currentTimeMillis() + OTP_VALIDITY_MS);
    }

    static void clearVerifiedOtp(String email, String purpose) {
        verifiedOtpStore.remove(otpVerificationKey(email, purpose));
    }

    static boolean consumeVerifiedOtp(String email, String purpose) {
        String key = otpVerificationKey(email, purpose);
        Long expiresAt = verifiedOtpStore.get(key);
        if (expiresAt == null) return false;
        if (expiresAt < System.currentTimeMillis()) {
            verifiedOtpStore.remove(key);
            return false;
        }
        verifiedOtpStore.remove(key);
        return true;
    }

    static double parseScore(String value, String fieldName) { return ValidationUtils.parseScore(value, fieldName); }

    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static Map<String, String> resolveStudent(String ref) throws SQLException {
        return resolveStudent(ref, false);
    }

    static Map<String, String> resolveStudent(String ref, boolean includeArchived) throws SQLException {
        if (ref == null || ref.trim().isEmpty()) return null;
        ResultSet rs = query(
            "SELECT id,user_id,student_id,full_name,email,course,year_level,section,is_archived FROM students" +
            " WHERE (CAST(id AS CHAR)=? OR student_id=?)" +
            (includeArchived ? "" : " AND COALESCE(is_archived,FALSE)=FALSE") +
            " LIMIT 1",
            ref, ref
        );
        if (!rs.next()) return null;
        Map<String, String> student = new LinkedHashMap<>();
        student.put("id", rs.getString("id"));
        student.put("user_id", rs.getString("user_id"));
        student.put("student_id", rs.getString("student_id"));
        student.put("full_name", rs.getString("full_name"));
        student.put("email", rs.getString("email"));
        student.put("course", rs.getString("course"));
        student.put("year_level", rs.getString("year_level"));
        student.put("section", rs.getString("section"));
        student.put("is_archived", rs.getString("is_archived"));
        return student;
    }

    static Map<String, String> resolveTeacher(String ref) throws SQLException {
        return resolveTeacher(ref, false);
    }

    static Map<String, String> resolveTeacher(String ref, boolean includeArchived) throws SQLException {
        if (ref == null || ref.trim().isEmpty()) return null;
        ResultSet rs = query(
            "SELECT id,user_id,full_name,email,department,subject,is_archived FROM teachers" +
            " WHERE (CAST(id AS CHAR)=? OR CAST(user_id AS CHAR)=?)" +
            (includeArchived ? "" : " AND COALESCE(is_archived,FALSE)=FALSE") +
            " LIMIT 1",
            ref, ref
        );
        if (!rs.next()) return null;
        Map<String, String> teacher = new LinkedHashMap<>();
        teacher.put("id", rs.getString("id"));
        teacher.put("user_id", rs.getString("user_id"));
        teacher.put("full_name", rs.getString("full_name"));
        teacher.put("email", rs.getString("email"));
        teacher.put("department", rs.getString("department"));
        teacher.put("subject", rs.getString("subject"));
        teacher.put("is_archived", rs.getString("is_archived"));
        return teacher;
    }

    static String studentIdPrefix(String yearLevel) {
        switch (yearLevel) {
            case "4": return "221-";
            case "3": return "231-";
            case "2": return "241-";
            case "1":
            default:  return "251-";
        }
    }

    static String generateStudentId(String yearLevel) throws SQLException {
        String prefix = studentIdPrefix(yearLevel);
        Random random = new Random();
        for (int attempt = 0; attempt < 500; attempt++) {
            String sid = prefix + String.format("%04d", random.nextInt(10000));
            ResultSet exists = query("SELECT id FROM students WHERE student_id=? LIMIT 1", sid);
            if (!exists.next()) return sid;
        }
        throw new SQLException("Unable to generate a unique student ID");
    }

    static String resolveStudentIdForUser(Map<String, String> user, String requestedId) throws SQLException {
        if (hasRole(user, "student")) return user.get("profile_id");
        if (requestedId == null || requestedId.trim().isEmpty()) return null;
        Map<String, String> student = resolveStudent(requestedId);
        return student == null ? null : student.get("id");
    }

    static boolean subjectMatchesStudent(Map<String, String> subject, Map<String, String> student) {
        if (subject == null || student == null) return false;
        String subjectCourse = normalizeCourse(subject.get("course"));
        String subjectYear = normalize(subject.get("year_level"));
        String subjectSection = normalizeSection(subject.get("section"));
        if (subjectCourse.isEmpty() || subjectYear.isEmpty() || subjectSection.isEmpty()) {
            return true;
        }
        return subjectCourse.equals(normalizeCourse(student.get("course")))
            && subjectYear.equals(normalize(student.get("year_level")))
            && subjectSection.equals(normalizeSection(student.get("section")));
    }

    static Map<String, String> resolveSubject(String ref) throws SQLException {
        return resolveSubject(ref, false);
    }

    static Map<String, String> resolveSubject(String ref, boolean includeArchived) throws SQLException {
        if (isBlank(ref)) return null;
        ResultSet rs = query(
            "SELECT s.*, t.full_name AS teacher_name FROM subjects s LEFT JOIN teachers t ON t.id=s.teacher_id" +
            " WHERE (CAST(s.id AS CHAR)=? OR UPPER(s.code)=UPPER(?))" +
            (includeArchived ? "" : " AND COALESCE(s.is_archived,FALSE)=FALSE") +
            " LIMIT 1",
            ref, ref
        );
        if (!rs.next()) return null;
        Map<String, String> subject = new LinkedHashMap<>();
        subject.put("id", rs.getString("id"));
        subject.put("code", rs.getString("code"));
        subject.put("name", rs.getString("name"));
        subject.put("teacher_id", rs.getString("teacher_id"));
        subject.put("teacher_name", rs.getString("teacher_name"));
        subject.put("units", rs.getString("units"));
        subject.put("course", rs.getString("course"));
        subject.put("year_level", rs.getString("year_level"));
        subject.put("section", rs.getString("section"));
        subject.put("is_archived", rs.getString("is_archived"));
        return subject;
    }

    static String findLinkedUserId(String tableName, String entityId) throws SQLException {
        ResultSet rs = query("SELECT user_id FROM " + tableName + " WHERE id=? LIMIT 1", entityId);
        return rs.next() ? rs.getString("user_id") : null;
    }

    static void archiveRecord(String tableName, String entityId) throws SQLException {
        update("UPDATE " + tableName + " SET is_archived=TRUE, archived_at=NOW() WHERE id=?", entityId);
    }

    static void restoreRecord(String tableName, String entityId) throws SQLException {
        update("UPDATE " + tableName + " SET is_archived=FALSE, archived_at=NULL WHERE id=?", entityId);
    }

    static void archiveUserAccount(String userId) throws SQLException {
        if (isBlank(userId)) return;
        update("UPDATE users SET is_archived=TRUE, archived_at=NOW() WHERE id=?", userId);
        update("DELETE FROM sessions WHERE user_id=?", userId);
    }

    static void restoreUserAccount(String userId) throws SQLException {
        if (isBlank(userId)) return;
        update("UPDATE users SET is_archived=FALSE, archived_at=NULL WHERE id=?", userId);
    }

    static boolean subjectAccessibleToUser(Map<String, String> user, String subjectId) throws SQLException {
        Map<String, String> subject = resolveSubject(subjectId);
        if (subject == null) return false;
        if (hasRole(user, "admin")) return true;
        if (hasRole(user, "teacher")) {
            return normalize(subject.get("teacher_id")).equals(normalize(user.get("profile_id")));
        }
        if (hasRole(user, "student")) {
            return subjectMatchesStudent(subject, resolveStudent(user.get("profile_id")));
        }
        return false;
    }

    static boolean isValidTimeValue(String value) {
        return normalize(value).matches("^\\d{2}:\\d{2}(:\\d{2})?$");
    }

    static boolean isValidWeekday(String value) {
        return Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").contains(normalize(value));
    }

    static void upsertAttendance(String studentId, String subjectId, String date, String timeIn,
                                 String status, String method, String latitude, String longitude,
                                 String remarks, String markedBy) throws SQLException {
        ResultSet existing = query(
            "SELECT id FROM attendance WHERE student_id=? AND subject_id=? AND date=?",
            studentId, subjectId, date
        );
        if (existing.next()) {
            update(
                "UPDATE attendance SET time_in=?,status=?,method=?,latitude=?,longitude=?,remarks=?,marked_by=? WHERE id=?",
                blankToNull(timeIn), status, method, blankToNull(latitude), blankToNull(longitude),
                blankToNull(remarks), blankToNull(markedBy), existing.getString("id")
            );
        } else {
            update(
                "INSERT INTO attendance(student_id,subject_id,date,time_in,status,method,latitude,longitude,remarks,marked_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                studentId, subjectId, date, blankToNull(timeIn), status, method,
                blankToNull(latitude), blankToNull(longitude), blankToNull(remarks), blankToNull(markedBy)
            );
        }
    }

    static void syncLowAttendanceNotifications() throws SQLException {
        List<Map<String, String>> alerts = new ArrayList<>();
        ResultSet alertRs = query(
            "SELECT s.full_name, ROUND(SUM(a.status='present')*100.0/NULLIF(COUNT(a.id),0),1) AS rate" +
            " FROM students s JOIN attendance a ON a.student_id=s.id" +
            " WHERE COALESCE(s.is_archived,FALSE)=FALSE" +
            " GROUP BY s.id HAVING rate < 75 ORDER BY rate"
        );
        while (alertRs.next()) {
            Map<String, String> alert = new LinkedHashMap<>();
            alert.put("full_name", alertRs.getString("full_name"));
            alert.put("rate", alertRs.getString("rate"));
            alerts.add(alert);
        }

        ResultSet users = query(
            "SELECT id FROM users WHERE role IN ('admin','teacher') AND COALESCE(is_archived,FALSE)=FALSE"
        );
        List<String> userIds = new ArrayList<>();
        while (users.next()) userIds.add(users.getString("id"));

        for (String userId : userIds) {
            for (Map<String, String> alert : alerts) {
                String message = "Low attendance alert: " + alert.get("full_name") + " is at " + alert.get("rate") + "% attendance.";
                ResultSet existing = query(
                    "SELECT id FROM notifications WHERE user_id=? AND message=? AND DATE(created_at)=CURDATE() LIMIT 1",
                    userId, message
                );
                if (!existing.next()) {
                    update("INSERT INTO notifications(user_id,message,type,is_read) VALUES(?,?,?,FALSE)", userId, message, "warning");
                }
            }
        }
    }

    // ── OTP / Email helpers ────────────────────────────────────
    static List<String> findUserIdsByRoles(String... roles) throws SQLException {
        List<String> result = new ArrayList<>();
        if (roles == null || roles.length == 0) return result;

        StringJoiner placeholders = new StringJoiner(",", "(", ")");
        List<Object> params = new ArrayList<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) continue;
            placeholders.add("?");
            params.add(role);
        }
        if (params.isEmpty()) return result;

        ResultSet rs = query(
            "SELECT id FROM users WHERE role IN " + placeholders +
            " AND COALESCE(is_archived,FALSE)=FALSE ORDER BY id",
            params.toArray()
        );
        while (rs.next()) result.add(rs.getString("id"));
        return result;
    }

    static void createNotificationForUsers(Collection<String> userIds, String message, String type) throws SQLException {
        if (userIds == null || userIds.isEmpty() || message == null || message.isBlank()) return;
        String notificationType = normalize(type).isEmpty() ? "info" : normalize(type).toLowerCase(Locale.ROOT);
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) continue;
            ResultSet existing = query(
                "SELECT id FROM notifications WHERE user_id=? AND message=? AND DATE(created_at)=CURDATE() LIMIT 1",
                userId, message
            );
            if (!existing.next()) {
                update(
                    "INSERT INTO notifications(user_id,message,type,is_read) VALUES(?,?,?,FALSE)",
                    userId, message, notificationType
                );
            }
        }
    }

    static void createNotificationForRoles(String message, String type, String... roles) throws SQLException {
        createNotificationForUsers(findUserIdsByRoles(roles), message, type);
    }

    static String findSubjectName(String subjectId) throws SQLException {
        if (!isPositiveInteger(subjectId)) return "";
        ResultSet rs = query("SELECT name FROM subjects WHERE id=? LIMIT 1", subjectId);
        return rs.next() ? normalize(rs.getString("name")) : "";
    }

    static String generateOtp() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static boolean isMailConfigured() {
        return MAIL_ENABLED &&
               MAIL_HOST != null && !MAIL_HOST.isBlank() &&
               MAIL_PORT > 0 &&
               MAIL_FROM != null && !MAIL_FROM.isBlank() &&
               MAIL_PASS != null && !MAIL_PASS.isBlank() &&
               !MAIL_FROM.equals("your_gmail@gmail.com") &&
               !MAIL_PASS.equals("xxxx xxxx xxxx xxxx");
    }

    static String sanitizeHeader(String value) {
        if (value == null) return "";
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    static void smtpWriteLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    static String smtpReadResponse(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("SMTP server closed the connection");
        String latest = line;
        while (latest.length() >= 4 && latest.charAt(3) == '-') {
            latest = reader.readLine();
            if (latest == null) throw new IOException("SMTP server closed the connection");
        }
        return latest;
    }

    static void smtpExpect(BufferedReader reader, String... expectedCodes) throws IOException {
        String response = smtpReadResponse(reader);
        for (String code : expectedCodes) {
            if (response.startsWith(code)) return;
        }
        throw new IOException(response);
    }

    static void smtpWriteDataLine(BufferedWriter writer, String line) throws IOException {
        String safeLine = line == null ? "" : line.replace("\r", "");
        if (safeLine.startsWith(".")) safeLine = "." + safeLine;
        writer.write(safeLine);
        writer.write("\r\n");
    }

    static void sendOtpEmail(String toEmail, String otp, String purpose) throws IOException {
        if (!isMailConfigured()) {
            throw new IOException("Email sending is not configured. Update config.properties first.");
        }

        String subject;
        String body;
        switch (purpose) {
            case "register":
                subject = "AttendEase — Verify your account";
                body    = "Welcome to AttendEase!\n\nYour verification code is:\n\n  " + otp +
                          "\n\nThis code expires in 3 minutes.\nDo not share it with anyone.";
                break;
            case "reset_password":
                subject = "AttendEase — Password reset code";
                body    = "You requested a password reset.\n\nYour code is:\n\n  " + otp +
                          "\n\nThis code expires in 3 minutes.\nIf you did not request this, ignore this email.";
                break;
            default:
                subject = "AttendEase — Profile update verification";
                body    = "You are updating your profile.\n\nYour verification code is:\n\n  " + otp +
                          "\n\nThis code expires in 3 minutes.";
        }

        try (Socket plainSocket = new Socket()) {
            plainSocket.connect(new InetSocketAddress(MAIL_HOST, MAIL_PORT), 10000);
            plainSocket.setSoTimeout(10000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(plainSocket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plainSocket.getOutputStream(), StandardCharsets.UTF_8));

            smtpExpect(reader, "220");
            smtpWriteLine(writer, "EHLO localhost");
            smtpExpect(reader, "250");
            smtpWriteLine(writer, "STARTTLS");
            smtpExpect(reader, "220");

            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket secureSocket = (SSLSocket) sslFactory.createSocket(plainSocket, MAIL_HOST, MAIL_PORT, true)) {
                secureSocket.setUseClientMode(true);
                secureSocket.setSoTimeout(10000);
                secureSocket.startHandshake();

                reader = new BufferedReader(new InputStreamReader(secureSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(secureSocket.getOutputStream(), StandardCharsets.UTF_8));

                smtpWriteLine(writer, "EHLO localhost");
                smtpExpect(reader, "250");
                smtpWriteLine(writer, "AUTH LOGIN");
                smtpExpect(reader, "334");
                smtpWriteLine(writer, Base64.getEncoder().encodeToString(MAIL_FROM.getBytes(StandardCharsets.UTF_8)));
                smtpExpect(reader, "334");
                smtpWriteLine(writer, Base64.getEncoder().encodeToString(MAIL_PASS.getBytes(StandardCharsets.UTF_8)));
                smtpExpect(reader, "235");
                smtpWriteLine(writer, "MAIL FROM:<" + sanitizeHeader(MAIL_FROM) + ">");
                smtpExpect(reader, "250");
                smtpWriteLine(writer, "RCPT TO:<" + sanitizeHeader(toEmail) + ">");
                smtpExpect(reader, "250", "251");
                smtpWriteLine(writer, "DATA");
                smtpExpect(reader, "354");

                smtpWriteDataLine(writer, "From: " + sanitizeHeader(MAIL_FROM_NAME) + " <" + sanitizeHeader(MAIL_FROM) + ">");
                smtpWriteDataLine(writer, "To: <" + sanitizeHeader(toEmail) + ">");
                smtpWriteDataLine(writer, "Subject: " + sanitizeHeader(subject));
                smtpWriteDataLine(writer, "MIME-Version: 1.0");
                smtpWriteDataLine(writer, "Content-Type: text/plain; charset=UTF-8");
                smtpWriteDataLine(writer, "Content-Transfer-Encoding: 8bit");
                smtpWriteDataLine(writer, "");
                for (String line : body.replace("\r", "").split("\n", -1)) {
                    smtpWriteDataLine(writer, line);
                }
                smtpWriteLine(writer, ".");
                smtpExpect(reader, "250");
                smtpWriteLine(writer, "QUIT");
            }
        }
    }

    // ==========================================================
    // STATIC FILES
    // ==========================================================
    static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".webmanifest")) return "application/manifest+json; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "text/html; charset=UTF-8";
    }

    static class StaticHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("GET")) {
                sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8.name());
            if (path.equals("/")) path = "/index.html";
            path = path.replace('\\', '/');
            while (path.startsWith("/")) path = path.substring(1);

            Path webRoot = Paths.get("web").toAbsolutePath().normalize();
            Path filePath = webRoot.resolve(path).normalize();
            if (!filePath.startsWith(webRoot) || Files.isDirectory(filePath) || !Files.exists(filePath)) {
                sendJson(ex, 404, "{\"error\":\"Not found\"}");
                return;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            String filename = filePath.getFileName().toString();
            ex.getResponseHeaders().set("Content-Type", contentTypeFor(filename));
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            if (filename.equals("sw.js")) {
                ex.getResponseHeaders().set("Cache-Control", "no-cache");
                ex.getResponseHeaders().set("Service-Worker-Allowed", "/");
            }
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.getResponseBody().close();
        }
    }

    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            String dbStatus = DATABASE_READY ? "ready" : "starting";
            sendJson(ex, 200,
                "{\"status\":\"ok\",\"service\":\"AttendEase\",\"database\":\"" + dbStatus + "\"" +
                ",\"detail\":\"" + escapeJson(DATABASE_STATUS) + "\"}"
            );
        }
    }

    // ==========================================================
    // OTP HANDLERS
    // ==========================================================

    static class SendOtpHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            try {
                Map<String, String> b = parseJson(readBody(ex));
                String email   = normalizeEmail(b.get("email"));
                String purpose = normalize(b.getOrDefault("purpose", "register")).toLowerCase(Locale.ROOT);
                if (email.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Email is required\"}"); return;
                }
                String emailError = getEmailValidationError(email);
                if (emailError != null) {
                    sendJson(ex, 400, "{\"error\":\"" + escapeJson(emailError) + "\"}"); return;
                }
                if (!isValidOtpPurpose(purpose)) {
                    sendJson(ex, 400, "{\"error\":\"Invalid OTP purpose\"}"); return;
                }
                clearVerifiedOtp(email, purpose);
                if (purpose.equals("register") && hasReachedEmailUsageLimit(email, null)) {
                    sendJson(ex, 409, "{\"error\":\"" + escapeJson(getEmailUsageLimitMessage()) + "\"}"); return;
                }
                // For reset_password: verify email exists in users table
                if (purpose.equals("reset_password")) {
                    ResultSet rs = query("SELECT id FROM users WHERE LOWER(email)=?", email);
                    if (!rs.next()) {
                        sendJson(ex, 404, "{\"error\":\"No account found with that email\"}"); return;
                    }
                }
                if (!isMailConfigured()) {
                    sendJson(ex, 500, "{\"error\":\"OTP email is not configured yet. Set mail.enabled=true plus your Gmail sender and App Password in config.properties.\"}");
                    return;
                }

                String otp = generateOtp();
                final String finalEmail = email;
                final String finalOtp = otp;
                final String finalPurpose = purpose;
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
                java.util.concurrent.Future<?> future = executor.submit(() -> {
                    try { sendOtpEmail(finalEmail, finalOtp, finalPurpose); }
                    catch (Exception e1) { throw new RuntimeException(e1.getMessage(), e1); }
                });
                try {
                    future.get(20, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException te) {
                    future.cancel(true);
                    sendJson(ex, 500, "{\"error\":\"OTP email timed out. Please check your internet connection and Gmail sender settings.\"}");
                    return;
                } catch (java.util.concurrent.ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    String message = cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()
                        ? cause.getMessage()
                        : "Unknown email sending error";
                    System.out.println("[OTP ERROR] " + message);
                    sendJson(ex, 500, "{\"error\":\"Failed to send OTP email: " + escapeJson(message) + "\"}");
                    return;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    sendJson(ex, 500, "{\"error\":\"Email sending was interrupted\"}");
                    return;
                } finally {
                    executor.shutdownNow();
                }

                update("UPDATE otp_verifications SET used=TRUE WHERE email=? AND purpose=? AND used=FALSE", email, purpose);
                update("INSERT INTO otp_verifications(email,otp,purpose,expires_at) VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL 3 MINUTE))",
                    email, otp, purpose);

                System.out.println("[OTP] Sent to " + email + " purpose=" + purpose);
                sendJson(ex, 200, "{\"message\":\"OTP sent to " + escapeJson(email) + "\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class VerifyOtpHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            try {
                Map<String, String> b = parseJson(readBody(ex));
                String email   = normalizeEmail(b.get("email"));
                String otp     = normalize(b.get("otp"));
                String purpose = normalize(b.getOrDefault("purpose", "register")).toLowerCase(Locale.ROOT);
                if (email.isEmpty() || otp.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"email and otp required\"}"); return;
                }
                String emailError = getEmailValidationError(email);
                if (emailError != null) {
                    sendJson(ex, 400, "{\"error\":\"" + escapeJson(emailError) + "\"}"); return;
                }
                if (!otp.matches("^\\d{6}$")) {
                    sendJson(ex, 400, "{\"error\":\"OTP must be 6 digits\"}"); return;
                }
                if (!isValidOtpPurpose(purpose)) {
                    sendJson(ex, 400, "{\"error\":\"Invalid OTP purpose\"}"); return;
                }
                ResultSet rs = query(
                    "SELECT id FROM otp_verifications WHERE LOWER(email)=? AND otp=? AND purpose=? AND used=FALSE AND expires_at > NOW()",
                    email, otp, purpose);
                if (!rs.next()) {
                    sendJson(ex, 400, "{\"error\":\"Invalid or expired code\"}"); return;
                }
                int otpId = rs.getInt("id");
                update("UPDATE otp_verifications SET used=TRUE WHERE id=?", otpId);
                rememberVerifiedOtp(email, purpose);
                sendJson(ex, 200, "{\"verified\":true}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ==========================================================
    // AUTH HANDLERS
    // ==========================================================

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            try {
                Map<String, String> b = parseJson(readBody(ex));
                String username = normalize(b.get("username"));
                String password = b.get("password");
                if (username == null || password == null) {
                    sendJson(ex, 400, "{\"error\":\"Username and password required\"}"); return;
                }
                ResultSet rs = query(
                    "SELECT u.id, u.username, u.role, u.password," +
                    " COALESCE(t.full_name, s.full_name, u.username) AS full_name," +
                    " s.id AS student_profile_id, t.id AS teacher_profile_id, s.student_id AS student_number" +
                    " FROM users u" +
                    " LEFT JOIN teachers t ON t.user_id = u.id AND COALESCE(t.is_archived,FALSE)=FALSE" +
                    " LEFT JOIN students s ON s.user_id = u.id AND COALESCE(s.is_archived,FALSE)=FALSE" +
                    " WHERE u.username = ? AND COALESCE(u.is_archived,FALSE)=FALSE", username);
                if (rs.next()) {
                    String role = rs.getString("role");
                    if (("student".equals(role) && isBlank(rs.getString("student_profile_id"))) ||
                        ("teacher".equals(role) && isBlank(rs.getString("teacher_profile_id")))) {
                        sendJson(ex, 401, "{\"error\":\"Invalid credentials\"}"); return;
                    }
                    String storedPassword = rs.getString("password");
                    if (!verifyPassword(password, storedPassword)) {
                        sendJson(ex, 401, "{\"error\":\"Invalid credentials\"}"); return;
                    }
                    upgradeLegacyPasswordIfNeeded(rs.getString("id"), password, storedPassword);
                    String token = UUID.randomUUID().toString();
                    Map<String, String> user = new HashMap<>();
                    user.put("id",        rs.getString("id"));
                    user.put("username",  rs.getString("username"));
                    user.put("role",      role);
                    user.put("full_name", rs.getString("full_name"));
                    String profileId = role.equals("student")
                        ? rs.getString("student_profile_id")
                        : role.equals("teacher")
                            ? rs.getString("teacher_profile_id")
                            : rs.getString("id");
                    user.put("profile_id", profileId == null ? "" : profileId);
                    user.put("student_number", rs.getString("student_number") == null ? "" : rs.getString("student_number"));
                    tokenStore.put(token, user);
                    persistSession(user.get("id"), token);
                    sendJson(ex, 200,
                        "{\"token\":\"" + token + "\"" +
                        ",\"role\":\""  + user.get("role")      + "\"" +
                        ",\"name\":\""  + user.get("full_name") + "\"" +
                        ",\"id\":\""    + user.get("id")        + "\"" +
                        ",\"profile_id\":\"" + user.get("profile_id") + "\"" +
                        ",\"student_number\":\"" + user.get("student_number") + "\"}");
                } else {
                    sendJson(ex, 401, "{\"error\":\"Invalid credentials\"}");
                }
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            try {
                Map<String, String> b = parseJson(readBody(ex));
                String username = normalize(b.get("username"));
                String password = b.get("password");
                String role     = normalize(b.getOrDefault("role", "student")).toLowerCase(Locale.ROOT);
                String fullName = toNameCase(b.getOrDefault("full_name", username));
                String email    = normalizeEmail(b.getOrDefault("email", ""));
                String phone    = normalize(b.getOrDefault("phone", ""));
                String birthDate = normalize(b.getOrDefault("date_of_birth", ""));
                String province = normalizeAddressComponent(b.getOrDefault("province", "LAGUNA")).toUpperCase(Locale.ROOT);
                String municipality = normalizeAddressComponent(b.getOrDefault("municipality", ""));
                String barangay = normalizeAddressComponent(b.getOrDefault("barangay", ""));
                String yearLevel = normalize(b.getOrDefault("year_level", "1"));
                int age = calculateAgeFromBirthDate(birthDate);
                if (username.isEmpty() || password == null || password.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Username and password are required\"}"); return;
                }
                require(isValidUsername(username), "Username must be 3-30 characters using letters, numbers, or underscores only");
                require(password.length() >= 6, "Password must be at least 6 characters");
                require(isAllowedSelfRegisterRole(role), "Only student or teacher accounts can self-register");
                require(isValidFullName(fullName), "Enter a valid full name");
                requireValidEmail(email);
                require(!isBlank(phone), "Phone number is required");
                require(isValidPhone(phone), "Enter a valid phone number");
                require(phone.length() == 11, "Phone number must be exactly 11 digits");
                require(isValidBirthDate(birthDate), getBirthDateValidationError(birthDate));
                require(isLagunaProvince(province), "Province must be LAGUNA");
                String municipalityError = getAddressComponentError("Municipality or city", municipality, 80);
                require(municipalityError == null, municipalityError);
                String barangayError = getAddressComponentError("Barangay", barangay, 100);
                require(barangayError == null, barangayError);
                if (role.equals("student")) {
                    require(isValidYearLevel(yearLevel), "Year level must be 1st to 4th year");
                    require(isValidCourse(b.get("course")), "Enter a valid course");
                    require(isValidSection(b.get("section")), "Enter a valid section");
                } else {
                    require(!isBlank(b.get("department")), "Department is required");
                    require(!isBlank(b.get("subject")), "Subject is required");
                }
                if (!consumeVerifiedOtp(email, "register")) {
                    sendJson(ex, 400, "{\"error\":\"Verify the OTP sent to your email before creating the account\"}"); return;
                }
                if (usernameExists(username, null)) {
                    sendJson(ex, 409, "{\"error\":\"Username already taken\"}"); return;
                }
                if (hasReachedEmailUsageLimit(email, null)) {
                    sendJson(ex, 409, "{\"error\":\"" + escapeJson(getEmailUsageLimitMessage()) + "\"}"); return;
                }
                int uid = update("INSERT INTO users(username,password,email,role) VALUES(?,?,?,?)",
                    username, hashPassword(password), email, role);
                if (role.equals("student")) {
                    String sid = generateStudentId(yearLevel);
                    update("INSERT INTO students(user_id,student_id,full_name,email,phone,date_of_birth,age,province,municipality,barangay,course,year_level,section) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        uid, sid, fullName, email,
                        phone, java.sql.Date.valueOf(birthDate), age, province, municipality, barangay,
                        normalizeCourse(b.getOrDefault("course","")), yearLevel, normalizeSection(b.getOrDefault("section","")));
                    createNotificationForRoles(
                        "New student registered: " + fullName + " (" + sid + ").",
                        "info",
                        "admin", "teacher"
                    );
                    sendJson(ex, 201,
                        "{\"message\":\"Account created successfully\"" +
                        ",\"student_id\":\"" + escapeJson(sid) + "\"}");
                    return;
                } else if (role.equals("teacher")) {
                    update("INSERT INTO teachers(user_id,full_name,email,phone,date_of_birth,age,province,municipality,barangay,department,subject) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        uid, fullName, email,
                        phone, java.sql.Date.valueOf(birthDate), age, province, municipality, barangay,
                        normalize(b.getOrDefault("department","")), normalize(b.getOrDefault("subject","")));
                    createNotificationForRoles(
                        "New teacher registered: " + fullName + ".",
                        "info",
                        "admin", "teacher"
                    );
                }
                sendJson(ex, 201, "{\"message\":\"Account created successfully\"}");
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            try {
                if (auth != null && auth.startsWith("Bearer ")) clearSession(auth.substring(7));
                sendJson(ex, 200, "{\"message\":\"Logged out\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class ResetPasswordHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            try {
                Map<String, String> b = parseJson(readBody(ex));
                String email   = normalizeEmail(b.get("email"));
                String newPass = b.get("new_password");
                if (email.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Email is required\"}"); return;
                }
                String emailError = getEmailValidationError(email);
                if (emailError != null) {
                    sendJson(ex, 400, "{\"error\":\"" + escapeJson(emailError) + "\"}"); return;
                }
                if (newPass == null || newPass.length() < 6) {
                    sendJson(ex, 400, "{\"error\":\"Password must be at least 6 characters\"}"); return;
                }
                ResultSet rs = query("SELECT id FROM users WHERE LOWER(email)=?", email);
                if (!rs.next()) {
                    sendJson(ex, 404, "{\"error\":\"No account found with that email\"}"); return;
                }
                if (!consumeVerifiedOtp(email, "reset_password")) {
                    sendJson(ex, 400, "{\"error\":\"Verify the OTP before resetting your password\"}"); return;
                }
                update("UPDATE users SET password=? WHERE LOWER(email)=?", hashPassword(newPass), email);
                sendJson(ex, 200, "{\"message\":\"Password reset successfully.\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class ChangePasswordHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                Map<String, String> b = parseJson(readBody(ex));
                String currentPass = b.get("current_password");
                String newPass     = b.get("new_password");
                if (currentPass == null || currentPass.isEmpty()) {
                    sendJson(ex, 400, "{\"error\":\"Current password is required\"}"); return;
                }
                if (newPass == null || newPass.length() < 6) {
                    sendJson(ex, 400, "{\"error\":\"Password must be at least 6 characters\"}"); return;
                }
                if (currentPass.equals(newPass)) {
                    sendJson(ex, 400, "{\"error\":\"New password must be different from the current password\"}"); return;
                }
                ResultSet rs = query("SELECT password FROM users WHERE id=?", user.get("id"));
                if (!rs.next() || !verifyPassword(currentPass, rs.getString("password"))) {
                    sendJson(ex, 400, "{\"error\":\"Current password is incorrect\"}"); return;
                }
                update("UPDATE users SET password=? WHERE id=?", hashPassword(newPass), user.get("id"));
                sendJson(ex, 200, "{\"message\":\"Password updated successfully.\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ==========================================================
    // PROFILE HANDLER
    // ==========================================================
    static class ProfileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                String role = user.get("role");
                String uid  = user.get("id");
                if (ex.getRequestMethod().equals("GET")) {
                    ResultSet rs;
                    if (role.equals("student")) {
                        rs = query("SELECT s.*, u.username, u.email AS account_email FROM students s JOIN users u ON u.id=s.user_id WHERE s.user_id=?", uid);
                    } else if (role.equals("teacher")) {
                        rs = query("SELECT t.*, u.username, u.email AS account_email FROM teachers t JOIN users u ON u.id=t.user_id WHERE t.user_id=?", uid);
                    } else {
                        rs = query("SELECT id,username,email,role,created_at FROM users WHERE id=?", uid);
                    }
                    String json = resultToJson(rs);
                    if (json.startsWith("[{")) json = json.substring(1, json.length() - 1);
                    sendJson(ex, 200, json);
                } else if (ex.getRequestMethod().equals("PUT")) {
                    Map<String, String> b = parseJson(readBody(ex));
                    String fullName = toNameCase(b.getOrDefault("full_name", ""));
                    String email    = normalizeEmail(b.getOrDefault("email", ""));
                    String phone    = normalize(b.getOrDefault("phone", ""));
                    String birthDate = normalize(b.getOrDefault("date_of_birth", ""));
                    String province = normalizeAddressComponent(b.getOrDefault("province", "LAGUNA")).toUpperCase(Locale.ROOT);
                    String municipality = normalizeAddressComponent(b.getOrDefault("municipality", ""));
                    String barangay = normalizeAddressComponent(b.getOrDefault("barangay", ""));
                    String picture  = b.get("profile_picture");
                    require(isValidFullName(fullName), "Enter a valid full name");
                    requireValidEmail(email);
                    require(isValidPhone(phone), "Enter a valid phone number");
                    require(isValidBirthDate(birthDate), getBirthDateValidationError(birthDate));
                    require(isLagunaProvince(province), "Province must be LAGUNA");
                    String municipalityError = getAddressComponentError("Municipality or city", municipality, 80);
                    require(municipalityError == null, municipalityError);
                    String barangayError = getAddressComponentError("Barangay", barangay, 100);
                    require(barangayError == null, barangayError);
                    int age = calculateAgeFromBirthDate(birthDate);
                    if (!consumeVerifiedOtp(email, "profile_update")) {
                        sendJson(ex, 400, "{\"error\":\"Verify the OTP before saving profile changes\"}"); return;
                    }
                    if (hasReachedEmailUsageLimit(email, uid)) {
                        sendJson(ex, 409, "{\"error\":\"" + escapeJson(getEmailUsageLimitMessage()) + "\"}"); return;
                    }
                    if (role.equals("student")) {
                        String course = normalizeCourse(b.getOrDefault("course",""));
                        String yearLevel = normalize(b.getOrDefault("year_level","1"));
                        String section = normalizeSection(b.getOrDefault("section",""));
                        require(isValidCourse(course), "Enter a valid course");
                        require(isValidYearLevel(yearLevel), "Year level must be 1st to 4th year");
                        require(isValidSection(section), "Enter a valid section");
                        if (picture != null && !picture.isEmpty()) {
                            update("UPDATE students SET full_name=?,email=?,phone=?,date_of_birth=?,age=?,province=?,municipality=?,barangay=?,course=?,year_level=?,section=?,profile_picture=? WHERE user_id=?",
                                fullName, email, phone, java.sql.Date.valueOf(birthDate), age, province, municipality, barangay, course, yearLevel, section, picture, uid);
                        } else {
                            update("UPDATE students SET full_name=?,email=?,phone=?,date_of_birth=?,age=?,province=?,municipality=?,barangay=?,course=?,year_level=?,section=? WHERE user_id=?",
                                fullName, email, phone, java.sql.Date.valueOf(birthDate), age, province, municipality, barangay, course, yearLevel, section, uid);
                        }
                    } else if (role.equals("teacher")) {
                        String department = normalize(b.getOrDefault("department",""));
                        String subject = normalize(b.getOrDefault("subject",""));
                        require(!department.isEmpty(), "Department is required");
                        require(!subject.isEmpty(), "Subject is required");
                        if (picture != null && !picture.isEmpty()) {
                            update("UPDATE teachers SET full_name=?,email=?,phone=?,date_of_birth=?,age=?,province=?,municipality=?,barangay=?,department=?,subject=?,profile_picture=? WHERE user_id=?",
                                fullName, email, phone, java.sql.Date.valueOf(birthDate), age, province, municipality, barangay, department, subject, picture, uid);
                        } else {
                            update("UPDATE teachers SET full_name=?,email=?,phone=?,date_of_birth=?,age=?,province=?,municipality=?,barangay=?,department=?,subject=? WHERE user_id=?",
                                fullName, email, phone, java.sql.Date.valueOf(birthDate), age, province, municipality, barangay, department, subject, uid);
                        }
                    }
                    update("UPDATE users SET email=? WHERE id=?", email, uid);
                    String auth = ex.getRequestHeaders().getFirst("Authorization");
                    if (auth != null && auth.startsWith("Bearer ")) {
                        Map<String, String> tok = tokenStore.get(auth.substring(7));
                        if (tok != null) tok.put("full_name", fullName);
                    }
                    sendJson(ex, 200, "{\"message\":\"Profile updated\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ==========================================================
    // CRUD HANDLERS
    // ==========================================================

    static class StudentsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
            try {
                String method = ex.getRequestMethod();
                if (method.equals("GET")) {
                    Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                    String subjectId = normalize(qs.get("subject_id"));
                    boolean archivedOnly = isTruthy(qs.get("archived"));
                    if (!subjectId.isEmpty()) {
                        if (hasRole(user, "teacher") && !subjectAccessibleToUser(user, subjectId)) {
                            sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return;
                        }
                        sendJson(ex, 200, resultToJson(query(
                            "SELECT st.* FROM students st JOIN subjects sub ON sub.id=?" +
                            " WHERE (NULLIF(TRIM(COALESCE(sub.course,'')), '') IS NULL OR UPPER(sub.course)=UPPER(COALESCE(st.course,'')))" +
                            " AND (sub.year_level IS NULL OR sub.year_level=st.year_level)" +
                            " AND (NULLIF(TRIM(COALESCE(sub.section,'')), '') IS NULL OR UPPER(sub.section)=UPPER(COALESCE(st.section,'')))" +
                            " AND COALESCE(st.is_archived,FALSE)=FALSE" +
                            " ORDER BY st.full_name",
                            subjectId
                        )));
                    } else {
                        sendJson(ex, 200, resultToJson(query(
                            "SELECT s.* FROM students s WHERE COALESCE(s.is_archived,FALSE)=? ORDER BY full_name",
                            archivedOnly
                        )));
                    }
                } else if (method.equals("POST")) {
                    Map<String, String> b = parseJson(readBody(ex));
                    String studentId = normalize(b.get("student_id"));
                    String fullName = toNameCase(b.get("full_name"));
                    String email = normalizeEmail(b.getOrDefault("email",""));
                    String phone = normalize(b.getOrDefault("phone",""));
                    String course = normalizeCourse(b.getOrDefault("course",""));
                    String yearLevel = normalize(b.getOrDefault("year_level","1"));
                    String section = normalizeSection(b.getOrDefault("section",""));
                    require(isValidStudentNumber(studentId), "Student ID must look like 251-1234");
                    require(isValidFullName(fullName), "Enter a valid full name");
                    requireValidEmail(email);
                    require(isValidPhone(phone), "Enter a valid phone number");
                    require(isValidCourse(course), "Enter a valid course");
                    require(isValidYearLevel(yearLevel), "Year level must be 1st to 4th year");
                    require(isValidSection(section), "Enter a valid section");
                    require(!usernameExists(studentId, null), "Student ID is already used as a username");
                    require(!hasReachedEmailUsageLimit(email, null), getEmailUsageLimitMessage());
                    require(!recordExists("SELECT id FROM students WHERE student_id=? LIMIT 1", studentId), "Student ID already exists");
                    int uid = update("INSERT INTO users(username,password,email,role) VALUES(?,?,?,?)",
                        studentId, hashPassword(studentId), email, "student");
                    update("INSERT INTO students(user_id,student_id,full_name,email,phone,course,year_level,section) VALUES(?,?,?,?,?,?,?,?)",
                        uid, studentId, fullName, email, phone, course, yearLevel, section);
                    createNotificationForRoles(
                        "New student account added: " + fullName + " (" + studentId + ").",
                        "info",
                        "admin", "teacher"
                    );
                    sendJson(ex, 201, "{\"message\":\"Student added\"}");
                } else if (method.equals("PUT")) {
                    Map<String, String> b = parseJson(readBody(ex));
                    String id = normalize(b.get("id"));
                    String action = normalize(b.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
                    if (action.equals("restore")) {
                        require(isPositiveInteger(id), "Student id is required");
                        String linkedUserId = findLinkedUserId("students", id);
                        restoreRecord("students", id);
                        restoreUserAccount(linkedUserId);
                        sendJson(ex, 200, "{\"message\":\"Student restored\"}");
                        return;
                    }
                    String fullName = toNameCase(b.get("full_name"));
                    String email = normalizeEmail(b.getOrDefault("email",""));
                    String phone = normalize(b.getOrDefault("phone",""));
                    String course = normalizeCourse(b.getOrDefault("course",""));
                    String yearLevel = normalize(b.getOrDefault("year_level","1"));
                    String section = normalizeSection(b.getOrDefault("section",""));
                    require(isPositiveInteger(id), "Student id is required");
                    require(isValidFullName(fullName), "Enter a valid full name");
                    requireValidEmail(email);
                    require(isValidPhone(phone), "Enter a valid phone number");
                    require(isValidCourse(course), "Enter a valid course");
                    require(isValidYearLevel(yearLevel), "Year level must be 1st to 4th year");
                    require(isValidSection(section), "Enter a valid section");
                    ResultSet linked = query("SELECT user_id FROM students WHERE id=?", id);
                    if (!linked.next()) {
                        sendJson(ex, 404, "{\"error\":\"Student not found\"}"); return;
                    }
                    String linkedUserId = linked.getString("user_id");
                    if (hasReachedEmailUsageLimit(email, linkedUserId)) {
                        sendJson(ex, 409, "{\"error\":\"" + escapeJson(getEmailUsageLimitMessage()) + "\"}"); return;
                    }
                    update("UPDATE students SET full_name=?,email=?,phone=?,course=?,year_level=?,section=? WHERE id=?",
                        fullName, email, phone, course, yearLevel, section, id);
                    update("UPDATE users SET email=? WHERE id=?", email, linkedUserId);
                    sendJson(ex, 200, "{\"message\":\"Student updated\"}");
                } else if (method.equals("DELETE")) {
                    String id = normalize(parseQuery(ex.getRequestURI().getQuery()).get("id"));
                    require(isPositiveInteger(id), "Student id is required");
                    String linkedUser = findLinkedUserId("students", id);
                    archiveRecord("students", id);
                    archiveUserAccount(linkedUser);
                    sendJson(ex, 200, "{\"message\":\"Student archived\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class TeachersHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            if (!hasRole(user, "admin")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
            try {
                String method = ex.getRequestMethod();
                if (method.equals("GET")) {
                    boolean archivedOnly = isTruthy(parseQuery(ex.getRequestURI().getQuery()).get("archived"));
                    sendJson(ex, 200, resultToJson(query(
                        "SELECT * FROM teachers WHERE COALESCE(is_archived,FALSE)=? ORDER BY full_name",
                        archivedOnly
                    )));
                } else if (method.equals("POST")) {
                    Map<String, String> b = parseJson(readBody(ex));
                    String fullName = toNameCase(b.get("full_name"));
                    String email = normalizeEmail(b.getOrDefault("email",""));
                    String phone = normalize(b.getOrDefault("phone",""));
                    String department = normalize(b.getOrDefault("department",""));
                    String subject = normalize(b.getOrDefault("subject",""));
                    require(isValidFullName(fullName), "Enter a valid full name");
                    requireValidEmail(email);
                    require(isValidPhone(phone), "Enter a valid phone number");
                    require(!department.isEmpty(), "Department is required");
                    require(!subject.isEmpty(), "Subject is required");
                    require(!usernameExists(email, null), "Email is already used as a username");
                    require(!hasReachedEmailUsageLimit(email, null), getEmailUsageLimitMessage());
                    int uid = update("INSERT INTO users(username,password,email,role) VALUES(?,?,?,?)",
                        email, hashPassword(email), email, "teacher");
                    update("INSERT INTO teachers(user_id,full_name,email,phone,department,subject) VALUES(?,?,?,?,?,?)",
                        uid, fullName, email, phone, department, subject);
                    createNotificationForRoles(
                        "New teacher account added: " + fullName + ".",
                        "info",
                        "admin", "teacher"
                    );
                    sendJson(ex, 201, "{\"message\":\"Teacher added\"}");
                } else if (method.equals("PUT")) {
                    Map<String, String> b = parseJson(readBody(ex));
                    String id = normalize(b.get("id"));
                    String action = normalize(b.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
                    if (action.equals("restore")) {
                        require(isPositiveInteger(id), "Teacher id is required");
                        String linkedUserId = findLinkedUserId("teachers", id);
                        restoreRecord("teachers", id);
                        restoreUserAccount(linkedUserId);
                        sendJson(ex, 200, "{\"message\":\"Teacher restored\"}");
                        return;
                    }
                    String fullName = toNameCase(b.get("full_name"));
                    String email = normalizeEmail(b.getOrDefault("email",""));
                    String phone = normalize(b.getOrDefault("phone",""));
                    String department = normalize(b.getOrDefault("department",""));
                    String subject = normalize(b.getOrDefault("subject",""));
                    require(isPositiveInteger(id), "Teacher id is required");
                    require(isValidFullName(fullName), "Enter a valid full name");
                    requireValidEmail(email);
                    require(isValidPhone(phone), "Enter a valid phone number");
                    require(!department.isEmpty(), "Department is required");
                    require(!subject.isEmpty(), "Subject is required");
                    ResultSet linked = query("SELECT user_id FROM teachers WHERE id=?", id);
                    if (!linked.next()) {
                        sendJson(ex, 404, "{\"error\":\"Teacher not found\"}"); return;
                    }
                    String linkedUserId = linked.getString("user_id");
                    if (hasReachedEmailUsageLimit(email, linkedUserId)) {
                        sendJson(ex, 409, "{\"error\":\"" + escapeJson(getEmailUsageLimitMessage()) + "\"}"); return;
                    }
                    update("UPDATE teachers SET full_name=?,email=?,phone=?,department=?,subject=? WHERE id=?",
                        fullName, email, phone, department, subject, id);
                    update("UPDATE users SET email=? WHERE id=?", email, linkedUserId);
                    sendJson(ex, 200, "{\"message\":\"Teacher updated\"}");
                } else if (method.equals("DELETE")) {
                    String id = normalize(parseQuery(ex.getRequestURI().getQuery()).get("id"));
                    require(isPositiveInteger(id), "Teacher id is required");
                    String linkedUser = findLinkedUserId("teachers", id);
                    archiveRecord("teachers", id);
                    archiveUserAccount(linkedUser);
                    sendJson(ex, 200, "{\"message\":\"Teacher archived\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class SubjectsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                String method = ex.getRequestMethod();
                if (method.equals("GET")) {
                    Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                    boolean archivedOnly = isTruthy(qs.get("archived"));
                    StringBuilder sql = new StringBuilder(
                        "SELECT s.*, t.full_name AS teacher_name FROM subjects s" +
                        " LEFT JOIN teachers t ON t.id=s.teacher_id"
                    );
                    List<Object> params = new ArrayList<>();
                    sql.append(" WHERE COALESCE(s.is_archived,FALSE)=?");
                    params.add(archivedOnly);
                    if (hasRole(user, "teacher")) {
                        sql.append(" AND s.teacher_id=?");
                        params.add(user.get("profile_id"));
                    } else if (hasRole(user, "student")) {
                        Map<String, String> student = resolveStudent(user.get("profile_id"));
                        if (student == null) {
                            sendJson(ex, 200, "[]");
                            return;
                        }
                        sql.append(" AND (NULLIF(TRIM(COALESCE(s.course,'')), '') IS NULL OR UPPER(s.course)=?)");
                        sql.append(" AND (s.year_level IS NULL OR s.year_level=?)");
                        sql.append(" AND (NULLIF(TRIM(COALESCE(s.section,'')), '') IS NULL OR UPPER(s.section)=?)");
                        params.add(normalizeCourse(student.get("course")));
                        params.add(normalize(student.get("year_level")));
                        params.add(normalizeSection(student.get("section")));
                    }
                    sql.append(" ORDER BY s.code, s.name");
                    sendJson(ex, 200, resultToJson(query(sql.toString(), params.toArray())));
                } else if (method.equals("POST")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    Map<String, String> b = parseJson(readBody(ex));
                    String code = normalizeSubjectCode(b.getOrDefault("code", ""));
                    String name = normalize(b.getOrDefault("name", "")).replaceAll("\\s+", " ");
                    String teacherId = hasRole(user, "teacher") ? user.get("profile_id") : normalize(b.getOrDefault("teacher_id", ""));
                    String course = normalizeCourse(b.getOrDefault("course", ""));
                    String yearLevel = normalize(b.getOrDefault("year_level", ""));
                    String section = normalizeSection(b.getOrDefault("section", ""));
                    int units = parseUnits(b.getOrDefault("units", "3"));

                    require(isValidSubjectCode(code), "Enter a valid subject code");
                    require(isValidSubjectName(name), "Enter a valid subject name");
                    require(isPositiveInteger(teacherId), "Teacher is required");
                    require(recordExists("SELECT id FROM teachers WHERE id=? AND COALESCE(is_archived,FALSE)=FALSE LIMIT 1", teacherId), "Selected teacher does not exist");
                    require(isValidCourse(course), "Enter a valid course");
                    require(isValidYearLevel(yearLevel), "Year level must be 1st to 4th year");
                    require(isValidSection(section), "Enter a valid section");
                    require(!recordExists("SELECT id FROM subjects WHERE UPPER(code)=UPPER(?) LIMIT 1", code), "Subject code already exists");

                    update(
                        "INSERT INTO subjects(code,name,teacher_id,units,course,year_level,section) VALUES(?,?,?,?,?,?,?)",
                        code, name, teacherId, units, course, yearLevel, section
                    );
                    sendJson(ex, 201, "{\"message\":\"Subject added\"}");
                } else if (method.equals("PUT")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    Map<String, String> b = parseJson(readBody(ex));
                    String id = normalize(b.get("id"));
                    String action = normalize(b.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
                    if (action.equals("restore")) {
                        require(isPositiveInteger(id), "Subject id is required");
                        if (hasRole(user, "teacher") && !recordExists("SELECT id FROM subjects WHERE id=? AND teacher_id=? LIMIT 1", id, user.get("profile_id"))) {
                            sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return;
                        }
                        restoreRecord("subjects", id);
                        sendJson(ex, 200, "{\"message\":\"Subject restored\"}");
                        return;
                    }
                    String code = normalizeSubjectCode(b.getOrDefault("code", ""));
                    String name = normalize(b.getOrDefault("name", "")).replaceAll("\\s+", " ");
                    String teacherId = hasRole(user, "teacher") ? user.get("profile_id") : normalize(b.getOrDefault("teacher_id", ""));
                    String course = normalizeCourse(b.getOrDefault("course", ""));
                    String yearLevel = normalize(b.getOrDefault("year_level", ""));
                    String section = normalizeSection(b.getOrDefault("section", ""));
                    int units = parseUnits(b.getOrDefault("units", "3"));

                    require(isPositiveInteger(id), "Subject id is required");
                    if (hasRole(user, "teacher") && !recordExists("SELECT id FROM subjects WHERE id=? AND teacher_id=? LIMIT 1", id, user.get("profile_id"))) {
                        sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return;
                    }

                    require(isValidSubjectCode(code), "Enter a valid subject code");
                    require(isValidSubjectName(name), "Enter a valid subject name");
                    require(isPositiveInteger(teacherId), "Teacher is required");
                    require(recordExists("SELECT id FROM teachers WHERE id=? AND COALESCE(is_archived,FALSE)=FALSE LIMIT 1", teacherId), "Selected teacher does not exist");
                    require(isValidCourse(course), "Enter a valid course");
                    require(isValidYearLevel(yearLevel), "Year level must be 1st to 4th year");
                    require(isValidSection(section), "Enter a valid section");
                    require(!recordExists("SELECT id FROM subjects WHERE UPPER(code)=UPPER(?) AND id<>? LIMIT 1", code, id), "Subject code already exists");

                    update(
                        "UPDATE subjects SET code=?,name=?,teacher_id=?,units=?,course=?,year_level=?,section=? WHERE id=?",
                        code, name, teacherId, units, course, yearLevel, section, id
                    );
                    sendJson(ex, 200, "{\"message\":\"Subject updated\"}");
                } else if (method.equals("DELETE")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    String id = normalize(parseQuery(ex.getRequestURI().getQuery()).get("id"));
                    require(isPositiveInteger(id), "Subject id is required");
                    if (hasRole(user, "teacher") && !recordExists("SELECT id FROM subjects WHERE id=? AND teacher_id=? LIMIT 1", id, user.get("profile_id"))) {
                        sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return;
                    }
                    archiveRecord("subjects", id);
                    sendJson(ex, 200, "{\"message\":\"Subject archived\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class AttendanceHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                String method = ex.getRequestMethod();
                Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                if (method.equals("GET")) {
                    String date = qs.get("date");
                    String from = qs.get("from");
                    String to   = qs.get("to");
                    String sid  = resolveStudentIdForUser(user, qs.get("student_id"));
                    StringBuilder sql = new StringBuilder(
                        "SELECT a.id,a.student_id AS student_profile_id,s.full_name,s.student_id,sub.name AS subject_name,a.subject_id,a.date,a.time_in,a.status,a.method,a.remarks" +
                        " FROM attendance a JOIN students s ON s.id=a.student_id JOIN subjects sub ON sub.id=a.subject_id WHERE COALESCE(s.is_archived,FALSE)=FALSE AND COALESCE(sub.is_archived,FALSE)=FALSE"
                    );
                    List<Object> params = new ArrayList<>();
                    if (date != null && !date.isEmpty()) { sql.append(" AND a.date=?"); params.add(date); }
                    if (from != null && !from.isEmpty()) { sql.append(" AND a.date>=?"); params.add(from); }
                    if (to   != null && !to.isEmpty())   { sql.append(" AND a.date<=?"); params.add(to); }
                    if (sid  != null && !sid.isEmpty())  { sql.append(" AND a.student_id=?"); params.add(sid); }
                    sql.append(" ORDER BY a.date DESC, a.time_in DESC");
                    sendJson(ex, 200, resultToJson(query(sql.toString(), params.toArray())));
                } else if (method.equals("POST")) {
                    Map<String, String> b = parseJson(readBody(ex));
                    String studentRef = hasRole(user, "student") ? user.get("profile_id") : b.get("student_id");
                    Map<String, String> student = resolveStudent(studentRef);
                    if (student == null) { sendJson(ex, 404, "{\"error\":\"Student not found\"}"); return; }

                    String methodName = normalize(b.getOrDefault("method","manual")).toLowerCase(Locale.ROOT);
                    String subjectId = normalize(b.get("subject_id"));
                    String attendanceDate = normalize(b.get("date"));
                    String attendanceStatus = normalize(b.getOrDefault("status","absent")).toLowerCase(Locale.ROOT);
                    require(isAllowedAttendanceMethod(methodName), "Invalid attendance method");
                    require(isPositiveInteger(subjectId), "Subject is required");
                    require(recordExists("SELECT id FROM subjects WHERE id=? AND COALESCE(is_archived,FALSE)=FALSE LIMIT 1", subjectId), "Selected subject does not exist");
                    if (!hasRole(user, "admin") && !subjectAccessibleToUser(user, subjectId)) {
                        sendJson(ex, 403, "{\"error\":\"You cannot use that subject\"}"); return;
                    }
                    require(isValidDate(attendanceDate), "Attendance date must use YYYY-MM-DD format");
                    require(isAllowedAttendanceStatus(attendanceStatus), "Invalid attendance status");
                    if (hasRole(user, "student") && !methodName.equals("geo")) {
                        sendJson(ex, 403, "{\"error\":\"Students can only submit geo attendance\"}"); return;
                    }
                    if (methodName.equals("geo") &&
                        b.get("allowed_latitude") != null && b.get("allowed_longitude") != null &&
                        b.get("latitude") != null && b.get("longitude") != null) {
                        double actualDistance = distanceMeters(
                            Double.parseDouble(b.get("latitude")),
                            Double.parseDouble(b.get("longitude")),
                            Double.parseDouble(b.get("allowed_latitude")),
                            Double.parseDouble(b.get("allowed_longitude"))
                        );
                        double maxDistance = Double.parseDouble(b.getOrDefault("radius_meters", "100"));
                        if (actualDistance > maxDistance) {
                            sendJson(ex, 400, "{\"error\":\"You are outside the allowed geo-fence\"}"); return;
                        }
                    }

                    upsertAttendance(
                        student.get("id"),
                        subjectId,
                        attendanceDate,
                        b.getOrDefault("time_in", currentTime()),
                        attendanceStatus,
                        methodName,
                        b.get("latitude"),
                        b.get("longitude"),
                        b.getOrDefault("remarks",""),
                        hasRole(user, "admin", "teacher") ? user.get("id") : null
                    );
                    String subjectName = findSubjectName(subjectId);
                    String studentName = normalize(student.get("full_name"));
                    createNotificationForRoles(
                        "Attendance saved for " + studentName + " in " + (subjectName.isEmpty() ? "the selected subject" : subjectName) + " on " + attendanceDate + ".",
                        "info",
                        "admin", "teacher"
                    );
                    syncLowAttendanceNotifications();
                    sendJson(ex, 201, "{\"message\":\"Attendance recorded\"}");
                } else if (method.equals("PUT")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    Map<String, String> b = parseJson(readBody(ex));
                    update("UPDATE attendance SET status=?,remarks=? WHERE id=?",
                        b.get("status"), b.getOrDefault("remarks",""), b.get("id"));
                    syncLowAttendanceNotifications();
                    sendJson(ex, 200, "{\"message\":\"Attendance updated\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class MarksHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                String method = ex.getRequestMethod();
                Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                if (method.equals("GET")) {
                    String sid = resolveStudentIdForUser(user, qs.get("student_id"));
                    String sql = "SELECT m.id,m.student_id AS student_profile_id,m.subject_id,m.exam_type,m.score,m.max_score,m.date,m.remarks,s.full_name,s.student_id,sub.name AS subject_name FROM marks m" +
                        " JOIN students s ON s.id=m.student_id JOIN subjects sub ON sub.id=m.subject_id" +
                        " WHERE COALESCE(s.is_archived,FALSE)=FALSE AND COALESCE(sub.is_archived,FALSE)=FALSE";
                    ResultSet rs = sid != null ? query(sql + " AND m.student_id=? ORDER BY m.date DESC", sid) : query(sql + " ORDER BY m.date DESC");
                    sendJson(ex, 200, resultToJson(rs));
                } else if (method.equals("POST")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    Map<String, String> b = parseJson(readBody(ex));
                    Map<String, String> student = resolveStudent(b.get("student_id"));
                    String subjectId = normalize(b.get("subject_id"));
                    String examType = normalize(b.get("exam_type")).toLowerCase(Locale.ROOT);
                    String markDate = normalize(b.get("date"));
                    double score = parseScore(b.get("score"), "Score");
                    double maxScore = parseScore(b.get("max_score"), "Max score");
                    require(student != null, "Student not found");
                    require(isPositiveInteger(subjectId), "Subject is required");
                    require(recordExists("SELECT id FROM subjects WHERE id=? AND COALESCE(is_archived,FALSE)=FALSE LIMIT 1", subjectId), "Selected subject does not exist");
                    if (!hasRole(user, "admin") && !subjectAccessibleToUser(user, subjectId)) {
                        sendJson(ex, 403, "{\"error\":\"You cannot use that subject\"}"); return;
                    }
                    require(isAllowedExamType(examType), "Invalid exam type");
                    require(isValidDate(markDate), "Date must use YYYY-MM-DD format");
                    require(maxScore > 0, "Max score must be greater than zero");
                    require(score >= 0, "Score cannot be negative");
                    require(score <= maxScore, "Score cannot be greater than max score");
                    update("INSERT INTO marks(student_id,subject_id,exam_type,score,max_score,date,remarks) VALUES(?,?,?,?,?,?,?)",
                        student.get("id"), subjectId, examType,
                        score, maxScore, markDate, b.getOrDefault("remarks",""));
                    String subjectName = findSubjectName(subjectId);
                    String studentName = normalize(student.get("full_name"));
                    createNotificationForRoles(
                        "Marks posted for " + studentName + " in " + (subjectName.isEmpty() ? "the selected subject" : subjectName) + " (" + examType + ").",
                        "info",
                        "admin", "teacher"
                    );
                    sendJson(ex, 201, "{\"message\":\"Mark added\"}");
                } else if (method.equals("PUT")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    Map<String, String> b = parseJson(readBody(ex));
                    String id = normalize(b.get("id"));
                    Map<String, String> student = resolveStudent(b.get("student_id"));
                    String subjectId = normalize(b.get("subject_id"));
                    String examType = normalize(b.get("exam_type")).toLowerCase(Locale.ROOT);
                    String markDate = normalize(b.get("date"));
                    double score = parseScore(b.get("score"), "Score");
                    double maxScore = parseScore(b.get("max_score"), "Max score");
                    require(isPositiveInteger(id), "Mark id is required");
                    require(student != null, "Student not found");
                    require(isPositiveInteger(subjectId), "Subject is required");
                    require(recordExists("SELECT id FROM subjects WHERE id=? AND COALESCE(is_archived,FALSE)=FALSE LIMIT 1", subjectId), "Selected subject does not exist");
                    if (!hasRole(user, "admin") && !subjectAccessibleToUser(user, subjectId)) {
                        sendJson(ex, 403, "{\"error\":\"You cannot use that subject\"}"); return;
                    }
                    require(isAllowedExamType(examType), "Invalid exam type");
                    require(isValidDate(markDate), "Date must use YYYY-MM-DD format");
                    require(maxScore > 0, "Max score must be greater than zero");
                    require(score >= 0, "Score cannot be negative");
                    require(score <= maxScore, "Score cannot be greater than max score");
                    update("UPDATE marks SET student_id=?,subject_id=?,exam_type=?,score=?,max_score=?,date=?,remarks=? WHERE id=?",
                        student.get("id"), subjectId, examType,
                        score, maxScore, markDate, b.getOrDefault("remarks",""), id);
                    sendJson(ex, 200, "{\"message\":\"Mark updated\"}");
                } else if (method.equals("DELETE")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    String id = qs.get("id");
                    update("DELETE FROM marks WHERE id=?", id);
                    sendJson(ex, 200, "{\"message\":\"Mark deleted\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class TimetableHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                String method = ex.getRequestMethod();
                if (method.equals("GET")) {
                    StringBuilder sql = new StringBuilder(
                        "SELECT t.*,s.name AS subject_name,s.code,s.course,s.year_level,s.section,te.full_name AS teacher_name" +
                        " FROM timetable t JOIN subjects s ON s.id=t.subject_id" +
                        " LEFT JOIN teachers te ON te.id=s.teacher_id" +
                        " WHERE COALESCE(s.is_archived,FALSE)=FALSE AND COALESCE(te.is_archived,FALSE)=FALSE"
                    );
                    List<Object> params = new ArrayList<>();
                    if (hasRole(user, "teacher")) {
                        sql.append(" AND s.teacher_id=?");
                        params.add(user.get("profile_id"));
                    } else if (hasRole(user, "student")) {
                        Map<String, String> student = resolveStudent(user.get("profile_id"));
                        if (student == null) {
                            sendJson(ex, 200, "[]");
                            return;
                        }
                        sql.append(" AND (NULLIF(TRIM(COALESCE(s.course,'')), '') IS NULL OR UPPER(s.course)=?)");
                        sql.append(" AND (s.year_level IS NULL OR s.year_level=?)");
                        sql.append(" AND (NULLIF(TRIM(COALESCE(s.section,'')), '') IS NULL OR UPPER(s.section)=?)");
                        params.add(normalizeCourse(student.get("course")));
                        params.add(normalize(student.get("year_level")));
                        params.add(normalizeSection(student.get("section")));
                    }
                    sql.append(" ORDER BY FIELD(t.day_of_week,'Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'),t.start_time");
                    sendJson(ex, 200, resultToJson(query(sql.toString(), params.toArray())));
                } else if (method.equals("POST")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    Map<String, String> b = parseJson(readBody(ex));
                    String subjectId = normalize(b.get("subject_id"));
                    String dayOfWeek = normalize(b.get("day_of_week"));
                    String startTime = normalize(b.get("start_time"));
                    String endTime = normalize(b.get("end_time"));
                    String room = normalize(b.getOrDefault("room", ""));
                    require(isPositiveInteger(subjectId), "Subject is required");
                    require(recordExists("SELECT id FROM subjects WHERE id=? AND COALESCE(is_archived,FALSE)=FALSE LIMIT 1", subjectId), "Selected subject does not exist");
                    if (!hasRole(user, "admin") && !subjectAccessibleToUser(user, subjectId)) {
                        sendJson(ex, 403, "{\"error\":\"You cannot schedule that subject\"}"); return;
                    }
                    require(isValidWeekday(dayOfWeek), "Select a valid day");
                    require(isValidTimeValue(startTime), "Start time is invalid");
                    require(isValidTimeValue(endTime), "End time is invalid");
                    require(startTime.compareTo(endTime) < 0, "End time must be after start time");
                    update("INSERT INTO timetable(subject_id,day_of_week,start_time,end_time,room) VALUES(?,?,?,?,?)",
                        subjectId, dayOfWeek, startTime, endTime, room);
                    sendJson(ex, 201, "{\"message\":\"Schedule added\"}");
                } else if (method.equals("DELETE")) {
                    if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                    String id = parseQuery(ex.getRequestURI().getQuery()).get("id");
                    require(isPositiveInteger(id), "Schedule id is required");
                    if (hasRole(user, "teacher") && !recordExists(
                        "SELECT t.id FROM timetable t JOIN subjects s ON s.id=t.subject_id WHERE t.id=? AND s.teacher_id=? LIMIT 1",
                        id, user.get("profile_id")
                    )) {
                        sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return;
                    }
                    update("DELETE FROM timetable WHERE id=?", id);
                    sendJson(ex, 200, "{\"message\":\"Schedule deleted\"}");
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class ReportsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
            try {
                Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                String type = qs.getOrDefault("type", "attendance_summary");
                String from = qs.getOrDefault("from", "2000-01-01");
                String to   = qs.getOrDefault("to", "2099-12-31");
                ResultSet rs;
                if (type.equals("marks_summary")) {
                    rs = query(
                        "SELECT s.student_id, s.full_name, sub.name AS subject, m.exam_type," +
                        " ROUND(AVG((m.score/NULLIF(m.max_score,0))*100),1) AS avg_grade" +
                        " FROM marks m JOIN students s ON s.id=m.student_id JOIN subjects sub ON sub.id=m.subject_id" +
                        " WHERE m.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE AND COALESCE(sub.is_archived,FALSE)=FALSE" +
                        " GROUP BY s.id, sub.id, m.exam_type ORDER BY s.full_name, sub.name, m.exam_type",
                        from, to
                    );
                } else if (type.equals("low_attendance")) {
                    rs = query(
                        "SELECT s.student_id, s.full_name, s.email," +
                        " ROUND(SUM(a.status='present')*100.0/NULLIF(COUNT(a.id),0),1) AS attendance_rate" +
                        " FROM students s JOIN attendance a ON a.student_id=s.id" +
                        " WHERE a.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE" +
                        " GROUP BY s.id HAVING attendance_rate < 75 ORDER BY attendance_rate",
                        from, to
                    );
                } else {
                    rs = query(
                        "SELECT s.student_id, s.full_name, s.course, s.section," +
                        " COUNT(a.id) AS total_days," +
                        " COALESCE(SUM(CASE WHEN a.status='present' THEN 1 ELSE 0 END),0) AS present_days," +
                        " COALESCE(SUM(CASE WHEN a.status='absent' THEN 1 ELSE 0 END),0)  AS absent_days," +
                        " COALESCE(SUM(CASE WHEN a.status='late' THEN 1 ELSE 0 END),0)    AS late_days," +
                        " COALESCE(ROUND(SUM(CASE WHEN a.status='present' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(a.id),0),1),0) AS attendance_rate" +
                        " FROM students s LEFT JOIN attendance a ON a.student_id=s.id AND a.date BETWEEN ? AND ?" +
                        " WHERE COALESCE(s.is_archived,FALSE)=FALSE" +
                        " GROUP BY s.id ORDER BY s.full_name",
                        from, to
                    );
                }
                sendJson(ex, 200, resultToJson(rs));
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                String[] range = resolveDashboardDateRange(qs);
                String from = range[0];
                String to = range[1];
                syncLowAttendanceNotifications();
                ResultSet stats = query(
                    "SELECT (SELECT COUNT(*) FROM students WHERE COALESCE(is_archived,FALSE)=FALSE) AS total_students," +
                    " (SELECT COUNT(*) FROM teachers WHERE COALESCE(is_archived,FALSE)=FALSE) AS total_teachers," +
                    " (SELECT COUNT(*) FROM attendance a JOIN students s ON s.id=a.student_id WHERE a.date BETWEEN ? AND ? AND a.status='present' AND COALESCE(s.is_archived,FALSE)=FALSE) AS present_today," +
                    " (SELECT COUNT(*) FROM attendance a JOIN students s ON s.id=a.student_id WHERE a.date BETWEEN ? AND ? AND a.status='absent' AND COALESCE(s.is_archived,FALSE)=FALSE)  AS absent_today",
                    from, to, from, to);
                ResultSet daily = query(
                    "SELECT DATE_FORMAT(date,'%a') AS day, SUM(status='present') AS present, SUM(status='absent') AS absent" +
                    " FROM attendance a JOIN students s ON s.id=a.student_id" +
                    " WHERE a.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE GROUP BY a.date ORDER BY a.date",
                    from, to);
                ResultSet weekly = query(
                    "SELECT DATE_FORMAT(MIN(date),'%b %d') AS label," +
                    " ROUND(AVG(status='present')*100,1) AS attendance_rate" +
                    " FROM attendance a JOIN students s ON s.id=a.student_id" +
                    " WHERE a.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE" +
                    " GROUP BY YEARWEEK(a.date,1) ORDER BY YEARWEEK(a.date,1)",
                    from, to);
                ResultSet monthly = query(
                    "SELECT DATE_FORMAT(MIN(date),'%b %Y') AS label," +
                    " ROUND(AVG(status='present')*100,1) AS attendance_rate" +
                    " FROM attendance a JOIN students s ON s.id=a.student_id" +
                    " WHERE a.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE" +
                    " GROUP BY YEAR(a.date), MONTH(a.date) ORDER BY YEAR(a.date), MONTH(a.date)",
                    from, to);
                ResultSet performance = query(
                    "SELECT s.full_name, ROUND(AVG((m.score/NULLIF(m.max_score,0))*100),1) AS avg_grade" +
                    " FROM marks m JOIN students s ON s.id=m.student_id JOIN subjects sub ON sub.id=m.subject_id" +
                    " WHERE m.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE AND COALESCE(sub.is_archived,FALSE)=FALSE" +
                    " GROUP BY s.id ORDER BY avg_grade DESC LIMIT 8",
                    from, to);
                ResultSet alerts = query(
                    "SELECT s.full_name, ROUND(SUM(a.status='present')*100.0/NULLIF(COUNT(a.id),0),1) AS rate" +
                    " FROM students s JOIN attendance a ON a.student_id=s.id" +
                    " WHERE a.date BETWEEN ? AND ? AND COALESCE(s.is_archived,FALSE)=FALSE" +
                    " GROUP BY s.id HAVING rate < 75 ORDER BY rate",
                    from, to);
                sendJson(ex, 200,
                    "{\"stats\":" + resultToJson(stats) +
                    ",\"meta\":{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}" +
                    ",\"daily\":" + resultToJson(daily) +
                    ",\"weekly\":" + resultToJson(weekly) +
                    ",\"monthly\":" + resultToJson(monthly) +
                    ",\"performance\":" + resultToJson(performance) +
                    ",\"alerts\":" + resultToJson(alerts) + "}");
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class QRHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                if (ex.getRequestMethod().equals("GET")) {
                    Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                    String ref = qs.get("student_id");
                    if ((ref == null || ref.isEmpty()) && hasRole(user, "student")) ref = user.get("profile_id");
                    Map<String, String> student = resolveStudent(ref);
                    if (student == null) { sendJson(ex, 404, "{\"error\":\"Student not found\"}"); return; }
                    String qrData = student.get("id") + "|" + student.get("student_id") + "|" + student.get("full_name");
                    sendJson(ex, 200,
                        "{\"student_ref\":\"" + student.get("id") + "\"" +
                        ",\"student_id\":\"" + student.get("student_id") + "\"" +
                        ",\"name\":\"" + student.get("full_name").replace("\"","\\\"") + "\"" +
                        ",\"qr_data\":\"" + qrData.replace("\"","\\\"") + "\"}");
                    return;
                }

                if (!hasRole(user, "admin", "teacher")) { sendJson(ex, 403, "{\"error\":\"Forbidden\"}"); return; }
                Map<String, String> b = parseJson(readBody(ex));
                String qrData = b.get("qr_data");
                if (qrData == null || qrData.isEmpty()) { sendJson(ex, 400, "{\"error\":\"qr_data required\"}"); return; }

                String[] parts = qrData.split("\\|");
                String studentRef = parts.length > 0 ? parts[0].trim() : "";
                Map<String, String> student = resolveStudent(studentRef);
                if (student == null) { sendJson(ex, 404, "{\"error\":\"Student not found\"}"); return; }
                String subjectId = normalize(b.get("subject_id"));
                require(isPositiveInteger(subjectId), "Subject is required for QR attendance");
                require(recordExists("SELECT id FROM subjects WHERE id=? LIMIT 1", subjectId), "Selected subject does not exist");
                if (!hasRole(user, "admin") && !subjectAccessibleToUser(user, subjectId)) {
                    sendJson(ex, 403, "{\"error\":\"You cannot use that subject\"}"); return;
                }
                String attendanceDate = b.getOrDefault("date", currentDate());
                String attendanceTime = b.getOrDefault("time_in", currentTime());

                upsertAttendance(
                    student.get("id"),
                    subjectId,
                    attendanceDate,
                    attendanceTime,
                    "present",
                    "qr",
                    null,
                    null,
                    "",
                    user.get("id")
                );
                syncLowAttendanceNotifications();
                sendJson(ex, 200,
                    "{\"message\":\"Attendance marked\",\"name\":\"" + student.get("full_name").replace("\"","\\\"") + "\"" +
                    ",\"student_id\":\"" + student.get("student_id") + "\"" +
                    ",\"date\":\"" + attendanceDate + "\"" +
                    ",\"time_in\":\"" + attendanceTime + "\"}");
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

    static class NotifyHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (ex.getRequestMethod().equals("OPTIONS")) { sendJson(ex, 200, "{}"); return; }
            Map<String, String> user = validateToken(ex);
            if (user == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }
            try {
                syncLowAttendanceNotifications();
                if (ex.getRequestMethod().equals("GET")) {
                    Map<String, String> qs = parseQuery(ex.getRequestURI().getQuery());
                    String from = normalize(qs.get("from"));
                    String to = normalize(qs.get("to"));
                    ResultSet rs;
                    if (!from.isEmpty() || !to.isEmpty()) {
                        String[] range = resolveDashboardDateRange(qs);
                        rs = query(
                            "SELECT * FROM notifications WHERE user_id=? AND DATE(created_at) BETWEEN ? AND ? ORDER BY created_at DESC",
                            user.get("id"), range[0], range[1]
                        );
                    } else {
                        rs = query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC", user.get("id"));
                    }
                    sendJson(ex, 200, resultToJson(rs));
                } else if (ex.getRequestMethod().equals("PUT")) {
                    Map<String, String> body = parseJson(readBody(ex));
                    String id = normalize(body.get("id"));
                    if (isPositiveInteger(id)) {
                        update("UPDATE notifications SET is_read=TRUE WHERE user_id=? AND id=?", user.get("id"), id);
                        sendJson(ex, 200, "{\"message\":\"Notification marked as read\"}");
                    } else {
                        update("UPDATE notifications SET is_read=TRUE WHERE user_id=?", user.get("id"));
                        sendJson(ex, 200, "{\"message\":\"Marked all read\"}");
                    }
                }
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}"); }
        }
    }

}
