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
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/api/v1/users/{userId}/accounts")
public class AccountControllerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AccountControllerAdapter.class);
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
    public AccountResponse create(
            @PathVariable("userId") @Positive(message = "userId must be a positive number") Long userId,
            @Valid @RequestBody CreateAccountRequest request) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=account.create userId={} accountName={} status=INITIATED", userId, request.name());

        Account created = createAccountPort.create(userId, request.name());
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=account.create userId={} accountName={} accountId={} status=SUCCESS", userId, request.name(), created.id());

        meterRegistry.counter(METRIC_NAME, "operation", "create").increment();
        logger.info("flow_stage=RESPONSE_PREPARED operation=account.create userId={} accountId={} accountBalance={} status=COMPLETED", userId, created.id(), created.balance());

        return toResponse(created);
    }

    @GetMapping
    public List<AccountResponse> getAllByUserId(
            @PathVariable("userId") @Positive(message = "userId must be a positive number") Long userId) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=account.getAll userId={} status=INITIATED", userId);

        List<AccountResponse> result = getUserAccountsPort.getAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=account.getAll userId={} accountCount={} status=SUCCESS", userId, result.size());

        meterRegistry.counter(METRIC_NAME, "operation", "get-all").increment();
        logger.info("flow_stage=RESPONSE_PREPARED operation=account.getAll userId={} totalAccounts={} status=COMPLETED", userId, result.size());

        return result;
    }

    @GetMapping("/{accountId}")
    public AccountResponse getById(
            @PathVariable("userId") @Positive(message = "userId must be a positive number") Long userId,
            @PathVariable("accountId") @Positive(message = "accountId must be a positive number") Long accountId) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=account.getById userId={} accountId={} status=INITIATED", userId, accountId);

        AccountResponse response = toResponse(getAccountPort.getById(userId, accountId));
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=account.getById userId={} accountId={} accountName={} status=SUCCESS", userId, accountId, response.name());

        meterRegistry.counter(METRIC_NAME, "operation", "get-by-id").increment();
        logger.info("flow_stage=RESPONSE_PREPARED operation=account.getById userId={} accountId={} accountBalance={} status=COMPLETED", userId, accountId, response.balance());

        return response;
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("userId") @Positive(message = "userId must be a positive number") Long userId,
            @PathVariable("accountId") @Positive(message = "accountId must be a positive number") Long accountId) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=account.delete userId={} accountId={} status=INITIATED", userId, accountId);

        deleteAccountPort.deleteById(userId, accountId);
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=account.delete userId={} accountId={} status=SUCCESS", userId, accountId);

        meterRegistry.counter(METRIC_NAME, "operation", "delete").increment();
        logger.info("flow_stage=RESPONSE_PREPARED operation=account.delete userId={} accountId={} status=COMPLETED", userId, accountId);
    }

    @PostMapping("/{accountId}/topup")
    public AccountResponse topUp(
            @PathVariable("userId") @Positive(message = "userId must be a positive number") Long userId,
            @PathVariable("accountId") @Positive(message = "accountId must be a positive number") Long accountId,
            @Valid @RequestBody TopUpAccountRequest request
    ) {
        logger.info("flow_stage=REQUEST_RECEIVED operation=account.topup userId={} accountId={} topupAmount={} status=INITIATED", userId, accountId, request.amount());

        Account account = topUpAccountPort.topUp(userId, accountId, request.amount());
        logger.info("flow_stage=DOMAIN_OPERATION_COMPLETED operation=account.topup userId={} accountId={} topupAmount={} newBalance={} status=SUCCESS", userId, accountId, request.amount(), account.balance());

        meterRegistry.counter(METRIC_NAME, "operation", "topup").increment();
        logger.info("flow_stage=RESPONSE_PREPARED operation=account.topup userId={} accountId={} finalBalance={} status=COMPLETED", userId, accountId, account.balance());

        return toResponse(account);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.id(), account.userId(), account.name(), account.balance());
    }
}
