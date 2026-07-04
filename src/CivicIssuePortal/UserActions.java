package CivicIssuePortal;

import java.sql.*;
import java.util.Scanner;

public class UserActions {
    private final Connection conn;
    private final Scanner sc;
    private int loggedInUserId = -1; // user_id column from UserLogin
    private int loggedInCitizenId = -1; // citizen_id from Citizen table (if separate)

    public UserActions(Connection conn, Scanner sc) {
        this.conn = conn;
        this.sc = sc;
    }

    /** GUI-only constructor: no console Scanner needed since GUI methods never read from stdin. */
    public UserActions(Connection conn) {
        this(conn, null);
    }

    // ---------- Registration ----------
    // We call AddCitizen() stored procedure (inserts into Citizen),
    // then create an entry in UserLogin table so the user can log in.
    // Improvement (Must Fix 1): the whole flow is now one try/catch/finally so ANY
    // SQLException — including one thrown by AddCitizen itself — triggers a rollback.
    // Previously an exception from AddCitizen would skip straight to the finally block,
    // and setAutoCommit(true) on an open transaction silently commits whatever was
    // pending instead of rolling it back.
    public void registerCitizen() throws SQLException {
        conn.setAutoCommit(false);
        try {
            System.out.println("=== Citizen Registration ===");
            String name = readNonEmpty("Full Name : ");
            String phone = readPhone();
            String email = readEmail();
            String houseNo = readNonEmpty("House no: ");
            String street = readNonEmpty("Street: ");
            String area = readNonEmpty("Area: ");
            int wardNo = readInt("Ward number: ");
            String pincode = readPincode();

            // Call AddCitizen stored procedure
            try (CallableStatement cs = conn.prepareCall("{CALL AddCitizen(?, ?, ?, ?, ?, ?, ?, ?)}")) {
                cs.setString(1, name);
                cs.setString(2, phone);
                cs.setString(3, email);
                cs.setString(4, houseNo);
                cs.setString(5, street);
                cs.setString(6, area);
                cs.setInt(7, wardNo);
                cs.setString(8, pincode);
                cs.execute();
                System.out.println("Citizen added to Citizen table.");
            }

            // Retrieve the citizen_id we just inserted by querying Citizen table by email
            int citizenId = -1;
            String q = "SELECT Citizen_id FROM Citizen WHERE Email = ? ORDER BY Citizen_id DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) citizenId = rs.getInt("Citizen_id");
                }
            }

            if (citizenId == -1) {
                System.out.println("Could not fetch new citizen id. Rolling back registration.");
                conn.rollback();
                return;
            }

            // Now create an entry in UserLogin so the user can log in (UserLogin table exists)
            String username = readUsername();
            String password = readPassword();

            String insertLogin = "INSERT INTO UserLogin (user_name, gmail, password) VALUES (?, ?, ?)";
            try (PreparedStatement ps2 = conn.prepareStatement(insertLogin)) {
                ps2.setString(1, username);
                ps2.setString(2, email);
                ps2.setString(3, password); // if you want hashing, do it here before insertion
                ps2.executeUpdate();
            }

