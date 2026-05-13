package com.example.user.api;

import com.example.user.api.dto.AccountResponse;
import com.example.user.api.dto.CreateAccountRequest;
import com.example.user.model.Account;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetUserAccountsPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/accounts")
public class AccountControllerAdapter {

    private final CreateAccountPort createAccountPort;
    private final GetUserAccountsPort getUserAccountsPort;
    private final GetAccountPort getAccountPort;
    private final DeleteAccountPort deleteAccountPort;

    public AccountControllerAdapter(
            CreateAccountPort createAccountPort,
            GetUserAccountsPort getUserAccountsPort,
            GetAccountPort getAccountPort,
            DeleteAccountPort deleteAccountPort
    ) {
        this.createAccountPort = createAccountPort;
        this.getUserAccountsPort = getUserAccountsPort;
        this.getAccountPort = getAccountPort;
        this.deleteAccountPort = deleteAccountPort;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@PathVariable("userId") Long userId, @Valid @RequestBody CreateAccountRequest request) {
        Account created = createAccountPort.create(userId, request.name());
        return toResponse(created);
    }

    @GetMapping
    public List<AccountResponse> getAllByUserId(@PathVariable("userId") Long userId) {
        return getUserAccountsPort.getAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(@PathVariable("userId") Long userId, @PathVariable("accountId") Long accountId) {
        return toResponse(getAccountPort.getById(userId, accountId));
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("userId") Long userId, @PathVariable("accountId") Long accountId) {
        deleteAccountPort.deleteById(userId, accountId);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.id(), account.userId(), account.name());
    }
}

