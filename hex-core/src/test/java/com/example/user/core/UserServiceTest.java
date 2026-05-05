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
    }
}
