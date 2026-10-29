package com.example.inventory;

import com.example.inventory.api.InventoryServer;
import com.example.inventory.repository.InMemoryNodeRepository;
import com.example.inventory.service.NodeRequest;
import com.example.inventory.service.NodeService;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Entry point.
 *
 * <pre>
 * java -jar target/network-device-inventory-api.jar [--port 8080] [--seed]
 * </pre>
 * The port may also come from the PORT environment variable. {@code --seed} preloads sample nodes.
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) throws IOException {
        int port = defaultPort();
        boolean seed = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = true;
                case "--port" -> {
                    if (i + 1 >= args.length) {
                        fail("--port requires a value");
                    }
                    port = parsePort(args[++i]);
                }
                default -> fail("Unknown argument: " + args[i]);
            }
        }

        NodeService service = new NodeService(new InMemoryNodeRepository(), Clock.systemUTC());
        if (seed) {
            seed(service);
        }

        InventoryServer server = InventoryServer.start(port, service);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Network Device Inventory API listening on http://localhost:" + server.port());
        System.out.println("Try: curl http://localhost:" + server.port() + "/api/nodes");
    }

    private static int defaultPort() {
        String env = System.getenv("PORT");
        return env == null || env.isBlank() ? 8080 : parsePort(env);
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 0 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException e) {
            fail("Invalid port: " + value);
            return -1; // unreachable
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.err.println("Usage: java -jar network-device-inventory-api.jar [--port N] [--seed]");
        System.exit(2);
    }

    private static void seed(NodeService service) {
        List<NodeRequest> samples = List.of(
                new NodeRequest("core-rtr-01", "Core Router Stockholm", "ROUTER", "ACTIVE", "10.0.0.1"),
                new NodeRequest("core-rtr-02", "Core Router Gothenburg", "ROUTER", "MAINTENANCE", "10.0.1.1"),
                new NodeRequest("edge-sw-01", "Edge Switch A", "SWITCH", "ACTIVE", "10.0.2.10"),
                new NodeRequest("fw-01", "Perimeter Firewall", "FIREWALL", "ACTIVE", "10.0.3.1"),
                new NodeRequest("bs-5g-0042", "5G Base Station 42", "BASE_STATION", "FAULTY", null));
        samples.forEach(service::register);
    }
}
