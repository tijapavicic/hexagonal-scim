/**
 * Transport-layer user DTOs — mirrors the backend User model exactly.
 * Kept separate from rendering/domain models so API shape changes land here first.
 */
export interface UserDto {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  active: boolean;
}

/** Fields required to create a user (id is server-assigned). */
export interface CreateUserDto {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  active: boolean;
}

/** Fields required to fully replace a user (PUT semantics, id in URL). */
export interface UpdateUserDto {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  active: boolean;
}

