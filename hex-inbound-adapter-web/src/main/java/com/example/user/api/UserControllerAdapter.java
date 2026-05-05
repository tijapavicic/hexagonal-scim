package com.example.user.api;

import com.example.user.api.config.ApiPaginationProperties;
import com.example.user.api.config.LegacyApiDeprecationProperties;
import com.example.user.api.dto.CreateUserRequest;
import com.example.user.api.dto.ErrorResponse;
import com.example.user.api.dto.PagedUserResponse;
import com.example.user.api.dto.PatchUserRequest;
import com.example.user.api.dto.UpdateUserRequest;
import com.example.user.api.dto.UserResponse;
import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.DeleteUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.in.PatchUserPort;
import com.example.user.port.in.UpdateUserPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "User management — create and retrieve users")
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
    private final GetAllUsersPort getAllUsersPort;
    private final UpdateUserPort updateUserPort;
    private final PatchUserPort patchUserPort;
    private final DeleteUserPort deleteUserPort;
    private final LegacyApiDeprecationProperties legacyApiDeprecationProperties;
    private final ApiPaginationProperties apiPaginationProperties;

    public UserControllerAdapter(
            CreateUserPort createUserPort,
            GetUserPort getUserPort,
            GetAllUsersPort getAllUsersPort,
            UpdateUserPort updateUserPort,
            PatchUserPort patchUserPort,
            DeleteUserPort deleteUserPort,
            LegacyApiDeprecationProperties legacyApiDeprecationProperties,
            ApiPaginationProperties apiPaginationProperties
    ) {
        this.createUserPort = createUserPort;
        this.getUserPort = getUserPort;
        this.getAllUsersPort = getAllUsersPort;
        this.updateUserPort = updateUserPort;
        this.patchUserPort = patchUserPort;
        this.deleteUserPort = deleteUserPort;
        this.legacyApiDeprecationProperties = legacyApiDeprecationProperties;
        this.apiPaginationProperties = apiPaginationProperties;
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

    @Operation(summary = "Create a user", description = "Creates a new user. E-mail must be unique and valid.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail already registered",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User created = createUserPort.create(request.email(), request.displayName());
        return new UserResponse(created.id(), created.email(), created.displayName());
    }

    @Operation(
            summary = "List users",
            description = """
                    Returns a paginated list of users.
                    
                    - Omit all params → first page of `api.pagination.default-size` users (default 10).
                    - Pass `pageable=false` → every user in the database, no pagination.
                    - Pass explicit `page` / `size` for fine-grained control.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged user list",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PagedUserResponse.class)))
    })
    @GetMapping
    public PagedUserResponse getAll(
            @Parameter(description = "Zero-indexed page number (uses configured default when absent)")
            @RequestParam(name = "page", required = false) Integer page,

            @Parameter(description = "Items per page, max 100 (uses configured default when absent)")
            @RequestParam(name = "size", required = false) Integer size,

            @Parameter(description = "Set to `false` to return ALL users without paging")
            @RequestParam(name = "pageable", required = false) Boolean pageable
    ) {
        int effectivePage = page != null ? page : apiPaginationProperties.getDefaultPage();
        int effectiveSize = size != null ? size : apiPaginationProperties.getDefaultSize();
        boolean effectivePageable = pageable != null ? pageable : apiPaginationProperties.isDefaultPageable();

        PagedUsers pagedUsers = getAllUsersPort.getAll(effectivePage, effectiveSize, effectivePageable);
        return new PagedUserResponse(
                pagedUsers.content().stream()
                        .map(user -> new UserResponse(user.id(), user.email(), user.displayName()))
                        .toList(),
                pagedUsers.pageNumber(),
                pagedUsers.pageSize(),
                pagedUsers.totalElements(),
                pagedUsers.totalPages(),
                pagedUsers.pageNumber() < pagedUsers.totalPages() - 1,
                pagedUsers.pageNumber() > 0
        );
    }

    @Operation(summary = "Get user by ID", description = "Returns a single user by their numeric ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public UserResponse getById(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable("id") Long id) {
        User user = getUserPort.getById(id);
        return new UserResponse(user.id(), user.email(), user.displayName());
    }

    @Operation(summary = "Replace a user (full update)",
            description = "Completely replaces the user with the provided values. Both fields are required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User replaced",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail already registered",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public UserResponse update(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        User updated = updateUserPort.update(id, request.email(), request.displayName());
        return new UserResponse(updated.id(), updated.email(), updated.displayName());
    }

    @Operation(summary = "Partially update a user (PATCH)",
            description = """
                    Applies a partial update — only the fields included in the request body are changed.
                    Omitted fields (JSON `null` or missing) retain their existing values.
                    At least one field must be provided.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or no fields provided",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail already registered",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public UserResponse patch(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchUserRequest request) {
        User patched = patchUserPort.patch(id, request.email(), request.displayName());
        return new UserResponse(patched.id(), patched.email(), patched.displayName());
    }

    @Operation(summary = "Delete a user",
            description = "Permanently deletes the user with the given ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable("id") Long id) {
        deleteUserPort.deleteById(id);
    }
}
