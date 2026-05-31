/**
 * Transport-layer user DTOs — mirrors the backend UserResponse exactly.
 * Kept separate from rendering/domain models so API shape changes land here first.
 */
export interface UserDto {
  id: number;
  email: string;
  displayName: string;
  keycloakId?: string;  // Optional: populated after OAuth2 login
}

/** Fields required to create a user (id is server-assigned). */
export interface CreateUserDto {
  email: string;
  displayName: string;
}

/** Fields required to fully replace a user (PUT semantics, id in URL). */
export interface UpdateUserDto {
  email: string;
  displayName: string;
}



