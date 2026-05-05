package com.example.user.port.out;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);

    Optional<User> findById(Long id);

    boolean existsByEmail(String email);

    /** Returns a window of users, sorted by id ascending. */
    PagedUsers findAll(int pageNumber, int pageSize);

    /** Returns every user in the database, sorted by id ascending. */
    List<User> findAll();
}

