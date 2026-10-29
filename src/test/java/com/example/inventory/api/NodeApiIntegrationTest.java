package com.example.inventory.api;

import com.example.inventory.repository.InMemoryNodeRepository;
import com.example.inventory.service.NodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Starts a real server on a random port and talks to it over HTTP. */
class NodeApiIntegrationTest {

    private InventoryServer server;
    private HttpClient client;
    private String base;

    record Resp(int status, String body, java.net.http.HttpHeaders headers) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return (Map<String, Object>) Json.parse(body);
        }
        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        NodeService service = new NodeService(new InMemoryNodeRepository(), Clock.systemUTC());
        server = InventoryServer.start(0, service);
        client = HttpClient.newHttpClient();
        base = "http://localhost:" + server.port();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private Resp send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json");
        b.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers());
    }

    private static final String ROUTER = """
            {"id":"rtr-001","name":"Core Router","type":"ROUTER","status":"ACTIVE","ipAddress":"10.0.0.1"}""";

    private void create(String id, String type, String status) throws Exception {
        Resp r = send("POST", "/api/nodes", String.format(
                "{\"id\":\"%s\",\"name\":\"Node %s\",\"type\":\"%s\",\"status\":\"%s\"}", id, id, type, status));
        assertEquals(201, r.status(), r.body());
    }

    // ---- happy path ---------------------------------------------------------------------------

    @Test
    void healthEndpoint() throws Exception {
        Resp r = send("GET", "/health", null);
        assertEquals(200, r.status());
        assertEquals("UP", r.json().get("status"));
        assertTrue(r.header("Content-Type").startsWith("application/json"));
    }

    @Test
    @DisplayName("full lifecycle: create, read, replace, patch status, delete")
    void fullLifecycle() throws Exception {
        Resp created = send("POST", "/api/nodes", ROUTER);
        assertEquals(201, created.status());
        assertEquals("/api/nodes/rtr-001", created.header("Location"));
        assertEquals("rtr-001", created.json().get("id"));
        assertEquals("ACTIVE", created.json().get("status"));
        assertNotNull(created.json().get("createdAt"));

        Resp fetched = send("GET", "/api/nodes/rtr-001", null);
        assertEquals(200, fetched.status());
        assertEquals("Core Router", fetched.json().get("name"));
        assertEquals("10.0.0.1", fetched.json().get("ipAddress"));

        Resp replaced = send("PUT", "/api/nodes/rtr-001",
                "{\"name\":\"Edge FW\",\"type\":\"firewall\",\"status\":\"maintenance\"}");
        assertEquals(200, replaced.status());
        assertEquals("Edge FW", replaced.json().get("name"));
        assertEquals("FIREWALL", replaced.json().get("type"));
        assertNull(replaced.json().get("ipAddress"));
        assertEquals(created.json().get("createdAt"), replaced.json().get("createdAt"));

        Resp patched = send("PATCH", "/api/nodes/rtr-001/status", "{\"status\":\"FAULTY\"}");
        assertEquals(200, patched.status());
        assertEquals("FAULTY", patched.json().get("status"));
        assertEquals("Edge FW", patched.json().get("name"));

        Resp deleted = send("DELETE", "/api/nodes/rtr-001", null);
        assertEquals(204, deleted.status());
        assertEquals("", deleted.body());

        assertEquals(404, send("GET", "/api/nodes/rtr-001", null).status());
    }

    @Test
    void listReturnsSortedNodesWithCountAndFilters() throws Exception {
        create("ccc", "ROUTER", "ACTIVE");
        create("aaa", "SWITCH", "ACTIVE");
        create("bbb", "ROUTER", "FAULTY");

        Resp all = send("GET", "/api/nodes", null);
        assertEquals(200, all.status());
        assertEquals(3L, all.json().get("count"));
        List<?> nodes = (List<?>) all.json().get("nodes");
        assertEquals("aaa", ((Map<?, ?>) nodes.get(0)).get("id"));
        assertEquals("ccc", ((Map<?, ?>) nodes.get(2)).get("id"));

        assertEquals(2L, send("GET", "/api/nodes?type=router", null).json().get("count"));
        assertEquals(1L, send("GET", "/api/nodes?type=ROUTER&status=faulty", null).json().get("count"));
        assertEquals(0L, send("GET", "/api/nodes?type=FIREWALL", null).json().get("count"));
    }

    @Test
    void emptyInventoryListsZeroNodes() throws Exception {
        Resp r = send("GET", "/api/nodes", null);
        assertEquals(200, r.status());
        assertEquals(0L, r.json().get("count"));
        assertEquals(List.of(), r.json().get("nodes"));
    }

    @Test
    void trailingSlashIsTolerated() throws Exception {
        create("rtr-001", "ROUTER", "ACTIVE");
        assertEquals(200, send("GET", "/api/nodes/", null).status());
        assertEquals(200, send("GET", "/api/nodes/rtr-001/", null).status());
    }

    // ---- validation errors (400) -------------------------------------------------------------

    @Test
    void invalidFieldsReturn400WithAllDetails() throws Exception {
        Resp r = send("POST", "/api/nodes",
                "{\"id\":\"!\",\"name\":\"\",\"type\":\"toaster\",\"status\":\"x\",\"ipAddress\":\"300.1.1.1\"}");
        assertEquals(400, r.status());
        assertEquals("VALIDATION_ERROR", r.json().get("error"));
        assertEquals(5, ((List<?>) r.json().get("details")).size());
        assertEquals(404, send("GET", "/api/nodes/rtr-001", null).status(), "nothing stored");
    }

    @Test
    void missingRequiredFieldsReturn400() throws Exception {
        Resp r = send("POST", "/api/nodes", "{}");
        assertEquals(400, r.status());
        assertTrue(r.body().contains("id is required"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        Resp r = send("POST", "/api/nodes", "{\"id\": ");
        assertEquals(400, r.status());
        assertEquals("INVALID_JSON", r.json().get("error"));
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        Resp r = send("POST", "/api/nodes", "");
        assertEquals(400, r.status());
        assertEquals("INVALID_JSON", r.json().get("error"));
    }

    @Test
    void nonObjectBodyReturns400() throws Exception {
        assertEquals(400, send("POST", "/api/nodes", "[1,2]").status());
        assertEquals(400, send("POST", "/api/nodes", "\"text\"").status());
    }

    @Test
    void unknownAndNonStringFieldsReturn400() throws Exception {
        Resp unknown = send("POST", "/api/nodes",
                "{\"id\":\"rtr-001\",\"name\":\"N\",\"type\":\"ROUTER\",\"status\":\"ACTIVE\",\"color\":\"red\"}");
        assertEquals(400, unknown.status());
        assertTrue(unknown.body().contains("unknown field 'color'"));

        Resp numeric = send("POST", "/api/nodes",
                "{\"id\":123,\"name\":\"N\",\"type\":\"ROUTER\",\"status\":\"ACTIVE\"}");
        assertEquals(400, numeric.status());
        assertTrue(numeric.body().contains("must be a string"));
    }

    @Test
    void invalidFilterValueReturns400() throws Exception {
        assertEquals(400, send("GET", "/api/nodes?type=toaster", null).status());
        assertEquals(400, send("GET", "/api/nodes?status=sleeping", null).status());
    }

    @Test
    void putWithMismatchedBodyIdReturns400() throws Exception {
        send("POST", "/api/nodes", ROUTER);
        Resp r = send("PUT", "/api/nodes/rtr-001",
                "{\"id\":\"rtr-999\",\"name\":\"N\",\"type\":\"ROUTER\",\"status\":\"ACTIVE\"}");
        assertEquals(400, r.status());
    }

    @Test
    void putWithMissingFieldsReturns400AndKeepsNode() throws Exception {
        send("POST", "/api/nodes", ROUTER);
        assertEquals(400, send("PUT", "/api/nodes/rtr-001", "{\"name\":\"Only name\"}").status());
        assertEquals("Core Router", send("GET", "/api/nodes/rtr-001", null).json().get("name"));
    }

    @Test
    void patchStatusValidation() throws Exception {
        send("POST", "/api/nodes", ROUTER);
        assertEquals(400, send("PATCH", "/api/nodes/rtr-001/status", "{\"status\":\"sleeping\"}").status());
        assertEquals(400, send("PATCH", "/api/nodes/rtr-001/status", "{}").status());
        assertEquals(400, send("PATCH", "/api/nodes/rtr-001/status", "{\"name\":\"x\"}").status());
        assertEquals("ACTIVE", send("GET", "/api/nodes/rtr-001", null).json().get("status"));
    }

    @Test
    void oversizedBodyReturns413() throws Exception {
        String huge = "{\"name\":\"" + "x".repeat(NodeApiHandler.MAX_BODY_BYTES) + "\"}";
        assertEquals(413, send("POST", "/api/nodes", huge).status());
    }

    // ---- not found / conflict / method errors ---------------------------------------------------

    @Test
    void unknownNodeReturns404ForEveryVerb() throws Exception {
        for (Resp r : List.of(
                send("GET", "/api/nodes/ghost-1", null),
                send("PUT", "/api/nodes/ghost-1", "{\"name\":\"N\",\"type\":\"ROUTER\",\"status\":\"ACTIVE\"}"),
                send("PATCH", "/api/nodes/ghost-1/status", "{\"status\":\"ACTIVE\"}"),
                send("DELETE", "/api/nodes/ghost-1", null))) {
            assertEquals(404, r.status(), r.body());
            assertEquals("NODE_NOT_FOUND", r.json().get("error"));
            assertTrue(r.json().get("message").toString().contains("ghost-1"));
        }
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        assertEquals(201, send("POST", "/api/nodes", ROUTER).status());
        Resp second = send("POST", "/api/nodes", ROUTER);
        assertEquals(409, second.status());
        assertEquals("NODE_ALREADY_EXISTS", second.json().get("error"));
    }

    @Test
    void deletingTwiceReturns404() throws Exception {
        send("POST", "/api/nodes", ROUTER);
        assertEquals(204, send("DELETE", "/api/nodes/rtr-001", null).status());
        assertEquals(404, send("DELETE", "/api/nodes/rtr-001", null).status());
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        Resp r = send("GET", "/api/widgets", null);
        assertEquals(404, r.status());
        assertEquals("NOT_FOUND", r.json().get("error"));
        assertEquals(404, send("GET", "/", null).status());
        assertEquals(404, send("GET", "/api/nodes/rtr-001/other", null).status());
        assertEquals(404, send("GET", "/api/nodes/rtr-001/status/extra", null).status());
    }

    @Test
    void wrongMethodReturns405WithAllowHeader() throws Exception {
        Resp collection = send("DELETE", "/api/nodes", null);
        assertEquals(405, collection.status());
        assertEquals("GET, POST", collection.header("Allow"));

        Resp item = send("POST", "/api/nodes/rtr-001", "{}");
        assertEquals(405, item.status());
        assertEquals("GET, PUT, DELETE", item.header("Allow"));

        assertEquals(405, send("GET", "/api/nodes/rtr-001/status", null).status());
        assertEquals(405, send("POST", "/health", null).status());
    }

    @Test
    void malformedPercentEncodingReturns400() throws Exception {
        // java.net.URI refuses to build this URL, so speak raw HTTP over a socket.
        try (java.net.Socket socket = new java.net.Socket("localhost", server.port())) {
            socket.getOutputStream().write(("GET /api/nodes/%ZZ HTTP/1.1\r\nHost: localhost\r\n"
                    + "Connection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String response = new String(socket.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 400"), response);
        }
    }

    @Test
    void errorResponsesNeverLeakStackTraces() throws Exception {
        Resp r = send("POST", "/api/nodes", "{\"id\": ");
        assertFalse(r.body().contains("Exception"));
        assertFalse(r.body().contains("at com."));
    }

    @Test
    void unicodeNamesSurviveRoundTrip() throws Exception {
        String body = "{\"id\":\"uni-001\",\"name\":\"Stockholm \\u00c5 \u4e2d\",\"type\":\"ROUTER\",\"status\":\"ACTIVE\"}";
        assertEquals(201, send("POST", "/api/nodes", body).status());
        assertEquals("Stockholm \u00c5 \u4e2d", send("GET", "/api/nodes/uni-001", null).json().get("name"));
    }
}
