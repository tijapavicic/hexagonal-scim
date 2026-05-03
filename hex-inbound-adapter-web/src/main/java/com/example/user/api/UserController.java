package com.example.user.api;

import com.example.user.api.dto.CreateUserRequest;
import com.example.user.api.dto.UserResponse;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserUseCase;
import com.example.user.port.in.GetUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({UserController.V1_BASE_PATH, UserController.LEGACY_BASE_PATH})
public class UserController {
    public static final String V1_BASE_PATH = "/api/v1/users";

    /**
     * @deprecated Legacy path retained temporarily for backward compatibility.
     */
    @Deprecated(forRemoval = false)
    public static final String LEGACY_BASE_PATH = "/api/users";

    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";
    private static final String LINK_HEADER = "Link";
    private static final String SUNSET_DATE = "Wed, 31 Dec 2026 23:59:59 GMT";
    private static final String SUCCESSOR_LINK = "</api/v1/users>; rel=\"successor-version\"";

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase, GetUserUseCase getUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
    }

    @ModelAttribute
    void addLegacyDeprecationHeaders(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();
        if (uri.equals(LEGACY_BASE_PATH) || uri.startsWith(LEGACY_BASE_PATH + "/")) {
            response.setHeader(DEPRECATION_HEADER, "true");
            response.setHeader(SUNSET_HEADER, SUNSET_DATE);
            response.setHeader(LINK_HEADER, SUCCESSOR_LINK);
        }
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
