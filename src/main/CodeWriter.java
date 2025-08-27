package main;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Emits Hack assembly that implements the VM language (Projects 7–8).
 * <p>
 * Conventions and mappings:
 * <ul>
 *   <li>Stack base at RAM[256], SP holds next-free slot.</li>
 *   <li>Segment bases: LCL, ARG, THIS, THAT.</li>
 *   <li>temp segment: RAM[5..12], pointer: THIS/THAT, static: FileName.i (RAM[16..255]).</li>
 *   <li>Label scoping: function-scope labels are emitted as {@code FunctionName$label}.</li>
 *   <li>Call/return protocol follows Fig. 8.5 (FRAME in R13, RET in R14).</li>
 * </ul>
 */
public class CodeWriter implements AutoCloseable {

    private final BufferedWriter out;
    private String fileName = "Static";     // used for static vars: FileName.i
    private String currentFunction = "";    // used for label scoping: Function$Label
    private int labelCounter = 0;           // for unique return labels, compares, etc.

    /**
     * Creates a writer to the given output .asm file.
     *
     * @param outputFile output path for generated Hack assembly
     * @throws IOException on I/O error
     */
    public CodeWriter(String outputFile) throws IOException {
        out = new BufferedWriter(new FileWriter(outputFile));
    }

    /**
     * Sets the current VM file base name (used for {@code static} symbols). Example: translating Foo.vm sets base to
     * "Foo" so static 3 becomes "Foo.3".
     *
     * @param base file base name without extension
     */
    public void setFileName(String base) {
        this.fileName = base;
    }

    // ------------------------------------------------------------
    // Bootstrap
    // ------------------------------------------------------------

    /**
     * Writes bootstrap code per spec (8.5.2):
     * <ol>
     *   <li>SP = 256</li>
     *   <li>call Sys.init</li>
     * </ol>
     * Should be emitted when translating a folder (multi-file program).
     *
     * @throws IOException on I/O error
     */
    public void writeInit() throws IOException {
        writeln("@256");
        writeln("D=A");
        writeln("@SP");
        writeln("M=D"); // SP=256
        writeCall("Sys.init", 0);
    }

    // ------------------------------------------------------------
    // Arithmetic / Logical (7.3)
    // ------------------------------------------------------------

    /**
     * Translates an arithmetic/logical VM command. Stack effects:
     * <ul>
     *   <li>Binary ops (add, sub, and, or): pop y, pop x, push x op y</li>
     *   <li>Unary ops (neg, not): replace top with op(top)</li>
     *   <li>Comparisons (eq, gt, lt): push -1 (true) or 0 (false)</li>
     * </ul>
     */
    public void writeArithmetic(String cmd) throws IOException {
        switch (cmd) {
            case "add" -> binary("M=D+M");
            case "sub" -> binary("M=M-D");
            case "and" -> binary("M=D&M");
            case "or" -> binary("M=D|M");
            case "neg" -> unary("M=-M");
            case "not" -> unary("M=!M");
            case "eq" -> compare("JEQ");
            case "gt" -> compare("JGT");
            case "lt" -> compare("JLT");
            default -> throw new IllegalArgumentException("Unknown arithmetic: " + cmd);
        }
    }

    private void binary(String op) throws IOException {
        // y=*--SP -> D; x at *SP-1
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("A=A-1");
        writeln(op);
    }

    private void unary(String op) throws IOException {
        writeln("@SP");
        writeln("A=M-1");
        writeln(op);
    }

    private void compare(String jump) throws IOException {
        // if (x - y) <cond> then true(-1) else false(0)
        String TRUE = unique("TRUE"), END = unique("END");
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("A=A-1");
        writeln("D=M-D");
        writeln("@" + TRUE);
        writeln("D;" + jump);
        writeln("@SP");
        writeln("A=M-1");
        writeln("M=0");
        writeln("@" + END);
        writeln("0;JMP");
        writeln("(" + TRUE + ")");
        writeln("@SP");
        writeln("A=M-1");
        writeln("M=-1");
        writeln("(" + END + ")");
    }

    // ------------------------------------------------------------
    // Memory Access (push/pop) (7.3/7.4.1)
    // ------------------------------------------------------------