            conn.commit();
            System.out.println("UserLogin created. You can now login using your email and password.");

        } catch (SQLException e) {
            conn.rollback();
            handleSQLException(e);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // ---------- Login ----------
    // Improvement 6: don't expose internal IDs to the user; greet them by name instead.
    public boolean loginUser() {
        try {
            System.out.println("=== User Login ===");
            String email = readEmail();
            String password = readNonEmpty("Password: ");

            String q = "SELECT user_id FROM UserLogin WHERE gmail = ? AND password = ?";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, email);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        loggedInUserId = rs.getInt("user_id");
                        // Try to fetch matching Citizen_id (and name) if available
                        int citizenId = findCitizenIdByEmail(email);
                        if (citizenId != -1) loggedInCitizenId = citizenId;

                        String name = findCitizenNameByEmail(email);
                        if (name != null) {
                            System.out.println("Login successful. Welcome, " + name + "!");
                        } else {
                            System.out.println("Login successful.");
                        }
                        return true;
                    } else {
                        System.out.println("Invalid credentials.");
                        return false;
                    }
                }
            }
        }catch(SQLException e){
            handleSQLException(e);
            return false;
        }
    }

    private int findCitizenIdByEmail(String email) {
        String q = "SELECT Citizen_id FROM Citizen WHERE Email = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("Citizen_id");
            }
        } catch(SQLException e){
            handleSQLException(e);
        }
        return -1;
    }

    private String findCitizenNameByEmail(String email) {
        String q = "SELECT Citizen_Name FROM Citizen WHERE Email = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Citizen_Name");
            }
        } catch (SQLException e) {
            handleSQLException(e);
        }
        return null;
    }

    // ---------- File Complaint ----------
    public void fileComplaint() {
        if (loggedInCitizenId == -1) {
            System.out.println("You must be associated with a Citizen record (or register) before filing a complaint.");
            return;
        }

        System.out.println("=== File a New Complaint ===");
        String type = readNonEmpty("Complaint Type (e.g., Garbage, Road): ");
        String description = readNonEmpty("Description: ");
        String area = readNonEmpty("Area: ");
        int wardNo = readInt("Ward Number: ");
        String pincode = readPincode();

        // Call FileComplaint stored procedure which returns a SELECT with confirmation
        try (CallableStatement cs = conn.prepareCall("{CALL FileComplaint(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, loggedInCitizenId);
            cs.setString(2, type);
            cs.setString(3, description);
            cs.setString(4, area);
            cs.setInt(5, wardNo);
            cs.setString(6, pincode);

            boolean hasResult = cs.execute();
            // If procedure SELECTs a confirmation message, show it
            if (hasResult) {
                try (ResultSet rs = cs.getResultSet()) {
                    while (rs.next()) {
                        // expected column "Confirmation"
                        try {
                            System.out.println(rs.getString(1));
                        } catch (SQLException ignored) {
                        }
                    }
                }
            } else {
                System.out.println("FileComplaint executed (no confirmation returned).");
            }
        }catch(SQLException e){
            handleSQLException(e);
        }
    }


    //-------------Update complaint details(if any)--------------
    public void updateComplaintPartial() {
        if (loggedInCitizenId == -1) {
            System.out.println("You must be logged in to update complaints.");
            return;
        }
        System.out.println("=== Update Complaint (Specific Field) ===");
        int complaintId = readInt("Enter Complaint ID: ");

        System.out.println("Select field to update:");
        System.out.println("1. Complaint Type");
        System.out.println("2. Description");
        System.out.println("3. Area");
        System.out.println("4. Ward Number");
        System.out.println("5. Pincode");
        System.out.print("Choice: ");
        int choice;

        while(true){
            choice=readInt("Enter choice : ");
            if(choice>=1 && choice<=5)
                break;
            System.out.println("Please choose between 1 and 5.");
        }

        String field;
        switch (choice) {
            case 1 -> field = "Complaint_type";
            case 2 -> field = "Description";
            case 3 -> field = "Area";
            case 4 -> field = "Ward_no";
            case 5 -> field = "Pincode";
            default -> {
                System.out.println("Invalid choice.");
                return;
            }
        }
        String newValue="";
        switch(choice){

            case 1 ->
                    newValue = readNonEmpty("New Complaint Type : ");

            case 2 ->
                    newValue = readNonEmpty("New Description : ");

            case 3 ->
                    newValue = readNonEmpty("New Area : ");

            case 4 ->
                    newValue = String.valueOf(readInt("New Ward Number : "));

            case 5 ->
                    newValue = readPincode();

        }

        try (CallableStatement cs = conn.prepareCall("{CALL UpdateComplaintPartial(?, ?, ?, ?)}")) {
            cs.setInt(1, complaintId);
            cs.setInt(2, loggedInCitizenId);
            cs.setString(3, field);
            cs.setString(4, newValue);

            boolean hasResult = cs.execute();
            if (hasResult) {
                try (ResultSet rs = cs.getResultSet()) {
                    while (rs.next()) System.out.println(rs.getString(1));
                }
            } else {
                System.out.println("Complaint updated successfully.");
            }
        }catch(SQLException e){
            handleSQLException(e);
        }
    }



    // ---------- View complaints (all/pending/resolved for this citizen) ----------
    public void viewAllComplaints() {
        viewComplaintsByStatus(null);
    }

    public void viewPendingComplaints() {
        viewComplaintsByStatus("!= 'Resolved'");
    }

    public void viewResolvedComplaints() {
        viewComplaintsByStatus("= 'Resolved'");
    }

    private void viewComplaintsByStatus(String statusCondition) {

        if (loggedInCitizenId == -1) {
            System.out.println("You must be logged in as a citizen.");
            return;
        }

        String q;

        if (statusCondition == null) {

            // View All Complaints
            q = """
            SELECT Complaint_id,
                   Complaint_type,
                   Description,
                   Area,
                   Ward_no,
                   Priority,
                   Status,
                   Date_filed
            FROM Complaint
            WHERE Citizen_id = ?
            ORDER BY Date_filed DESC
            """;

        }
        else if (statusCondition.equals("= 'Resolved'")) {

            // View Resolved Complaints
            q = """
            SELECT
                c.Complaint_id,
                c.Complaint_type,
                c.Description,
                c.Area,
                c.Ward_no,
                c.Priority,
                c.Status,
                r.Resolution_Details,
                r.Date_resolved,
                o.Officer_name
            FROM Complaint c
            JOIN Resolution r
                ON c.Complaint_id = r.Complaint_id
            JOIN Officer o
                ON r.Officer_id = o.Officer_id
            WHERE c.Citizen_id = ?
              AND c.Status='Resolved'
            ORDER BY r.Date_resolved DESC
            """;

        }
        else {

            // View Pending Complaints
            q = """
            SELECT Complaint_id,
                   Complaint_type,
                   Description,
                   Area,
                   Ward_no,
                   Priority,
                   Status,
                   Date_filed
            FROM Complaint
            WHERE Citizen_id = ?
              AND Status != 'Resolved'
            ORDER BY Date_filed DESC
            """;
        }

        try (PreparedStatement ps = conn.prepareStatement(q)) {

            ps.setInt(1, loggedInCitizenId);

            try (ResultSet rs = ps.executeQuery()) {
                printResultSet(rs);
            }

        }catch(SQLException e){
            handleSQLException(e);
        }
    }

    // ---------- Submit Feedback ----------
    public void submitFeedback() {

        if (loggedInCitizenId == -1) {
            System.out.println("You must be logged in to submit feedback.");
            return;
        }

        try {

            System.out.println("=== Submit Feedback ===");
            int complaintId = readInt("Complaint ID : ");

            // ------------------------------
            // Step 1 : Check complaint belongs to this citizen and is Resolved
            // ------------------------------

            String checkQuery =
                    "SELECT Status, Citizen_id FROM Complaint WHERE Complaint_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(checkQuery)) {

                ps.setInt(1, complaintId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        System.out.println("No complaint found with ID : " + complaintId);
                        return;
                    }

                    int ownerCitizenId = rs.getInt("Citizen_id");
                    String status      = rs.getString("Status");

                    // Make sure the complaint belongs to the logged-in citizen
                    if (ownerCitizenId != loggedInCitizenId) {
                        System.out.println("You can only submit feedback for your own complaints.");
                        return;
                    }

                    // Make sure the complaint is resolved
                    if (!status.equalsIgnoreCase("Resolved")) {
                        System.out.println("Feedback can only be submitted for resolved complaints.");
                        System.out.println("Current status of complaint " + complaintId + " : " + status);
                        return;
                    }
                }
            }

            // ------------------------------
            // Step 2 : Fetch Resolution_id from Resolution table
            // ------------------------------

            int resolutionId = -1;

            String resQuery =
                    "SELECT Resolution_id FROM Resolution WHERE Complaint_id = ? LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(resQuery)) {

                ps.setInt(1, complaintId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        System.out.println("No resolution record found for complaint " + complaintId + ".");
                        System.out.println("Cannot submit feedback without a resolution entry.");
                        return;
                    }

                    resolutionId = rs.getInt("Resolution_id");
                }
            }

            // ------------------------------
            // Step 3 : Collect feedback from user
            // ------------------------------

            int rating = readRating();
            String comments = readNonEmpty("Comments : ");

            // ------------------------------
            // Step 4 : Call SubmitFeedback stored procedure with Resolution_id
            // ------------------------------
            // Expected procedure signature:
            //   CALL SubmitFeedback(complaint_id, citizen_id, comments, rating, resolution_id)

            try (CallableStatement cs = conn.prepareCall("{CALL SubmitFeedback(?, ?, ?, ?, ?)}")) {

                cs.setInt(1, complaintId);
                cs.setInt(2, loggedInCitizenId);
                cs.setString(3, comments);
                cs.setInt(4, rating);
                cs.setInt(5, resolutionId);

                boolean hasResult = cs.execute();

                if (hasResult) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            System.out.println(rs.getString(1));
                        }
                    }
                } else {
                    System.out.println("\n=========================================");
                    System.out.println("   Feedback Submitted Successfully");
                    System.out.println("=========================================");
                    System.out.println("Complaint ID  : " + complaintId);
                    System.out.println("Resolution ID : " + resolutionId);
                    System.out.println("Rating        : " + rating + " / 5");
                    System.out.println("=========================================\n");
                }

            }catch(SQLException e){
                handleSQLException(e);
            }

        }catch (SQLException e) {
            handleSQLException(e);
        }
    }

    // ---------- View Activity Log ----------
    public void viewActivityLog() {
        if (loggedInCitizenId == -1) {
            System.out.println("You must be logged in to view activity log.");
            return;
        }
        String q = "SELECT log_id, complaint_id, dept_id, log_time FROM Activity_Log WHERE citizen_id = ? ORDER BY log_time DESC";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, loggedInCitizenId);
            try (ResultSet rs = ps.executeQuery()) {
                printResultSet(rs);
            }
        } catch(SQLException e){
            handleSQLException(e);
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

    private int readInt(String message) {

        while (true) {

            System.out.print(message);

            String input = sc.nextLine().trim();

            try {

                return Integer.parseInt(input);

            }

            catch (NumberFormatException e) {

                System.out.println("----------------------------------");
                System.out.println("Invalid input.");
                System.out.println("Please enter a numeric value.");
                System.out.println("----------------------------------");
            }
        }
    }
    private String readPhone() {

        while(true){

            System.out.print("Phone Number : ");

            String phone=sc.nextLine().trim();

            if(phone.matches("\\d{10}"))
                return phone;

            System.out.println("Phone number must contain exactly 10 digits.");

        }

    }
    private String readEmail(){

        while(true){

            System.out.print("Email : ");

            String email=sc.nextLine().trim();

            if(email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
                return email;

            System.out.println("Please enter a valid email address.");

        }

    }
    private String readPincode(){

        while(true){

            System.out.print("Pincode : ");

            String pin=sc.nextLine().trim();

            if(pin.matches("\\d{6}"))
                return pin;

            System.out.println("Pincode must contain exactly 6 digits.");

        }

    }
    private String readNonEmpty(String message){

        while(true){

            System.out.print(message);

            String input=sc.nextLine().trim();

            if(!input.isBlank())
                return input;

            System.out.println("This field cannot be empty.");

        }

    }

    // Improvement 5: letters, numbers, and underscores only, 3-20 characters.
    private String readUsername() {

        while (true) {

            System.out.print("Create Username: ");

            String username = sc.nextLine().trim();

            if (username.matches("^[A-Za-z0-9_]{3,20}$"))
                return username;

            System.out.println("Username must be 3-20 characters: letters, numbers, and underscores only.");

        }

    }

    // Improvement 2: minimum length check on password (kept as plain text per project scope;
    // hash before insertion if this ever goes beyond a college project).
    private String readPassword() {

        while (true) {

            System.out.print("Create password: ");

            String password = sc.nextLine().trim();

            if (password.length() >= 6)
                return password;

            System.out.println("Password must be at least 6 characters long.");

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
                }
                else {
                    System.out.println("Unexpected database error.");
                    System.out.println("Error Code : " + e.getErrorCode());
                    System.out.println("Message    : " + e.getMessage());
                }
        }
    }
    private int readRating(){

        while(true){

            int rating=readInt("Rating (1-5) : ");

            if(rating>=1 && rating<=5)
                return rating;

            System.out.println("Rating must be between 1 and 5.");

        }

    }

    // ======================================================================
    //  GUI SUPPORT METHODS (used by the JavaFX MainUI)
    //  These reuse the same connection and session state (loggedInCitizenId)
    //  as the console flow above, but take parameters directly instead of
    //  reading from a Scanner, and return values / throw exceptions instead
    //  of printing to System.out, so a GUI layer can show results and
    //  validation errors in dialogs, labels, and tables.
    // ======================================================================

    public boolean isLoggedIn() {
        return loggedInCitizenId != -1;
    }

    public int getLoggedInCitizenId() {
        return loggedInCitizenId;
    }

    public void logout() {
        loggedInUserId = -1;
        loggedInCitizenId = -1;
    }

    public LoginResult loginUserGui(String email, String password) throws SQLException {
        if (isBlank(email) || isBlank(password)) {
            return new LoginResult(false, "Email and password are required.", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return new LoginResult(false, "Please enter a valid email address.", null);
        }

        String q = "SELECT user_id FROM UserLogin WHERE gmail = ? AND password = ?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, email);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loggedInUserId = rs.getInt("user_id");
                    int citizenId = findCitizenIdByEmail(email);
                    if (citizenId != -1) loggedInCitizenId = citizenId;
                    String name = findCitizenNameByEmail(email);
                    return new LoginResult(true, "Login successful.", name);
                }
                return new LoginResult(false, "Invalid email or password.", null);
            }
        }
    }

    /** Registers a new citizen + login. Returns a success message on completion.
     *  Throws IllegalArgumentException for bad input and SQLException for DB errors. */
    public String registerCitizenGui(String name, String phone, String email, String houseNo,
                                     String street, String area, String wardNoStr, String pincode,
                                     String username, String password) throws SQLException {

        if (isBlank(name) || isBlank(houseNo) || isBlank(street) || isBlank(area)) {
            throw new IllegalArgumentException("All fields are required.");
        }
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Phone number must contain exactly 10 digits.");
        }
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (pincode == null || !pincode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Pincode must contain exactly 6 digits.");
        }
        if (username == null || !username.matches("^[A-Za-z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Username must be 3-20 characters: letters, numbers, and underscores only.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        int wardNo;
        try {
            wardNo = Integer.parseInt(wardNoStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Ward number must be numeric.");
        }

        conn.setAutoCommit(false);
        try {
            try (CallableStatement cs = conn.prepareCall("{CALL AddCitizen(?, ?, ?, ?, ?, ?, ?, ?)}")) {
                cs.setString(1, name);
                cs.setString(2, phone);
                cs.setString(3, email);
                cs.setString(4, houseNo);
                cs.setString(5, street);
                cs.setString(6, area);
                cs.setInt(7, wardNo);
                cs.setString(8, pincode);
                cs.execute();
            }

            int citizenId = -1;
            String q = "SELECT Citizen_id FROM Citizen WHERE Email = ? ORDER BY Citizen_id DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) citizenId = rs.getInt("Citizen_id");
                }
            }
            if (citizenId == -1) {
                conn.rollback();
                throw new SQLException("Could not create citizen record. Registration cancelled.");
            }

            String insertLogin = "INSERT INTO UserLogin (user_name, gmail, password) VALUES (?, ?, ?)";
            try (PreparedStatement ps2 = conn.prepareStatement(insertLogin)) {
                ps2.setString(1, username);
                ps2.setString(2, email);
                ps2.setString(3, password);
                ps2.executeUpdate();
            }

            conn.commit();
            return "Registration successful! You can now log in with your email and password.";
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public String fileComplaintGui(String type, String description, String area, String wardNoStr, String pincode) throws SQLException {
        requireLogin();
        if (isBlank(type) || isBlank(description) || isBlank(area)) {
            throw new IllegalArgumentException("Complaint type, description, and area are required.");
        }
        if (pincode == null || !pincode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Pincode must contain exactly 6 digits.");
        }
        int wardNo;
        try {
            wardNo = Integer.parseInt(wardNoStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Ward number must be numeric.");
        }

        try (CallableStatement cs = conn.prepareCall("{CALL FileComplaint(?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, loggedInCitizenId);
            cs.setString(2, type);
            cs.setString(3, description);
            cs.setString(4, area);
            cs.setInt(5, wardNo);
            cs.setString(6, pincode);
            boolean hasResult = cs.execute();
            if (hasResult) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
            return "Complaint filed successfully.";
        }
    }

    public String updateComplaintPartialGui(String complaintIdStr, String field, String newValue) throws SQLException {
        requireLogin();
        int complaintId;
        try {
            complaintId = Integer.parseInt(complaintIdStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Complaint ID must be numeric.");
        }
        if (isBlank(field) || isBlank(newValue)) {
            throw new IllegalArgumentException("Field and new value are required.");
        }

        try (CallableStatement cs = conn.prepareCall("{CALL UpdateComplaintPartial(?, ?, ?, ?)}")) {
            cs.setInt(1, complaintId);
            cs.setInt(2, loggedInCitizenId);
            cs.setString(3, field);
            cs.setString(4, newValue);
            boolean hasResult = cs.execute();
            if (hasResult) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
            return "Complaint updated successfully.";
        }
    }

    public QueryResult viewAllComplaintsGui() throws SQLException {
        return queryComplaintsByStatus(null);
    }

    public QueryResult viewPendingComplaintsGui() throws SQLException {
        return queryComplaintsByStatus("PENDING");
    }

    public QueryResult viewResolvedComplaintsGui() throws SQLException {
        return queryComplaintsByStatus("RESOLVED");
    }

    private QueryResult queryComplaintsByStatus(String mode) throws SQLException {
        requireLogin();
        String q;
        if (mode == null) {
            q = "SELECT Complaint_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed " +
                    "FROM Complaint WHERE Citizen_id = ? ORDER BY Date_filed DESC";
        } else if (mode.equals("RESOLVED")) {
            q = "SELECT c.Complaint_id, c.Complaint_type, c.Description, c.Area, c.Ward_no, c.Priority, c.Status, " +
                    "r.Resolution_Details, r.Date_resolved, o.Officer_name " +
                    "FROM Complaint c JOIN Resolution r ON c.Complaint_id = r.Complaint_id " +
                    "JOIN Officer o ON r.Officer_id = o.Officer_id " +
                    "WHERE c.Citizen_id = ? AND c.Status='Resolved' ORDER BY r.Date_resolved DESC";
        } else {
            q = "SELECT Complaint_id, Complaint_type, Description, Area, Ward_no, Priority, Status, Date_filed " +
                    "FROM Complaint WHERE Citizen_id = ? AND Status != 'Resolved' ORDER BY Date_filed DESC";
        }

        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, loggedInCitizenId);
            try (ResultSet rs = ps.executeQuery()) {
                return QueryResult.from(rs);
            }
        }
    }

    public String submitFeedbackGui(String complaintIdStr, int rating, String comments) throws SQLException {
        requireLogin();
        int complaintId;
        try {
            complaintId = Integer.parseInt(complaintIdStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Complaint ID must be numeric.");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        if (isBlank(comments)) {
            throw new IllegalArgumentException("Comments cannot be empty.");
        }

        String checkQuery = "SELECT Status, Citizen_id FROM Complaint WHERE Complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkQuery)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No complaint found with ID " + complaintId + ".");
                }
                int ownerCitizenId = rs.getInt("Citizen_id");
                String status = rs.getString("Status");
                if (ownerCitizenId != loggedInCitizenId) {
                    throw new IllegalArgumentException("You can only submit feedback for your own complaints.");
                }
                if (!status.equalsIgnoreCase("Resolved")) {
                    throw new IllegalArgumentException("Feedback can only be submitted for resolved complaints. Current status: " + status);
                }
            }
        }

        int resolutionId;
        String resQuery = "SELECT Resolution_id FROM Resolution WHERE Complaint_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(resQuery)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No resolution record found for complaint " + complaintId + ".");
                }
                resolutionId = rs.getInt("Resolution_id");
            }
        }

        try (CallableStatement cs = conn.prepareCall("{CALL SubmitFeedback(?, ?, ?, ?, ?)}")) {
            cs.setInt(1, complaintId);
            cs.setInt(2, loggedInCitizenId);
            cs.setString(3, comments);
            cs.setInt(4, rating);
            cs.setInt(5, resolutionId);
            boolean hasResult = cs.execute();
            if (hasResult) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
            return "Feedback submitted successfully for complaint " + complaintId + ".";
        }
    }

    public QueryResult viewActivityLogGui() throws SQLException {
        requireLogin();
        String q = "SELECT log_id, complaint_id, dept_id, log_time FROM Activity_Log WHERE citizen_id = ? ORDER BY log_time DESC";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, loggedInCitizenId);
            try (ResultSet rs = ps.executeQuery()) {
                return QueryResult.from(rs);
            }
        }
    }

    private void requireLogin() {
        if (loggedInCitizenId == -1) {
            throw new IllegalStateException("You must be logged in to do this.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}