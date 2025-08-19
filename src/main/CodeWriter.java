package main;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CodeWriter {

    private final BufferedWriter out;
    private Parser parser = null;
    private String fileName = "Static";
    private String currentVmCommand;
    private boolean withLoggingVmCommand = false;
    private int labelCounter = 0;

    public CodeWriter(String outputFile) throws IOException {
        out = new BufferedWriter(new FileWriter(outputFile));
    }

    public CodeWriter setParser(Parser parser) {
        this.parser = parser;
        return this;
    }

    public CodeWriter withFileName(String base) {
        this.fileName = base;
        return this;
    }

    public void setCurrentVmCommand(String vmCommand) {
        this.currentVmCommand = vmCommand;
    }

    /**
     * Logs VM command as comment before translating into assembly.
     *
     * @param vmCommandLogger log VM command
     * @return this
     */
    public CodeWriter withLoggingVmCommand(boolean vmCommandLogger) {
        this.withLoggingVmCommand = vmCommandLogger;
        return this;
    }

    private void logCurrentVmCommandAsComment() throws IOException {
        writeln("// " + currentVmCommand);
    }

    public void writeCommand() throws IOException {
        CommandType type = parser.commandType();
        switch (type) {
            case C_ARITHMETIC -> this.writeArithmetic();
            case C_PUSH, C_POP -> this.writePushPop();
            default -> throw new UnsupportedOperationException("Unknown command: " + type);
        }
    }

    public void writeArithmetic() throws IOException {
        if (withLoggingVmCommand) {
            logCurrentVmCommandAsComment();
        }

        String cmd = parser.arg1();
        switch (cmd) {
            case "add" -> writeBinary("M=D+M");
            case "sub" -> writeBinary("M=M-D");
            case "and" -> writeBinary("M=D&M");
            case "or" -> writeBinary("M=D|M");
            case "neg" -> writeUnary("M=-M");
            case "not" -> writeUnary("M=!M");
            case "eq" -> writeCompare("JEQ");
            case "gt" -> writeCompare("JGT");
            case "lt" -> writeCompare("JLT");
            default -> throw new IllegalArgumentException("Unknown arithmetic: " + cmd);
        }
    }

    public void writePushPop() throws IOException {
        if (withLoggingVmCommand) {
            logCurrentVmCommandAsComment();
        }

        CommandType type = parser.commandType();
        String segment = parser.arg1();
        int index = parser.arg2();
        if (type == CommandType.C_PUSH) {
            switch (segment) {
                case "constant" -> {
                    writeln("@" + index);
                    writeln("D=A");
                    pushD();
                }
                case "local", "argument", "this", "that" -> {
                    addrFromBase(segment, index);   // R13 = base+index
                    writeln("@R13");
                    writeln("A=M");
                    writeln("D=M");
                    pushD();
                }
                case "temp" -> {
                    int addr = 5 + index;          // R5..R12
                    writeln("@" + addr);
                    writeln("D=M");
                    pushD();
                }
                case "pointer" -> {
                    String sym = (index == 0) ? "THIS" : "THAT";
                    writeln("@" + sym);
                    writeln("D=M");
                    pushD();
                }
                case "static" -> {
                    String sym = fileName + "." + index;
                    writeln("@" + sym);
                    writeln("D=M");
                    pushD();
                }
                default -> throw new IllegalArgumentException("push: bad segment " + segment);
            }
        } else { // POP
            switch (segment) {
                case "local", "argument", "this", "that" -> {
                    addrFromBase(segment, index);   // R13 = base+index
                    popToR13Target();
                }
                case "temp" -> {
                    int addr = 5 + index;
                    popToDirectAddress(addr);
                }
                case "pointer" -> {
                    String sym = (index == 0) ? "THIS" : "THAT";
                    popToSymbol(sym);
                }
                case "static" -> {
                    String sym = fileName + "." + index;
                    popToSymbol(sym);
                }
                case "constant" -> throw new IllegalArgumentException("pop constant is invalid");
                default -> throw new IllegalArgumentException("pop: bad segment " + segment);
            }
        }
    }

    // ---------- helpers ----------

    private void writeBinary(String op) throws IOException {
        // y = *--SP -> D; x at * (SP-1)
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("A=A-1");
        writeln(op);
    }

    private void writeUnary(String op) throws IOException {
        writeln("@SP");
        writeln("A=M-1");
        writeln(op);
    }

    private void writeCompare(String jump) throws IOException {
        String TRUE = unique("TRUE");
        String END = unique("END");
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");       // y
        writeln("A=A-1");     // x at *SP-1
        writeln("D=M-D");     // x - y
        writeln("@" + TRUE);
        writeln("D;" + jump);
        // false -> 0
        writeln("@SP");
        writeln("A=M-1");
        writeln("M=0");
        writeln("@" + END);
        writeln("0;JMP");
        // true -> -1
        writeln("(" + TRUE + ")");
        writeln("@SP");
        writeln("A=M-1");
        writeln("M=-1");
        writeln("(" + END + ")");
    }

    private void addrFromBase(String segment, int index) throws IOException {
        String base = switch (segment) {
            case "local" -> "LCL";
            case "argument" -> "ARG";
            case "this" -> "THIS";
            case "that" -> "THAT";
            default -> throw new IllegalArgumentException("bad base segment: " + segment);
        };
        writeln("@" + index);
        writeln("D=A");
        writeln("@" + base);
        writeln("A=M");
        writeln("D=A+D");
        writeln("@R13");
        writeln("M=D");       // R13 = base + index
    }

    private void popToR13Target() throws IOException {
        // *R13 = pop()
        writeln("@SP");
        writeln("AM=M-1");
        writeln("D=M");
        writeln("@R13");
        writeln("A=M");
        writeln("M=D");
    }

    private void popToDirectAddress(int addr) throws IOException {
        writeln("@" + addr);
        writeln("D=A");
        writeln("@R13");
        writeln("M=D");
        popToR13Target();
    }

    private void popToSymbol(String symbol) throws IOException {
        writeln("@" + symbol);
        writeln("D=A");
        writeln("@R13");
        writeln("M=D");
        popToR13Target();
    }

    private void pushD() throws IOException {
        writeln("@SP");
        writeln("A=M");
        writeln("M=D");
        writeln("@SP");
        writeln("M=M+1");
    }

    private String unique(String base) {
        return base + "_" + (labelCounter++);
    }

    private void writeln(String s) throws IOException {
        out.write(s);
        out.write('\n');
    }

    public void close() throws IOException {
        out.flush();
        out.close();
    }

}
