import {authService} from "@/api/auth"
import {create} from "zustand"
import {persist} from "zustand/middleware"
import { apiUrl } from "@/api/base"
import { safeJsonParse, safeJsonStringify } from "@/utils/safeJsonParse"


export enum TierType {
  FREE = "free",
  PRO = "pro",
  PROMAX = "promax",
}
export interface TierMessage {
  startTime: Date
  tier: TierType
  resetTime: Date
}

export interface User {
  id: string
  username: string
  error?: any
  email: string
  githubId: string
  wechatId: string
  avatar?: string
  userType: string
  userQuota: {
    // 用户当前拥有的配额
    quota: number
    resetTime: Date
    tierType: TierType
    // 加油包的配额
    refillQuota: number
    // 该周期的额度
    usedQuota: number
    quotaTotal: number

  }
}

interface UserState {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  rememberMe: boolean
  isLoginModalOpen: boolean
  setRememberMe: (remember: boolean) => void
  setUser: (user: User | null) => void
  setToken: (token: string | null) => void
  login: (user: User, token: string) => void
  logout: () => void
  updateUser: (userData: Partial<User>) => void
  openLoginModal: () => void
  closeLoginModal: () => void
  fetchUser: () => Promise<User>
  isLoading: boolean
}

const useUserStore = create<UserState>()(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      rememberMe: false,
      isLoginModalOpen: false,
      isLoading: false,

      setRememberMe: (remember) => {
        localStorage.setItem("rememberMe", remember.toString())
        set({ rememberMe: remember })
      },

      setUser: (user) => {
        if (user) {
          localStorage.setItem("user", JSON.stringify(user))
        } else {
          localStorage.removeItem("user")
        }

        set(() => ({
          user,
          isAuthenticated: !!user,
        }))
      },

      setToken: (token) => {
        if (token) {
          localStorage.setItem("token", token)
        } else {
          localStorage.removeItem("token")
        }
        set(() => ({ token }))
      },

      fetchUser: async () => {
        set(() => ({ isLoading: true }))
        try {
          const token = localStorage.getItem("token")
          if (token) {
            const user = await authService.getUserInfo(token)
            console.log('Fetched user info:', user)
            if (!user) {
              localStorage.removeItem("user")
              localStorage.removeItem("token")
              localStorage.removeItem("rememberMe")
              localStorage.removeItem("user-storage")
              try {
                await fetch(apiUrl('/auth/logout'), {
                  method: "POST",
                  headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                  },
                })
              } catch {}
              document.cookie =
              "token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; secure=true;";
              set(() => ({
                user: null,
                token: null,
                isAuthenticated: false,
                rememberMe: false,
              }))
            } else {
              // 处理字段映射：后端返回 userId，前端期望 id
              const userWithMappedFields = {
                ...user,
                id: user.id || user.userId, // 如果没有 id，用 userId 代替
                userType: user.userType || 'sys_user' // 默认设置为 sys_user
              };
              get().setUser(userWithMappedFields)
            }
            return user
          }
        } catch (error) {
          console.error(error)
        } finally {
          set(() => ({ isLoading: false }))
        }
      },

      login: (user, token) => {
        localStorage.setItem("user", JSON.stringify(user))
        localStorage.setItem("token", token)

        set(() => ({
          user,
          token,
          isAuthenticated: true,
          isLoginModalOpen: false,
        }))
      },

      logout: () => {
        const token = localStorage.getItem("token")
        if (!window.electron) {
          document.cookie =
            "token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/;"
          if (process.env.NODE_ENV === "production") {
            document.cookie =
              "token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; secure=true;"
          }
          if (token) {
            fetch(apiUrl('/auth/logout'), {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${token}`,
                satoken: token,
              },
            })
          }
        }
        localStorage.removeItem("user")
        localStorage.removeItem("token")
        localStorage.removeItem("rememberMe")
        localStorage.removeItem("user-storage")
        set(() => ({
          user: null,
          token: null,
          isAuthenticated: false,
          rememberMe: false,
        }))
      },

      updateUser: (userData) =>
        set((state) => {
          const newUser = state.user ? { ...state.user, ...userData } : null
          localStorage.setItem("user", safeJsonStringify(newUser))

          return { user: newUser }
        }),

      openLoginModal: () =>
        set(() => ({
          isLoginModalOpen: true,
        })),

      closeLoginModal: () =>
        set(() => ({
          isLoginModalOpen: false,
        })),
    }),
    {
      name: "user-storage",
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
        rememberMe: state.rememberMe,
      }),
      version: 1,
      storage: {
        getItem: (name) => {
          const value = localStorage.getItem(name)
          return value ? safeJsonParse(value) : null
        },
        setItem: (name, value) => {
          localStorage.setItem(name, safeJsonStringify(value))
        },
        removeItem: (name) => {
          localStorage.removeItem(name)
        },
      },
      onRehydrateStorage: () => (state) => {
        const rememberMe = localStorage.getItem("rememberMe") === "true"
        if (rememberMe) {
          const storedUser = localStorage.getItem("user")
          const storedToken = localStorage.getItem("token")
          if (storedUser && storedToken) {
            state?.setUser(safeJsonParse(storedUser))
            state?.setToken(storedToken)
            state?.setRememberMe(true)
          }
        }
      },
    }
  )
)

export default useUserStore
