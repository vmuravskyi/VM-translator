package main;

/**
 * Enumeration of all VM command categories (Ch. 7 &amp; 8).
 * <ul>
 *   <li>{@link #C_ARITHMETIC}: Arithmetic-logical commands
 *   (add, sub, neg, eq, gt, lt, and, or, not).</li>
 *   <li>{@link #C_PUSH}: Memory access push.</li>
 *   <li>{@link #C_POP}: Memory access pop.</li>
 *   <li>{@link #C_LABEL}: label &lt;symbol&gt; (scoped to current function).</li>
 *   <li>{@link #C_GOTO}: goto &lt;symbol&gt; (unconditional, scoped to current function).</li>
 *   <li>{@link #C_IF}: if-goto &lt;symbol&gt; (conditional, scoped to current function).</li>
 *   <li>{@link #C_FUNCTION}: function &lt;name&gt; &lt;nLocals&gt;.</li>
 *   <li>{@link #C_CALL}: call &lt;name&gt; &lt;nArgs&gt;.</li>
 *   <li>{@link #C_RETURN}: return.</li>
 * </ul>
 */
public enum CommandType {
    C_ARITHMETIC,
    C_PUSH,
    C_POP,
    C_LABEL,
    C_GOTO,
    C_IF,
    C_FUNCTION,
    C_CALL,
    C_RETURN
}
