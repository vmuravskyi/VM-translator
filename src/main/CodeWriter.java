package main;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CodeWriter {

    private final BufferedWriter out;
    private String fileName = "Static";
    private int labelCounter = 0;

    public CodeWriter(String outputFile) throws IOException {
        out = new BufferedWriter(new FileWriter(outputFile));
    }

    public void setFileName(String base) {
        this.fileName = base;
    }

    public void writeArithmetic(String cmd) throws IOException {
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

    public void writePushPop(CommandType type, String segment, int index) throws IOException {
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
