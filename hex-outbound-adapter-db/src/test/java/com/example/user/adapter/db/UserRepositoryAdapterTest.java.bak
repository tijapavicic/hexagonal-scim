package com.example.user.adapter.db;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(userJpaRepository);
    }

    // ─── save / find ──────────────────────────────────────────────────────────

    @Test
    void saveAndFindRoundTrip() {
        UserEntity savedEntity = new UserEntity("bob@example.com", "Bob");
        withId(savedEntity, 10L);
        when(userJpaRepository.save(any(UserEntity.class))).thenReturn(savedEntity);
        when(userJpaRepository.findById(10L)).thenReturn(Optional.of(savedEntity));

        User saved = adapter.save(User.createBuyer(null, "bob@example.com", "Bob"));

        assertEquals(10L, saved.id());
        assertEquals("bob@example.com", adapter.findById(saved.id()).orElseThrow().email());
    }

    @Test
    void findByIdReturnsEmptyForNonExistentId() {
        when(userJpaRepository.findById(999999L)).thenReturn(Optional.empty());

        assertTrue(adapter.findById(999999L).isEmpty());
    }

    // ─── existsByEmail ────────────────────────────────────────────────────────

    @Test
    void detectsExistingEmailCaseInsensitively() {
        when(userJpaRepository.existsByEmailIgnoreCase("SAM@example.com")).thenReturn(true);
        when(userJpaRepository.existsByEmailIgnoreCase("sam@example.com")).thenReturn(true);

        assertTrue(adapter.existsByEmail("SAM@example.com"));
        assertTrue(adapter.existsByEmail("sam@example.com"));
    }

    @Test
    void existsByEmailReturnsFalseForUnknownEmail() {
        when(userJpaRepository.existsByEmailIgnoreCase("nobody@example.com")).thenReturn(false);

        assertFalse(adapter.existsByEmail("nobody@example.com"));
    }

    // ─── findAll (paged) ──────────────────────────────────────────────────────

    @Test
    void findAllPagedReturnsEmptyWhenNoRows() {
        when(userJpaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));

        PagedUsers result = adapter.findAll(0, 10);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @Test
    void findAllPagedReturnsCorrectWindow() {
        UserEntity a = new UserEntity("a@example.com", "A");
        withId(a, 1L);
        UserEntity b = new UserEntity("b@example.com", "B");
        withId(b, 2L);
        when(userJpaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), Pageable.ofSize(2), 3));

        PagedUsers page = adapter.findAll(0, 2);

        assertEquals(2, page.content().size());
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(0, page.pageNumber());
    }

    @Test
    void findAllPagedSecondPageReturnsRemainingEntry() {
        UserEntity z = new UserEntity("z@example.com", "Z");
        withId(z, 3L);
        when(userJpaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(z), Pageable.ofSize(2).withPage(1), 3));

        PagedUsers page2 = adapter.findAll(1, 2);

        assertEquals(1, page2.content().size());
        assertEquals(1, page2.pageNumber());
        assertEquals(2, page2.pageSize());
    }

    @Test
    void pageSizeIsClampedToMax100() {
        UserEntity u1 = new UserEntity("clamp1@example.com", "U1");
        withId(u1, 1L);
        UserEntity u2 = new UserEntity("clamp2@example.com", "U2");
        withId(u2, 2L);
        UserEntity u3 = new UserEntity("clamp3@example.com", "U3");
        withId(u3, 3L);
        UserEntity u4 = new UserEntity("clamp4@example.com", "U4");
        withId(u4, 4L);
        UserEntity u5 = new UserEntity("clamp5@example.com", "U5");
        withId(u5, 5L);
        when(userJpaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u1, u2, u3, u4, u5), Pageable.ofSize(100), 5));

        PagedUsers result = adapter.findAll(0, 9999);

        // Size clamped to 100 — all 5 rows still returned (they fit in one page)
        assertEquals(100, result.pageSize(), "pageSize must be clamped to 100");
        assertEquals(5, result.content().size(), "all rows must be returned");
    }

    @Test
    void negativePageNumberIsClampedToZero() {
        UserEntity entity = new UserEntity("neg@example.com", "Neg");
        withId(entity, 1L);
        when(userJpaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), Pageable.ofSize(10), 1));

        PagedUsers result = adapter.findAll(-5, 10);

        assertEquals(0, result.pageNumber(), "negative page must be clamped to 0");
        assertEquals(1, result.content().size());
    }

    // ─── findAll (unpaged) ────────────────────────────────────────────────────

    @Test
    void findAllUnpagedReturnsEmptyWhenNoRows() {
        when(userJpaRepository.findAll(any(Sort.class))).thenReturn(List.of());

        List<User> all = adapter.findAll();

        assertTrue(all.isEmpty());
    }

    @Test
    void findAllUnpagedReturnsEveryRow() {
        UserEntity x = new UserEntity("x@example.com", "X");
        withId(x, 1L);
        UserEntity y = new UserEntity("y@example.com", "Y");
        withId(y, 2L);
        UserEntity z = new UserEntity("z@example.com", "Z");
        withId(z, 3L);
        when(userJpaRepository.findAll(any(Sort.class))).thenReturn(List.of(x, y, z));

        List<User> all = adapter.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void findAllUnpagedIsSortedByIdAscending() {
        UserEntity a = new UserEntity("first@example.com", "First");
        withId(a, 1L);
        UserEntity b = new UserEntity("second@example.com", "Second");
        withId(b, 2L);
        UserEntity c = new UserEntity("third@example.com", "Third");
        withId(c, 3L);
        when(userJpaRepository.findAll(any(Sort.class))).thenReturn(List.of(a, b, c));

        List<User> all = adapter.findAll();

        assertEquals(3, all.size());
        assertTrue(all.get(0).id() < all.get(1).id(), "rows must be sorted by id ASC");
        assertTrue(all.get(1).id() < all.get(2).id());
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    void updatePersistsFieldChanges() {
        UserEntity existing = new UserEntity("before@example.com", "Before");
        withId(existing, 4L);
        when(userJpaRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(userJpaRepository.save(existing)).thenReturn(existing);

        User updated = adapter.update(User.createBuyer(4L, "after@example.com", "After"));

        assertEquals("after@example.com", updated.email());
        assertEquals("After", updated.displayName());
        assertEquals(4L, updated.id());
        assertEquals("after@example.com", existing.getEmail());

        verify(userJpaRepository).save(existing);
    }

    @Test
    void updateNonExistentIdThrowsIllegalStateException() {
        when(userJpaRepository.findById(99999L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> adapter.update(User.createBuyer(99999L, "ghost@example.com", "Ghost")));
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    void deleteRemovesRow() {
        adapter.deleteById(5L);

        verify(userJpaRepository).deleteById(5L);
    }

    @Test
    void deleteNonExistentIdDoesNotThrow() {
        adapter.deleteById(99999L);

        verify(userJpaRepository).deleteById(99999L);
    }

    private static void withId(UserEntity entity, Long id) {
        try {
            var idField = UserEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set UserEntity id in test", ex);
        }
    }
}
