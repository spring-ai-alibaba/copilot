import type {User} from "@/stores/userSlice"
import { apiUrl } from "./base"
import { safeJsonParse } from "@/utils/safeJsonParse"
import { safeJsonStringify } from "@/utils/safeJsonParse"

export const authService = {
  async login(username: string, password: string) {
    const res = await fetch(apiUrl('/auth/login'), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    })

    // 获取原始响应文本，避免JSON.parse自动转换大整数
    const rawText = await res.text()
    console.log('[auth.login] 原始响应文本:', rawText)

    const data = safeJsonParse(rawText)
    console.log('[auth.login] 解析后数据:', data)

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
        },
      })

      // 获取原始响应文本，避免JSON.parse自动转换大整数
      const rawText = await res.text()
      console.log('[auth.getUserInfo] 原始响应文本:', rawText)

      const wrapper = safeJsonParse(rawText)
      console.log('[auth.getUserInfo] 解析后数据:', wrapper)

      if (!res.ok || (typeof wrapper?.code === 'number' && wrapper.code !== 200)) throw wrapper

      // 数据已经通过safeJsonParse处理过，直接返回
      return wrapper?.data ?? null
    } catch (_) {
      try {
        const cached = localStorage.getItem('user')
        console.log('[auth.getUserInfo] 使用缓存数据:', cached)
        return cached ? safeJsonParse(cached) as User : null
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

    const rawText = await res.text()
    const data = safeJsonParse(rawText)

    if (!res.ok || (typeof data?.code === 'number' && data.code !== 200)) throw data
    return data
  },

  async updatePassword(email: string, oldPassword: string, newPassword: string) {
    const res = await fetch(apiUrl('/auth/reset/password'), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email,
        oldPassword,
        newPassword,
        language: localStorage.getItem('language')
      }),
    })

    const rawText = await res.text()
    const data = safeJsonParse(rawText)

    if (!res.ok) throw data
    return data
  }
}
