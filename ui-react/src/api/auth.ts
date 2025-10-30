import type {User} from "@/stores/userSlice"

const BASE = process.env.APP_BASE_URL || ""
const apiUrl = (path: string) => (BASE ? `${BASE}${path}` : `/api${path}`)

export const authService = {
  async login(email: string, password: string) {
    const res = await fetch(apiUrl('/auth/login'), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: email, password, loginType: "PASSWORD" }),
    })

    const data = await res.json()
    if (!res.ok || (typeof data?.code === 'number' && data.code !== 200)) throw data
    return data
  },
  async getUserInfo(token: string): Promise<User> {
    try {
      const res = await fetch(apiUrl('/auth/me'), {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
          satoken: token,
        },
      })
      const wrapper = await res.json()
      if (!res.ok || (typeof wrapper?.code === 'number' && wrapper.code !== 200)) throw wrapper
      return wrapper?.data ?? null
    } catch (_) {
      try {
        const cached = localStorage.getItem('user')
        return cached ? JSON.parse(cached) as User : null
      } catch {
        return null
      }
    }
  },

  async register(username: string, email: string, password: string) {
    const res = await fetch(apiUrl('/auth/register'), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password, email }),
    })

    const data = await res.json()
    if (!res.ok || (typeof data?.code === 'number' && data.code !== 200)) throw data
    return data
  },

  async updatePassword(email: string, oldPassword: string, newPassword: string) {
    const res = await fetch(`${process.env.APP_BASE_URL}/api/auth/update-password`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ 
        email, 
        oldPassword, 
        newPassword,
        language: localStorage.getItem('language')
      }),
    })

    const data = await res.json()
    if (!res.ok) throw data
    return data
  }
}
