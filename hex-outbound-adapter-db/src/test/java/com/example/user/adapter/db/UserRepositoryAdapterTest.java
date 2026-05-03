package com.example.user.adapter.db;

import com.example.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

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
}

