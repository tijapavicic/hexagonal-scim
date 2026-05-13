package com.example.user.port.in;

import com.example.user.model.Account;

public interface GetAccountPort {
    Account getById(Long userId, Long accountId);
}

