import apiClient from './client';
import type {
  PagedUserResponse,
  User,
  CreateUserRequest,
  UpdateUserRequest,
  PatchUserRequest,
} from '../types/user';

const BASE = '/api/v1/users';

export const getUsers = (page = 0, size = 10) =>
  apiClient
    .get<PagedUserResponse>(BASE, { params: { page, size } })
    .then((r) => r.data);

export const getUserById = (id: number) =>
  apiClient.get<User>(`${BASE}/${id}`).then((r) => r.data);

export const createUser = (data: CreateUserRequest) =>
  apiClient.post<User>(BASE, data).then((r) => r.data);

/** Full replacement (PUT) */
export const updateUser = (id: number, data: UpdateUserRequest) =>
  apiClient.put<User>(`${BASE}/${id}`, data).then((r) => r.data);

/** Partial update (PATCH) */
export const patchUser = (id: number, data: PatchUserRequest) =>
  apiClient.patch<User>(`${BASE}/${id}`, data).then((r) => r.data);

export const deleteUser = (id: number) =>
  apiClient.delete(`${BASE}/${id}`);

