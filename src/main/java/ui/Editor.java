package ui;


import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import ui.action.ExitAction;
import ui.action.OpenAction;
import ui.action.SaveAction;
import ui.listener.ProgramTextKeyListener;
import interpreter.Interpreter;
import interpreter.Result;
import interpreter.error.ParserError;
import ui.listener.ProgramTextMouseMotionListener;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 *  Main Editor's frame. Consists of two component: component for program text and for program output.
 *  Error lines are highlighted by {@link SquiggleUnderlineHighlightPainter}
 *
 *  Program is executed in background thread with "execute only last statement" strategy.
 */
public class Editor extends JFrame {
    private final JTextArea programTextComponent;
    private final JTextArea programOutputTextComponent;
    private final Interpreter interpreter;
    private final Highlighter.HighlightPainter painter;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Runnable> lastTask = new AtomicReference<>();

    private volatile Map<Range, String> errorsMap = new HashMap<>();

    public Editor() {
        super("Tiny Interpreter");
        programTextComponent = createProgramTextArea(this);
        programOutputTextComponent = createProgramOutputTextArea();
        interpreter = new Interpreter();
        painter = new SquiggleUnderlineHighlightPainter(Color.red);
        Container content = getContentPane();
        content.add(new JScrollPane(programTextComponent), BorderLayout.CENTER);
        content.add(new JScrollPane(programOutputTextComponent), BorderLayout.SOUTH);
        setJMenuBar(createMenuBar(this));
        setSize(600, 450);
    }

    public Map<Range, String> getErrorsMap() {
        return errorsMap;
    }

    public JTextArea getProgramTextComponent() {
        return programTextComponent;
    }

    public void onSaveAction(FileWriter writer) throws IOException {
        programTextComponent.write(writer);
    }

    public void onOpenAction(Reader reader) throws IOException {
        programTextComponent.read(reader, null);
        onProgramTextChanged();
    }

    public void onProgramTextChanged() {
        String programText = programTextComponent.getText();
        lastTask.set(() -> executeOnProgramChange(programText));
        backgroundExecutor.execute(() -> {
            Runnable task = lastTask.getAndSet(null);
            if (task != null) {
                task.run();
            }
        });
    }

    private void executeOnProgramChange(String programText) {
        Result result = interpreter.execute(programText);
        Map<String, List<ParserError>> errors = result.getErrors();
        StringJoiner joiner = new StringJoiner("\n");
        result.getOutput().forEach(joiner::add);
        errorsMap = processErrors(errors);
        SwingUtilities.invokeLater(() -> programOutputTextComponent.setText(joiner.toString()));
    }

    private HashMap<Range, String> processErrors(Map<String, List<ParserError>> errors) {
        String text = "\n" + programTextComponent.getText() + "\n";
        Highlighter highlighter = programTextComponent.getHighlighter();
        highlighter.removeAllHighlights();
        HashMap<Range, String> newMapWithErrors = new HashMap<>();
        for (Map.Entry<String, List<ParserError>> errorWithLine : errors.entrySet()) {
            String line = errorWithLine.getKey();
            int start = text.indexOf("\n" + line + "\n");
            while (start != -1) {
                int end = start + line.length();
                try {
                    highlighter.addHighlight(start, end, painter);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
                newMapWithErrors.put(new Range(start, end), errorWithLine.getValue().get(0).getMessage());
                start = text.indexOf("\n" + line + "\n", start + 1);
            }
        }
        return newMapWithErrors;
    }

    private static JTextArea createProgramTextArea(Editor editor) {
        final JTextArea textArea = new JTextArea();
        textArea.setBorder(createBorder("Program"));
        textArea.setFont(new Font("Arial", Font.PLAIN, 14));
        textArea.addKeyListener(new ProgramTextKeyListener(editor));
        textArea.addMouseMotionListener(new ProgramTextMouseMotionListener(editor));
        return textArea;
    }

    private static JTextArea createProgramOutputTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setBorder(createBorder("Program output"));
        return textArea;
    }

    private static JMenuBar createMenuBar(Editor editor) {
        JMenuBar menu = new JMenuBar();
        JMenu file = new JMenu("File");
        menu.add(file);
        file.add(new OpenAction(editor));
        file.add(new SaveAction((editor)));
        file.add(new ExitAction());
        return menu;
    }

    private static CompoundBorder createBorder(String label) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(label),
                BorderFactory.createEmptyBorder(3, 3, 3, 3));
    }
}
