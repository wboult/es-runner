package io.github.wboult.esrunner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ProcessTree {
    record Termination(boolean hadAliveProcesses, boolean graceful, boolean forced) {
    }

    private ProcessTree() {
    }

    static boolean isAlive(Process process, long pid) {
        if (process.isAlive()) {
            return true;
        }
        return handle(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    static Termination terminate(Process process, long pid, Duration timeout) {
        List<ProcessHandle> handles = handles(process, pid);
        if (handles.isEmpty()) {
            return terminateDirect(process, timeout);
        }

        destroy(handles, false);
        if (waitForExit(handles, timeout)) {
            return new Termination(true, true, false);
        }

        destroy(handles, true);
        waitForExit(handles, Duration.ofSeconds(5));
        return new Termination(true, false, true);
    }

    private static Termination terminateDirect(Process process, Duration timeout) {
        if (!process.isAlive()) {
            return new Termination(false, false, false);
        }
        process.destroy();
        try {
            if (process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return new Termination(true, true, false);
            }
            process.destroyForcibly();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return new Termination(true, false, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Termination(true, false, true);
        }
    }

    private static List<ProcessHandle> handles(Process process, long pid) {
        Map<Long, ProcessHandle> handles = new LinkedHashMap<>();
        rootHandle(process).ifPresent(handle -> {
            handles.put(handle.pid(), handle);
            handle.descendants().forEach(descendant -> handles.put(descendant.pid(), descendant));
        });
        handle(pid).ifPresent(handle -> handles.put(handle.pid(), handle));

        return handles.values().stream()
                .filter(ProcessHandle::isAlive)
                .sorted(Comparator.comparingInt(ProcessTree::depth).reversed())
                .toList();
    }

    private static void destroy(List<ProcessHandle> handles, boolean force) {
        for (ProcessHandle handle : handles) {
            if (!handle.isAlive()) {
                continue;
            }
            if (force) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        }
    }

    private static boolean waitForExit(List<ProcessHandle> handles, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (handles.stream().noneMatch(ProcessHandle::isAlive)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return handles.stream().noneMatch(ProcessHandle::isAlive);
            }
        }
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }

    private static Optional<ProcessHandle> rootHandle(Process process) {
        try {
            return Optional.of(process.toHandle());
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ProcessHandle> handle(long pid) {
        if (pid <= 0) {
            return Optional.empty();
        }
        try {
            return ProcessHandle.of(pid);
        } catch (UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    private static int depth(ProcessHandle handle) {
        int depth = 0;
        Optional<ProcessHandle> current = handle.parent();
        while (current.isPresent()) {
            depth++;
            current = current.get().parent();
        }
        return depth;
    }
}
