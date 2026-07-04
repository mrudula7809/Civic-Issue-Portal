package CivicIssuePortal;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public class AdminActions {
    private final Connection conn;
    private final Scanner sc;

    // Improvement 2: not read anywhere yet, but kept for future features
    // (e.g. audit logging "resolved by admin_id X") rather than removed outright.
    private int loggedInAdminId = -1;


    public AdminActions(Connection conn, Scanner sc) {
        this.conn = conn;
        this.sc = sc;
    }

    /** GUI-only constructor: no console Scanner needed since GUI methods never read from stdin. */
    public AdminActions(Connection conn) {
        this(conn, null);
    }

    // ---------- Login ----------
    // Improvement 1: don't expose the internal admin_id to the user.
    public boolean loginAdmin() {
        try {
            System.out.println("=== Admin Login ===");
            String email = readEmail();
            String password = readNonEmpty("Password: ");

            String q = "SELECT admin_id FROM AdminLogin WHERE admin_gmail = ? AND admin_password = ?";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, email);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        loggedInAdminId = rs.getInt("admin_id");
                        System.out.println("Admin login successful.");
                        return true;
                    } else {
                        System.out.println("Invalid admin credentials.");
                        return false;
                    }
                }
            }
        } catch (SQLException e) {
            handleSQLException(e);
            return false;
        }
    }

    // ---------- View complaints ----------
    public void viewAllComplaints() {
        String q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed FROM Complaint ORDER BY Date_filed DESC";
        try (PreparedStatement ps = conn.prepareStatement(q);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    public void viewPendingComplaints() {
        String q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed FROM Complaint WHERE Status != 'Resolved' ORDER BY Date_filed DESC";
        try (PreparedStatement ps = conn.prepareStatement(q);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    public void viewResolvedComplaints() {
        String q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed FROM Complaint WHERE Status = 'Resolved' ORDER BY Date_filed DESC";
        try (PreparedStatement ps = conn.prepareStatement(q);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    // ---------- Search by location ----------
    // Improvement 3: readInt() already loops until it gets a valid integer, so
    // NumberFormatException can never reach this method — the extra catch was dead code.
    public void searchComplaintsByLocation() {
        try {
            System.out.println("Search by (1) Area, (2) Pincode, (3) Ward number");
            int opt = readInt("Choose option: ");
            String q = null;
            PreparedStatement ps = null;

            switch (opt) {
                case 1:
                    String area = readNonEmpty("Enter area (partial match allowed): ");
                    q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Pincode, Priority, Status FROM Complaint WHERE Area LIKE ? ORDER BY Date_filed DESC";
                    ps = conn.prepareStatement(q);
                    ps.setString(1, "%" + area + "%");
                    break;
                case 2:
                    String pin = readPincode();
                    q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Pincode, Priority, Status FROM Complaint WHERE Pincode = ? ORDER BY Date_filed DESC";
                    ps = conn.prepareStatement(q);
                    ps.setString(1, pin);
                    break;
                case 3:
                    int ward = readInt("Enter ward number: ");
                    q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Pincode, Priority, Status FROM Complaint WHERE Ward_no = ? ORDER BY Date_filed DESC";
                    ps = conn.prepareStatement(q);
                    ps.setInt(1, ward);
                    break;
                default:
                    System.out.println("Invalid choice.");
                    return;
            }

            try (ResultSet rs = ps.executeQuery()) {
                printResultSet(rs);
            } finally {
                if (ps != null) ps.close();
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    // ---------- Generate simple reports ----------
    // Improvement 4: "7. Back" now actually returns instead of falling into "Invalid option."
    // Improvement 5: date range report uses a validated readDate() helper instead of raw input.
    public void generateReports() {
        try {
            System.out.println("=== Reports ===");
            System.out.println("1. Complaints by Department\n2. Complaints by Status\n3. Complaints by Priority\n4. Complaints by Date Range\n5. Area wise Complaints\n6. Monthly Complaint Report\n7. Back");
            System.out.print("Choose option: ");
            String opt = sc.nextLine().trim();

            switch (opt) {
                case "1":
                    String q1 = """
                            SELECT
                            d.Dept_name,
                            COUNT(*) AS Total_Complaints
                            FROM Complaint c
                            JOIN Department d
                            ON c.Dept_id = d.Dept_id
                            GROUP BY d.Dept_name
                            ORDER BY Total_Complaints DESC;""";
                    try (PreparedStatement ps = conn.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                        printResultSet(rs);
                    }
                    break;
                case "2":
                    String q2 = "SELECT Status, COUNT(*) AS Total_Count FROM Complaint GROUP BY Status";
                    try (PreparedStatement ps = conn.prepareStatement(q2); ResultSet rs = ps.executeQuery()) {
                        printResultSet(rs);
                    }
                    break;
                case "3":
                    String q3 = "SELECT Priority, COUNT(*) AS Total_Count FROM Complaint GROUP BY Priority";
                    try (PreparedStatement ps = conn.prepareStatement(q3); ResultSet rs = ps.executeQuery()) {
                        printResultSet(rs);
                    }
                    break;
                case "4":
                    LocalDate from = readDate("From date (YYYY-MM-DD): ");
                    LocalDate to = readDate("To date (YYYY-MM-DD): ");
                    String q4 = "SELECT Complaint_id, Citizen_id, Complaint_type, Area, Ward_no, Priority, Status, Date_filed FROM Complaint WHERE DATE(Date_filed) BETWEEN ? AND ? ORDER BY Date_filed DESC";
                    try (PreparedStatement ps = conn.prepareStatement(q4)) {
                        ps.setString(1, from.toString());
                        ps.setString(2, to.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            printResultSet(rs);
                        }
                    }
                    break;
                case "5":
                    String q5 = """
                            SELECT
                            Area,
                            COUNT(*) AS Total
                            FROM Complaint
                            GROUP BY Area
                            ORDER BY Total DESC;""";
                    try (PreparedStatement ps = conn.prepareStatement(q5)) {
                        try (ResultSet rs = ps.executeQuery()) {
                            printResultSet(rs);
                        }
                    }
                    break;
                case "6":
                    String q6 = """
                            SELECT
                            MONTHNAME(Date_filed) AS Month,
                            COUNT(*) AS Total
                            FROM Complaint
                            GROUP BY MONTH(Date_filed),
                            MONTHNAME(Date_filed)
                            ORDER BY MONTH(Date_filed);""";
                    try (PreparedStatement ps = conn.prepareStatement(q6)) {
                        try (ResultSet rs = ps.executeQuery()) {
                            printResultSet(rs);
                        }
                    }
                    break;
                case "7":
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }
    }

    // ---------- Run UpdateResolutionStatus stored procedure (synchronize Resolution table with Complaint) ----------
    public void markComplaintResolved() {

        try {

            conn.setAutoCommit(false);

            int complaintId = readInt("Enter Complaint ID : ");

            // ------------------------------
            // Step 1 : Check Complaint
            // ------------------------------

            String checkQuery =
                    "SELECT Complaint_type, Description, Citizen_id, Officer_id, Status FROM Complaint WHERE Complaint_id = ?";

            int officerId = -1;
            String status = null;   // Improvement 6
            String desc = null;   // Improvement 6
            String complaintType = null;   // Improvement 6
            int citizenId = -1;

            try (PreparedStatement ps = conn.prepareStatement(checkQuery)) {

                ps.setInt(1, complaintId);

                try (ResultSet rs = ps.executeQuery()) {   // Improvement 3

                    if (!rs.next()) {
                        System.out.println("Complaint not found.");
                        conn.rollback();
                        return;
                    }

                    officerId = rs.getInt("Officer_id");

                    // Improvement 1 : NULL officer guard
                    if (rs.wasNull()) {
                        System.out.println("No officer has been assigned to this complaint. Cannot resolve.");
                        conn.rollback();
                        return;
                    }

                    status = rs.getString("Status");
                    desc = rs.getString("Description");
                    complaintType = rs.getString("Complaint_type");
                    citizenId = rs.getInt("Citizen_id");

                    System.out.println("\nComplaint Summary");
                    System.out.println("-------------------------");
                    System.out.println("Complaint Type : " + complaintType);
                    System.out.println("Citizen ID     : " + citizenId);
                    System.out.println("Officer ID     : " + officerId);
                    System.out.println("Current Status : " + status);
                    System.out.println("Description    : " + desc);
                    System.out.println("-------------------------");
                }
            }

            if (status.equalsIgnoreCase("Resolved")) {
                System.out.println("Complaint is already resolved.");
                conn.rollback();
                return;
            }

            // Improvement 4 : Confirmation prompt before proceeding (validated loop)
            System.out.println("\nDo you want to continue?");
            System.out.println("1. Yes");
            System.out.println("2. No");

            boolean confirmed = false;
            while (true) {
                String choice = readNonEmpty("Choice : ");

                if (choice.equals("1")) {
                    confirmed = true;
                    break;
                }
                if (choice.equals("2")) {
                    System.out.println("Resolution cancelled.");
                    conn.rollback();
                    return;
                }
                System.out.println("Enter 1 or 2.");
            }

            if (!confirmed) {
                // Defensive; loop above never exits without setting confirmed = true or returning.
                conn.rollback();
                return;
            }

            // ------------------------------
            // Step 2 : Resolution Details
            // ------------------------------

            String resolutionDetails = readNonEmpty("Enter Resolution Details : ");

            // ------------------------------
            // Step 3 : Insert Resolution
            // ------------------------------
            // Extra defensive check: confirm the insert actually wrote a row.

            String insertResolution =
                    "INSERT INTO Resolution " +
                            "(Complaint_id, Officer_id, Resolution_Details, Date_resolved) " +
                            "VALUES (?, ?, ?, CURDATE())";

            try (PreparedStatement ps = conn.prepareStatement(insertResolution)) {
                ps.setInt(1, complaintId);
                ps.setInt(2, officerId);
                ps.setString(3, resolutionDetails);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Resolution record could not be inserted.");
                }
            }

            // ------------------------------
            // Step 4 : Update Complaint
            // ------------------------------
            // Extra defensive check: confirm the update actually changed a row
            // (e.g. catches a Complaint_id that vanished between Step 1 and here).

            String updateComplaint =
                    "UPDATE Complaint " +
                            "SET Status = 'Resolved' " +
                            "WHERE Complaint_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(updateComplaint)) {
                ps.setInt(1, complaintId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Complaint could not be updated.");
                }
            }

            conn.commit();

            // Improvement 5 : Polished success output
            String today = java.time.LocalDate.now().toString();
            System.out.println("\n=========================================");
            System.out.println("   Complaint Successfully Resolved");
            System.out.println("=========================================");
            System.out.println("Complaint ID     : " + complaintId);
            System.out.println("Officer ID       : " + officerId);
            System.out.println("Resolution Date  : " + today);
            System.out.println("=========================================\n");

        } catch (SQLException e) {

            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }

            // Improvement 13 : consistent error handling across the class
            handleSQLException(e);

        } finally {

            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {

            }
        }
    }

    // ---------- Utility: pretty print ResultSet ----------
    private void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();

        // Print header
        for (int i = 1; i <= cols; i++) {
            System.out.print(String.format("%-20s", md.getColumnLabel(i)));
        }
        System.out.println();
        System.out.println("=".repeat(20 * cols));

        // Print rows
        boolean any = false;
        while (rs.next()) {
            any = true;
            for (int i = 1; i <= cols; i++) {
                Object val = rs.getObject(i);
                String out = (val == null) ? "NULL" : val.toString();
                if (out.length() > 50) out = out.substring(0, 40) + "..";
                System.out.print(String.format("%-45s", out));
            }
            System.out.println();
        }
        if (!any) System.out.println("No records found.");
    }

    public void viewResolutionHistory() {

        String query = """
                SELECT
                    r.Resolution_id,
                    c.Complaint_id,
                    ci.Citizen_Name,
                    c.Complaint_type,
                    o.Officer_name,
                    r.Resolution_Details,
                    r.Date_resolved
                FROM Resolution r
                JOIN Complaint c
                    ON r.Complaint_id = c.Complaint_id
                JOIN Citizen ci
                    ON c.Citizen_id = ci.Citizen_id
                JOIN Officer o
                    ON r.Officer_id = o.Officer_id
                ORDER BY r.Date_resolved DESC;
                """;

        try (PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            printResultSet(rs);

        } catch (SQLException e) {
            handleSQLException(e);
        }
    }


    // ---------- Dashboard ----------
    // Improvement 1 & 2 : rs.next() is always called before reading, COUNT(*) uses getInt(),
    // only AVG(Rating) uses getDouble().
    // Improvement 11 : the four Complaint counts are combined into a single query instead of
    // four separate round-trips; the feedback average stays a separate query since it hits
    // a different table.
    public void showDashboard() {

        String summaryQuery = """
                SELECT
                    COUNT(*) AS Total,
                    SUM(Status = 'Pending')  AS PendingCount,
                    SUM(Status = 'Resolved') AS ResolvedCount,
                    SUM(DATE(Date_filed) = CURDATE()) AS TodayCount
                FROM Complaint
                """;

        String avgFeedbackQuery = "SELECT ROUND(AVG(Rating),2) FROM FEEDBACK";

        System.out.println("""
                =========================================
                        ADMIN DASHBOARD
                =========================================""");

        try (PreparedStatement ps = conn.prepareStatement(summaryQuery);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                System.out.println("Total Complaints    : " + rs.getInt("Total"));
                System.out.println("Pending Complaints  : " + rs.getInt("PendingCount"));
                System.out.println("Resolved Complaints : " + rs.getInt("ResolvedCount"));
                System.out.println("Today's Complaints  : " + rs.getInt("TodayCount"));
            } else {
                System.out.println("No complaint data available.");
            }

        } catch (SQLException e) {
            handleSQLException(e);
        }

        try (PreparedStatement ps = conn.prepareStatement(avgFeedbackQuery);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                double avg = rs.getDouble(1);
                if (rs.wasNull()) {
                    System.out.println("Average Feedback Rating : No feedback yet");
                } else {
                    System.out.println("Average Feedback Rating : " + avg);
                }
            } else {
                System.out.println("Average Feedback Rating : No feedback yet");
            }

        } catch (SQLException e) {
            handleSQLException(e);
        }

        System.out.println("=========================================");
    }

    private int readInt(String message) {

        while (true) {

            System.out.print(message);

            String input = sc.nextLine().trim();

            try {

                return Integer.parseInt(input);

            } catch (NumberFormatException e) {

                System.out.println("----------------------------------");
                System.out.println("Invalid input.");
                System.out.println("Please enter a numeric value.");
                System.out.println("----------------------------------");
            }
        }
    }

    private String readEmail() {

        while (true) {

            System.out.print("Admin Email: ");

            String email = sc.nextLine().trim();

            if (email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
                return email;

            System.out.println("Please enter a valid email address.");

        }

    }

    private String readPincode() {

        while (true) {

            System.out.print("Pincode : ");

            String pin = sc.nextLine().trim();

            if (pin.matches("\\d{6}"))
                return pin;

            System.out.println("Pincode must contain exactly 6 digits.");

        }

    }

    private String readNonEmpty(String message) {

        while (true) {

            System.out.print(message);

            String input = sc.nextLine().trim();

            if (!input.isBlank())
                return input;

            System.out.println("This field cannot be empty.");

        }

    }

    // Improvement 5: validated date input using LocalDate.parse(); reprompts on
    // malformed input (e.g. "abc") instead of letting it reach the SQL layer.
    private LocalDate readDate(String message) {

        while (true) {

            System.out.print(message);

            String input = sc.nextLine().trim();

            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println("Please enter a valid date in YYYY-MM-DD format.");
            }

        }

    }

    private void handleSQLException(SQLException e) {

        switch (e.getErrorCode()) {

            case 1062:
                System.out.println("A record with the same details already exists.");
                break;

            case 1452:
                System.out.println("Invalid reference. Related record does not exist.");
                break;

            case 1048:
                System.out.println("Required field cannot be left empty.");
                break;

            case 1406:
                System.out.println("Input is too long for the database field.");
                break;

            case 1264:
                System.out.println("Numeric value is out of allowed range.");
                break;

            default:

                // Handle SIGNAL SQLSTATE '45000'
                if ("45000".equals(e.getSQLState())) {
                    System.out.println(e.getMessage());
                } else {
                    System.out.println("Unexpected database error.");
                    System.out.println("Error Code : " + e.getErrorCode());
                    System.out.println("Message    : " + e.getMessage());
                }
        }
    }

    // ======================================================================
    //  GUI SUPPORT METHODS (used by the JavaFX MainUI)
    //  Same DB logic as the console methods above, but parameterized and
    //  returning/throwing instead of printing, so the UI can display
    //  results in tables and errors in dialogs.
    // ======================================================================

    public boolean isLoggedIn() {
        return loggedInAdminId != -1;
    }

    public void logout() {
        loggedInAdminId = -1;
    }

    public LoginResult loginAdminGui(String email, String password) throws SQLException {
        if (isBlank(email) || isBlank(password)) {
            return new LoginResult(false, "Email and password are required.", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return new LoginResult(false, "Please enter a valid email address.", null);
        }
        String q = "SELECT admin_id FROM AdminLogin WHERE admin_gmail = ? AND admin_password = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, email);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loggedInAdminId = rs.getInt("admin_id");
                    return new LoginResult(true, "Admin login successful.", null);
                }
                return new LoginResult(false, "Invalid admin credentials.", null);
            }
        }
    }

    public QueryResult viewAllComplaintsGui() throws SQLException {
        String q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed FROM Complaint ORDER BY Date_filed DESC";
        try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            return QueryResult.from(rs);
        }
    }

    public QueryResult viewPendingComplaintsGui() throws SQLException {
        String q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed FROM Complaint WHERE Status != 'Resolved' ORDER BY Date_filed DESC";
        try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            return QueryResult.from(rs);
        }
    }

    public QueryResult viewResolvedComplaintsGui() throws SQLException {
        String q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed FROM Complaint WHERE Status = 'Resolved' ORDER BY Date_filed DESC";
        try (PreparedStatement ps = conn.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            return QueryResult.from(rs);
        }
    }

    /** option: 1 = Area (partial match), 2 = Pincode, 3 = Ward number. */
    public QueryResult searchComplaintsByLocationGui(int option, String value) throws SQLException {
        try (PreparedStatement ps = buildLocationSearchStatement(option, value);
             ResultSet rs = ps.executeQuery()) {
            return QueryResult.from(rs);
        }
    }

    private PreparedStatement buildLocationSearchStatement(int option, String value) throws SQLException {
        String q;
        PreparedStatement ps;
        switch (option) {
            case 1:
                if (isBlank(value)) throw new IllegalArgumentException("Enter an area to search.");
                q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Pincode, Priority, Status FROM Complaint WHERE Area LIKE ? ORDER BY Date_filed DESC";
                ps = conn.prepareStatement(q);
                ps.setString(1, "%" + value + "%");
                return ps;
            case 2:
                if (value == null || !value.matches("\\d{6}")) throw new IllegalArgumentException("Pincode must contain exactly 6 digits.");
                q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Pincode, Priority, Status FROM Complaint WHERE Pincode = ? ORDER BY Date_filed DESC";
                ps = conn.prepareStatement(q);
                ps.setString(1, value);
                return ps;
            case 3:
                int ward;
                try {
                    ward = Integer.parseInt(value.trim());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Ward number must be numeric.");
                }
                q = "SELECT Complaint_id, Citizen_id, Complaint_type, Description, Area, Ward_no, Pincode, Priority, Status FROM Complaint WHERE Ward_no = ? ORDER BY Date_filed DESC";
                ps = conn.prepareStatement(q);
                ps.setInt(1, ward);
                return ps;
            default:
                throw new IllegalArgumentException("Invalid search option.");
        }
    }

    /** reportOption: 1=Department, 2=Status, 3=Priority, 4=Date range, 5=Area, 6=Monthly. */
    public QueryResult generateReportGui(int reportOption, String fromDate, String toDate) throws SQLException {
        switch (reportOption) {
            case 1: {
                String q1 = """
                        SELECT d.Dept_name, COUNT(*) AS Total_Complaints
                        FROM Complaint c JOIN Department d ON c.Dept_id = d.Dept_id
                        GROUP BY d.Dept_name ORDER BY Total_Complaints DESC""";
                try (PreparedStatement ps = conn.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                    return QueryResult.from(rs);
                }
            }
            case 2: {
                String q2 = "SELECT Status, COUNT(*) AS Total_Count FROM Complaint GROUP BY Status";
                try (PreparedStatement ps = conn.prepareStatement(q2); ResultSet rs = ps.executeQuery()) {
                    return QueryResult.from(rs);
                }
            }
            case 3: {
                String q3 = "SELECT Priority, COUNT(*) AS Total_Count FROM Complaint GROUP BY Priority";
                try (PreparedStatement ps = conn.prepareStatement(q3); ResultSet rs = ps.executeQuery()) {
                    return QueryResult.from(rs);
                }
            }
            case 4: {
                LocalDate from, to;
                try {
                    from = LocalDate.parse(fromDate.trim());
                    to = LocalDate.parse(toDate.trim());
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Please enter valid dates in YYYY-MM-DD format.");
                }
                String q4 = "SELECT Complaint_id, Citizen_id, Complaint_type, Area, Ward_no, Priority, Status, Date_filed FROM Complaint WHERE DATE(Date_filed) BETWEEN ? AND ? ORDER BY Date_filed DESC";
                try (PreparedStatement ps = conn.prepareStatement(q4)) {
                    ps.setString(1, from.toString());
                    ps.setString(2, to.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        return QueryResult.from(rs);
                    }
                }
            }
            case 5: {
                String q5 = "SELECT Area, COUNT(*) AS Total FROM Complaint GROUP BY Area ORDER BY Total DESC";
                try (PreparedStatement ps = conn.prepareStatement(q5); ResultSet rs = ps.executeQuery()) {
                    return QueryResult.from(rs);
                }
            }
            case 6: {
                String q6 = "SELECT MONTHNAME(Date_filed) AS Month, COUNT(*) AS Total FROM Complaint GROUP BY MONTH(Date_filed), MONTHNAME(Date_filed) ORDER BY MONTH(Date_filed)";
                try (PreparedStatement ps = conn.prepareStatement(q6); ResultSet rs = ps.executeQuery()) {
                    return QueryResult.from(rs);
                }
            }
            default:
                throw new IllegalArgumentException("Invalid report option.");
        }
    }

    /** Holds the complaint details shown to the admin before confirming a resolution. */
    public static class ComplaintSummary {
        public final int complaintId, officerId, citizenId;
        public final String complaintType, status, description;

        public ComplaintSummary(int complaintId, int officerId, int citizenId, String complaintType, String status, String description) {
            this.complaintId = complaintId;
            this.officerId = officerId;
            this.citizenId = citizenId;
            this.complaintType = complaintType;
            this.status = status;
            this.description = description;
        }
    }

    /** Step 1: fetch complaint details so the UI can show a confirmation dialog before resolving. */
    public ComplaintSummary getComplaintSummaryGui(int complaintId) throws SQLException {
        String q = "SELECT Complaint_type, Description, Citizen_id, Officer_id, Status FROM Complaint WHERE Complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Complaint not found.");
                int officerId = rs.getInt("Officer_id");
                if (rs.wasNull()) throw new IllegalArgumentException("No officer has been assigned to this complaint. Cannot resolve.");
                String status = rs.getString("Status");
                String desc = rs.getString("Description");
                String type = rs.getString("Complaint_type");
                int citizenId = rs.getInt("Citizen_id");
                if (status.equalsIgnoreCase("Resolved")) throw new IllegalArgumentException("Complaint is already resolved.");
                return new ComplaintSummary(complaintId, officerId, citizenId, type, status, desc);
            }
        }
    }

    /** Step 2: after the UI confirms (using the summary from getComplaintSummaryGui), mark it resolved. */
    public String resolveComplaintGui(ComplaintSummary summary, String resolutionDetails) throws SQLException {
        if (isBlank(resolutionDetails)) throw new IllegalArgumentException("Resolution details cannot be empty.");

        conn.setAutoCommit(false);
        try {
            String insertResolution = "INSERT INTO Resolution (Complaint_id, Officer_id, Resolution_Details, Date_resolved) VALUES (?, ?, ?, CURDATE())";
            try (PreparedStatement ps = conn.prepareStatement(insertResolution)) {
                ps.setInt(1, summary.complaintId);
                ps.setInt(2, summary.officerId);
                ps.setString(3, resolutionDetails);
                if (ps.executeUpdate() == 0) throw new SQLException("Resolution record could not be inserted.");
            }
            String updateComplaint = "UPDATE Complaint SET Status = 'Resolved' WHERE Complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateComplaint)) {
                ps.setInt(1, summary.complaintId);
                if (ps.executeUpdate() == 0) throw new SQLException("Complaint could not be updated.");
            }
            conn.commit();
            return "Complaint " + summary.complaintId + " resolved on " + LocalDate.now() + ".";
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public QueryResult viewResolutionHistoryGui() throws SQLException {
        String query = """
                SELECT r.Resolution_id, c.Complaint_id, ci.Citizen_Name, c.Complaint_type,
                       o.Officer_name, r.Resolution_Details, r.Date_resolved
                FROM Resolution r
                JOIN Complaint c ON r.Complaint_id = c.Complaint_id
                JOIN Citizen ci ON c.Citizen_id = ci.Citizen_id
                JOIN Officer o ON r.Officer_id = o.Officer_id
                ORDER BY r.Date_resolved DESC""";
        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            return QueryResult.from(rs);
        }
    }

    public DashboardStats getDashboardStatsGui() throws SQLException {
        String summaryQuery = """
                SELECT COUNT(*) AS Total,
                       SUM(Status = 'Pending')  AS PendingCount,
                       SUM(Status = 'Resolved') AS ResolvedCount,
                       SUM(DATE(Date_filed) = CURDATE()) AS TodayCount
                FROM Complaint""";
        int total = 0, pending = 0, resolved = 0, today = 0;
        try (PreparedStatement ps = conn.prepareStatement(summaryQuery); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                total = rs.getInt("Total");
                pending = rs.getInt("PendingCount");
                resolved = rs.getInt("ResolvedCount");
                today = rs.getInt("TodayCount");
            }
        }
        Double avg = null;
        String avgFeedbackQuery = "SELECT ROUND(AVG(Rating),2) FROM FEEDBACK";
        try (PreparedStatement ps = conn.prepareStatement(avgFeedbackQuery); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double a = rs.getDouble(1);
                if (!rs.wasNull()) avg = a;
            }
        }
        return new DashboardStats(total, pending, resolved, today, avg);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}