/**
 * Transport-layer user DTOs — mirrors the backend UserResponse exactly.
 * Kept separate from rendering/domain models so API shape changes land here first.
 */

/** User role in the marketplace. */
export enum UserRole {
  BUYER = 'BUYER',
  SELLER = 'SELLER',
  ADMIN = 'ADMIN'
}

export interface UserDto {
  id: number;
  email: string;
  displayName: string;
  role: UserRole;
  keycloakId?: string;  // Optional: populated after OAuth2 login
}

/** Fields required to create a user (id is server-assigned). */
export interface CreateUserDto {
  email: string;
  displayName: string;
  role?: UserRole;  // Optional, defaults to BUYER on backend
}

/** Fields required to fully replace a user (PUT semantics, id in URL). */
export interface UpdateUserDto {
  email: string;
  displayName: string;
  role?: UserRole;  // Optional, defaults to existing value
}

/**
 * Fields for a partial user update (PATCH semantics, id in URL).
 * Only the provided fields are changed; omitted fields retain their current values.
 * At least one field must be provided.
 */
export interface PatchUserDto {
  email?: string;
  displayName?: string;
  role?: UserRole;
}



