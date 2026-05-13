package com.example.user.port.in;

import com.example.user.model.Account;

public interface CreateAccountPort {
    Account create(Long userId, String name);
}

