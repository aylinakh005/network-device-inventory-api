package com.example.inventory.model;

import com.example.inventory.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T12:30:00Z");

    private static Node valid() {
        return Node.create("rtr-001", "Core Router", "router", "active", "10.0.0.1", T0);
    }

    // ---- creation: normal cases --------------------------------------------------------------

    @Test
    @DisplayName("creates a node from valid input and normalises type/status")
    void createsValidNode() {
        Node node = valid();

        assertEquals("rtr-001", node.id());
        assertEquals("Core Router", node.name());
        assertEquals(NodeType.ROUTER, node.type());
        assertEquals(NodeStatus.ACTIVE, node.status());
        assertEquals("10.0.0.1", node.ipAddress());
        assertEquals(T0, node.createdAt());
        assertEquals(T0, node.updatedAt());
    }

    @Test
    void ipAddressIsOptional() {
        assertNull(Node.create("rtr-001", "R", "ROUTER", "ACTIVE", null, T0).ipAddress());
        assertNull(Node.create("rtr-001", "R", "ROUTER", "ACTIVE", "   ", T0).ipAddress());
    }

    @Test
    void trimsNameAndIpAddress() {
        Node node = Node.create("rtr-001", "  Edge  ", " switch ", " faulty ", " 192.168.1.1 ", T0);
        assertEquals("Edge", node.name());
        assertEquals(NodeType.SWITCH, node.type());
        assertEquals(NodeStatus.FAULTY, node.status());
        assertEquals("192.168.1.1", node.ipAddress());
    }

    // ---- id validation -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"abc", "A1B", "node_1", "node-1", "0abc", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void acceptsValidIds(String id) {
        assertEquals(id, Node.create(id, "N", "ROUTER", "ACTIVE", null, T0).id());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "ab", "-abc", "_abc", "has space", "node/1", "node.1",
            "caf\u00e9-1", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidIds(String id) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create(id, "N", "ROUTER", "ACTIVE", null, T0));
        assertEquals(1, ex.getErrors().size());
        assertTrue(ex.getErrors().get(0).startsWith("id"), ex.getErrors().get(0));
    }

    // ---- name validation ---------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingName(String name) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("rtr-001", name, "ROUTER", "ACTIVE", null, T0));
        assertEquals("name is required", ex.getErrors().get(0));
    }

    @Test
    void nameLengthBoundary() {
        assertDoesNotThrow(() -> Node.create("rtr-001", "n".repeat(64), "ROUTER", "ACTIVE", null, T0));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("rtr-001", "n".repeat(65), "ROUTER", "ACTIVE", null, T0));
        assertTrue(ex.getErrors().get(0).contains("at most 64"));
    }

    // ---- type / status validation --------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"toaster", "ROUTER2", "base station"})
    void rejectsInvalidType(String type) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("rtr-001", "N", type, "ACTIVE", null, T0));
        assertTrue(ex.getErrors().get(0).startsWith("type"), ex.getErrors().get(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"broken", "UP", "act ive"})
    void rejectsInvalidStatus(String status) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("rtr-001", "N", "ROUTER", status, null, T0));
        assertTrue(ex.getErrors().get(0).startsWith("status"), ex.getErrors().get(0));
    }

    @Test
    void invalidTypeMessageListsAllowedValues() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("rtr-001", "N", "toaster", "ACTIVE", null, T0));
        assertTrue(ex.getErrors().get(0).contains("BASE_STATION"));
    }

    // ---- IP validation -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.0.0.1", "192.168.100.254", "255.255.255.255"})
    void acceptsValidIpv4(String ip) {
        assertEquals(ip, Node.create("rtr-001", "N", "ROUTER", "ACTIVE", ip, T0).ipAddress());
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.0.0.1", "1.2.3", "1.2.3.4.5", "a.b.c.d", "1..2.3", "1.2.3.-4",
            "1.2.3.4444", "::1", "10.0.0.1/24", "10.0.0.1 extra"})
    void rejectsInvalidIpv4(String ip) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("rtr-001", "N", "ROUTER", "ACTIVE", ip, T0));
        assertEquals("ipAddress must be a valid IPv4 address", ex.getErrors().get(0));
    }

    // ---- multiple errors -------------------------------------------------------------------------

    @Test
    @DisplayName("reports every invalid field at once")
    void collectsAllErrors() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> Node.create("!", null, "nope", "", "999.1.1.1", T0));
        assertEquals(5, ex.getErrors().size());
    }

    // ---- update / withStatus -------------------------------------------------------------------

    @Test
    void updateKeepsIdAndCreatedAtAndBumpsUpdatedAt() {
        Node updated = valid().update("New Name", "FIREWALL", "MAINTENANCE", null, T1);

        assertEquals("rtr-001", updated.id());
        assertEquals("New Name", updated.name());
        assertEquals(NodeType.FIREWALL, updated.type());
        assertEquals(NodeStatus.MAINTENANCE, updated.status());
        assertNull(updated.ipAddress());
        assertEquals(T0, updated.createdAt());
        assertEquals(T1, updated.updatedAt());
    }

    @Test
    void updateRejectsInvalidInputAndLeavesOriginalUntouched() {
        Node original = valid();
        assertThrows(ValidationException.class, () -> original.update("", "ROUTER", "ACTIVE", null, T1));
        assertEquals("Core Router", original.name());
    }

    @Test
    void withStatusChangesOnlyStatus() {
        Node faulty = valid().withStatus("faulty", T1);

        assertEquals(NodeStatus.FAULTY, faulty.status());
        assertEquals("Core Router", faulty.name());
        assertEquals(NodeType.ROUTER, faulty.type());
        assertEquals("10.0.0.1", faulty.ipAddress());
        assertEquals(T1, faulty.updatedAt());
    }

    @Test
    void withStatusRejectsUnknownStatus() {
        assertThrows(ValidationException.class, () -> valid().withStatus("sleeping", T1));
    }

    // ---- equality --------------------------------------------------------------------------------

    @Test
    void equalNodesAreEqual() {
        assertEquals(valid(), valid());
        assertEquals(valid().hashCode(), valid().hashCode());
        assertNotEquals(valid(), valid().withStatus("FAULTY", T1));
    }
}
