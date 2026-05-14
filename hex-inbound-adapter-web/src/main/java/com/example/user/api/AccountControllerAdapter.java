package com.example.user.api;

import com.example.user.api.dto.AccountResponse;
import com.example.user.api.dto.CreateAccountRequest;
import com.example.user.api.dto.TopUpAccountRequest;
import com.example.user.model.Account;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetUserAccountsPort;
import com.example.user.port.in.TopUpAccountPort;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * REST adapter for Account API endpoints.
 *
 * <p>Exposes CRUD operations for user accounts at
 * {@code /api/v1/users/{userId}/accounts}.
 *
 * <p>Emits a {@code account.operations} Micrometer counter (tag: {@code operation})
 * for every successful invocation. The counter is scraped by Prometheus and
 * displayed in the <em>Account Operations</em> Grafana panels.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/accounts")
public class AccountControllerAdapter {

    private static final String METRIC_NAME = "account.operations";

    private final CreateAccountPort createAccountPort;
    private final GetUserAccountsPort getUserAccountsPort;
    private final GetAccountPort getAccountPort;
    private final DeleteAccountPort deleteAccountPort;
    private final TopUpAccountPort topUpAccountPort;
    private final MeterRegistry meterRegistry;

    public AccountControllerAdapter(
            CreateAccountPort createAccountPort,
            GetUserAccountsPort getUserAccountsPort,
            GetAccountPort getAccountPort,
            DeleteAccountPort deleteAccountPort,
            TopUpAccountPort topUpAccountPort,
            MeterRegistry meterRegistry
    ) {
        this.createAccountPort = createAccountPort;
        this.getUserAccountsPort = getUserAccountsPort;
        this.getAccountPort = getAccountPort;
        this.deleteAccountPort = deleteAccountPort;
        this.topUpAccountPort = topUpAccountPort;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@PathVariable("userId") Long userId, @Valid @RequestBody CreateAccountRequest request) {
        Account created = createAccountPort.create(userId, request.name());
        meterRegistry.counter(METRIC_NAME, "operation", "create").increment();
        return toResponse(created);
    }

    @GetMapping
    public List<AccountResponse> getAllByUserId(@PathVariable("userId") Long userId) {
        List<AccountResponse> result = getUserAccountsPort.getAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
        meterRegistry.counter(METRIC_NAME, "operation", "get-all").increment();
        return result;
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(@PathVariable("userId") Long userId, @PathVariable("accountId") Long accountId) {
        AccountResponse response = toResponse(getAccountPort.getById(userId, accountId));
        meterRegistry.counter(METRIC_NAME, "operation", "get-by-id").increment();
        return response;
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("userId") Long userId, @PathVariable("accountId") Long accountId) {
        deleteAccountPort.deleteById(userId, accountId);
        meterRegistry.counter(METRIC_NAME, "operation", "delete").increment();
    }

    @PostMapping("/{accountId}/topup")
    public AccountResponse topUp(
            @PathVariable("userId") Long userId,
            @PathVariable("accountId") Long accountId,
            @Valid @RequestBody TopUpAccountRequest request
    ) {
        Account account = topUpAccountPort.topUp(userId, accountId, request.amount());
        meterRegistry.counter(METRIC_NAME, "operation", "topup").increment();
        return toResponse(account);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.id(), account.userId(), account.name(), account.balance());
    }
}
