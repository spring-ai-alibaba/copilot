import type { User } from "@/stores/userSlice"
export const authService = {
  async login(email: string, password: string) {
    const res = await fetch(`${process.env.APP_BASE_URL}/api/auth/login`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, language: localStorage.getItem('language') }),
    })

    const data = await res.json()
    if (!res.ok) throw data
    return data
  },
  async getUserInfo(token: string): Promise<User> {
    try {
      const res = await fetch(`${process.env.APP_BASE_URL}/api/user`, {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      })
      const response = await res.json()
      return response
    } catch (error) {

    }
  },

  async register(username: string, email: string, password: string) {
    const res = await fetch(`${process.env.APP_BASE_URL}/api/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password, language: localStorage.getItem('language') }),
    })

    const data = await res.json()
    if (!res.ok) throw data
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
