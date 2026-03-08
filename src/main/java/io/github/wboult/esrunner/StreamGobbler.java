package io.github.wboult.esrunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class StreamGobbler implements Runnable {
    private final InputStream input;
    private final Path logFile;
    private final boolean quiet;

    StreamGobbler(InputStream input, Path logFile, boolean quiet) {
        this.input = input;
        this.logFile = logFile;
        this.quiet = quiet;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[8192];
        try (InputStream in = input;
             OutputStream out = Files.newOutputStream(logFile,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (!quiet) {
                    System.out.write(buffer, 0, read);
                }
            }
            out.flush();
            if (!quiet) {
                System.out.flush();
            }
        } catch (IOException e) {
            System.err.println("[es-runner] StreamGobbler error writing to " + logFile + ": " + e.getMessage());
        }
    }
}
