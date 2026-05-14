import { getJson } from './http';
import type { UsersPageDto } from '../types/user-list.dto';

const USERS_ENDPOINT = '/api/v1/users?page=0&size=10';

export async function fetchUsersPage(): Promise<UsersPageDto> {
  return getJson<UsersPageDto>(USERS_ENDPOINT);
}

