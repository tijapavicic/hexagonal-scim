/** Mirrors UserResponse from hex-inbound-adapter-web */
export interface User {
  id: number;
  email: string;
  displayName: string;
}

/** Mirrors PagedUserResponse from hex-inbound-adapter-web */
export interface PagedUserResponse {
  content: User[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

/** Mirrors CreateUserRequest */
export interface CreateUserRequest {
  email: string;
  displayName: string;
}

/** Mirrors UpdateUserRequest — both fields required for PUT */
export interface UpdateUserRequest {
  email: string;
  displayName: string;
}

/** Mirrors PatchUserRequest — at least one field for PATCH */
export interface PatchUserRequest {
  email?: string;
  displayName?: string;
}

