package com.example.user.core;

import com.example.user.model.User;
import com.example.user.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    @Test
    void createStoresUser() {
        UserService service = new UserService(new InMemoryRepo());

        User created = service.create("john@example.com", "John");

        assertEquals(1L, created.id());
        assertEquals("john@example.com", created.email());
    }

    @Test
    void createRejectsDuplicateEmail() {
        UserService service = new UserService(new InMemoryRepo());
        service.create("john@example.com", "John");

        assertThrows(DuplicateUserException.class, () -> service.create("john@example.com", "John Again"));
    }

    @Test
    void getByIdReturnsStoredUser() {
        UserService service = new UserService(new InMemoryRepo());
        User created = service.create("jane@example.com", "Jane");

        User found = service.getById(created.id());

        assertEquals(created.id(), found.id());
        assertEquals(created.email(), found.email());
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UserService service = new UserService(new InMemoryRepo());

        assertThrows(UserNotFoundException.class, () -> service.getById(100L));
    }

    private static final class InMemoryRepo implements UserRepositoryPort {
        private final AtomicLong sequence = new AtomicLong(0);
        private final Map<Long, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            long id = sequence.incrementAndGet();
            User saved = new User(id, user.email(), user.displayName());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByEmail(String email) {
            return store.values().stream().anyMatch(u -> u.email().equalsIgnoreCase(email));
        }
    }
}

