package com.example.inventory.service;

import com.example.inventory.exception.DuplicateNodeException;
import com.example.inventory.exception.NodeNotFoundException;
import com.example.inventory.exception.ValidationException;
import com.example.inventory.model.Node;
import com.example.inventory.model.NodeStatus;
import com.example.inventory.model.NodeType;
import com.example.inventory.repository.InMemoryNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-01T08:00:00Z");

    /** Clock the tests can move forward to check timestamps deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now = T0;
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private NodeService service;

    private static NodeRequest req(String id, String type, String status) {
        return new NodeRequest(id, "Name " + id, type, status, null);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new NodeService(new InMemoryNodeRepository(), clock);
    }

    // ---- register ----------------------------------------------------------------------------

    @Test
    void registerStoresAndReturnsNode() {
        Node node = service.register(new NodeRequest("rtr-001", "Core", "router", "active", "10.0.0.1"));

        assertEquals("rtr-001", node.id());
        assertEquals(NodeType.ROUTER, node.type());
        assertEquals(NodeStatus.ACTIVE, node.status());
        assertEquals(T0, node.createdAt());
        assertEquals(node, service.get("rtr-001"));
    }

    @Test
    void registerRejectsDuplicateId() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        DuplicateNodeException ex = assertThrows(DuplicateNodeException.class,
                () -> service.register(req("rtr-001", "SWITCH", "FAULTY")));
        assertEquals("rtr-001", ex.getNodeId());
        assertEquals(NodeType.ROUTER, service.get("rtr-001").type(), "original must be unchanged");
    }

    @Test
    void registerRejectsInvalidInput() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.register(new NodeRequest("x", "", "toaster", null, "1.1.1")));
        assertEquals(5, ex.getErrors().size());
        assertTrue(service.list(null, null).isEmpty(), "nothing may be stored on failure");
    }

    @Test
    void registerRejectsNullRequest() {
        assertThrows(ValidationException.class, () -> service.register(null));
    }

    // ---- get ---------------------------------------------------------------------------------

    @Test
    void getUnknownIdThrowsNotFound() {
        NodeNotFoundException ex = assertThrows(NodeNotFoundException.class, () -> service.get("nope-1"));
        assertEquals("nope-1", ex.getNodeId());
        assertEquals("Node not found: nope-1", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void getWithBlankIdIsValidationError(String id) {
        assertThrows(ValidationException.class, () -> service.get(id));
    }

    // ---- list --------------------------------------------------------------------------------

    @Test
    void listReturnsAllNodesSortedById() {
        service.register(req("ccc", "ROUTER", "ACTIVE"));
        service.register(req("aaa", "SWITCH", "ACTIVE"));
        service.register(req("bbb", "FIREWALL", "FAULTY"));

        assertEquals(List.of("aaa", "bbb", "ccc"),
                service.list(null, null).stream().map(Node::id).toList());
    }

    @Test
    void listFiltersByTypeStatusAndBoth() {
        service.register(req("r-1", "ROUTER", "ACTIVE"));
        service.register(req("r-2", "ROUTER", "FAULTY"));
        service.register(req("s-1", "SWITCH", "ACTIVE"));

        assertEquals(List.of("r-1", "r-2"), ids(service.list("router", null)));
        assertEquals(List.of("r-1", "s-1"), ids(service.list(null, "ACTIVE")));
        assertEquals(List.of("r-2"), ids(service.list("ROUTER", "faulty")));
        assertTrue(service.list("FIREWALL", null).isEmpty());
    }

    @Test
    void blankFiltersAreIgnored() {
        service.register(req("r-1", "ROUTER", "ACTIVE"));
        assertEquals(1, service.list("  ", "").size());
    }

    @Test
    void unknownFilterValuesAreRejected() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.list("toaster", "sleeping"));
        assertEquals(2, ex.getErrors().size());
    }

    private static List<String> ids(List<Node> nodes) {
        return nodes.stream().map(Node::id).toList();
    }

    // ---- update ------------------------------------------------------------------------------

    @Test
    void updateReplacesFieldsAndTracksTimestamps() {
        Node created = service.register(new NodeRequest("rtr-001", "Old", "ROUTER", "ACTIVE", "10.0.0.1"));
        clock.advance(60);

        Node updated = service.update("rtr-001",
                new NodeRequest(null, "New", "firewall", "maintenance", null));

        assertEquals("New", updated.name());
        assertEquals(NodeType.FIREWALL, updated.type());
        assertEquals(NodeStatus.MAINTENANCE, updated.status());
        assertNull(updated.ipAddress(), "PUT semantics: omitted optional fields are cleared");
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(T0.plusSeconds(60), updated.updatedAt());
        assertEquals(updated, service.get("rtr-001"));
    }

    @Test
    void updateAcceptsMatchingIdInBody() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        assertDoesNotThrow(() -> service.update("rtr-001", req("rtr-001", "SWITCH", "ACTIVE")));
    }

    @Test
    @DisplayName("update refuses to change a node's id")
    void updateRejectsMismatchedBodyId() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        assertThrows(ValidationException.class,
                () -> service.update("rtr-001", req("rtr-002", "ROUTER", "ACTIVE")));
        assertThrows(NodeNotFoundException.class, () -> service.get("rtr-002"));
    }

    @Test
    void updateUnknownNodeThrowsNotFoundAndDoesNotCreateIt() {
        assertThrows(NodeNotFoundException.class,
                () -> service.update("ghost-1", req(null, "ROUTER", "ACTIVE")));
        assertTrue(service.list(null, null).isEmpty());
    }

    @Test
    void updateWithInvalidDataKeepsExistingNode() {
        Node original = service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        assertThrows(ValidationException.class,
                () -> service.update("rtr-001", new NodeRequest(null, "", "ROUTER", "ACTIVE", null)));
        assertEquals(original, service.get("rtr-001"));
    }

    @Test
    void updateWithBlankIdOrNullRequestIsValidationError() {
        assertThrows(ValidationException.class, () -> service.update(" ", req(null, "ROUTER", "ACTIVE")));
        assertThrows(ValidationException.class, () -> service.update("rtr-001", null));
    }

    // ---- updateStatus ----------------------------------------------------------------------------

    @Test
    void updateStatusChangesOnlyStatus() {
        service.register(new NodeRequest("rtr-001", "Core", "ROUTER", "ACTIVE", "10.0.0.1"));
        clock.advance(5);

        Node node = service.updateStatus("rtr-001", "faulty");

        assertEquals(NodeStatus.FAULTY, node.status());
        assertEquals("Core", node.name());
        assertEquals("10.0.0.1", node.ipAddress());
        assertEquals(T0.plusSeconds(5), node.updatedAt());
    }

    @Test
    void updateStatusRejectsInvalidStatusAndKeepsOldOne() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        assertThrows(ValidationException.class, () -> service.updateStatus("rtr-001", "sleeping"));
        assertEquals(NodeStatus.ACTIVE, service.get("rtr-001").status());
    }

    @Test
    void updateStatusOnUnknownNodeThrowsNotFound() {
        assertThrows(NodeNotFoundException.class, () -> service.updateStatus("ghost-1", "ACTIVE"));
    }

    // ---- remove ------------------------------------------------------------------------------

    @Test
    void removeDeletesNode() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        service.remove("rtr-001");
        assertThrows(NodeNotFoundException.class, () -> service.get("rtr-001"));
    }

    @Test
    void removeUnknownNodeThrowsNotFound() {
        assertThrows(NodeNotFoundException.class, () -> service.remove("ghost-1"));
    }

    @Test
    void removeTwiceFailsTheSecondTime() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        service.remove("rtr-001");
        assertThrows(NodeNotFoundException.class, () -> service.remove("rtr-001"));
    }

    @Test
    void removedIdCanBeRegisteredAgain() {
        service.register(req("rtr-001", "ROUTER", "ACTIVE"));
        service.remove("rtr-001");
        assertDoesNotThrow(() -> service.register(req("rtr-001", "SWITCH", "ACTIVE")));
    }

    @Test
    void removeWithBlankIdIsValidationError() {
        assertThrows(ValidationException.class, () -> service.remove(""));
    }

    // ---- constructor ---------------------------------------------------------------------------

    @Test
    void constructorRejectsNullDependencies() {
        assertThrows(NullPointerException.class, () -> new NodeService(null, clock));
        assertThrows(NullPointerException.class, () -> new NodeService(new InMemoryNodeRepository(), null));
    }
}
