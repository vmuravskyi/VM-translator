package main;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length != 1 || !args[0].endsWith(".vm")) {
            LOGGER.log(Level.INFO, "Usage: java -cp out main.Main <File.vm>");
            return;
        }

        String inputPath = args[0];
        String outputPath = inputPath.replace(".vm", ".asm");

        Parser parser = null;
        CodeWriter writer = null;
        try {
            parser = new Parser(inputPath);
            writer = new CodeWriter(outputPath)
                .setParser(parser)
                .withLoggingVmCommand(true)
                .withFileName(Path.of(inputPath).getFileName().toString().replace(".vm", ""));

            while (parser.hasMoreCommands()) {
                parser.advance();
                writer.setCurrentVmCommand(parser.getCurrentCommand());
                writer.writeCommand();
            }

            LOGGER.log(Level.INFO, "Wrote: {0}", outputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "I/O error while writing {0}: {1}", new Object[]{outputPath, e.getMessage()});
            LOGGER.log(Level.FINEST, "Stacktrace", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to close writer for {0}: {1}", new Object[]{outputPath, e.getMessage()});
                }
            }

        }
    }

}