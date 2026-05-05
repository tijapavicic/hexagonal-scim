package com.example.user.core;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.out.UserRepositoryPort;

import java.util.List;

public class UserService implements CreateUserPort, GetUserPort, GetAllUsersPort {
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

    @Override
    public PagedUsers getAll(int pageNumber, int pageSize, boolean pageable) {
        if (!pageable) {
            List<User> all = userRepositoryPort.findAll();
            return new PagedUsers(all, 0, all.size(), all.size(), 1);
        }
        return userRepositoryPort.findAll(pageNumber, pageSize);
    }
}

