import { getJson, postJson, deleteVoid } from './http';
import type { AccountDto, CreateAccountDto, TopUpAccountDto } from '../types/account.dto';

export async function listAccounts(userId: number): Promise<AccountDto[]> {
  return getJson<AccountDto[]>(`/api/v1/users/${userId}/accounts`);
}

export async function getAccountById(userId: number, accountId: number): Promise<AccountDto> {
  return getJson<AccountDto>(`/api/v1/users/${userId}/accounts/${accountId}`);
}

export async function createAccount(userId: number, body: CreateAccountDto): Promise<AccountDto> {
  return postJson<AccountDto>(`/api/v1/users/${userId}/accounts`, body);
}

export async function topUpAccount(userId: number, accountId: number, body: TopUpAccountDto): Promise<AccountDto> {
  return postJson<AccountDto>(`/api/v1/users/${userId}/accounts/${accountId}/topup`, body);
}

export async function deleteAccount(userId: number, accountId: number): Promise<void> {
  return deleteVoid(`/api/v1/users/${userId}/accounts/${accountId}`);
}
