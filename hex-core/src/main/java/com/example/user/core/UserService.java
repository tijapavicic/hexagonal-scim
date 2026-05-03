package com.example.user.core;

import com.example.user.model.User;
import com.example.user.port.in.CreateUserUseCase;
import com.example.user.port.in.GetUserUseCase;
import com.example.user.port.out.UserRepositoryPort;

public class UserService implements CreateUserUseCase, GetUserUseCase {
    private final UserRepositoryPort userRepositoryPort;

    public UserService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    @Override
    public User create(String email, String displayName) {
        if (userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }

        return userRepositoryPort.save(new User(null, email, displayName));
    }

    @Override
    public User getById(Long id) {
        return userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
    }
}

