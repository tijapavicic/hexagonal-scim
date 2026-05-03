package com.example.user.port.out;

import com.example.user.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);

    Optional<User> findById(Long id);

    boolean existsByEmail(String email);
}

