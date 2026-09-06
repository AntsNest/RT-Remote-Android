package com.limelight.antsnest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable reports remain on disk until the server acknowledges persistence. */
public final class DiagnosticOutbox {
    private final File directory;
    public interface Sender { boolean send(String payload) throws Exception; }
    public DiagnosticOutbox(File directory) { this.directory = directory; }

    public String add(String payload) throws IOException {
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("Diagnostic directory unavailable");
        String name = UUID.randomUUID() + ".json";
        File temporary = new File(directory, name + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary)) {
            stream.write(payload.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        if (!temporary.renameTo(new File(directory, name))) throw new IOException("Diagnostic could not be saved");
        return name;
    }

    public List<String> pending() {
        File[] files = directory.listFiles(file -> file.isFile() && validName(file.getName()));
        if (files == null) return Collections.emptyList();
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        List<String> names = new ArrayList<>();
        for (File file : files) names.add(file.getName());
        return names;
    }

    public boolean deliver(String name, Sender sender) throws Exception {
        if (!validName(name)) throw new IllegalArgumentException("Invalid diagnostic file");
        File file = new File(directory, name);
        if (!file.exists()) return true;
        if (!sender.send(read(file))) return false;
        return file.delete() || !file.exists();
    }

    public static String read(File file) throws IOException {
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static boolean validName(String name) { return name.matches("[0-9a-f-]{36}\\.json"); }

    public static String redact(String text) {
        return text.replaceAll("(?i)Bearer\\s+[^\\s\"'<>]+", "Bearer [redacted]")
                .replaceAll("(?i)((?:password|passwd|pw|pin|(?:access_|refresh_|sso_)?token|authorization|secret)[\"']?\\s*[:=]\\s*[\"']?)[^\\s&\"'<>},]+", "$1[redacted]")
                .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[redacted-jwt]");
    }
}
