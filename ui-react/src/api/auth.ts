import type {User} from "@/stores/userSlice"
import { apiUrl } from "./base"
import { safeJsonParse } from "@/utils/safeJsonParse"

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
  async getUserInfo(token: string): Promise<User | null> {
    let res: Response
    try {
      res = await fetch(apiUrl('/auth/me'), {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      })
    } catch (error) {
      // 网络中断时只能复用与同一个 token 一起持久化的用户，不能把旧用户配给新 token。
      try {
        const persisted = safeJsonParse(localStorage.getItem('user-storage') || '')
        if (persisted?.state?.token === token && persisted?.state?.user) {
          return persisted.state.user as User
        }
      } catch {
      }
      throw error
    }

    // 获取原始响应文本，避免JSON.parse自动转换大整数
    const rawText = await res.text()
    const wrapper = safeJsonParse(rawText)
    const responseCode = typeof wrapper?.code === 'number' ? wrapper.code : res.status

    if (!res.ok || responseCode !== 200) {
      if (res.status === 401 || res.status === 403 || responseCode === 401 || responseCode === 403) {
        return null
      }
      throw wrapper
    }

    // 数据已经通过safeJsonParse处理过，直接返回
    return wrapper?.data ?? null
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
