package com.example.user.core;

import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.DeleteUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.in.PatchUserPort;
import com.example.user.port.in.UpdateUserPort;
import com.example.user.port.out.UserRepositoryPort;

import java.util.List;

public class UserService
        implements CreateUserPort, GetUserPort, GetAllUsersPort,
                   UpdateUserPort, PatchUserPort, DeleteUserPort {

    private final UserRepositoryPort userRepositoryPort;

    public UserService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    @Override
    public User create(String email, String displayName) {
        if (userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }
        return userRepositoryPort.save(User.createBuyer(null, email, displayName));
    }

    @Override
    public User getById(Long id) {
        return userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
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
    public User update(Long id, String email, String displayName) {
        User existing = userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
        if (!existing.email().equalsIgnoreCase(email) && userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }
        return userRepositoryPort.update(new User(
                id,
                email,
                displayName,
                existing.isSeller(),
                existing.sellerDisplayName(),
                existing.sellerBio(),
                existing.sellerRating(),
                existing.sellerReviewCount(),
                existing.sellerVerifiedAt(),
                existing.sellerJoinedAt()
        ));
    }

    @Override
    public User patch(Long id, String email, String displayName) {
        User existing = userRepositoryPort.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + id));
        String newEmail       = email       != null ? email       : existing.email();
        String newDisplayName = displayName != null ? displayName : existing.displayName();
        if (email != null && !existing.email().equalsIgnoreCase(email)
                && userRepositoryPort.existsByEmail(email)) {
            throw new DuplicateUserException("User already exists for email: " + email);
        }
        return userRepositoryPort.update(new User(
                id,
                newEmail,
                newDisplayName,
                existing.isSeller(),
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
