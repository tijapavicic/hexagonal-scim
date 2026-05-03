package com.example.user.api;

import com.example.user.api.dto.CreateUserRequest;
import com.example.user.api.dto.UserResponse;
import com.example.user.config.LegacyApiDeprecationProperties;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.GetUserPort;
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
@RequestMapping({UserControllerAdapter.V1_BASE_PATH, UserControllerAdapter.LEGACY_BASE_PATH})
public class UserControllerAdapter {
    public static final String V1_BASE_PATH = "/api/v1/users";

    /**
     * @deprecated Legacy path retained temporarily for backward compatibility.
     */
    @Deprecated(forRemoval = false)
    public static final String LEGACY_BASE_PATH = "/api/users";

    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";
    private static final String LINK_HEADER = "Link";

    private final CreateUserPort createUserPort;
    private final GetUserPort getUserPort;
    private final LegacyApiDeprecationProperties legacyApiDeprecationProperties;

    public UserControllerAdapter(
            CreateUserPort createUserPort,
            GetUserPort getUserPort,
            LegacyApiDeprecationProperties legacyApiDeprecationProperties
    ) {
        this.createUserPort = createUserPort;
        this.getUserPort = getUserPort;
        this.legacyApiDeprecationProperties = legacyApiDeprecationProperties;
    }

    @ModelAttribute
    void addLegacyDeprecationHeaders(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();
        if (uri.equals(LEGACY_BASE_PATH) || uri.startsWith(LEGACY_BASE_PATH + "/")) {
            response.setHeader(DEPRECATION_HEADER, legacyApiDeprecationProperties.getDeprecationValue());
            response.setHeader(SUNSET_HEADER, legacyApiDeprecationProperties.getSunsetDate());
            response.setHeader(LINK_HEADER, legacyApiDeprecationProperties.getSuccessorLink());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User created = createUserPort.create(request.email(), request.displayName());
        return new UserResponse(created.id(), created.email(), created.displayName());
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable("id") Long id) {
        User user = getUserPort.getById(id);
        return new UserResponse(user.id(), user.email(), user.displayName());
    }
}

