package CivicIssuePortal;

/** Snapshot of the admin dashboard summary numbers. */
public class DashboardStats {
    public final int total;
    public final int pending;
    public final int resolved;
    public final int today;
    public final Double avgRating; // null if no feedback yet

    public DashboardStats(int total, int pending, int resolved, int today, Double avgRating) {
        this.total = total;
        this.pending = pending;
        this.resolved = resolved;
        this.today = today;
        this.avgRating = avgRating;
    }

    public String avgRatingDisplay() {
        return avgRating == null ? "No feedback yet" : (avgRating + " / 5");
    }
}