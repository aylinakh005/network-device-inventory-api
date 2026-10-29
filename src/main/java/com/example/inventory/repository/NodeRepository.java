package com.example.inventory.repository;

import com.example.inventory.model.Node;

import java.util.List;
import java.util.Optional;

/** Storage abstraction for nodes. Implementations must be thread-safe. */
public interface NodeRepository {

    /** Stores the node only if its id is unused. @return true if stored, false if the id exists. */
    boolean saveIfAbsent(Node node);

    /** Replaces an existing node. @return true if replaced, false if no node has that id. */
    boolean replace(Node node);

    Optional<Node> findById(String id);

    /** All nodes, ordered by id. */
    List<Node> findAll();

    /** @return true if a node was removed. */
    boolean deleteById(String id);
}
