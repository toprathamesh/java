import java.sql.*;

public class CheckDatabase {
    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
            return;
        }
        
        String dbUrl = "jdbc:sqlite:texteditor.db";
        
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            System.out.println("Connected to SQLite database successfully!");
            
            // Check if files table exists
            String checkTableSQL = """
                SELECT name FROM sqlite_master 
                WHERE type='table' AND name='files'
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkTableSQL)) {
                
                if (rs.next()) {
                    System.out.println("✓ 'files' table exists");
                } else {
                    System.out.println("✗ 'files' table does not exist");
                    return;
                }
            }
            
            // Count total files
            String countSQL = "SELECT COUNT(*) as total FROM files";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSQL)) {
                
                if (rs.next()) {
                    int totalFiles = rs.getInt("total");
                    System.out.println("Total files in database: " + totalFiles);
                }
            }
            
            // List all files
            String listSQL = """
                SELECT filename, filepath, LENGTH(content) as content_length, 
                       last_modified, created_at 
                FROM files 
                ORDER BY last_modified DESC
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(listSQL)) {
                
                System.out.println("\nFiles in database:");
                System.out.println("=".repeat(80));
                
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("File #" + count + ":");
                    System.out.println("  Name: " + rs.getString("filename"));
                    System.out.println("  Path: " + rs.getString("filepath"));
                    System.out.println("  Size: " + rs.getInt("content_length") + " characters");
                    System.out.println("  Last Modified: " + rs.getString("last_modified"));
                    System.out.println("  Created: " + rs.getString("created_at"));
                    System.out.println();
                }
                
                if (count == 0) {
                    System.out.println("No files found in database.");
                    System.out.println("Try saving a file in the text editor first!");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }
}
