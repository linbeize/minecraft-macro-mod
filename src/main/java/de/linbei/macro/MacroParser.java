package de.linbei.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MacroParser {
    private static final Pattern PREFIX_COUNT = Pattern.compile("^\\s*\\d+\\s+");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private MacroParser() {}

    public static List<MacroInstruction> parse(String script) {
        List<MacroInstruction> out = new ArrayList<>();
        String[] lines = script.split("\\R+");

        Stack<Integer> forStack = new Stack<>();

        for (String raw : lines) {
            String line = raw.trim();
            while (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (line.isEmpty() || line.startsWith("#")) continue;

            line = PREFIX_COUNT.matcher(line).replaceFirst("").trim();
            String upper = line.toUpperCase(Locale.ROOT);

            if (upper.startsWith("KEYDOWN")) {
                out.add(new MacroInstruction(MacroInstruction.Type.KEY_DOWN, extractKey(line), 0, -1));
            } else if (upper.startsWith("KEYUP")) {
                out.add(new MacroInstruction(MacroInstruction.Type.KEY_UP, extractKey(line), 0, -1));
            } else if (upper.startsWith("KEYPRESS")) {
                out.add(new MacroInstruction(MacroInstruction.Type.KEY_PRESS, extractKey(line), 0, -1));
            } else if (upper.startsWith("LEFTDOWN")) {
                out.add(new MacroInstruction(MacroInstruction.Type.LEFT_DOWN, null, 0, -1));
            } else if (upper.startsWith("LEFTUP")) {
                out.add(new MacroInstruction(MacroInstruction.Type.LEFT_UP, null, 0, -1));
            } else if (upper.startsWith("DELAY")) {
                long ms = Long.parseLong(line.replaceAll("(?i)delay", "").trim());
                out.add(new MacroInstruction(MacroInstruction.Type.DELAY, null, ms, -1));
            } else if (upper.startsWith("FOR")) {
                int count = Integer.parseInt(line.replaceAll("(?i)for", "").trim());
                int idx = out.size();
                out.add(new MacroInstruction(MacroInstruction.Type.FOR, null, count, -1));
                forStack.push(idx);
            } else if (upper.startsWith("NEXT")) {
                if (forStack.isEmpty()) throw new IllegalArgumentException("NEXT without FOR");
                int forIdx = forStack.pop();
                int nextIdx = out.size();
                out.add(new MacroInstruction(MacroInstruction.Type.NEXT, null, 0, forIdx));
                out.set(forIdx, out.get(forIdx).withJump(nextIdx));
            } else {
                throw new IllegalArgumentException("Unknown command: " + line);
            }
        }

        if (!forStack.isEmpty()) throw new IllegalArgumentException("FOR without NEXT");
        return out;
    }

    private static String extractKey(String line) {
        Matcher m = QUOTED.matcher(line);
        if (m.find()) return m.group(1);
        String[] p = line.split("\\s+", 2);
        if (p.length < 2) throw new IllegalArgumentException("Missing key in: " + line);
        return p[1].trim();
    }
}
