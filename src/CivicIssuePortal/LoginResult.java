package CivicIssuePortal;

/** Outcome of a GUI login attempt, so MainUI can react without parsing console text. */
public class LoginResult {
    public final boolean success;
    public final String message;
    public final String displayName; // e.g. citizen's name; null if not applicable

    public LoginResult(boolean success, String message, String displayName) {
        this.success = success;
        this.message = message;
        this.displayName = displayName;
    }
}