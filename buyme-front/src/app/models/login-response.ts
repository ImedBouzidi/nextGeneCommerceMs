export interface LoginResponse {
  token: string | null;
  refreshToken?: string | null;
  expiresIn?: number | null;
  roles: string[] | null;
  message: string;
}
