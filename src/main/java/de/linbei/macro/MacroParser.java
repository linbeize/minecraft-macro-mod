package de.linbei.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses macro scripts.
 *
 * <pre>
 * KeyDown "W", 1        # hold a key (the trailing count is accepted for compatibility and ignored)
 * KeyUp "W"
 * KeyPress "2", 3       # tap a key N times (default 1)
 * LeftDown / LeftUp / LeftClick [n]
 * RightDown / RightUp / RightClick [n]
 * Delay 500             # milliseconds
 * For 9 ... Next        # loops, may be nested (EndFor is an alias of Next)
 * </pre>
 *
 * Statements are separated by new lines or ';'. Comments start with '#' or '//'.
 * A leading line number ("12 KeyDown W") is ignored for compatibility with exported scripts.
 * Pure Java, no Minecraft classes, so it can be unit tested.
 */
public final class MacroParser {
    public static final long MAX_DELAY_MS = 24L * 60 * 60 * 1000;
    public static final int MAX_LOOP = 1_000_000;
    public static final int MAX_CLICKS = 64;

    private static final Pattern LINE_NUMBER_PREFIX = Pattern.compile("^\\d+\\s+");
    private static final Pattern COMMAND = Pattern.compile("^([A-Za-z]+)\\s*(.*)$");

    private MacroParser() {}

    /** Parse error with a 1-based line number and a translation key (see the lang files). */
    public static final class ParseException extends IllegalArgumentException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        private final int line;
        private final String key;
        private final transient Object[] args;

        ParseException(int line, String key, String fallback, Object... args) {
            super("Line " + line + ": " + fallback);
            this.line = line;
            this.key = key;
            this.args = args;
        }

