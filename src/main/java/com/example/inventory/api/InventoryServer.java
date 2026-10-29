package com.example.inventory.api;

import com.example.inventory.service.NodeService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thin lifecycle wrapper around the JDK's built-in HTTP server. */
public final class InventoryServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    private InventoryServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    /** Starts a server. Pass port 0 to let the OS pick a free port (see {@link #port()}). */
    public static InventoryServer start(int port, NodeService service) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        server.createContext("/", new NodeApiHandler(service));
        server.setExecutor(executor);
        server.start();
        return new InventoryServer(server, executor);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }
}
