package com.elastic.runner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

final class PortPicker {
    private PortPicker() {
    }

    static int pick(int start, int end) {
        for (int port = start; port <= end; port++) {
            if (isAvailable(port)) {
                return port;
            }
        }
        return -1;
    }

    private static boolean isAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
