package com.example.user.port.in;

import com.example.user.model.Account;

import java.util.List;

public interface GetUserAccountsPort {
    List<Account> getAllByUserId(Long userId);
}