    /**
     * Translates push/pop commands. Segments supported: constant, local, argument, this, that, temp, pointer, static.
     *
     * @param type    C_PUSH or C_POP
     * @param segment VM segment name
     * @param index   non-negative index (pointer: 0=THIS,1=THAT; temp: 0..7)
     */
    public void writePushPop(CommandType type, String segment, int index) throws IOException {
        if (type == CommandType.C_PUSH) {
            switch (segment) {
                case "constant" -> {
                    writeln("@" + index);
                    writeln("D=A");
                    pushD();
                }
                case "local", "argument", "this", "that" -> {
                    addrFromBase(segment, index);
                    writeln("A=M");
                    writeln("D=M");
                    pushD();
                }
                case "temp" -> {
                    checkRange(index, 0, 7, "temp");
                    writeln("@" + (5 + index));
                    writeln("D=M");
                    pushD();
                }
                case "pointer" -> {
                    checkRange(index, 0, 1, "pointer");
                    writeln("@" + (index == 0 ? "THIS" : "THAT"));
                    writeln("D=M");
                    pushD();
                }
                case "static" -> {
                    writeln("@" + fileName + "." + index);
                    writeln("D=M");
                    pushD();
                }
                default -> throw new IllegalArgumentException("push: bad segment " + segment);
            }
        } else { // C_POP
            switch (segment) {
                case "local", "argument", "this", "that" -> {
                    addrFromBase(segment, index);
                    popToR13Target();
                }
                case "temp" -> {
                    checkRange(index, 0, 7, "temp");
                    writeln("@" + (5 + index));
                    writeln("D=A");
                    writeln("@R13");
                    writeln("M=D");
                    popToR13Target();
                }
                case "pointer" -> {
                    checkRange(index, 0, 1, "pointer");
                    writeln("@" + (index == 0 ? "THIS" : "THAT"));
                    writeln("D=A");
                    writeln("@R13");
                    writeln("M=D");
                    popToR13Target();
                }
                case "static" -> {
                    writeln("@" + fileName + "." + index);
                    writeln("D=A");
                    writeln("@R13");
                    writeln("M=D");
                    popToR13Target();
                }
                case "constant" -> throw new IllegalArgumentException("pop constant is invalid");
                default -> throw new IllegalArgumentException("pop: bad segment " + segment);
            }
        }
    }

    // ------------------------------------------------------------
    // Branching (8.2 / 8.4)
    // ------------------------------------------------------------

    /**
     * Emits a function-scoped label in the form {@code (Function$label)}.
     *
     * @param label raw VM label
     */
    public void writeLabel(String label) throws IOException {
        writeln("(" + scoped(label) + ")");
    }

    /**
     * Unconditional jump to the function-scoped label.
     *
     * @param label raw VM label
     */
    public void writeGoto(String label) throws IOException {
        writeln("@" + scoped(label));
        writeln("0;JMP");
    }

    /**
     * Conditional jump: pop top; if != 0 goto label (function-scoped).
     *
     * @param label raw VM label
     */
    public void writeIf(String label) throws IOException {
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("@" + scoped(label));
        writeln("D;JNE");
    }

    // ------------------------------------------------------------
    // Functions (8.3 / 8.4 / 8.5.1)
    // ------------------------------------------------------------

    /**
     * Declares a function entry point and allocates nLocals (pushing zeros).
     *
     * @param functionName global VM function name (e.g., Foo.bar)
     * @param nLocals      number of local variables
     */
    public void writeFunction(String functionName, int nLocals) throws IOException {
        currentFunction = functionName;
        writeln("(" + functionName + ")");
        // Initialize locals to 0: push 0 ... (nLocals times)
        for (int i = 0; i < nLocals; i++) {
            writeln("@0");
            writeln("D=A");
            pushD();
        }
    }

