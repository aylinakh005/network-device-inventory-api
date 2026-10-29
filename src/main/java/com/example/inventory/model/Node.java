package com.example.inventory.model;

import com.example.inventory.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An immutable network node. Instances can only be built through {@link #create}, so every
 * Node in the system is guaranteed to hold validated data.
 */
public final class Node {

    public static final int MAX_NAME_LENGTH = 64;
    public static final String ID_RULE =
            "id must be 3-32 characters: letters, digits, '-' or '_', starting with a letter or digit";

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{2,31}");

    private final String id;
    private final String name;
    private final NodeType type;
    private final NodeStatus status;
    private final String ipAddress; // optional, IPv4 only, may be null
    private final Instant createdAt;
    private final Instant updatedAt;

    private Node(String id, String name, NodeType type, NodeStatus status, String ipAddress,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Validates raw input and builds a new node.
     *
     * @throws ValidationException listing every invalid field
     */
    public static Node create(String id, String name, String type, String status,
                              String ipAddress, Instant now) {
        Objects.requireNonNull(now, "now");
        Validated v = validate(id, name, type, status, ipAddress);
        return new Node(id, v.name, v.type, v.status, v.ipAddress, now, now);
    }

    /** Returns a copy with new field values. The id and creation time are preserved. */
    public Node update(String name, String type, String status, String ipAddress, Instant now) {
        Objects.requireNonNull(now, "now");
        Validated v = validate(this.id, name, type, status, ipAddress);
        return new Node(id, v.name, v.type, v.status, v.ipAddress, createdAt, now);
    }

    /** Returns a copy with only the status changed. */
    public Node withStatus(String newStatus, Instant now) {
        return update(name, type.name(), newStatus, ipAddress, now);
    }

    public String id() { return id; }
    public String name() { return name; }
    public NodeType type() { return type; }
    public NodeStatus status() { return status; }
    /** May be null. */
    public String ipAddress() { return ipAddress; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    // ---- validation -------------------------------------------------------------------------

    private record Validated(String name, NodeType type, NodeStatus status, String ipAddress) { }

    private static Validated validate(String id, String name, String type, String status,
                                      String ipAddress) {
        List<String> errors = new ArrayList<>();

        if (id == null || id.isBlank()) {
            errors.add("id is required");
        } else if (!ID_PATTERN.matcher(id).matches()) {
            errors.add(ID_RULE);
        }

        String cleanName = null;
        if (name == null || name.isBlank()) {
            errors.add("name is required");
        } else {
            cleanName = name.trim();
            if (cleanName.length() > MAX_NAME_LENGTH) {
                errors.add("name must be at most " + MAX_NAME_LENGTH + " characters");
                cleanName = null;
            }
        }

        NodeType parsedType = null;
        if (type == null || type.isBlank()) {
            errors.add("type is required");
        } else {
            parsedType = NodeType.fromString(type).orElse(null);
            if (parsedType == null) {
                errors.add("type must be one of " + Arrays.toString(NodeType.values()));
            }
        }

        NodeStatus parsedStatus = null;
        if (status == null || status.isBlank()) {
            errors.add("status is required");
        } else {
            parsedStatus = NodeStatus.fromString(status).orElse(null);
            if (parsedStatus == null) {
                errors.add("status must be one of " + Arrays.toString(NodeStatus.values()));
            }
        }

        String cleanIp = null;
        if (ipAddress != null && !ipAddress.isBlank()) {
            cleanIp = ipAddress.trim();
            if (!isValidIpv4(cleanIp)) {
                errors.add("ipAddress must be a valid IPv4 address");
                cleanIp = null;
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Validated(cleanName, parsedType, parsedStatus, cleanIp);
    }

    static boolean isValidIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node other)) return false;
        return id.equals(other.id) && name.equals(other.name) && type == other.type
                && status == other.status && Objects.equals(ipAddress, other.ipAddress)
                && createdAt.equals(other.createdAt) && updatedAt.equals(other.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, status, ipAddress, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "Node{id=" + id + ", name=" + name + ", type=" + type + ", status=" + status
                + ", ipAddress=" + ipAddress + "}";
    }
}
