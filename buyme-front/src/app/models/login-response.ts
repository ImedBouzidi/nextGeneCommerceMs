export interface LoginResponse {
  token: string | null;
  roles: string[] | null;
  message: string;
}
