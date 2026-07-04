package CivicIssuePortal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("""
                =========================================================
                        WELCOME TO PUNE CIVIC ISSUE PORTAL
                =========================================================""");
        try (Connection conn = DBConnection.getConnection();
             Scanner sc = new Scanner(System.in)) {

            UserActions userActions = new UserActions(conn, sc);
            AdminActions adminActions = new AdminActions(conn, sc);

            outer:
            while (true) {
                System.out.println("\n===== MAIN MENU =====");
                System.out.println("1. Register If You are a New User");
                System.out.println("2. Login If already registered");
                System.out.println("3. Admin Login");
                System.out.println("4. Exit");
                System.out.print("Select one of the above options : ");
                String choice = sc.nextLine().trim();

                switch (choice) {
                    case "1":
                        userActions.registerCitizen();
                        break;
                    case "2":
                        if (userActions.loginUser()) {
                            // user menu
                            userMenuLoop(userActions, sc);
                        }
                        break;
                    case "3":
                        if(adminActions.loginAdmin()){
                            adminMenuLoop(adminActions,sc);
                        }
                        break;
                    case "4":
                        System.out.println("Exiting. Goodbye!");
                        break outer;
                    default:
                        System.out.println("Invalid option. Try again.");
                }
            }

        } catch (SQLException e) {
            System.err.println("Could not connect to DB: " + e.getMessage());
        }
    }

    private static void userMenuLoop(UserActions userActions, Scanner sc) {
        while (true) {
            System.out.println("\n=== USER MENU ===");
            System.out.println("1. File Complaint");
            System.out.println("2. View All Complaints");
            System.out.println("3. View Pending Complaints");
            System.out.println("4. View Resolved Complaints");
            System.out.println("5. Submit Feedback (for a complaint)");
            System.out.println("6. Update Details Of Previously Filed Complaints");
            System.out.println("7. View Activity Log");
            System.out.println("8. Logout");
            System.out.print("Select One Of The Above Options : ");
            String ch = sc.nextLine().trim();
            switch (ch) {
                case "1":
                    userActions.fileComplaint();
                    break;
                case "2":
                    userActions.viewAllComplaints();
                    break;
                case "3":
                    userActions.viewPendingComplaints();
                    break;
                case "4":
                    userActions.viewResolvedComplaints();
                    break;
                case "5":
                    userActions.submitFeedback();
                    break;
                case "6":
                    userActions.updateComplaintPartial();
                    break;
                case "7":
                    userActions.viewActivityLog();
                    break;
                case "8":
                    System.out.println("Logged out.");
                    return;
                default:
                    System.out.println("Invalid choice!!Try again please");
            }
        }
    }

    private static void adminMenuLoop(AdminActions adminActions, Scanner sc) {
        adminActions.showDashboard();
        while (true) {
            System.out.println("\n=== ADMIN MENU ===");
            System.out.println("1. View All Complaints logged by citizens");
            System.out.println("2. View Pending Complaints");
            System.out.println("3. View Resolved Complaints");
            System.out.println("4. Search Complaints by Location");
            System.out.println("5. Generate Reports");
            System.out.println("6. Update resolution status of the complaint");
            System.out.println("7. View Resolution History");
            System.out.println("8. Logout");
            System.out.print("Select: ");
            String ch = sc.nextLine().trim();
            switch (ch) {
                case "1":
                    adminActions.viewAllComplaints();
                    break;
                case "2":
                    adminActions.viewPendingComplaints();
                    break;
                case "3":
                    adminActions.viewResolvedComplaints();
                    break;
                case "4":
                    adminActions.searchComplaintsByLocation();
                    break;
                case "5":
                    adminActions.generateReports();
                    break;
                case "6":
                    adminActions.markComplaintResolved();
                    break;
                case "7":
                    adminActions.viewResolutionHistory();
                    break;
                case "8":
                    System.out.println("Logging Out...");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }
}

