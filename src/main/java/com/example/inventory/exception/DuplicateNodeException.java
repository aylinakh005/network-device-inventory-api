package com.example.inventory.exception;

/** Thrown when registering a node whose identifier is already in use. */
public class DuplicateNodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String nodeId;

    public DuplicateNodeException(String nodeId) {
        super("Node already exists: " + nodeId);
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
