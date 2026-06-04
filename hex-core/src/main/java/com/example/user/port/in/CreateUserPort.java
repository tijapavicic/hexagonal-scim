package com.example.user.port.in;

import com.example.user.model.User;
import com.example.user.model.UserRole;

public interface CreateUserPort {
    User create(String email, String displayName, UserRole role);
}


