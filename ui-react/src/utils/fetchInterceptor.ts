import useUserStore from "@/stores/userSlice"
import { apiUrl } from "@/api/base"

/**
 * Setup global fetch interceptor:
 * - Auto attach Authorization header if token exists and not provided
 * - When HTTP 401 or response body { code: 401 } is detected, open login modal
 * - Skip auth endpoints to avoid loops
 */
export function setupFetchInterceptors() {
  if (typeof window === "undefined" || (window as any).__FETCH_INTERCEPTOR_INSTALLED__) {
    return
  }

  const originalFetch = window.fetch.bind(window)
  ;(window as any).__FETCH_INTERCEPTOR_INSTALLED__ = true

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    try {
      const req = new Request(input as RequestInfo, init)
      const url = req.url

      // Skip non-http(s) schemes
      if (!/^https?:/i.test(url) && !url.startsWith("/")) {
        return originalFetch(input as any, init)
      }

      const headers = new Headers(req.headers)
      const token = localStorage.getItem("token")

      // Only attach token for safe targets: same-origin or backend base origin
      let isSafeTarget = false
      try {
        const reqUrl = new URL(url, window.location.origin)
        const appBaseAbs = new URL(apiUrl('/'), window.location.origin)
        isSafeTarget =
          reqUrl.origin === window.location.origin ||
          reqUrl.origin === appBaseAbs.origin ||
          url.startsWith("/")
      } catch {
        isSafeTarget = url.startsWith("/")
      }
      if (isSafeTarget && token && !headers.has("Authorization")) {
        headers.set("Authorization", `Bearer ${token}`)
      }

      const newInit: RequestInit = {
        ...init,
        headers,
        // preserve body/method/etc handled by Request cloning
        method: req.method,
        body: req.method === "GET" || req.method === "HEAD" ? undefined : (init?.body ?? req.body),
        credentials: init?.credentials ?? req.credentials,
        mode: init?.mode ?? req.mode,
        cache: init?.cache ?? req.cache,
        redirect: init?.redirect ?? req.redirect,
        referrer: init?.referrer ?? req.referrer,
        referrerPolicy: init?.referrerPolicy ?? req.referrerPolicy,
        integrity: init?.integrity ?? req.integrity,
        keepalive: init?.keepalive ?? req.keepalive,
        signal: init?.signal ?? req.signal,
      }

      const res = await originalFetch(url, newInit)

      // Fast path: HTTP status 401
      if (res.status === 401 && !isAuthEndpoint(url)) {
        promptLogin()
        return res
      }

      // Try to detect wrapper { code: 401 } without consuming original response
      const contentType = res.headers.get("content-type") || ""
      if (contentType.includes("application/json")) {
        try {
          const clone = res.clone()
          const json = await clone.json().catch(() => null)
          if (
            json &&
            typeof json.code === "number" &&
            json.code === 401 &&
            !isAuthEndpoint(url)
          ) {
            promptLogin()
          }
        } catch {
          // ignore parse errors
        }
      }

      return res
    } catch (_err) {
      // On unexpected errors, fallback to original fetch
      return originalFetch(input as any, init)
    }
  }

  function promptLogin() {
    // Do not clear token aggressively; just prompt login
    const store = useUserStore.getState()
    try { store.openLoginModal() } catch {}
  }

  function isAuthEndpoint(url: string): boolean {
    // Avoid triggering on login/register/me/logout endpoints
    try {
      const u = new URL(url, window.location.origin)
      return (
        u.pathname.startsWith("/auth/") ||
        u.pathname.endsWith("/auth")
      )
    } catch {
      // relative path without origin
      return url.startsWith("/auth/")
    }
  }
}