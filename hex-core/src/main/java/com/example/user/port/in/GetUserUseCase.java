package com.example.user.port.in;

import com.example.user.model.User;

public interface GetUserUseCase {
    User getById(Long id);
}

