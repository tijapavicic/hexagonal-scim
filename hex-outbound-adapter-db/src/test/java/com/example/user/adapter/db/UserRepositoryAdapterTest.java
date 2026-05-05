package com.example.user.adapter.db;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterTest {

    @Autowired
    private UserRepositoryAdapter adapter;

    @Test
    void saveAndFindRoundTrip() {
        User saved = adapter.save(new User(null, "bob@example.com", "Bob"));

        assertTrue(saved.id() != null);
        assertEquals("bob@example.com", adapter.findById(saved.id()).orElseThrow().email());
    }

    @Test
    void detectsExistingEmailCaseInsensitively() {
        adapter.save(new User(null, "sam@example.com", "Sam"));

        assertTrue(adapter.existsByEmail("SAM@example.com"));
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
    void findAllUnpagedReturnsEveryRow() {
        adapter.save(new User(null, "x@example.com", "X"));
        adapter.save(new User(null, "y@example.com", "Y"));
        adapter.save(new User(null, "z@example.com", "Z"));

        List<User> all = adapter.findAll();

        assertEquals(3, all.size());
    }

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
    void deleteRemovesRow() {
        User saved = adapter.save(new User(null, "todelete@example.com", "ToDelete"));

        adapter.deleteById(saved.id());

        assertTrue(adapter.findById(saved.id()).isEmpty());
    }
}

