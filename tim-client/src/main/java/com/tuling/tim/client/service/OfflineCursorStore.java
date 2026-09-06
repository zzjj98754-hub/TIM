package com.tuling.tim.client.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps the largest continuous offline cursor processed by this client session. */
@Component
public class OfflineCursorStore {
    private final AtomicLong cursor = new AtomicLong();
    private final Set<Long> completed = new HashSet<>();
    @Value("${tim.offline.cursor.file:}") private String configuredFile;
    private Path file;
    public OfflineCursorStore() { }
    OfflineCursorStore(long initialCursor) { cursor.set(initialCursor); }
    @PostConstruct void load() {
        if (configuredFile == null || configuredFile.isBlank()) return;
        file = Path.of(configuredFile);
        try { if (Files.exists(file)) cursor.set(Long.parseLong(Files.readString(file).trim())); }
        catch (Exception ignored) { }
    }
    public long current() { return cursor.get(); }
    public synchronized void advance(long next) {
        if (next <= cursor.get()) return;
        completed.add(next);
        long candidate = cursor.get() + 1;
        while (completed.remove(candidate)) cursor.set(candidate++);
        persist();
    }
    private void persist() {
        if (file == null) return;
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, Long.toString(cursor.get()), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) { }
    }
}
