import { getJson, postJson, putJson, deleteVoid } from './http';
import type { UserDto, CreateUserDto, UpdateUserDto } from '../types/user.dto';

const BASE = '/api/v1/users';

/** Response format from /api/v1/users/all endpoint */
interface UserAllResponse {
  id: number;
  email: string;
  displayName: string;
}

/**
 * List users — backend returns a plain array (no pagination envelope).
 * The caller tracks page/size state and determines whether more pages exist
 * by comparing result length to the requested size.
 */
export async function listUsers(page = 0, size = 10): Promise<UserDto[]> {
  return getJson<UserDto[]>(`${BASE}?page=${page}&size=${size}`);
}

/** Fetch all users without pagination (admin only). Throws if not authorized. */
export async function getAllUsersAdminOnly(): Promise<UserAllResponse[]> {
  return getJson<UserAllResponse[]>(`${BASE}/all`);
}

/** Fetch a single user by numeric ID. */
export async function getUserById(id: number): Promise<UserDto> {
  return getJson<UserDto>(`${BASE}/${id}`);
}

/** Create a new user. Returns the persisted entity (201 Created). */
export async function createUser(body: CreateUserDto): Promise<UserDto> {
  return postJson<UserDto>(BASE, body);
}

/** Fully replace a user by ID. Returns the updated entity. */
export async function updateUser(id: number, body: UpdateUserDto): Promise<UserDto> {
  return putJson<UserDto>(`${BASE}/${id}`, body);
}

/** Delete a user by ID. Returns void (204 No Content). */
export async function deleteUser(id: number): Promise<void> {
  return deleteVoid(`${BASE}/${id}`);
}

