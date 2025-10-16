# Java Text Editor with SQLite Backend

A feature-rich Java Swing text editor with SQLite database integration for file storage and management.

## Features

- **Rich Text Editing**: Full-featured text editor with syntax highlighting
- **SQLite Database Integration**: Automatic file storage and retrieval
- **Multiple Color Themes**: 7 built-in themes including Dark, Ocean Blue, Sunset Orange, Forest Green, Lavender Purple, and Matrix Green
- **File Management**: Open, save, create, and manage files
- **Database Manager**: View and manage files stored in SQLite database
- **Find & Replace**: Advanced search and replace functionality
- **Line Numbers**: Line number gutter for better code navigation
- **Word Wrap**: Toggle word wrapping on/off
- **Undo/Redo**: Full undo/redo support
- **Status Bar**: Real-time cursor position and file information

## Prerequisites

- **Java Development Kit (JDK) 11 or higher**
- **SQLite JDBC Driver** (included)

## Quick Start

### 1. Clone the Repository
```bash
git clone <your-repository-url>
cd java-text-editor
```

### 2. Run the Application

#### Option A: Direct Java Execution (Recommended)
```bash
java -cp ".;sqlite-jdbc-old.jar" TextEditor
```

#### Option B: Compile and Run
```bash
javac -cp ".;sqlite-jdbc-old.jar" TextEditor.java
java -cp ".;sqlite-jdbc-old.jar" TextEditor
```

#### Option C: Using Batch File (Windows)
```bash
run.bat
```

## Detailed Setup Instructions

### Windows
1. Open Command Prompt or PowerShell
2. Navigate to the project directory:
   ```cmd
   cd C:\path\to\java-text-editor
   ```
3. Run the application:
   ```cmd
   java -cp ".;sqlite-jdbc-old.jar" TextEditor
   ```

### Linux/macOS
1. Open Terminal
2. Navigate to the project directory:
   ```bash
   cd /path/to/java-text-editor
   ```
3. Run the application:
   ```bash
   java -cp ".:sqlite-jdbc-old.jar" TextEditor
   ```

## Project Structure

```
java-text-editor/
├── TextEditor.java              # Main application file
├── sqlite-jdbc-old.jar          # SQLite JDBC driver
├── texteditor.db               # SQLite database file (auto-created)
├── pom.xml                     # Maven configuration
├── README.md                   # This file
├── SQLite_Access_Guide.md      # Database documentation
├── check_db.bat                # Database checker script
└── lib/
    └── sqlite-jdbc.jar         # Alternative SQLite driver
```

## Usage Guide

### Basic Operations
- **New File**: `Ctrl+N` or File → New
- **Open File**: `Ctrl+O` or File → Open
- **Save File**: `Ctrl+S` or File → Save
- **Save As**: `Ctrl+Shift+S` or File → Save As

### Color Themes
- Click the "🌈 Colors" button in the toolbar
- Select from 7 available themes:
  - Default (White background)
  - Dark (Dark background)
  - Ocean Blue
  - Sunset Orange
  - Forest Green
  - Lavender Purple
  - Matrix Green (Green on black)

### Database Features
- **Database Manager**: Click "DB Manager" button to view stored files
- **Auto-save**: Files are automatically saved to SQLite database
- **File History**: View previously saved files with timestamps

### Advanced Features
- **Find & Replace**: `Ctrl+F` for find, `Ctrl+H` for replace
- **Word Wrap**: Toggle button in toolbar
- **Line Numbers**: Automatically displayed in left gutter
- **Undo/Redo**: `Ctrl+Z` for undo, `Ctrl+Y` for redo

## Troubleshooting

### Common Issues

#### 1. "NoClassDefFoundError: org/slf4j/LoggerFactory"
**Solution**: Use the older SQLite driver:
```bash
java -cp ".;sqlite-jdbc-old.jar" TextEditor
```

#### 2. "java: command not found"
**Solution**: Install JDK and add to PATH:
- Download JDK from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
- Add Java to your system PATH

#### 3. Database Connection Issues
**Solution**: Ensure `texteditor.db` file is writable and SQLite driver is in classpath.

### System Requirements
- **Java**: JDK 11 or higher
- **Memory**: Minimum 512MB RAM
- **Storage**: 50MB free space
- **OS**: Windows, Linux, or macOS

## Development

### Building from Source
```bash
# Compile all Java files
javac -cp ".;sqlite-jdbc-old.jar" *.java

# Run the application
java -cp ".;sqlite-jdbc-old.jar" TextEditor
```

### Maven Support
If you have Maven installed:
```bash
mvn clean compile exec:java
```

## Database Schema

The application uses SQLite with the following schema:

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

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is open source. Feel free to use, modify, and distribute.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review the SQLite_Access_Guide.md
3. Create an issue in the repository

## Version History

- **v1.0.0**: Initial release with SQLite integration and multiple themes
- Features: Text editing, database storage, color themes, file management

---

**Note**: This application requires Java 11 or higher. The SQLite database file (`texteditor.db`) will be created automatically on first run.