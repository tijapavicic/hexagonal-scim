package com.example.user.config;

import com.example.user.adapter.db.UserJpaRepository;
import com.example.user.adapter.db.UserRepositoryAdapter;
import com.example.user.core.UserService;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
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
    public CreateUserPort createUserPort(UserRepositoryPort userRepositoryPort) {
        return new UserService(userRepositoryPort)::create;
    }

    @Bean
    public GetUserPort getUserPort(UserRepositoryPort userRepositoryPort) {
        return new UserService(userRepositoryPort)::getById;
    }

    @Bean
    public GetAllUsersPort getAllUsersPort(UserRepositoryPort userRepositoryPort) {
        return new UserService(userRepositoryPort)::getAll;
    }
}
