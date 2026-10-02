import { readonly, ref } from "vue";
import { authApi } from "./api";

const authenticated = ref(false);
const username = ref("");
let initialized = false;
let pending: Promise<boolean> | undefined;

export async function refreshAuth() {
  if (pending) return pending;
  pending = authApi.status()
    .then((result) => {
      authenticated.value = result.authenticated;
      username.value = result.username || "";
      initialized = true;
      return authenticated.value;
    })
    .catch(() => {
      authenticated.value = false;
      username.value = "";
      initialized = true;
      return false;
    })
    .finally(() => pending = undefined);
  return pending;
}

export async function ensureAuth() {
  return initialized ? authenticated.value : refreshAuth();
}

export async function login(usernameValue: string, password: string) {
  const result = await authApi.login(usernameValue, password);
  authenticated.value = result.authenticated;
  username.value = result.username || "";
}

export async function logout() {
  await authApi.logout();
  authenticated.value = false;
  username.value = "";
}

export function useAuth() {
  return { authenticated: readonly(authenticated), username: readonly(username) };
}
