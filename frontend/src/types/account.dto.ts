// Account DTOs matching backend response/request shapes
export interface AccountDto {
  id: number;
  name: string;
  balance: string;
  userId: number;
}
export interface CreateAccountDto {
  name: string;
}
export interface TopUpAccountDto {
  amount: number;
}
