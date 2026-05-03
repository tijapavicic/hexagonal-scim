package com.example.user.config;

import com.example.user.adapter.db.UserJpaRepository;
import com.example.user.adapter.db.UserRepositoryAdapter;
import com.example.user.core.UserService;
import com.example.user.port.in.CreateUserUseCase;
import com.example.user.port.in.GetUserUseCase;
import com.example.user.port.out.UserRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserConfig {

    @Bean
    public UserRepositoryPort userRepositoryPort(UserJpaRepository userJpaRepository) {
        return new UserRepositoryAdapter(userJpaRepository);
    }

    @Bean
    public CreateUserUseCase createUserUseCase(UserRepositoryPort userRepositoryPort) {
        UserService userService = new UserService(userRepositoryPort);
        return userService::create;
    }

    @Bean
    public GetUserUseCase getUserUseCase(UserRepositoryPort userRepositoryPort) {
        UserService userService = new UserService(userRepositoryPort);
        return userService::getById;
    }
}
