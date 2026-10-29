package com.example.inventory.repository;

import com.example.inventory.model.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory repository. Data is lost when the process stops. */
public class InMemoryNodeRepository implements NodeRepository {

    private final Map<String, Node> nodes = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(Node node) {
        return nodes.putIfAbsent(node.id(), node) == null;
    }

    @Override
    public boolean replace(Node node) {
        return nodes.replace(node.id(), node) != null;
    }

    @Override
    public Optional<Node> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(nodes.get(id));
    }

    @Override
    public List<Node> findAll() {
        return nodes.values().stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return id != null && nodes.remove(id) != null;
    }
}
