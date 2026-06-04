package com.example.user.core;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.model.UserRole;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.DeleteUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserByKeycloakIdPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.in.PatchUserPort;
import com.example.user.port.in.UpdateUserPort;
import com.example.user.port.out.UserRepositoryPort;

import java.util.List;

public class UserService
        implements CreateUserPort, GetUserPort, GetAllUsersPort,
                   UpdateUserPort, PatchUserPort, DeleteUserPort, GetUserByKeycloakIdPort {

    private final UserRepositoryPort userRepositoryPort;

    public UserService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    @Override
    public User create(String email, String displayName, UserRole role) {
        if (userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }
        UserRole effectiveRole = role != null ? role : UserRole.BUYER;
        return userRepositoryPort.save(new User(null, null, email, displayName, effectiveRole, null, null, null, null, null, null));
    }

    @Override
    public User getById(Long id) {
        return userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
    }

    @Override
    public User getByKeycloakId(String keycloakId) {
        return userRepositoryPort.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found for keycloakId: " + keycloakId));
    }

    @Override
    public PagedUsers getAll(int pageNumber, int pageSize, boolean pageable) {
        if (!pageable) {
            List<User> all = userRepositoryPort.findAll();
            return new PagedUsers(all, 0, all.size(), all.size(), 1);
        }
        return userRepositoryPort.findAll(pageNumber, pageSize);
    }

    @Override
    public User update(Long id, String email, String displayName, UserRole role) {
        User existing = userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
        if (!existing.email().equalsIgnoreCase(email) && userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }
        UserRole effectiveRole = role != null ? role : existing.role();
        return userRepositoryPort.update(new User(
                id,
                existing.keycloakId(),
                email,
                displayName,
                effectiveRole,
                existing.sellerDisplayName(),
                existing.sellerBio(),
                existing.sellerRating(),
                existing.sellerReviewCount(),
                existing.sellerVerifiedAt(),
                existing.sellerJoinedAt()
        ));
    }

    @Override
    public User patch(Long id, String email, String displayName, UserRole role) {
        User existing = userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
        String newEmail       = email       != null ? email       : existing.email();
        String newDisplayName = displayName != null ? displayName : existing.displayName();
        UserRole newRole      = role        != null ? role        : existing.role();
        if (email != null && !existing.email().equalsIgnoreCase(email)
                && userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }
        return userRepositoryPort.update(new User(
                id,
                existing.keycloakId(),
                newEmail,
                newDisplayName,
                newRole,
                existing.sellerDisplayName(),
                existing.sellerBio(),
                existing.sellerRating(),
                existing.sellerReviewCount(),
                existing.sellerVerifiedAt(),
                existing.sellerJoinedAt()
        ));
    }

    @Override
    public void deleteById(Long id) {
        if (userRepositoryPort.findById(id).isEmpty()) {
            throw new UserNotFoundException("User not found for id: " + id);
        }
        userRepositoryPort.deleteById(id);
    }
}
