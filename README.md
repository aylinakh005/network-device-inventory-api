![CI](https://github.com/aylinakh005/network-device-inventory-api/actions/workflows/ci.yml/badge.svg)
# Network Device Inventory API

A small REST API for managing an inventory of simulated network nodes (routers, switches,
firewalls, base stations, ...). The focus is **correctness and test coverage**, not breadth:
strict input validation, clear error responses, and a layered design that is easy to test.

**Stack:** Java 17+ · Maven · JUnit 5 · Git · REST over the JDK's built-in HTTP server

The application has **zero runtime dependencies** (JUnit 5 is the only dependency, test scope only),
so it builds fast and runs anywhere a JDK is installed.

## Requirements

- JDK 17 or newer (`java -version`)
- Maven 3.8+ (`mvn -version`)

## Build, test, run

```bash
mvn test                      # run the full test suite
mvn package                   # compile, test, and build target/network-device-inventory-api.jar
java -jar target/network-device-inventory-api.jar              # listen on :8080
java -jar target/network-device-inventory-api.jar --port 9090  # custom port (or set PORT env var)
java -jar target/network-device-inventory-api.jar --seed       # preload five sample nodes
```

Run a single test class: `mvn test -Dtest=NodeServiceTest`

## Quick tour

```bash
# Register a node (type/status are case-insensitive)
curl -i -X POST localhost:8080/api/nodes -H 'Content-Type: application/json' \
  -d '{"id":"core-rtr-01","name":"Core Router","type":"router","status":"active","ipAddress":"10.0.0.1"}'

# Retrieve one / list all / filter
curl localhost:8080/api/nodes/core-rtr-01
curl localhost:8080/api/nodes
curl "localhost:8080/api/nodes?type=ROUTER&status=FAULTY"

# Replace a node (full update; the id comes from the path)
curl -X PUT localhost:8080/api/nodes/core-rtr-01 \
  -d '{"name":"Core Router A","type":"ROUTER","status":"MAINTENANCE"}'

# Change only the operational status
curl -X PATCH localhost:8080/api/nodes/core-rtr-01/status -d '{"status":"FAULTY"}'

# Remove
curl -i -X DELETE localhost:8080/api/nodes/core-rtr-01
```

## API reference

| Method | Path                       | Success | Description                              |
|--------|----------------------------|---------|------------------------------------------|
| GET    | `/health`                  | 200     | Liveness check                           |
| GET    | `/api/nodes`               | 200     | List nodes sorted by id; optional `type` and `status` filters |
| POST   | `/api/nodes`               | 201     | Register a node (`Location` header set)  |
| GET    | `/api/nodes/{id}`          | 200     | Retrieve a node                          |
| PUT    | `/api/nodes/{id}`          | 200     | Replace name/type/status/ipAddress       |
| PATCH  | `/api/nodes/{id}/status`   | 200     | Change only the status                   |
| DELETE | `/api/nodes/{id}`          | 204     | Remove a node                            |

### Node fields

| Field       | Rules |
|-------------|-------|
| `id`        | Required on POST. 3-32 chars: letters, digits, `-`, `_`; must start with a letter or digit. Immutable. |
| `name`      | Required, 1-64 characters after trimming. |
| `type`      | Required: `ROUTER`, `SWITCH`, `FIREWALL`, `BASE_STATION`, `CORE_GATEWAY`, `ACCESS_POINT`, `LOAD_BALANCER`. |
| `status`    | Required: `ACTIVE`, `INACTIVE`, `MAINTENANCE`, `FAULTY`. |
| `ipAddress` | Optional IPv4 address. Omitted on PUT means cleared. |

Responses also include `createdAt` and `updatedAt` (ISO-8601 UTC).

### Errors

Every error is JSON, with a stable machine-readable `error` code and no stack traces:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": ["name is required", "type must be one of [ROUTER, SWITCH, ...]"]
}
```

| HTTP | `error` code           | When |
|------|------------------------|------|
| 400  | `VALIDATION_ERROR`     | Invalid/missing fields, unknown fields, non-string values, bad filters, body id differs from path id. All problems are reported together. |
| 400  | `INVALID_JSON`         | Empty body, malformed JSON, or body is not a JSON object. |
| 404  | `NODE_NOT_FOUND`       | No node with that id (GET/PUT/PATCH/DELETE). |
| 404  | `NOT_FOUND`            | Unknown route. |
| 405  | `METHOD_NOT_ALLOWED`   | Wrong verb for the route; `Allow` header is set. |
| 409  | `NODE_ALREADY_EXISTS`  | POST with an id that is already registered. |
| 413  | `PAYLOAD_TOO_LARGE`    | Request body over 64 KB. |
| 500  | `INTERNAL_ERROR`       | Unexpected failure (details are logged, not returned). |

## Project structure

```
src/main/java/com/example/inventory/
  Main.java                  CLI entry point (--port, --seed)
  model/                     Node (immutable, self-validating), NodeType, NodeStatus
  exception/                 ValidationException, NodeNotFoundException, DuplicateNodeException
  repository/                NodeRepository interface + thread-safe in-memory implementation
  service/                   NodeService (business rules) and NodeRequest (raw input)
  api/                       NodeApiHandler (routing, HTTP <-> exceptions), InventoryServer, Json
src/test/java/...            Mirrors the main packages
```

## Design notes

- **Validation lives in the domain.** `Node` can only be built through `Node.create`, so an
  invalid node cannot exist. All field errors are collected and reported in one response.
- **Layers are independently testable.** The service takes a `NodeRepository` and a `Clock`,
  so tests use a controllable clock and need no HTTP.
- **Strict input handling.** Unknown JSON fields, wrong value types, and unknown filter values
  are rejected rather than silently ignored.
- **Concurrency.** The repository uses `ConcurrentHashMap` (atomic `putIfAbsent`); service
  mutations are synchronised so read-modify-write updates cannot interleave. A test fires 50
  concurrent registrations of one id and asserts exactly one succeeds.
- **No dependencies.** A small strict JSON reader/writer (`Json`) replaces Jackson; it has
  its own tests, including malformed input and nesting limits.
- **Storage is in-memory** by design: data resets on restart. Swapping in a database means
  implementing `NodeRepository`.

## Tests

| Suite | What it covers |
|-------|----------------|
| `NodeTest`, `NodeEnumsTest` | Field validation, boundaries (id length 3/32, name length 64/65), IPv4 rules, error aggregation, update semantics |
| `InMemoryNodeRepositoryTest` | Store/find/replace/delete, ordering, duplicate protection, concurrent registration |
| `NodeServiceTest` | Register/get/list/update/status/remove, duplicates, not-found, filters, id-mismatch, timestamps via a fake clock, no partial writes on failure |
| `JsonTest` | Parsing, escapes, malformed inputs, nesting limit, writer escaping, round trip |
| `NodeApiIntegrationTest` | Real server on a random port: full lifecycle, every status code in the table above, `Allow`/`Location` headers, Unicode, no stack-trace leaks |

## Possible extensions

Pagination, persistence (JDBC/JPA behind `NodeRepository`), IPv6, OpenAPI spec, Dockerfile, CI workflow.
