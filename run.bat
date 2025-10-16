@echo off
echo Starting Java Text Editor...
echo.
java -cp ".;sqlite-jdbc-old.jar" TextEditor
if %errorlevel% neq 0 (
    echo.
    echo Error: Failed to start the text editor.
    echo Please ensure Java is installed and the SQLite driver is present.
    echo.
    pause
)
