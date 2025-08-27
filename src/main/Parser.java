package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-pass parser over a VM source file.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Strips comments ({@code // ...}) and whitespace-only lines.</li>
 *   <li>Exposes VM commands sequentially.</li>
 *   <li>Classifies commands and provides access to arguments per spec.</li>
 * </ul>
 *
 * <h3>arg1/arg2 semantics (spec 7.3 / 8.4):</h3>
 * <ul>
 *   <li>{@code arg1()}:
 *     <ul>
 *       <li>Arithmetic: returns the command itself (e.g., {@code "add"}).</li>
 *       <li>push/pop/label/goto/if-goto/function/call: returns the first token after the keyword.</li>
 *       <li>return: invalid to call (throws).</li>
 *     </ul>
 *   </li>
 *   <li>{@code arg2()} is valid only for push, pop, function, call and returns the integer value.</li>
 * </ul>
 */
public class Parser {

    private static final Map<String, CommandType> COMMAND_MAP = new HashMap<>();

    static {
        COMMAND_MAP.put("push", CommandType.C_PUSH);
        COMMAND_MAP.put("pop", CommandType.C_POP);
        COMMAND_MAP.put("label", CommandType.C_LABEL);
        COMMAND_MAP.put("goto", CommandType.C_GOTO);
        COMMAND_MAP.put("if-goto", CommandType.C_IF);
        COMMAND_MAP.put("function", CommandType.C_FUNCTION);
        COMMAND_MAP.put("call", CommandType.C_CALL);
        COMMAND_MAP.put("return", CommandType.C_RETURN);
    }

    private final List<String> lines = new ArrayList<>();
    private int index = -1;
    private String current;

    /**
     * Loads and preprocesses a single .vm file.
     *
     * @param filePath path to VM file
     * @throws IOException on I/O error
     */
    public Parser(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String raw;
            while ((raw = br.readLine()) != null) {
                String line = raw.split("//", 2)[0].trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
    }

    /**
     * @return whether additional VM commands remain.
     */
    public boolean hasMoreCommands() {
        return index + 1 < lines.size();
    }

    /**
     * Advances to the next VM command (skips whitespace/comments already).
     */
    public void advance() {
        current = lines.get(++index);
    }

    /**
     * @return the {@link CommandType} of the current command.
     */
    public CommandType commandType() {
        String keyword = current.split("\\s+")[0];
        return COMMAND_MAP.getOrDefault(keyword, CommandType.C_ARITHMETIC);
    }

    /**
     * Returns the first argument per spec.
     *
     * @return argument string or the arithmetic command itself
     * @throws IllegalStateException if called on {@code return}
     */
    public String arg1() {
        CommandType t = commandType();
        if (t == CommandType.C_ARITHMETIC) {
            return current;
        }
        if (t == CommandType.C_RETURN) {
            throw new IllegalStateException("arg1() is invalid for 'return'");
        }
        return current.split("\\s+")[1];
    }

    /**
     * Returns the second argument per spec. Valid only for {@code push}, {@code pop}, {@code function}, and
     * {@code call}.
     *
     * @return integer second argument
     * @throws IllegalStateException if called for an unsupported command
     * @throws NumberFormatException if the second token is not an integer
     */
    public int arg2() {
        CommandType t = commandType();
        if (t != CommandType.C_PUSH && t != CommandType.C_POP
            && t != CommandType.C_FUNCTION && t != CommandType.C_CALL) {
            throw new IllegalStateException("arg2() is invalid for " + t);
        }
        return Integer.parseInt(current.split("\\s+")[2]);
    }

}
