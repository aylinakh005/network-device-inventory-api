package com.example.inventory.service;

import com.example.inventory.exception.DuplicateNodeException;
import com.example.inventory.exception.NodeNotFoundException;
import com.example.inventory.exception.ValidationException;
import com.example.inventory.model.Node;
import com.example.inventory.model.NodeStatus;
import com.example.inventory.model.NodeType;
import com.example.inventory.repository.NodeRepository;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Business logic for the inventory. Mutating operations are synchronised so that
 * read-modify-write sequences (update, status change) cannot interleave.
 */
public class NodeService {

    private final NodeRepository repository;
    private final Clock clock;

    public NodeService(NodeRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers a new node. @throws DuplicateNodeException if the id is taken. */
    public synchronized Node register(NodeRequest request) {
        requireRequest(request);
        Node node = Node.create(request.id(), request.name(), request.type(), request.status(),
                request.ipAddress(), clock.instant());
        if (!repository.saveIfAbsent(node)) {
            throw new DuplicateNodeException(node.id());
        }
        return node;
    }

    /** @throws NodeNotFoundException if no node has this id */
    public Node get(String id) {
        requireId(id);
        return repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
    }

    /**
     * Lists nodes ordered by id, optionally filtered. Null or blank filters are ignored;
     * unknown filter values are rejected rather than silently returning nothing.
     */
    public List<Node> list(String type, String status) {
        List<String> errors = new java.util.ArrayList<>();
        NodeType typeFilter = null;
        NodeStatus statusFilter = null;

        if (type != null && !type.isBlank()) {
            typeFilter = NodeType.fromString(type).orElse(null);
            if (typeFilter == null) {
                errors.add("type must be one of " + Arrays.toString(NodeType.values()));
            }
        }
        if (status != null && !status.isBlank()) {
            statusFilter = NodeStatus.fromString(status).orElse(null);
            if (statusFilter == null) {
                errors.add("status must be one of " + Arrays.toString(NodeStatus.values()));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        final NodeType t = typeFilter;
        final NodeStatus s = statusFilter;
        return repository.findAll().stream()
                .filter(n -> t == null || n.type() == t)
                .filter(n -> s == null || n.status() == s)
                .toList();
    }

    /**
     * Replaces all mutable fields of an existing node. If the request carries an id it must
     * match {@code id}; the id of a node can never change.
     */
    public synchronized Node update(String id, NodeRequest request) {
        requireId(id);
        requireRequest(request);
        if (request.id() != null && !request.id().equals(id)) {
            throw new ValidationException("id in request body must match the id in the path");
        }
        Node existing = repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
        Node updated = existing.update(request.name(), request.type(), request.status(),
                request.ipAddress(), clock.instant());
        if (!repository.replace(updated)) {
            throw new NodeNotFoundException(id);
        }
        return updated;
    }

    /** Changes only the operational status of a node. */
    public synchronized Node updateStatus(String id, String status) {
        requireId(id);
        Node existing = repository.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
        Node updated = existing.withStatus(status, clock.instant());
        if (!repository.replace(updated)) {
            throw new NodeNotFoundException(id);
        }
        return updated;
    }

    /** @throws NodeNotFoundException if no node has this id */
    public synchronized void remove(String id) {
        requireId(id);
        if (!repository.deleteById(id)) {
            throw new NodeNotFoundException(id);
        }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("node id must not be blank");
        }
    }

    private static void requireRequest(NodeRequest request) {
        if (request == null) {
            throw new ValidationException("request must not be null");
        }
    }
}
