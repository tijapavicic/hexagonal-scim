package com.example.user.port.in;

import com.example.user.model.User;

public interface CreateUserPort {
    User create(String email, String displayName);
}


