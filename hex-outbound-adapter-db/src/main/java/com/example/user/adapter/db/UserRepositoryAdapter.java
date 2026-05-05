package com.example.user.adapter.db;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.out.UserRepositoryPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public class UserRepositoryAdapter implements UserRepositoryPort {
    private final UserJpaRepository userJpaRepository;

    public UserRepositoryAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity saved = userJpaRepository.save(new UserEntity(user.email(), user.displayName()));
        return new User(saved.getId(), saved.getEmail(), saved.getDisplayName());
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id)
                .map(entity -> new User(entity.getId(), entity.getEmail(), entity.getDisplayName()));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public PagedUsers findAll(int pageNumber, int pageSize) {
        int safePage = Math.max(0, pageNumber);
        int safeSize = Math.max(1, Math.min(pageSize, 100));

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").ascending());
        Page<UserEntity> page = userJpaRepository.findAll(pageable);

        List<User> users = page.getContent().stream()
                .map(entity -> new User(entity.getId(), entity.getEmail(), entity.getDisplayName()))
                .toList();

        return new PagedUsers(users, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public List<User> findAll() {
        return userJpaRepository.findAll(Sort.by("id").ascending()).stream()
                .map(entity -> new User(entity.getId(), entity.getEmail(), entity.getDisplayName()))
                .toList();
    }

    @Override
    public User update(User user) {
        UserEntity entity = userJpaRepository.findById(user.id())
                .orElseThrow(() -> new IllegalStateException("Entity not found for id: " + user.id()));
        entity.setEmail(user.email());
        entity.setDisplayName(user.displayName());
        UserEntity saved = userJpaRepository.save(entity);
        return new User(saved.getId(), saved.getEmail(), saved.getDisplayName());
    }

    @Override
    public void deleteById(Long id) {
        userJpaRepository.deleteById(id);
    }
}