        public int line() { return line; }
        public String key() { return key; }
        public Object[] args() { return args.clone(); }
    }

    public static List<MacroInstruction> parse(String script) {
        List<MacroInstruction> out = new ArrayList<>();
        Deque<Integer> forStack = new ArrayDeque<>();
        String[] lines = (script == null ? "" : script).split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            for (String stmt : splitStatements(lines[i])) {
                parseStatement(stmt, lineNo, out, forStack);
            }
        }

        if (!forStack.isEmpty()) {
            int forLine = out.get(forStack.peek()).line();
            throw new ParseException(forLine, "macro_mod.parse.for_without_next", "For without Next");
        }
        return out;
    }

    private static void parseStatement(String raw, int lineNo, List<MacroInstruction> out, Deque<Integer> forStack) {
        String stmt = raw.trim();
        while (stmt.endsWith(",")) stmt = stmt.substring(0, stmt.length() - 1).trim();
        if (stmt.isEmpty()) return;
        stmt = LINE_NUMBER_PREFIX.matcher(stmt).replaceFirst("");

        Matcher m = COMMAND.matcher(stmt);
        if (!m.matches()) {
            throw new ParseException(lineNo, "macro_mod.parse.unknown_command", "Unknown command: " + stmt, stmt);
        }
        String cmd = m.group(1).toUpperCase(Locale.ROOT);
        List<String> args = splitArgs(m.group(2), lineNo);

        switch (cmd) {
            case "KEYDOWN" -> out.add(key(MacroInstruction.Type.KEY_DOWN, requireKey(args, lineNo, cmd), 0, lineNo));
            case "KEYUP" -> out.add(key(MacroInstruction.Type.KEY_UP, requireKey(args, lineNo, cmd), 0, lineNo));
            case "KEYPRESS" -> {
                String k = requireKey(args, lineNo, cmd);
                long n = args.size() > 1 ? number(args.get(1), 1, MAX_CLICKS, lineNo) : 1;
                out.add(key(MacroInstruction.Type.KEY_PRESS, k, n, lineNo));
            }
            // "LeftDown 1" style trailing counts are accepted and ignored for down/up.
            case "LEFTDOWN" -> out.add(key(MacroInstruction.Type.KEY_DOWN, "LMB", 0, lineNo));
            case "LEFTUP" -> out.add(key(MacroInstruction.Type.KEY_UP, "LMB", 0, lineNo));
            case "LEFTCLICK" -> out.add(key(MacroInstruction.Type.KEY_PRESS, "LMB", optCount(args, lineNo), lineNo));
            case "RIGHTDOWN" -> out.add(key(MacroInstruction.Type.KEY_DOWN, "RMB", 0, lineNo));
            case "RIGHTUP" -> out.add(key(MacroInstruction.Type.KEY_UP, "RMB", 0, lineNo));
            case "RIGHTCLICK" -> out.add(key(MacroInstruction.Type.KEY_PRESS, "RMB", optCount(args, lineNo), lineNo));
            case "DELAY", "WAIT" -> {
                long ms = number(requireArg(args, lineNo, cmd), 0, MAX_DELAY_MS, lineNo);
                out.add(new MacroInstruction(MacroInstruction.Type.DELAY, null, ms, -1, lineNo));
            }
            case "FOR" -> {
                long count = number(requireArg(args, lineNo, cmd), 0, MAX_LOOP, lineNo);
                forStack.push(out.size());
                out.add(new MacroInstruction(MacroInstruction.Type.FOR, null, count, -1, lineNo));
            }
            case "NEXT", "ENDFOR" -> {
                if (forStack.isEmpty()) {
                    throw new ParseException(lineNo, "macro_mod.parse.next_without_for", "Next without For");
                }
                int forIdx = forStack.pop();
                int nextIdx = out.size();
                out.add(new MacroInstruction(MacroInstruction.Type.NEXT, null, 0, forIdx, lineNo));
                out.set(forIdx, out.get(forIdx).withJump(nextIdx));
            }
            default -> throw new ParseException(lineNo, "macro_mod.parse.unknown_command", "Unknown command: " + m.group(1), m.group(1));
        }
    }

    private static MacroInstruction key(MacroInstruction.Type type, String key, long n, int line) {
        return new MacroInstruction(type, KeyMapper.normalize(key), n, -1, line);
    }

    private static long optCount(List<String> args, int line) {
        return args.isEmpty() ? 1 : number(args.get(0), 1, MAX_CLICKS, line);
    }

    private static String requireArg(List<String> args, int line, String cmd) {
        if (args.isEmpty()) {
            throw new ParseException(line, "macro_mod.parse.missing_arg", "Missing argument for " + cmd, cmd);
        }
        return args.get(0);
    }

    private static String requireKey(List<String> args, int line, String cmd) {
        String k = requireArg(args, line, cmd);
        if (!KeyMapper.isKnown(k)) {
            throw new ParseException(line, "macro_mod.parse.unknown_key", "Unknown key: " + k, k);
        }
        return k;
    }

    private static long number(String s, long min, long max, int line) {
        long v;
        try {
            v = Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new ParseException(line, "macro_mod.parse.not_a_number", "Not a number: " + s, s);
        }
        if (v < min || v > max) {
            throw new ParseException(line, "macro_mod.parse.out_of_range", v + " is outside " + min + ".." + max, v, min, max);
        }
        return v;
    }

    /** Splits one physical line on ';' (outside quotes) and strips '#' / '//' comments. */
    static List<String> splitStatements(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '#' || (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/')) break;
                if (c == ';') {
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    /** Splits arguments on ',' outside quotes, unquotes them and drops empty ones. */
    private static List<String> splitArgs(String s, int line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                addArg(out, cur);
            } else {
                cur.append(c);
            }
        }
        if (quoted) throw new ParseException(line, "macro_mod.parse.unclosed_quote", "Unclosed quote");
        addArg(out, cur);
        return out;
    }

    private static void addArg(List<String> out, StringBuilder cur) {
        String a = cur.toString().trim();
        if (!a.isEmpty()) out.add(a);
        cur.setLength(0);
    }
}
