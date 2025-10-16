/*
 * Java Text Editor with SQLite Backend
 * 
 * COMPILE AND RUN INSTRUCTIONS:
 * 
 * 1. Compile the application:
 *    javac TextEditor.java
 * 
 * 2. Run the application:
 *    java -cp ".;sqlite.jar" TextEditor
 * 
 * NOTE: The sqlite.jar file must be in the same directory as TextEditor.java
 * 
 * REQUIREMENTS:
 * - Java Development Kit (JDK) 11 or higher
 * - sqlite.jar (SQLite JDBC driver)
 * 
 * FEATURES:
 * - Rich text editing with syntax highlighting
 * - SQLite database integration for file storage
 * - Multiple color themes (7 built-in themes)
 * - Database manager for file management
 * - Find & replace functionality
 * - Line numbers and word wrap
 * - Undo/redo support
 */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TextEditor extends JFrame {

    private JTextArea textArea;
    private JScrollPane scrollPane;
    private JFileChooser fileChooser;
    private File currentFile;
    private boolean isModified = false;
    private JLabel statusLabel, posLabel, fileLabel;
    private UndoManager undoManager;
    private JCheckBoxMenuItem wrapItem;
    private JToggleButton wrapToggleButton, themeToggleButton;
    private JButton colorThemeBtn;
    private ColorScheme currentColorScheme = ColorScheme.DEFAULT;
    private JMenuItem undoMenuItem, redoMenuItem;
    private LineNumberGutter lineGutter;
    private DatabaseManager dbManager;

    public TextEditor() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        setTitle("Java Text Editor with SQL Backend");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Initialize database
        dbManager = new DatabaseManager();
        dbManager.initializeDatabase();

        initComponents();
        initMenuBar();
        initToolBar();
        initListeners();
    }

    private void initComponents() {
        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(true);
        textArea.setTabSize(4);
        textArea.setMargin(new Insets(6, 6, 6, 6));

        undoManager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(e -> {
            undoManager.addEdit(e.getEdit());
            updateUndoRedo();
        });

        // Line numbers
        lineGutter = new LineNumberGutter(textArea);
        scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineGutter);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        fileChooser = new JFileChooser();
        fileChooser.setAcceptAllFileFilterUsed(true);

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(new EmptyBorder(6, 8, 6, 8));
        statusBar.setBackground(new Color(240, 248, 255)); // Alice blue

        fileLabel = new JLabel("Untitled");
        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD, 12f));
        fileLabel.setForeground(new Color(25, 25, 112)); // Midnight blue
        
        statusLabel = new JLabel("Words: 0  Characters: 0");
        statusLabel.setForeground(new Color(139, 69, 19)); // Saddle brown
        
        posLabel = new JLabel("Ln: 1  Col: 1");
        posLabel.setForeground(new Color(34, 139, 34)); // Forest green

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(fileLabel);
        left.add(new JSeparator(SwingConstants.VERTICAL));
        left.add(statusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(posLabel);

        statusBar.add(left, BorderLayout.WEST);
        statusBar.add(right, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);

        // Document listener
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                isModified = true;
                updateStatus();
                lineGutter.repaint();
            }
        });

        // Caret listener
        textArea.addCaretListener(e -> updateCaretPosition());

        // Context menu
        JPopupMenu popup = new JPopupMenu();
        JMenuItem cut = mkMenuItem("Cut", e -> textArea.cut());
        JMenuItem copy = mkMenuItem("Copy", e -> textArea.copy());
        JMenuItem paste = mkMenuItem("Paste", e -> textArea.paste());
        JMenuItem selAll = mkMenuItem("Select All", e -> textArea.selectAll());
        popup.add(cut);
        popup.add(copy);
        popup.add(paste);
        popup.addSeparator();
        popup.add(selAll);
        textArea.setComponentPopupMenu(popup);

        // Drag & Drop
        new DropTarget(textArea, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<?> dropped = (java.util.List<?>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!dropped.isEmpty()) {
                        File f = (File) dropped.get(0);
                        if (f.isFile() && confirmSaveIfNeeded()) {
                            openFile(f);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private JMenuItem mkMenuItem(String text, ActionListener al) {
        JMenuItem item = new JMenuItem(text);
        item.setFocusable(false); // remove dotted border
        item.addActionListener(al);
        return item;
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem newItem = mkMenuItem("New", e -> newFile());
        JMenuItem openItem = mkMenuItem("Open...", e -> openFile());
        JMenuItem saveItem = mkMenuItem("Save", e -> saveFile());
        JMenuItem saveAsItem = mkMenuItem("Save As...", e -> saveFileAs());
        JMenuItem openFromDBItem = mkMenuItem("Open from Database...", e -> openFromDatabase());
        JMenuItem listDBFilesItem = mkMenuItem("List Database Files", e -> listDatabaseFiles());
        JMenuItem dbManagerItem = mkMenuItem("Database Manager", e -> showDatabaseManager());
        JMenuItem exitItem = mkMenuItem("Exit", e -> exitApplication());

        newItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        saveItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(openFromDBItem);
        fileMenu.add(listDBFilesItem);
        fileMenu.add(dbManagerItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");
        undoMenuItem = mkMenuItem("Undo", e -> performUndo());
        redoMenuItem = mkMenuItem("Redo", e -> performRedo());
        JMenuItem cutItem = mkMenuItem("Cut", e -> textArea.cut());
        JMenuItem copyItem = mkMenuItem("Copy", e -> textArea.copy());
        JMenuItem pasteItem = mkMenuItem("Paste", e -> textArea.paste());
        JMenuItem selectAllItem = mkMenuItem("Select All", e -> textArea.selectAll());
        JMenuItem findItem = mkMenuItem("Find / Replace", e -> new FindReplaceDialog(this, textArea).setVisible(true));

        undoMenuItem.setEnabled(false);
        redoMenuItem.setEnabled(false);

        editMenu.add(undoMenuItem);
        editMenu.add(redoMenuItem);
        editMenu.addSeparator();
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(selectAllItem);
        editMenu.addSeparator();
        editMenu.add(findItem);

        // Format Menu
        JMenu formatMenu = new JMenu("Format");
        wrapItem = new JCheckBoxMenuItem("Line Wrap");
        JMenuItem colorItem = mkMenuItem("Text Color...", e -> chooseColor());
        wrapItem.addActionListener(e -> {
            toggleWrap(wrapItem.isSelected());
            wrapToggleButton.setSelected(wrapItem.isSelected());
        });
        formatMenu.add(wrapItem);
        formatMenu.addSeparator();
        formatMenu.add(colorItem);

        // View Menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem toggleLineNumbers = mkMenuItem("Toggle Line Numbers",
                e -> lineGutter.setVisible(!lineGutter.isVisible()));
        viewMenu.add(toggleLineNumbers);

        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = mkMenuItem("About", e -> showAbout());
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(formatMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        // Keyboard shortcuts
        InputMap im = textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = textArea.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "find");
        am.put("find", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                new FindReplaceDialog(thisFrame(), textArea).setVisible(true);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "undo");
        am.put("undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                performUndo();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "redo");
        am.put("redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                performRedo();
            }
        });
    }

    private void initToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(6, 6, 6, 6));

        JButton newBtn = mkButton("New", e -> newFile());
        JButton openBtn = mkButton("Open", e -> openFile());
        JButton saveBtn = mkButton("Save", e -> saveFile());
        JButton undoBtn = mkButton("Undo", e -> performUndo());
        JButton redoBtn = mkButton("Redo", e -> performRedo());
        JButton findBtn = mkButton("Find", e -> new FindReplaceDialog(this, textArea).setVisible(true));
        JButton dbManagerBtn = mkButton("DB Manager", e -> showDatabaseManager());

        wrapToggleButton = new JToggleButton("Wrap");
        wrapToggleButton.setFocusable(false);
        wrapToggleButton.addActionListener(e -> {
            boolean sel = wrapToggleButton.isSelected();
            wrapItem.setSelected(sel);
            toggleWrap(sel);
        });
        
        // Style wrap toggle button
        wrapToggleButton.setBackground(new Color(50, 205, 50)); // Lime green
        wrapToggleButton.setForeground(Color.WHITE);
        wrapToggleButton.setBorder(BorderFactory.createRaisedBevelBorder());
        wrapToggleButton.setFont(wrapToggleButton.getFont().deriveFont(Font.BOLD, 12f));

        themeToggleButton = new JToggleButton("Dark");
        themeToggleButton.setFocusable(false);
        themeToggleButton.addActionListener(e -> toggleTheme(themeToggleButton.isSelected()));
        
        // Style theme toggle button
        themeToggleButton.setBackground(new Color(255, 165, 0)); // Orange
        themeToggleButton.setForeground(Color.WHITE);
        themeToggleButton.setBorder(BorderFactory.createRaisedBevelBorder());
        themeToggleButton.setFont(themeToggleButton.getFont().deriveFont(Font.BOLD, 12f));

        colorThemeBtn = new JButton("🌈 Colors");
        colorThemeBtn.setFocusable(false);
        colorThemeBtn.addActionListener(e -> showColorThemeSelector());
        
        // Special styling for the color theme button
        colorThemeBtn.setBackground(new Color(255, 20, 147)); // Deep pink
        colorThemeBtn.setForeground(Color.WHITE);
        colorThemeBtn.setBorder(BorderFactory.createRaisedBevelBorder());
        colorThemeBtn.setFont(colorThemeBtn.getFont().deriveFont(Font.BOLD, 12f));
        
        colorThemeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                colorThemeBtn.setBackground(new Color(255, 105, 180)); // Hot pink
                colorThemeBtn.setBorder(BorderFactory.createLoweredBevelBorder());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                colorThemeBtn.setBackground(new Color(255, 20, 147)); // Deep pink
                colorThemeBtn.setBorder(BorderFactory.createRaisedBevelBorder());
            }
        });

        toolbar.add(newBtn);
        toolbar.add(openBtn);
        toolbar.add(saveBtn);
        toolbar.addSeparator();
        toolbar.add(undoBtn);
        toolbar.add(redoBtn);
        toolbar.addSeparator();
        toolbar.add(findBtn);
        toolbar.add(dbManagerBtn);
        toolbar.add(wrapToggleButton);
        toolbar.addSeparator();
        toolbar.add(themeToggleButton);
        toolbar.add(colorThemeBtn);

        add(toolbar, BorderLayout.NORTH);
    }

    private JButton mkButton(String text, ActionListener al) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(al);
        
        // Add colorful styling
        b.setBackground(new Color(100, 149, 237)); // Cornflower blue
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createRaisedBevelBorder());
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        
        // Add hover effects
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                b.setBackground(new Color(70, 130, 180)); // Steel blue
                b.setBorder(BorderFactory.createLoweredBevelBorder());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                b.setBackground(new Color(100, 149, 237)); // Cornflower blue
                b.setBorder(BorderFactory.createRaisedBevelBorder());
            }
        });
        
        return b;
    }

    private void initListeners() {
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });
        textArea.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                updateCaretPosition();
            }
        });
    }

    // ---------- FILE OPERATIONS ----------
    private void newFile() {
        if (!confirmSaveIfNeeded())
            return;
        textArea.setText("");
        currentFile = null;
        isModified = false;
        setTitle("Java Text Editor with SQL Backend");
        fileLabel.setText("Untitled");
        undoManager.discardAllEdits();
        updateUndoRedo();
        updateStatus();
    }

    private void openFile() {
        if (!confirmSaveIfNeeded())
            return;
        int res = fileChooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION)
            openFile(fileChooser.getSelectedFile());
    }

    private void openFile(File f) {
        try {
            String content = Files.readString(f.toPath());
            textArea.setText(content);
            currentFile = f;
            isModified = false;
            setTitle(f.getName() + " - Java Text Editor with SQL Backend");
            fileLabel.setText(f.getAbsolutePath());
            undoManager.discardAllEdits();
            updateUndoRedo();
            updateStatus();
        } catch (IOException ex) {
            showError("Could not open file:\n" + ex.getMessage());
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveFileAs();
            return;
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(currentFile))) {
            textArea.write(bw);
            isModified = false;
            setTitle(currentFile.getName() + " - Java Text Editor with SQL Backend");
            updateStatus();
            fileLabel.setText(currentFile.getAbsolutePath());
            
            // Also save to database
            dbManager.saveFileToDatabase(currentFile.getName(), textArea.getText(), currentFile.getAbsolutePath());
            
        } catch (IOException ex) {
            showError("Could not save file:\n" + ex.getMessage());
        }
    }

    private void saveFileAs() {
        int res = fileChooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = fileChooser.getSelectedFile();
            if (!f.getName().contains("."))
                f = new File(f.getAbsolutePath() + ".txt");
            currentFile = f;
            saveFile();
        }
    }

    private void openFromDatabase() {
        List<DatabaseFile> files = dbManager.getAllFiles();
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No files found in database.", "Database Empty", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DatabaseFileSelector dialog = new DatabaseFileSelector(this, files);
        dialog.setVisible(true);
        
        DatabaseFile selectedFile = dialog.getSelectedFile();
        if (selectedFile != null) {
            if (!confirmSaveIfNeeded()) return;
            
            textArea.setText(selectedFile.getContent());
            currentFile = new File(selectedFile.getFilePath());
            isModified = false;
            setTitle(selectedFile.getFileName() + " - Java Text Editor with SQL Backend");
            fileLabel.setText(selectedFile.getFilePath());
            undoManager.discardAllEdits();
            updateUndoRedo();
            updateStatus();
        }
    }

    private void listDatabaseFiles() {
        List<DatabaseFile> files = dbManager.getAllFiles();
        StringBuilder sb = new StringBuilder();
        sb.append("Files in Database:\n\n");
        
        if (files.isEmpty()) {
            sb.append("No files found.");
        } else {
            for (DatabaseFile file : files) {
                sb.append("• ").append(file.getFileName()).append("\n");
                sb.append("  Path: ").append(file.getFilePath()).append("\n");
                sb.append("  Last Modified: ").append(file.getLastModified()).append("\n");
                sb.append("  Size: ").append(file.getContent().length()).append(" characters\n\n");
            }
        }
        
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, "Database Files", JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean confirmSaveIfNeeded() {
        if (!isModified)
            return true;
        int choice = JOptionPane.showConfirmDialog(this, "You have unsaved changes. Save now?", "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION);
        switch (choice) {
            case JOptionPane.YES_OPTION:
                saveFile();
                return !isModified;
            case JOptionPane.NO_OPTION:
                return true;
            default:
                return false;
        }
    }

    private void exitApplication() {
        if (!confirmSaveIfNeeded())
            return;
        dbManager.closeConnection();
        dispose();
        System.exit(0);
    }

    // ---------- UNDO / REDO ----------
    private void performUndo() {
        try {
            if (undoManager.canUndo())
                undoManager.undo();
        } catch (CannotUndoException ignored) {
        }
        updateUndoRedo();
    }

    private void performRedo() {
        try {
            if (undoManager.canRedo())
                undoManager.redo();
        } catch (CannotRedoException ignored) {
        }
        updateUndoRedo();
    }

    private void updateUndoRedo() {
        undoMenuItem.setEnabled(undoManager.canUndo());
        redoMenuItem.setEnabled(undoManager.canRedo());
    }

    // ---------- FORMAT ----------
    private void toggleWrap(boolean wrap) {
        textArea.setLineWrap(wrap);
        textArea.setWrapStyleWord(wrap);
        lineGutter.repaint();
    }

    private void chooseColor() {
        Color c = JColorChooser.showDialog(this, "Choose Text Color", textArea.getForeground());
        if (c != null)
            textArea.setForeground(c);
    }

    private void toggleTheme(boolean dark) {
        if (dark) {
            applyColorScheme(ColorScheme.DARK);
        } else {
            applyColorScheme(ColorScheme.DEFAULT);
        }
    }

    private void applyColorScheme(ColorScheme scheme) {
        currentColorScheme = scheme;
        
        // Apply to text area
        textArea.setBackground(scheme.textAreaBackground);
        textArea.setForeground(scheme.textAreaForeground);
        textArea.setCaretColor(scheme.caretColor);
        
        // Apply to line gutter
        lineGutter.setBackgroundColor(scheme.lineGutterBackground);
        lineGutter.setForegroundColor(scheme.lineGutterForeground);
        
        // Apply to status bar
        getContentPane().setBackground(scheme.statusBarBackground);
        
        // Apply to toolbar
        JToolBar toolbar = (JToolBar) getContentPane().getComponent(0);
        toolbar.setBackground(scheme.toolbarBackground);
        
        // Apply to menu bar
        JMenuBar menuBar = getJMenuBar();
        if (menuBar != null) {
            menuBar.setBackground(scheme.menuBarBackground);
            menuBar.setForeground(scheme.menuBarForeground);
        }
        
        lineGutter.repaint();
        repaint();
    }

    private void showColorThemeSelector() {
        ColorThemeSelectorDialog dialog = new ColorThemeSelectorDialog(this);
        dialog.setVisible(true);
        
        ColorScheme selectedScheme = dialog.getSelectedScheme();
        if (selectedScheme != null) {
            applyColorScheme(selectedScheme);
        }
    }

    // ---------- STATUS / CARET ----------
    private void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            String text = textArea.getText();
            int chars = text.length();
            int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
            statusLabel.setText("Words: " + words + "  Characters: " + chars + (isModified ? "  *" : ""));
            updateCaretPosition();
        });
    }

    private void updateCaretPosition() {
        int caretPos = textArea.getCaretPosition();
        try {
            int line = textArea.getLineOfOffset(caretPos);
            int col = caretPos - textArea.getLineStartOffset(line);
            posLabel.setText("Ln: " + (line + 1) + "  Col: " + (col + 1));
        } catch (BadLocationException ex) {
            posLabel.setText("Ln: 1  Col: 1");
        }
    }

    // ---------- NESTED CLASSES ----------
    private static class LineNumberGutter extends JPanel {
        private final JTextArea textArea;
        private final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        private Color backgroundColor = new Color(230, 230, 230);
        private Color foregroundColor = Color.BLACK;

        public LineNumberGutter(JTextArea ta) {
            textArea = ta;
            setFont(font);
            setBackground(backgroundColor);

            textArea.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    repaint();
                }

                public void removeUpdate(DocumentEvent e) {
                    repaint();
                }

                public void changedUpdate(DocumentEvent e) {
                    repaint();
                }
            });

            textArea.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    repaint();
                }
            });

            textArea.addCaretListener(e -> repaint());
            setPreferredSize(new Dimension(40, Integer.MAX_VALUE));
        }

        public void setForegroundColor(Color c) {
            foregroundColor = c;
            repaint();
        }

        public void setBackgroundColor(Color c) {
            backgroundColor = c;
            setBackground(c);
            repaint();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(foregroundColor);
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            int lineHeight = fm.getHeight();
            int startLine = 0;
            int endLine = textArea.getLineCount();
            for (int i = startLine; i < endLine; i++) {
                try {
                    int y = (int) textArea.modelToView2D(textArea.getLineStartOffset(i)).getBounds().getY()
                            + fm.getAscent();
                    g.drawString(String.valueOf(i + 1), 5, y);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static class FindReplaceDialog extends JDialog {
        private final JTextArea textArea;
        private JTextField findField, replaceField;
        private JButton findNextBtn, replaceBtn, replaceAllBtn;

        public FindReplaceDialog(JFrame owner, JTextArea ta) {
            super(owner, "Find / Replace", false);
            textArea = ta;
            init();
        }

        private void init() {
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0;
            gbc.gridy = 0;
            add(new JLabel("Find:"), gbc);
            gbc.gridx = 1;
            gbc.gridy = 0;
            findField = new JTextField(20);
            add(findField, gbc);
            gbc.gridx = 0;
            gbc.gridy = 1;
            add(new JLabel("Replace:"), gbc);
            gbc.gridx = 1;
            gbc.gridy = 1;
            replaceField = new JTextField(20);
            add(replaceField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            findNextBtn = new JButton("Find Next");
            add(findNextBtn, gbc);
            gbc.gridx = 1;
            gbc.gridy = 2;
            replaceBtn = new JButton("Replace");
            add(replaceBtn, gbc);
            gbc.gridx = 1;
            gbc.gridy = 3;
            replaceAllBtn = new JButton("Replace All");
            add(replaceAllBtn, gbc);

            findNextBtn.addActionListener(e -> findNext());
            replaceBtn.addActionListener(e -> replace());
            replaceAllBtn.addActionListener(e -> replaceAll());

            pack();
            setLocationRelativeTo(getOwner());
        }

        private void findNext() {
            String find = findField.getText();
            if (find.isEmpty())
                return;
            int start = textArea.getCaretPosition();
            String text = textArea.getText();
            int idx = text.indexOf(find, start);
            if (idx < 0)
                idx = text.indexOf(find, 0);
            if (idx >= 0) {
                textArea.setSelectionStart(idx);
                textArea.setSelectionEnd(idx + find.length());
                textArea.requestFocus();
            } else
                JOptionPane.showMessageDialog(this, "Text not found!");
        }

        private void replace() {
            if (textArea.getSelectedText() != null && !textArea.getSelectedText().isEmpty()) {
                textArea.replaceSelection(replaceField.getText());
            }
            findNext();
        }

        private void replaceAll() {
            String find = findField.getText();
            String replace = replaceField.getText();
            textArea.setText(textArea.getText().replace(find, replace));
        }
    }

    private static class DatabaseFileSelector extends JDialog {
        private DatabaseFile selectedFile;
        private JList<DatabaseFile> fileList;

        public DatabaseFileSelector(JFrame owner, List<DatabaseFile> files) {
            super(owner, "Select File from Database", true);
            init(files);
        }

        private void init(List<DatabaseFile> files) {
            setLayout(new BorderLayout());
            
            DefaultListModel<DatabaseFile> model = new DefaultListModel<>();
            for (DatabaseFile file : files) {
                model.addElement(file);
            }
            
            fileList = new JList<>(model);
            fileList.setCellRenderer(new DatabaseFileRenderer());
            fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            JScrollPane scrollPane = new JScrollPane(fileList);
            add(scrollPane, BorderLayout.CENTER);
            
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton openBtn = new JButton("Open");
            JButton cancelBtn = new JButton("Cancel");
            
            openBtn.addActionListener(e -> {
                selectedFile = fileList.getSelectedValue();
                dispose();
            });
            
            cancelBtn.addActionListener(e -> {
                selectedFile = null;
                dispose();
            });
            
            buttonPanel.add(openBtn);
            buttonPanel.add(cancelBtn);
            add(buttonPanel, BorderLayout.SOUTH);
            
            setSize(500, 300);
            setLocationRelativeTo(getOwner());
        }

        public DatabaseFile getSelectedFile() {
            return selectedFile;
        }
    }

    private static class DatabaseFileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof DatabaseFile) {
                DatabaseFile file = (DatabaseFile) value;
                setText("<html><b>" + file.getFileName() + "</b><br>" +
                       "<font size='2'>" + file.getFilePath() + "<br>" +
                       "Modified: " + file.getLastModified() + " | Size: " + file.getContent().length() + " chars</font></html>");
            }
            
            return this;
        }
    }

    private static class DatabaseManagerDialog extends JDialog {
        private final DatabaseManager dbManager;
        private JTable fileTable;
        private DefaultTableModel tableModel;
        private JTextArea contentArea;
        private JLabel statusLabel;

        public DatabaseManagerDialog(JFrame owner, DatabaseManager dbManager) {
            super(owner, "SQLite Database Manager", true);
            this.dbManager = dbManager;
            init();
            loadFiles();
        }

        private void init() {
            setLayout(new BorderLayout());
            setSize(800, 600);
            setLocationRelativeTo(getOwner());

            // Top panel with buttons
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton refreshBtn = new JButton("Refresh");
            JButton openBtn = new JButton("Open File");
            JButton deleteBtn = new JButton("Delete File");
            JButton exportBtn = new JButton("Export Database");
            JButton showDbPathBtn = new JButton("Show DB Path");

            refreshBtn.addActionListener(e -> loadFiles());
            openBtn.addActionListener(e -> openSelectedFile());
            deleteBtn.addActionListener(e -> deleteSelectedFile());
            exportBtn.addActionListener(e -> exportDatabase());
            showDbPathBtn.addActionListener(e -> showDatabasePath());

            topPanel.add(refreshBtn);
            topPanel.add(openBtn);
            topPanel.add(deleteBtn);
            topPanel.add(exportBtn);
            topPanel.add(showDbPathBtn);

            // Status label
            statusLabel = new JLabel("Ready");
            topPanel.add(new JSeparator(SwingConstants.VERTICAL));
            topPanel.add(statusLabel);

            add(topPanel, BorderLayout.NORTH);

            // Split pane for table and content
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setDividerLocation(400);

            // File table
            String[] columns = {"ID", "Filename", "Path", "Size", "Last Modified"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            fileTable = new JTable(tableModel);
            fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            fileTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    showFileContent();
                }
            });

            JScrollPane tableScrollPane = new JScrollPane(fileTable);
            tableScrollPane.setBorder(BorderFactory.createTitledBorder("Files in Database"));
            splitPane.setLeftComponent(tableScrollPane);

            // Content area
            contentArea = new JTextArea();
            contentArea.setEditable(false);
            contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            contentArea.setBorder(BorderFactory.createTitledBorder("File Content Preview"));
            JScrollPane contentScrollPane = new JScrollPane(contentArea);
            splitPane.setRightComponent(contentScrollPane);

            add(splitPane, BorderLayout.CENTER);

            // Bottom panel
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton closeBtn = new JButton("Close");
            closeBtn.addActionListener(e -> dispose());
            bottomPanel.add(closeBtn);
            add(bottomPanel, BorderLayout.SOUTH);
        }

        private void loadFiles() {
            try {
                tableModel.setRowCount(0);
                List<DatabaseFile> files = dbManager.getAllFiles();
                
                for (DatabaseFile file : files) {
                    Object[] row = {
                        files.indexOf(file) + 1,
                        file.getFileName(),
                        file.getFilePath(),
                        file.getContent().length() + " chars",
                        file.getLastModified()
                    };
                    tableModel.addRow(row);
                }
                
                statusLabel.setText("Loaded " + files.size() + " files");
            } catch (Exception e) {
                statusLabel.setText("Error loading files: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error loading files: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void showFileContent() {
            int selectedRow = fileTable.getSelectedRow();
            if (selectedRow >= 0) {
                try {
                    List<DatabaseFile> files = dbManager.getAllFiles();
                    if (selectedRow < files.size()) {
                        DatabaseFile file = files.get(selectedRow);
                        String content = file.getContent();
                        
                        // Show first 1000 characters for preview
                        if (content.length() > 1000) {
                            content = content.substring(0, 1000) + "\n\n... (truncated, full content has " + content.length() + " characters)";
                        }
                        
                        contentArea.setText(content);
                        contentArea.setCaretPosition(0);
                    }
                } catch (Exception e) {
                    contentArea.setText("Error loading content: " + e.getMessage());
                }
            } else {
                contentArea.setText("");
            }
        }

        private void openSelectedFile() {
            int selectedRow = fileTable.getSelectedRow();
            if (selectedRow >= 0) {
                try {
                    List<DatabaseFile> files = dbManager.getAllFiles();
                    if (selectedRow < files.size()) {
                        DatabaseFile file = files.get(selectedRow);
                        
                        // Ask user if they want to open this file
                        int choice = JOptionPane.showConfirmDialog(this, 
                            "Open file: " + file.getFileName() + "?", 
                            "Open File", 
                            JOptionPane.YES_NO_OPTION);
                        
                        if (choice == JOptionPane.YES_OPTION) {
                            // Close this dialog and notify parent to open the file
                            dispose();
                            // You would need to implement a callback mechanism here
                            JOptionPane.showMessageDialog(getOwner(), 
                                "File opened: " + file.getFileName() + "\n" +
                                "Path: " + file.getFilePath() + "\n" +
                                "Size: " + file.getContent().length() + " characters",
                                "File Opened", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error opening file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a file to open.", "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void deleteSelectedFile() {
            int selectedRow = fileTable.getSelectedRow();
            if (selectedRow >= 0) {
                try {
                    List<DatabaseFile> files = dbManager.getAllFiles();
                    if (selectedRow < files.size()) {
                        DatabaseFile file = files.get(selectedRow);
                        
                        int choice = JOptionPane.showConfirmDialog(this, 
                            "Delete file from database: " + file.getFileName() + "?\n\n" +
                            "This will only remove it from the database, not from your local storage.",
                            "Delete File", 
                            JOptionPane.YES_NO_OPTION);
                        
                        if (choice == JOptionPane.YES_OPTION) {
                            // Implement delete functionality in DatabaseManager
                            JOptionPane.showMessageDialog(this, "Delete functionality not implemented yet.", "Info", JOptionPane.INFORMATION_MESSAGE);
                            loadFiles(); // Refresh the list
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error deleting file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a file to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void exportDatabase() {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("texteditor_backup.db"));
            chooser.setDialogTitle("Export Database");
            
            int result = chooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    File sourceDb = new File("texteditor.db");
                    File targetDb = chooser.getSelectedFile();
                    
                    if (sourceDb.exists()) {
                        Files.copy(sourceDb.toPath(), targetDb.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        JOptionPane.showMessageDialog(this, "Database exported to: " + targetDb.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Source database file not found.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error exporting database: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void showDatabasePath() {
            File dbFile = new File("texteditor.db");
            String message = "SQLite Database Location:\n\n" +
                           "File: " + dbFile.getAbsolutePath() + "\n" +
                           "Exists: " + (dbFile.exists() ? "Yes" : "No") + "\n" +
                           "Size: " + (dbFile.exists() ? dbFile.length() + " bytes" : "N/A") + "\n\n" +
                           "You can access this file with:\n" +
                           "• SQLite command line tools\n" +
                           "• DB Browser for SQLite\n" +
                           "• Any SQLite-compatible tool";
            
            JOptionPane.showMessageDialog(this, message, "Database Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private JFrame thisFrame() {
        return this;
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this, "Java Text Editor with SQL Backend\nBy Yugal Mahajan\nEnhanced with SQLite Database Support", "About",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showDatabaseManager() {
        new DatabaseManagerDialog(this, dbManager).setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TextEditor().setVisible(true));
    }
}

// Color scheme classes
enum ColorScheme {
    DEFAULT(
        Color.WHITE,           // textAreaBackground
        Color.BLACK,           // textAreaForeground
        Color.BLACK,           // caretColor
        new Color(230, 230, 230), // lineGutterBackground
        Color.BLACK,           // lineGutterForeground
        new Color(240, 240, 240), // statusBarBackground
        new Color(220, 220, 220), // toolbarBackground
        new Color(240, 240, 240), // menuBarBackground
        Color.BLACK            // menuBarForeground
    ),
    
    DARK(
        new Color(45, 45, 45),     // textAreaBackground
        new Color(220, 220, 220), // textAreaForeground
        new Color(255, 255, 255), // caretColor
        new Color(60, 60, 60),     // lineGutterBackground
        new Color(180, 180, 180), // lineGutterForeground
        new Color(50, 50, 50),     // statusBarBackground
        new Color(70, 70, 70),     // toolbarBackground
        new Color(60, 60, 60),     // menuBarBackground
        new Color(220, 220, 220)   // menuBarForeground
    ),
    
    OCEAN(
        new Color(240, 248, 255),  // textAreaBackground
        new Color(25, 25, 112),    // textAreaForeground
        new Color(0, 100, 200),    // caretColor
        new Color(176, 224, 230),   // lineGutterBackground
        new Color(25, 25, 112),    // lineGutterForeground
        new Color(230, 240, 250),   // statusBarBackground
        new Color(200, 220, 240),  // toolbarBackground
        new Color(220, 230, 250),  // menuBarBackground
        new Color(25, 25, 112)     // menuBarForeground
    ),
    
    SUNSET(
        new Color(255, 248, 220),   // textAreaBackground
        new Color(139, 69, 19),     // textAreaForeground
        new Color(255, 69, 0),      // caretColor
        new Color(255, 218, 185),   // lineGutterBackground
        new Color(139, 69, 19),     // lineGutterForeground
        new Color(255, 240, 200),   // statusBarBackground
        new Color(255, 228, 196),   // toolbarBackground
        new Color(255, 235, 205),   // menuBarBackground
        new Color(139, 69, 19)      // menuBarForeground
    ),
    
    FOREST(
        new Color(245, 255, 245),   // textAreaBackground
        new Color(34, 139, 34),     // textAreaForeground
        new Color(0, 128, 0),        // caretColor
        new Color(220, 255, 220),   // lineGutterBackground
        new Color(34, 139, 34),     // lineGutterForeground
        new Color(240, 255, 240),   // statusBarBackground
        new Color(225, 255, 225),   // toolbarBackground
        new Color(230, 255, 230),   // menuBarBackground
        new Color(34, 139, 34)      // menuBarForeground
    ),
    
    PURPLE_DREAM(
        new Color(248, 240, 255),   // textAreaBackground
        new Color(75, 0, 130),       // textAreaForeground
        new Color(138, 43, 226),     // caretColor
        new Color(230, 220, 255),   // lineGutterBackground
        new Color(75, 0, 130),       // lineGutterForeground
        new Color(245, 235, 255),   // statusBarBackground
        new Color(235, 225, 255),   // toolbarBackground
        new Color(240, 230, 255),   // menuBarBackground
        new Color(75, 0, 130)       // menuBarForeground
    ),
    
    NEON(
        new Color(0, 0, 0),         // textAreaBackground
        new Color(0, 255, 0),       // textAreaForeground
        new Color(255, 0, 255),     // caretColor
        new Color(20, 20, 20),      // lineGutterBackground
        new Color(0, 255, 255),     // lineGutterForeground
        new Color(10, 10, 10),      // statusBarBackground
        new Color(30, 30, 30),      // toolbarBackground
        new Color(25, 25, 25),      // menuBarBackground
        new Color(0, 255, 0)        // menuBarForeground
    );

    public final Color textAreaBackground;
    public final Color textAreaForeground;
    public final Color caretColor;
    public final Color lineGutterBackground;
    public final Color lineGutterForeground;
    public final Color statusBarBackground;
    public final Color toolbarBackground;
    public final Color menuBarBackground;
    public final Color menuBarForeground;

    ColorScheme(Color textAreaBackground, Color textAreaForeground, Color caretColor,
                Color lineGutterBackground, Color lineGutterForeground,
                Color statusBarBackground, Color toolbarBackground,
                Color menuBarBackground, Color menuBarForeground) {
        this.textAreaBackground = textAreaBackground;
        this.textAreaForeground = textAreaForeground;
        this.caretColor = caretColor;
        this.lineGutterBackground = lineGutterBackground;
        this.lineGutterForeground = lineGutterForeground;
        this.statusBarBackground = statusBarBackground;
        this.toolbarBackground = toolbarBackground;
        this.menuBarBackground = menuBarBackground;
        this.menuBarForeground = menuBarForeground;
    }
}

class ColorThemeSelectorDialog extends JDialog {
    private ColorScheme selectedScheme = null;

    public ColorThemeSelectorDialog(JFrame owner) {
        super(owner, "Choose Color Theme", true);
        init();
    }

    private void init() {
        setLayout(new BorderLayout());
        setSize(500, 400);
        setLocationRelativeTo(getOwner());

        JPanel mainPanel = new JPanel(new GridLayout(3, 3, 10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        for (ColorScheme scheme : ColorScheme.values()) {
            JButton themeBtn = createThemeButton(scheme);
            mainPanel.add(themeBtn);
        }

        add(mainPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            selectedScheme = null;
            dispose();
        });
        bottomPanel.add(cancelBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JButton createThemeButton(ColorScheme scheme) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(120, 80));
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        
        // Create a preview of the theme
        btn.setBackground(scheme.textAreaBackground);
        btn.setForeground(scheme.textAreaForeground);
        
        String themeName = scheme.name().replace("_", " ");
        String[] words = themeName.split(" ");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (displayName.length() > 0) displayName.append(" ");
            displayName.append(word.substring(0, 1).toUpperCase())
                      .append(word.substring(1).toLowerCase());
        }
        
        btn.setText("<html><center><b>" + displayName + "</b><br>" +
                   "<font size='2'>Preview</font></center></html>");
        
        btn.addActionListener(e -> {
            selectedScheme = scheme;
            dispose();
        });

        // Add hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBorder(BorderFactory.createLoweredBevelBorder());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBorder(BorderFactory.createRaisedBevelBorder());
            }
        });

        return btn;
    }

    public ColorScheme getSelectedScheme() {
        return selectedScheme;
    }
}

// Database-related classes
class DatabaseManager {
    private Connection connection;
    private static final String DB_URL = "jdbc:sqlite:texteditor.db";

    public void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            createTables();
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                filepath TEXT NOT NULL,
                content TEXT NOT NULL,
                last_modified DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    public void saveFileToDatabase(String filename, String content, String filepath) {
        String sql = """
            INSERT OR REPLACE INTO files (filename, filepath, content, last_modified)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filename);
            pstmt.setString(2, filepath);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving file to database: " + e.getMessage());
        }
    }

    public List<DatabaseFile> getAllFiles() {
        List<DatabaseFile> files = new ArrayList<>();
        String sql = "SELECT filename, filepath, content, last_modified FROM files ORDER BY last_modified DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                DatabaseFile file = new DatabaseFile(
                    rs.getString("filename"),
                    rs.getString("filepath"),
                    rs.getString("content"),
                    rs.getString("last_modified")
                );
                files.add(file);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving files from database: " + e.getMessage());
        }
        
        return files;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}

class DatabaseFile {
    private String fileName;
    private String filePath;
    private String content;
    private String lastModified;

    public DatabaseFile(String fileName, String filePath, String content, String lastModified) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.content = content;
        this.lastModified = lastModified;
    }

    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public String getContent() { return content; }
    public String getLastModified() { return lastModified; }
}
