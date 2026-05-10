package com.example.user.adapter.db;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterTest {

    @Autowired
    private UserRepositoryAdapter adapter;

    // ─── save / find ──────────────────────────────────────────────────────────

    @Test
    void saveAndFindRoundTrip() {
        User saved = adapter.save(new User(null, "bob@example.com", "Bob"));

        assertTrue(saved.id() != null);
        assertEquals("bob@example.com", adapter.findById(saved.id()).orElseThrow().email());
    }

    @Test
    void findByIdReturnsEmptyForNonExistentId() {
        assertTrue(adapter.findById(999999L).isEmpty());
    }

    // ─── existsByEmail ────────────────────────────────────────────────────────

    @Test
    void detectsExistingEmailCaseInsensitively() {
        adapter.save(new User(null, "sam@example.com", "Sam"));

        assertTrue(adapter.existsByEmail("SAM@example.com"));
        assertTrue(adapter.existsByEmail("sam@example.com"));
    }

    @Test
    void existsByEmailReturnsFalseForUnknownEmail() {
        assertFalse(adapter.existsByEmail("nobody@example.com"));
    }

    // ─── findAll (paged) ──────────────────────────────────────────────────────

    @Test
    void findAllPagedReturnsEmptyWhenNoRows() {
        PagedUsers result = adapter.findAll(0, 10);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @Test
    void findAllPagedReturnsCorrectWindow() {
        adapter.save(new User(null, "a@example.com", "A"));
        adapter.save(new User(null, "b@example.com", "B"));
        adapter.save(new User(null, "c@example.com", "C"));

        PagedUsers page = adapter.findAll(0, 2);

        assertEquals(2, page.content().size());
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(0, page.pageNumber());
    }

    @Test
    void findAllPagedSecondPageReturnsRemainingEntry() {
        adapter.save(new User(null, "x@example.com", "X"));
        adapter.save(new User(null, "y@example.com", "Y"));
        adapter.save(new User(null, "z@example.com", "Z"));

        PagedUsers page2 = adapter.findAll(1, 2);

        assertEquals(1, page2.content().size());
        assertEquals(1, page2.pageNumber());
        assertEquals(2, page2.pageSize());
    }

    @Test
    void pageSizeIsClampedToMax100() {
        for (int i = 1; i <= 5; i++) {
            adapter.save(new User(null, "clamp" + i + "@example.com", "U" + i));
        }

        PagedUsers result = adapter.findAll(0, 9999);

        // Size clamped to 100 — all 5 rows still returned (they fit in one page)
        assertEquals(100, result.pageSize(), "pageSize must be clamped to 100");
        assertEquals(5, result.content().size(), "all rows must be returned");
    }

    @Test
    void negativePageNumberIsClampedToZero() {
        adapter.save(new User(null, "neg@example.com", "Neg"));

        PagedUsers result = adapter.findAll(-5, 10);

        assertEquals(0, result.pageNumber(), "negative page must be clamped to 0");
        assertEquals(1, result.content().size());
    }

    // ─── findAll (unpaged) ────────────────────────────────────────────────────

    @Test
    void findAllUnpagedReturnsEmptyWhenNoRows() {
        List<User> all = adapter.findAll();

        assertTrue(all.isEmpty());
    }

    @Test
    void findAllUnpagedReturnsEveryRow() {
        adapter.save(new User(null, "x@example.com", "X"));
        adapter.save(new User(null, "y@example.com", "Y"));
        adapter.save(new User(null, "z@example.com", "Z"));

        List<User> all = adapter.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void findAllUnpagedIsSortedByIdAscending() {
        User a = adapter.save(new User(null, "first@example.com", "First"));
        User b = adapter.save(new User(null, "second@example.com", "Second"));
        User c = adapter.save(new User(null, "third@example.com", "Third"));

        List<User> all = adapter.findAll();

        assertEquals(3, all.size());
        assertTrue(all.get(0).id() < all.get(1).id(), "rows must be sorted by id ASC");
        assertTrue(all.get(1).id() < all.get(2).id());
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    void updatePersistsFieldChanges() {
        User saved = adapter.save(new User(null, "before@example.com", "Before"));

        User updated = adapter.update(new User(saved.id(), "after@example.com", "After"));

        assertEquals("after@example.com", updated.email());
        assertEquals("After", updated.displayName());
        assertEquals(saved.id(), updated.id());
        // verify persisted — re-read from DB
        User reloaded = adapter.findById(saved.id()).orElseThrow();
        assertEquals("after@example.com", reloaded.email());
    }

    @Test
    void updateNonExistentIdThrowsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> adapter.update(new User(99999L, "ghost@example.com", "Ghost")));
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    void deleteRemovesRow() {
        User saved = adapter.save(new User(null, "todelete@example.com", "ToDelete"));

        adapter.deleteById(saved.id());

        assertTrue(adapter.findById(saved.id()).isEmpty());
    }

    @Test
    void deleteNonExistentIdDoesNotThrow() {
        // Spring Data's deleteById is a no-op for missing IDs — not a domain error
        adapter.deleteById(99999L);
    }
}
