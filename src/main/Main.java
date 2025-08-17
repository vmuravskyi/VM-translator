package main;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].endsWith(".vm")) {
            System.out.println("Usage: java -cp out main.Main <File.vm>");
            return;
        }

        String inputPath = args[0];
        String outputPath = inputPath.replace(".vm", ".asm");

        try {
            Parser parser = new Parser(inputPath);
            CodeWriter writer = new CodeWriter(outputPath);
            writer.setFileName(Path.of(inputPath).getFileName().toString().replace(".vm", ""));

            while (parser.hasMoreCommands()) {
                parser.advance();
                CommandType type = parser.commandType();

                switch (type) {
                    case C_ARITHMETIC -> writer.writeArithmetic(parser.arg1());
                    case C_PUSH, C_POP -> writer.writePushPop(type, parser.arg1(), parser.arg2());
                    default -> { /* ignore */ }
                }
            }

            writer.close();
            System.out.println("Wrote: " + outputPath);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

}