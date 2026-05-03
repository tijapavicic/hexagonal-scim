package com.example.user.api;

import com.example.user.api.dto.CreateUserRequest;
import com.example.user.api.dto.UserResponse;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserUseCase;
import com.example.user.port.in.GetUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase, GetUserUseCase getUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User created = createUserUseCase.create(request.email(), request.displayName());
        return new UserResponse(created.id(), created.email(), created.displayName());
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable("id") Long id) {
        User user = getUserUseCase.getById(id);
        return new UserResponse(user.id(), user.email(), user.displayName());
    }
}