    /**
     * Implements the standard calling sequence:
     * <ol>
     *   <li>push return-address</li>
     *   <li>push LCL, ARG, THIS, THAT</li>
     *   <li>ARG = SP - 5 - nArgs</li>
     *   <li>LCL = SP</li>
     *   <li>goto functionName</li>
     *   <li>(return-address)</li>
     * </ol>
     *
     * @param functionName callee name
     * @param nArgs        number of arguments already pushed by the caller
     */
    public void writeCall(String functionName, int nArgs) throws IOException {
        String ret = unique("RET");

        // push return address
        writeln("@" + ret);
        writeln("D=A");
        pushD();

        // push LCL, ARG, THIS, THAT
        pushSymbol("LCL");
        pushSymbol("ARG");
        pushSymbol("THIS");
        pushSymbol("THAT");

        // ARG = SP - 5 - nArgs
        writeln("@SP");
        writeln("D=M");
        writeln("@5");
        writeln("D=D-A");
        writeln("@" + nArgs);
        writeln("D=D-A");
        writeln("@ARG");
        writeln("M=D");

        // LCL = SP
        writeln("@SP");
        writeln("D=M");
        writeln("@LCL");
        writeln("M=D");

        // goto function
        writeln("@" + functionName);
        writeln("0;JMP");

        // (return-address)
        writeln("(" + ret + ")");
    }

    /**
     * Implements the return protocol (FRAME in R13, RET in R14):
     * <ol>
     *   <li>FRAME = LCL</li>
     *   <li>RET = *(FRAME - 5)</li>
     *   <li>*ARG = pop()</li>
     *   <li>SP = ARG + 1</li>
     *   <li>Restore THAT, THIS, ARG, LCL from FRAME-1..4</li>
     *   <li>goto RET</li>
     * </ol>
     */
    public void writeReturn() throws IOException {
        // FRAME = LCL
        writeln("@LCL");
        writeln("D=M");
        writeln("@R13");
        writeln("M=D");

        // RET = *(FRAME - 5)
        writeln("@5");
        writeln("A=D-A");
        writeln("D=M");
        writeln("@R14");
        writeln("M=D");

        // *ARG = pop()
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("@ARG");
        writeln("A=M");
        writeln("M=D");

        // SP = ARG + 1
        writeln("@ARG");
        writeln("D=M+1");
        writeln("@SP");
        writeln("M=D");

        // Restore THAT, THIS, ARG, LCL
        restoreFromFrame(1, "THAT");
        restoreFromFrame(2, "THIS");
        restoreFromFrame(3, "ARG");
        restoreFromFrame(4, "LCL");

        // goto RET
        writeln("@R14");
        writeln("A=M");
        writeln("0;JMP");
    }

    // ------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------

    private void restoreFromFrame(int offset, String dest) throws IOException {
        writeln("@R13");
        writeln("D=M");       // D = FRAME
        writeln("@" + offset);
        writeln("A=D-A"); // A = FRAME - offset
        writeln("D=M");                          // D = *(FRAME - offset)
        writeln("@" + dest);
        writeln("M=D");     // dest = D
    }

    private void pushD() throws IOException {
        writeln("@SP");
        writeln("A=M");
        writeln("M=D");
        writeln("@SP");
        writeln("M=M+1");
    }

    private void pushSymbol(String sym) throws IOException {
        writeln("@" + sym);
        writeln("D=M");
        pushD();
    }

    private void addrFromBase(String segment, int index) throws IOException {
        String base = switch (segment) {
            case "local" -> "LCL";
            case "argument" -> "ARG";
            case "this" -> "THIS";
            case "that" -> "THAT";
            default -> throw new IllegalArgumentException("bad base segment: " + segment);
        };
        // R13 = *(base) + index  (effective address)
        writeln("@" + index);
        writeln("D=A");
        writeln("@" + base);
        writeln("A=M");
        writeln("D=A+D");
        writeln("@R13");
        writeln("M=D");
    }

    private void popToR13Target() throws IOException {
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("@R13");
        writeln("A=M");
        writeln("M=D");
    }

    private String scoped(String label) {
        return (currentFunction == null || currentFunction.isEmpty())
            ? label
            : currentFunction + "$" + label;
    }

    private String unique(String prefix) {
        return prefix + "_" + (labelCounter++);
    }

    private void checkRange(int i, int lo, int hi, String seg) {
        if (i < lo || i > hi) {
            throw new IllegalArgumentException(seg + " index out of range: " + i);
        }
    }

    private void writeln(String s) throws IOException {
        out.write(s);
        out.newLine();
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
