package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Parser {

    private final List<String> lines = new ArrayList<>();
    private int index = -1;
    private String current;

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

    public String getCurrentCommand() {
        return current;
    }

    public boolean hasMoreCommands() {
        return index + 1 < lines.size();
    }

    public void advance() {
        current = lines.get(++index);
    }

    public CommandType commandType() {
        if (current.startsWith("push")) {
            return CommandType.C_PUSH;
        }
        if (current.startsWith("pop")) {
            return CommandType.C_POP;
        }
        return CommandType.C_ARITHMETIC;
    }

    // arg1: for arithmetic, the command itself; for push/pop, the segment
    public String arg1() {
        if (commandType() == CommandType.C_ARITHMETIC) {
            return current;
        }
        return current.split("\\s+")[1];
    }

    // arg2: only for push/pop
    public int arg2() {
        String[] parts = current.split("\\s+");
        return Integer.parseInt(parts[2]);
    }

}
