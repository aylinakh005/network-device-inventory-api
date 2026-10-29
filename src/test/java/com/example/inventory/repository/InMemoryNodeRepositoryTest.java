package com.example.inventory.repository;

import com.example.inventory.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryNodeRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private InMemoryNodeRepository repo;

    private static Node node(String id) {
        return Node.create(id, "Node " + id, "ROUTER", "ACTIVE", null, NOW);
    }

    @BeforeEach
    void setUp() {
        repo = new InMemoryNodeRepository();
    }

    @Test
    void savesAndFindsNode() {
        assertTrue(repo.saveIfAbsent(node("aaa")));
        assertEquals(node("aaa"), repo.findById("aaa").orElseThrow());
    }

    @Test
    void saveIfAbsentRefusesDuplicateAndKeepsOriginal() {
        Node first = node("aaa");
        assertTrue(repo.saveIfAbsent(first));
        assertFalse(repo.saveIfAbsent(Node.create("aaa", "Other", "SWITCH", "FAULTY", null, NOW)));
        assertEquals(first, repo.findById("aaa").orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyForUnknownOrNull() {
        assertTrue(repo.findById("zzz").isEmpty());
        assertTrue(repo.findById(null).isEmpty());
    }

    @Test
    void findAllIsSortedById() {
        repo.saveIfAbsent(node("ccc"));
        repo.saveIfAbsent(node("aaa"));
        repo.saveIfAbsent(node("bbb"));
        assertEquals(List.of("aaa", "bbb", "ccc"), repo.findAll().stream().map(Node::id).toList());
    }

    @Test
    void findAllOnEmptyRepositoryIsEmpty() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void replaceUpdatesExistingNodeOnly() {
        repo.saveIfAbsent(node("aaa"));
        Node changed = node("aaa").withStatus("FAULTY", NOW.plusSeconds(1));

        assertTrue(repo.replace(changed));
        assertEquals(changed, repo.findById("aaa").orElseThrow());
        assertFalse(repo.replace(node("missing")));
        assertTrue(repo.findById("missing").isEmpty(), "replace must not create nodes");
    }

    @Test
    void deleteRemovesNode() {
        repo.saveIfAbsent(node("aaa"));
        assertTrue(repo.deleteById("aaa"));
        assertTrue(repo.findById("aaa").isEmpty());
        assertFalse(repo.deleteById("aaa"));
        assertFalse(repo.deleteById(null));
    }

    @Test
    void concurrentRegistrationOfSameIdSucceedsExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                if (repo.saveIfAbsent(node("race"))) {
                    successes.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(1, successes.get());
        assertEquals(1, repo.findAll().size());
    }
}
