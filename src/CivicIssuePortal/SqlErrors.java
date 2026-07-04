package CivicIssuePortal;

import java.sql.SQLException;

/** Turns a SQLException into a message safe to show a citizen/admin in a dialog. */
public class SqlErrors {
    public static String describe(SQLException e) {
        switch (e.getErrorCode()) {
            case 1062:
                return "A record with the same details already exists.";
            case 1452:
                return "Invalid reference. Related record does not exist.";
            case 1048:
                return "Required field cannot be left empty.";
            case 1406:
                return "Input is too long for the database field.";
            case 1264:
                return "Numeric value is out of allowed range.";
            default:
                if ("45000".equals(e.getSQLState())) {
                    return e.getMessage();
                }
                return "Unexpected database error: " + e.getMessage();
        }
    }
}