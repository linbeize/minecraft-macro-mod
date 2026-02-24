package de.linbei.macro;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class MacroStorage {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("macro_mod").resolve("scripts");

    private MacroStorage() {}

    public static Path ensureDir() throws IOException {
        Files.createDirectories(DIR);
        return DIR;
    }

    public static String load(String name) throws IOException {
        Path path = ensureDir().resolve(normalizeName(name) + ".txt");
        return Files.readString(path);
    }

    public static void save(String name, String content) throws IOException {
        Path path = ensureDir().resolve(normalizeName(name) + ".txt");
        Files.writeString(path, content);
    }

    public static Path scriptPath(String name) throws IOException {
        return ensureDir().resolve(normalizeName(name) + ".txt");
    }

    public static List<String> listScriptNames() throws IOException {
        try (Stream<Path> s = Files.list(ensureDir())) {
            return s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase().endsWith(".txt"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(scriptPath(name));
    }

    private static String normalizeName(String name) {
        String n = name.trim();
        if (n.toLowerCase().endsWith(".txt")) {
            n = n.substring(0, n.length() - 4);
        }
        return n;
    }
}
