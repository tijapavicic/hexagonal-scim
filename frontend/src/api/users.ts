import { getJson, postJson, putJson, patchJson, deleteVoid } from './http';
import type { UserDto, CreateUserDto, UpdateUserDto, PatchUserDto } from '../types/user.dto';

const BASE = '/api/v1/users';

/**
 * List users — backend returns a paginated response.
 * The caller tracks page/size state and determines whether more pages exist
 * by comparing result length to the requested size.
 */
export async function listUsers(page = 0, size = 10): Promise<UserDto[]> {
  return getJson<UserDto[]>(`${BASE}?page=${page}&size=${size}`);
}

/** Fetch all users without pagination (admin only). Throws if not authorized. */
export async function getAllUsersAdminOnly(): Promise<UserDto[]> {
  return getJson<UserDto[]>(`${BASE}/all`);
}

/** Fetch a single user by numeric ID. */
export async function getUserById(id: number): Promise<UserDto> {
  return getJson<UserDto>(`${BASE}/${id}`);
}

/**
 * Fetch a single user by Keycloak UUID (external OAuth2 identity).
 *
 * Use this when you have the Keycloak user ID from the JWT token
 * and need to map it to the internal user record.
 *
 * @param keycloakId - Keycloak user UUID (from JWT 'sub' claim)
 * @returns User data matching the Keycloak ID
 * @throws 404 NOT_FOUND if no user with this keycloak_id exists
 */
export async function getUserByKeycloakId(keycloakId: string): Promise<UserDto> {
  return getJson<UserDto>(`${BASE}/by-keycloak-id/${keycloakId}`);
}

/** Create a new user. Returns the persisted entity (201 Created). */
export async function createUser(body: CreateUserDto): Promise<UserDto> {
  return postJson<UserDto>(BASE, body);
}

/** Fully replace a user by ID. Returns the updated entity. */
export async function updateUser(id: number, body: UpdateUserDto): Promise<UserDto> {
  return putJson<UserDto>(`${BASE}/${id}`, body);
}

/**
 * Partially update a user by ID (PATCH semantics).
 * Only the fields present in `body` are changed; omitted fields keep their current values.
 * At least one field must be provided.
 */
export async function patchUser(id: number, body: PatchUserDto): Promise<UserDto> {
  return patchJson<UserDto>(`${BASE}/${id}`, body);
}

/** Delete a user by ID. Returns void (204 No Content). */
export async function deleteUser(id: number): Promise<void> {
  return deleteVoid(`${BASE}/${id}`);
}



