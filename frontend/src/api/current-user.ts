import { getUserByKeycloakId } from '../api/users';
import { getKeycloakUserId } from '../auth/keycloak';
import type { UserDto } from '../types/user.dto';

/**
 * Get the current authenticated user's data from the backend.
 *
 * This function:
 * 1. Extracts the Keycloak user ID from the JWT token
 * 2. Calls the backend GET /api/v1/users/by-keycloak-id/{keycloakId}
 * 3. Returns the user data
 *
 * @returns User data for the currently authenticated user
 * @throws Error if not authenticated or user not found
 *
 * @example
 * ```typescript
 * try {
 *   const currentUser = await getCurrentUser();
 *   console.log('Logged in as:', currentUser.email);
 * } catch (error) {
 *   console.error('Not authenticated or user not found');
 * }
 * ```
 */
export async function getCurrentUser(): Promise<UserDto> {
  const keycloakId = getKeycloakUserId();

  if (!keycloakId) {
    throw new Error('Not authenticated: No Keycloak user ID found in token');
  }

  try {
    return await getUserByKeycloakId(keycloakId);
  } catch (error) {
    // Check if it's a 404 (user not found in database)
    if (error instanceof Error && error.message.includes('404')) {
      throw new Error(
        `User not found in database for Keycloak ID: ${keycloakId}. ` +
        'This user may need to be created or linked during first login.'
      );
    }
    throw error;
  }
}

/**
 * Check if the current authenticated user exists in the backend database.
 *
 * Useful for determining if a user needs to be created on first OAuth2 login.
 *
 * @returns true if user exists, false otherwise
 */
export async function currentUserExists(): Promise<boolean> {
  const keycloakId = getKeycloakUserId();

  if (!keycloakId) {
    return false;
  }

  try {
    await getUserByKeycloakId(keycloakId);
    return true;
  } catch {
    return false;
  }
}

