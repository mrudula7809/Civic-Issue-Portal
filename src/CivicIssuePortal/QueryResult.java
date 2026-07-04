package CivicIssuePortal;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A generic, UI-friendly snapshot of a query result: column labels plus rows
 * of string values. Used so MainUI can render any query (complaints, reports,
 * resolution history, activity log, ...) in a TableView without needing a
 * dedicated model class per query.
 */
public class QueryResult {
    private final List<String> columns;
    private final List<List<String>> rows;

    public QueryResult(List<String> columns, List<List<String>> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Reads the ResultSet fully into memory. Must be called while the ResultSet is still open. */
    public static QueryResult from(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();

        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            columns.add(md.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                row.add(val == null ? "" : val.toString());
            }
            rows.add(row);
        }

        return new QueryResult(columns, rows);
    }
}