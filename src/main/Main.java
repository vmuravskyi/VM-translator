package main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * # compile javac -d out src/main/java/vm/*.java
 * <p>
 * # run on a single .vm file java -cp out vm.Main path/to/File.vm
 * <p>
 * # run on a folder of .vm files (produces FolderName/FolderName.asm with bootstrap) java -cp out vm.Main
 * path/to/Folder
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length != 1) {
            LOGGER.log(Level.INFO, "Usage: java -cp out vm.Main <File.vm|Directory>");
            return;
        }

        try {
            Path input = Paths.get(args[0]).toAbsolutePath();
            boolean isDir = Files.isDirectory(input);
            List<Path> vmFiles = new ArrayList<>();
            Path output;

            if (isDir) {
                try (Stream<Path> s = Files.list(input)) {
                    s.filter(p -> p.toString().endsWith(".vm"))
                        .sorted()
                        .forEach(vmFiles::add);
                }
                if (vmFiles.isEmpty()) {
                    LOGGER.log(Level.SEVERE, "No .vm files found in directory: {0}", input);
                    return;
                }
                String base = input.getFileName().toString();
                output = input.resolve(base + ".asm");
            } else {
                if (!input.toString().endsWith(".vm")) {
                    LOGGER.log(Level.SEVERE, "Expected .vm file or directory");
                    return;
                }
                vmFiles.add(input);
                output = Paths.get(input.toString().replaceFirst("\\.vm$", ".asm"));
            }

            try (CodeWriter writer = new CodeWriter(output.toString())) {
                // Per spec: emit bootstrap iff translating a folder (multi-file program).
                if (isDir) {
                    writer.writeInit();
                }

                for (Path vmFile : vmFiles) {
                    String base = vmFile.getFileName().toString().replaceFirst("\\.vm$", "");
                    writer.setFileName(base);

                    Parser parser = new Parser(vmFile.toString());
                    while (parser.hasMoreCommands()) {
                        parser.advance();
                        switch (parser.commandType()) {
                            case C_ARITHMETIC -> writer.writeArithmetic(parser.arg1());
                            case C_PUSH, C_POP -> writer.writePushPop(parser.commandType(), parser.arg1(), parser.arg2());
                            case C_LABEL -> writer.writeLabel(parser.arg1());
                            case C_GOTO -> writer.writeGoto(parser.arg1());
                            case C_IF -> writer.writeIf(parser.arg1());
                            case C_FUNCTION -> writer.writeFunction(parser.arg1(), parser.arg2());
                            case C_CALL -> writer.writeCall(parser.arg1(), parser.arg2());
                            case C_RETURN -> writer.writeReturn();
                        }
                    }
                }
            }

            LOGGER.log(Level.INFO, "Wrote: {0}", output);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "I/O error: {0}", e.getMessage());
        }
    }

}