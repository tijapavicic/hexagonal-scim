package com.example.user.api;

import com.example.user.api.config.ApiPaginationProperties;
import com.example.user.api.config.LegacyApiDeprecationProperties;
import com.example.user.api.dto.CreateUserRequest;
import com.example.user.api.dto.PatchUserRequest;
import com.example.user.api.dto.UpdateUserRequest;
import com.example.user.model.PagedUsers;
import com.example.user.model.User;
import com.example.user.port.in.CreateUserPort;
import com.example.user.port.in.DeleteUserPort;
import com.example.user.port.in.GetAllUsersPort;
import com.example.user.port.in.GetUserPort;
import com.example.user.port.in.PatchUserPort;
import com.example.user.port.in.UpdateUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerAdapterTest {

    @Mock private CreateUserPort createUserPort;
    @Mock private GetUserPort getUserPort;
    @Mock private GetAllUsersPort getAllUsersPort;
    @Mock private UpdateUserPort updateUserPort;
    @Mock private PatchUserPort patchUserPort;
    @Mock private DeleteUserPort deleteUserPort;

    private UserControllerAdapter controller;

    @BeforeEach
    void setUp() {
        LegacyApiDeprecationProperties legacy = new LegacyApiDeprecationProperties();
        ApiPaginationProperties pagination = new ApiPaginationProperties();
        pagination.setDefaultPage(0);
        pagination.setDefaultSize(10);
        pagination.setDefaultPageable(true);

        controller = new UserControllerAdapter(
                createUserPort,
                getUserPort,
                getAllUsersPort,
                updateUserPort,
                patchUserPort,
                deleteUserPort,
                legacy,
                pagination
        );
    }

    @Test
    void createDelegatesToPortAndMapsResponse() {
        when(createUserPort.create("john@example.com", "John"))
                .thenReturn(User.createBuyer(1L, "john@example.com", "John"));

        var response = controller.create(new CreateUserRequest("john@example.com", "John"));

        assertEquals(1L, response.id());
        assertEquals("john@example.com", response.email());
        verify(createUserPort).create("john@example.com", "John");
    }

    @Test
    void getAllUsesConfiguredDefaultsWhenParamsMissing() {
        when(getAllUsersPort.getAll(0, 10, true))
                .thenReturn(new PagedUsers(List.of(User.createBuyer(1L, "a@example.com", "A")), 0, 10, 1, 1));

        var response = controller.getAll(null, null, null);

        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
        verify(getAllUsersPort).getAll(0, 10, true);
    }

    @Test
    void getByIdDelegatesToPort() {
        when(getUserPort.getById(7L)).thenReturn(User.createBuyer(7L, "seven@example.com", "Seven"));

        var response = controller.getById(7L);

        assertEquals(7L, response.id());
        verify(getUserPort).getById(7L);
    }

    @Test
    void updateDelegatesToPortAndMapsResponse() {
        when(updateUserPort.update(3L, "new@example.com", "New Name"))
                .thenReturn(User.createBuyer(3L, "new@example.com", "New Name"));

        var response = controller.update(3L, new UpdateUserRequest("new@example.com", "New Name"));

        assertEquals("new@example.com", response.email());
        verify(updateUserPort).update(3L, "new@example.com", "New Name");
    }

    @Test
    void patchDelegatesToPortAndMapsResponse() {
        when(patchUserPort.patch(4L, "patched@example.com", null))
                .thenReturn(User.createBuyer(4L, "patched@example.com", "Display"));

        var response = controller.patch(4L, new PatchUserRequest("patched@example.com", null));

        assertEquals("patched@example.com", response.email());
        verify(patchUserPort).patch(4L, "patched@example.com", null);
    }

    @Test
    void deleteDelegatesToPort() {
        controller.delete(8L);

        verify(deleteUserPort).deleteById(8L);
    }
}
