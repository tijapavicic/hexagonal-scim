/**
 * Minimal DTOs for the first users-list vertical slice.
 * Mirrors backend /api/v1/users paginated response shape.
 */
export interface UserListItemDto {
  id: number;
  email: string;
  displayName: string;
}

export interface UsersPageDto {
  content: UserListItemDto[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

