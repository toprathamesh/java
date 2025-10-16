# SQLite Database Access Guide

## Database Location
Your SQLite database is stored at: `C:\Users\prath\java-2\texteditor.db`

## Ways to Access SQLite Files

### 1. Built-in Database Manager (Recommended)
- **Toolbar Button**: Click "DB Manager" button
- **Menu**: File → Database Manager
- **Features**:
  - View all files in database
  - Preview file content
  - Export database
  - Show database path
  - Refresh file list

### 2. Command Line Tools

#### SQLite Command Line (if installed)
```bash
sqlite3 texteditor.db
.tables
SELECT * FROM files;
.quit
```

#### Using our CheckDatabase tool
```bash
java -cp ".;sqlite-jdbc-old.jar" CheckDatabase
```

### 3. GUI Database Tools

#### DB Browser for SQLite (Free)
- Download: https://sqlitebrowser.org/
- Open `texteditor.db` file
- Browse tables, run queries, export data

#### SQLiteStudio (Free)
- Download: https://sqlitestudio.pl/
- Open `texteditor.db` file
- Advanced SQL editor and database management

#### DBeaver (Free)
- Download: https://dbeaver.io/
- Connect to SQLite database
- Professional database management

### 4. Online SQLite Viewers
- SQLite Online: https://sqliteonline.com/
- Upload your `texteditor.db` file
- Run queries and browse data

### 5. Programming Access

#### Java (like our application)
```java
Class.forName("org.sqlite.JDBC");
Connection conn = DriverManager.getConnection("jdbc:sqlite:texteditor.db");
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM files");
```

#### Python
```python
import sqlite3
conn = sqlite3.connect('texteditor.db')
cursor = conn.cursor()
cursor.execute("SELECT * FROM files")
rows = cursor.fetchall()
```

#### Node.js
```javascript
const sqlite3 = require('sqlite3').verbose();
const db = new sqlite3.Database('texteditor.db');
db.all("SELECT * FROM files", (err, rows) => {
    console.log(rows);
});
```

## Database Schema

```sql
CREATE TABLE files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filename TEXT NOT NULL,
    filepath TEXT NOT NULL,
    content TEXT NOT NULL,
    last_modified DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## Common Queries

### List all files
```sql
SELECT filename, filepath, LENGTH(content) as size, last_modified 
FROM files 
ORDER BY last_modified DESC;
```

### Find files by name
```sql
SELECT * FROM files WHERE filename LIKE '%search%';
```

### Get file content
```sql
SELECT content FROM files WHERE filename = 'yourfile.txt';
```

### Count files
```sql
SELECT COUNT(*) as total_files FROM files;
```

### Export all file names
```sql
SELECT filename FROM files;
```

## Backup and Restore

### Backup Database
```bash
cp texteditor.db texteditor_backup.db
```

### Restore Database
```bash
cp texteditor_backup.db texteditor.db
```

## Troubleshooting

### Database Locked
- Close the text editor application
- Wait a few seconds
- Try accessing again

### Database Corrupted
- Delete `texteditor.db` file
- Restart the application
- Database will be recreated automatically

### Permission Issues
- Ensure you have write permissions in the directory
- Run as administrator if needed

## Security Notes

- The database file contains your file contents in plain text
- Keep backups of important data
- Don't share the database file with sensitive information
- Consider encryption for sensitive documents
