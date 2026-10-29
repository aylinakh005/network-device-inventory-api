package com.example.inventory.exception;

/** Thrown when no node exists with the requested identifier. */
public class NodeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String nodeId;

    public NodeNotFoundException(String nodeId) {
        super("Node not found: " + nodeId);
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
