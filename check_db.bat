@echo off
echo Checking SQLite database contents...
java -cp ".;sqlite-jdbc-old.jar" CheckDatabase
pause
