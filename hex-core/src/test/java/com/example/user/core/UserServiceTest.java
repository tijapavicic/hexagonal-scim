package com.example.user.core;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @Test
    void getAllPageableReturnsSinglePage() {
        UserService service = new UserService(new InMemoryRepo());
        service.create("a@example.com", "A");
        service.create("b@example.com", "B");
        service.create("c@example.com", "C");

        PagedUsers result = service.getAll(0, 2, true);

        assertEquals(2, result.content().size());
        assertEquals(0, result.pageNumber());
        assertEquals(2, result.pageSize());
        assertEquals(3, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    @Test
    void getAllNotPageableReturnsAllUsers() {
        UserService service = new UserService(new InMemoryRepo());
        service.create("a@example.com", "A");
        service.create("b@example.com", "B");
        service.create("c@example.com", "C");

        PagedUsers result = service.getAll(0, 2, false);

        assertEquals(3, result.content().size());
        assertEquals(3, result.totalElements());
        assertEquals(1, result.totalPages());
    }

    @Test
    void getAllDefaultReturnsTenUsers() {
        UserService service = new UserService(new InMemoryRepo());
        for (int i = 1; i <= 15; i++) {
            service.create("user" + i + "@example.com", "User " + i);
        }

        PagedUsers result = service.getAll(0, 10, true);

        assertEquals(10, result.content().size());
        assertEquals(15, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    void updateReplacesAllFields() {
        UserService service = new UserService(new InMemoryRepo());
        User created = service.create("old@example.com", "Old");

        User updated = service.update(created.id(), "new@example.com", "New");

        assertEquals("new@example.com", updated.email());
        assertEquals("New", updated.displayName());
        assertEquals(created.id(), updated.id());
    }

    @Test
    void updateThrowsWhenUserNotFound() {
        UserService service = new UserService(new InMemoryRepo());

        assertThrows(UserNotFoundException.class, () -> service.update(999L, "x@x.com", "X"));
    }

    @Test
    void updateThrowsOnDuplicateEmail() {
        UserService service = new UserService(new InMemoryRepo());
        service.create("alice@example.com", "Alice");
        User bob = service.create("bob@example.com", "Bob");

        assertThrows(DuplicateUserException.class,
                () -> service.update(bob.id(), "alice@example.com", "Bob"));
    }

    @Test
    void updateAllowsSameEmailOnSameUser() {
        UserService service = new UserService(new InMemoryRepo());
        User created = service.create("alice@example.com", "Alice");

        User updated = service.update(created.id(), "alice@example.com", "Alice Renamed");

        assertEquals("Alice Renamed", updated.displayName());
    }

    // ─── patch ────────────────────────────────────────────────────────────────

    @Test
    void patchUpdatesOnlyProvidedFields() {
        UserService service = new UserService(new InMemoryRepo());
        User created = service.create("alice@example.com", "Alice");

        User patched = service.patch(created.id(), null, "Alice Updated");

        assertEquals("alice@example.com", patched.email());   // unchanged
        assertEquals("Alice Updated", patched.displayName()); // changed
    }

    @Test
    void patchUpdatesEmailOnly() {
        UserService service = new UserService(new InMemoryRepo());
        User created = service.create("alice@example.com", "Alice");

        User patched = service.patch(created.id(), "alice2@example.com", null);

        assertEquals("alice2@example.com", patched.email());
        assertEquals("Alice", patched.displayName()); // unchanged
    }

    @Test
    void patchThrowsWhenUserNotFound() {
        UserService service = new UserService(new InMemoryRepo());

        assertThrows(UserNotFoundException.class, () -> service.patch(999L, "x@x.com", null));
    }

    @Test
    void patchThrowsOnDuplicateEmail() {
        UserService service = new UserService(new InMemoryRepo());
        service.create("alice@example.com", "Alice");
        User bob = service.create("bob@example.com", "Bob");

        assertThrows(DuplicateUserException.class,
                () -> service.patch(bob.id(), "alice@example.com", null));
    }

    // ─── deleteById ───────────────────────────────────────────────────────────

    @Test
    void deleteRemovesUser() {
        UserService service = new UserService(new InMemoryRepo());
        User created = service.create("alice@example.com", "Alice");

        service.deleteById(created.id());

        assertThrows(UserNotFoundException.class, () -> service.getById(created.id()));
    }

    @Test
    void deleteThrowsWhenUserNotFound() {
        UserService service = new UserService(new InMemoryRepo());

        assertThrows(UserNotFoundException.class, () -> service.deleteById(999L));
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

        @Override
        public PagedUsers findAll(int pageNumber, int pageSize) {
            List<User> all = new ArrayList<>(store.values());
            int start = pageNumber * pageSize;
            int end = Math.min(start + pageSize, all.size());
            List<User> page = start >= all.size() ? List.of() : all.subList(start, end);
            int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / pageSize);
            return new PagedUsers(page, pageNumber, pageSize, all.size(), totalPages);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public User update(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }
    }
}
