package de.linbei.macro;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MacroStorage {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("macro_mod").resolve("scripts");
    /** Letters (including CJK), digits, '_' and '-'. No path separators or dots, so no traversal. */
    private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}\\p{N}_\\-]{1,48}");
    private static final String EXT = ".txt";

    private MacroStorage() {}

    public static Path ensureDir() throws IOException {
        Files.createDirectories(DIR);
        return DIR;
    }

    /** Strips whitespace and an optional ".txt" suffix. */
    public static String normalizeName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(EXT)) n = n.substring(0, n.length() - EXT.length());
        return n;
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(normalizeName(name)).matches();
    }

    private static Path pathOf(String name) throws IOException {
        String n = normalizeName(name);
        if (!VALID_NAME.matcher(n).matches()) {
            throw new IOException("Invalid script name: '" + n + "' (letters, digits, _ and - only)");
        }
        return ensureDir().resolve(n + EXT);
    }

    public static boolean exists(String name) {
        try {
            return Files.isRegularFile(pathOf(name));
        } catch (IOException e) {
            return false;
        }
    }

    public static String load(String name) throws IOException {
        return Files.readString(pathOf(name), StandardCharsets.UTF_8);
    }

    /** Writes via a temp file so a crash never leaves a half-written script behind. */
    public static void save(String name, String content) throws IOException {
        Path target = pathOf(name);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void rename(String from, String to) throws IOException {
        Path src = pathOf(from);
        Path dst = pathOf(to);
        if (Files.exists(dst)) throw new FileAlreadyExistsException(normalizeName(to));
        Files.move(src, dst);
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(pathOf(name));
    }

    public static List<String> listScriptNames() throws IOException {
        try (Stream<Path> s = Files.list(ensureDir())) {
            return s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(EXT))
                    .map(n -> n.substring(0, n.length() - EXT.length()))
                    .filter(n -> VALID_NAME.matcher(n).matches())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    /** Never throws; for command suggestions. */
    public static List<String> listScriptNamesQuietly() {
        try {
            return listScriptNames();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** First unused name of the form {@code base}, {@code base_2}, {@code base_3}, ... */
    public static String uniqueName(String base) {
        List<String> names = listScriptNamesQuietly();
        if (!containsIgnoreCase(names, base)) return base;
        for (int i = 2; i < 10_000; i++) {
            String n = base + "_" + i;
            if (!containsIgnoreCase(names, n)) return n;
        }
        return base + "_" + System.currentTimeMillis();
    }

    private static boolean containsIgnoreCase(List<String> names, String n) {
        for (String s : names) if (s.equalsIgnoreCase(n)) return true;
        return false;
    }
}
