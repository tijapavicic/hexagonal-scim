package com.example.user.config;

import com.example.user.adapter.db.UserJpaRepository;
import com.example.user.adapter.db.UserRepositoryAdapter;
import com.example.user.adapter.db.AccountJpaRepository;
import com.example.user.adapter.db.AccountRepositoryAdapter;
import com.example.user.core.AccountService;
import com.example.user.core.UserService;
import com.example.user.port.in.CreateAccountPort;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.DeleteAccountPort;
import com.example.user.port.in.DeleteUserPort;
import com.example.user.port.in.GetAccountPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.in.GetUserAccountsPort;
import com.example.user.port.in.PatchUserPort;
import com.example.user.port.in.TopUpAccountPort;
import com.example.user.port.in.UpdateUserPort;
import com.example.user.port.out.AccountRepositoryPort;
import com.example.user.port.out.CreditAccountPort;
import com.example.user.port.out.DebitAccountPort;
import com.example.user.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-layer wiring — the sole place where all hexagonal components are assembled.
 *
 * <p>A <strong>single</strong> {@link UserService} instance is created and shared across
 * all inbound port bindings. Earlier versions created one service instance per port using
 * separate {@code new UserService(...)} calls, which was wasteful and obscured the fact
 * that every port operation belongs to the same domain service.
 *
 * <p>Dependency flow:
 * <pre>
 *   UserJpaRepository  →  UserRepositoryAdapter  →  UserRepositoryPort
 *                                                         ↓
 *                                                    UserService  (one instance)
 *                                                         ↓  (method references)
 *   CreateUserPort, GetUserPort, GetAllUsersPort, UpdateUserPort, PatchUserPort, DeleteUserPort
 * </pre>
 */
@Configuration
public class UserConfig {

    @Bean
    UserRepositoryPort userRepositoryPort(UserJpaRepository userJpaRepository) {
        return new UserRepositoryAdapter(userJpaRepository);
    }

    @Bean
    AccountRepositoryPort accountRepositoryPort(AccountJpaRepository accountJpaRepository) {
        return new AccountRepositoryAdapter(accountJpaRepository);
    }

    @Bean
    DebitAccountPort debitAccountPort(AccountRepositoryPort accountRepositoryPort) {
        if (accountRepositoryPort instanceof DebitAccountPort debitAccountPort) {
            return debitAccountPort;
        }
        throw new IllegalStateException("Configured account repository does not implement DebitAccountPort");
    }

    @Bean
    CreditAccountPort creditAccountPort(AccountRepositoryPort accountRepositoryPort) {
        if (accountRepositoryPort instanceof CreditAccountPort creditAccountPort) {
            return creditAccountPort;
        }
        throw new IllegalStateException("Configured account repository does not implement CreditAccountPort");
    }

    /**
     * Single shared domain service — registered under an internal bean name that does NOT
     * match any port interface type.  All port beans below reference it via this @Bean method.
     *
     * <p>Spring's {@code @Configuration} CGLIB proxy guarantees that calling
     * {@code userServiceBean(userRepositoryPort)} multiple times within the same
     * {@code @Configuration} class returns the <em>same cached instance</em>, so only
     * one {@link UserService} is created regardless of how many port beans reference it.
     *
     * <p>The bean is typed as {@code UserService} (concrete class), so Spring never
     * considers it as a candidate when autowiring any of the port interfaces — eliminating
     * the "expected single matching bean but found 2" ambiguity that occurs when the service
     * is registered directly as a {@code @Bean} that implements those interfaces.
     */
    @Bean
    UserService userServiceBean(UserRepositoryPort userRepositoryPort) {
        return new UserService(userRepositoryPort);
    }

    @Bean
    CreateUserPort createUserPort(UserService userServiceBean) {
        return userServiceBean::create;
    }

    @Bean
    GetUserPort getUserPort(UserService userServiceBean) {
        return userServiceBean::getById;
    }

    @Bean
    GetAllUsersPort getAllUsersPort(UserService userServiceBean) {
        return userServiceBean::getAll;
    }

    @Bean
    UpdateUserPort updateUserPort(UserService userServiceBean) {
        return userServiceBean::update;
    }

    @Bean
    PatchUserPort patchUserPort(UserService userServiceBean) {
        return userServiceBean::patch;
    }

    @Bean
    DeleteUserPort deleteUserPort(UserService userServiceBean) {
        return userServiceBean::deleteById;
    }

    @Bean
    AccountService accountServiceBean(
            UserRepositoryPort userRepositoryPort,
            AccountRepositoryPort accountRepositoryPort,
            CreditAccountPort creditAccountPort
    ) {
        return new AccountService(userRepositoryPort, accountRepositoryPort, creditAccountPort);
    }

    @Bean
    CreateAccountPort createAccountPort(AccountService accountServiceBean) {
        return accountServiceBean::create;
    }

    @Bean
    GetUserAccountsPort getUserAccountsPort(AccountService accountServiceBean) {
        return accountServiceBean::getAllByUserId;
    }

    @Bean
    GetAccountPort getAccountPort(AccountService accountServiceBean) {
        return accountServiceBean::getById;
    }

    @Bean
    DeleteAccountPort deleteAccountPort(AccountService accountServiceBean) {
        return accountServiceBean::deleteById;
    }

    @Bean
    TopUpAccountPort topUpAccountPort(AccountService accountServiceBean) {
        return accountServiceBean::topUp;
    }
}
