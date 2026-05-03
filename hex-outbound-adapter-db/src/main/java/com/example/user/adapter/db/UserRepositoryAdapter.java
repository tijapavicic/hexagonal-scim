package com.example.user.adapter.db;

import com.example.user.model.User;
import com.example.user.port.out.UserRepositoryPort;

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
}
