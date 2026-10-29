package com.example.inventory.api;

import com.example.inventory.exception.DuplicateNodeException;
import com.example.inventory.exception.NodeNotFoundException;
import com.example.inventory.exception.ValidationException;
import com.example.inventory.model.Node;
import com.example.inventory.service.NodeRequest;
import com.example.inventory.service.NodeService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes HTTP requests to {@link NodeService} and translates exceptions into JSON errors.
 *
 * <pre>
 * GET    /health
 * GET    /api/nodes[?type=&amp;status=]
 * POST   /api/nodes
 * GET    /api/nodes/{id}
 * PUT    /api/nodes/{id}
 * DELETE /api/nodes/{id}
 * PATCH  /api/nodes/{id}/status
 * </pre>
 */
public final class NodeApiHandler implements HttpHandler {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Set<String> NODE_FIELDS = Set.of("id", "name", "type", "status", "ipAddress");
    private static final Set<String> STATUS_FIELDS = Set.of("status");

    private final NodeService service;

    public NodeApiHandler(NodeService service) {
        this.service = service;
    }

    /** Internal carrier for an HTTP error response. */
    private static final class HttpError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;
        final String code;
        final transient List<String> details;
        final String allow;

        HttpError(int status, String code, String message, List<String> details, String allow) {
            super(message);
            this.status = status;
            this.code = code;
            this.details = details;
            this.allow = allow;
        }

        HttpError(int status, String code, String message) {
            this(status, code, message, List.of(), null);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ValidationException e) {
            sendError(exchange, new HttpError(400, "VALIDATION_ERROR", "Request validation failed",
                    e.getErrors(), null));
        } catch (NodeNotFoundException e) {
            sendError(exchange, new HttpError(404, "NODE_NOT_FOUND", e.getMessage()));
        } catch (DuplicateNodeException e) {
            sendError(exchange, new HttpError(409, "NODE_ALREADY_EXISTS", e.getMessage()));
        } catch (HttpError e) {
            sendError(exchange, e);
        } catch (RuntimeException e) {
            System.err.println("Unexpected error handling " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI() + ": " + e);
            sendError(exchange, new HttpError(500, "INTERNAL_ERROR", "Unexpected server error"));
        } finally {
            exchange.close();
        }
    }

    // ---- routing ------------------------------------------------------------------------------

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getRawPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (path.equals("/health")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, Map.of("status", "UP"));
            return;
        }
        if (path.equals("/api/nodes")) {
            switch (method) {
                case "GET" -> listNodes(ex);
                case "POST" -> createNode(ex);
                default -> throw methodNotAllowed("GET, POST");
            }
            return;
        }
        if (path.startsWith("/api/nodes/")) {
            String[] segments = path.substring("/api/nodes/".length()).split("/", -1);
            String id = decode(segments[0]);
            if (segments.length == 1) {
                switch (method) {
                    case "GET" -> sendJson(ex, 200, toMap(service.get(id)));
                    case "PUT" -> sendJson(ex, 200, toMap(service.update(id, readNodeRequest(ex))));
                    case "DELETE" -> {
                        service.remove(id);
                        send(ex, 204, null);
                    }
                    default -> throw methodNotAllowed("GET, PUT, DELETE");
                }
                return;
            }
            if (segments.length == 2 && segments[1].equals("status")) {
                requireMethod(method, "PATCH");
                Map<String, String> body = readFields(ex, STATUS_FIELDS);
                sendJson(ex, 200, toMap(service.updateStatus(id, body.get("status"))));
                return;
            }
        }
        throw new HttpError(404, "NOT_FOUND", "No such endpoint: " + path);
    }

    private static void requireMethod(String actual, String allowed) {
        if (!actual.equals(allowed)) {
            throw methodNotAllowed(allowed);
        }
    }

    private static HttpError methodNotAllowed(String allow) {
        return new HttpError(405, "METHOD_NOT_ALLOWED", "Method not allowed; use " + allow,
                List.of(), allow);
    }

    // ---- endpoints ----------------------------------------------------------------------------

    private void listNodes(HttpExchange ex) throws IOException {
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        List<Node> nodes = service.list(query.get("type"), query.get("status"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", nodes.size());
        body.put("nodes", nodes.stream().map(NodeApiHandler::toMap).toList());
        sendJson(ex, 200, body);
    }

    private void createNode(HttpExchange ex) throws IOException {
        Node node = service.register(readNodeRequest(ex));
        ex.getResponseHeaders().set("Location", "/api/nodes/" + node.id());
        sendJson(ex, 201, toMap(node));
    }

    // ---- request parsing ------------------------------------------------------------------------

    private static NodeRequest readNodeRequest(HttpExchange ex) throws IOException {
        Map<String, String> f = readFields(ex, NODE_FIELDS);
        return new NodeRequest(f.get("id"), f.get("name"), f.get("type"), f.get("status"),
                f.get("ipAddress"));
    }

    /** Reads a JSON object body whose values must all be strings (or null) and keys allowed. */
    private static Map<String, String> readFields(HttpExchange ex, Set<String> allowed)
            throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new HttpError(413, "PAYLOAD_TOO_LARGE",
                    "Request body must not exceed " + MAX_BODY_BYTES + " bytes");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new HttpError(400, "INVALID_JSON", "Request body must not be empty");
        }

        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (Json.JsonException e) {
            throw new HttpError(400, "INVALID_JSON", "Malformed JSON: " + e.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new HttpError(400, "INVALID_JSON", "Request body must be a JSON object");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = (String) entry.getKey();
            Object value = entry.getValue();
            if (!allowed.contains(key)) {
                errors.add("unknown field '" + key + "'");
            } else if (value != null && !(value instanceof String)) {
                errors.add("field '" + key + "' must be a string");
            } else {
                fields.put(key, (String) value);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return fields;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new HttpError(400, "BAD_REQUEST", "Malformed percent-encoding in URL");
        }
    }

    // ---- responses ------------------------------------------------------------------------------

    private static Map<String, Object> toMap(Node n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.id());
        m.put("name", n.name());
        m.put("type", n.type());
        m.put("status", n.status());
        m.put("ipAddress", n.ipAddress());
        m.put("createdAt", n.createdAt().toString());
        m.put("updatedAt", n.updatedAt().toString());
        return m;
    }

    private static void sendError(HttpExchange ex, HttpError e) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.code);
        body.put("message", e.getMessage());
        if (!e.details.isEmpty()) {
            body.put("details", e.details);
        }
        if (e.allow != null) {
            ex.getResponseHeaders().set("Allow", e.allow);
        }
        sendJson(ex, e.status, body);
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, byte[] body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }
}
