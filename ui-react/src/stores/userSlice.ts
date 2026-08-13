import {authService} from "@/api/auth"
import {create} from "zustand"
import {persist} from "zustand/middleware"
import { apiUrl } from "@/api/base"
import { useConversationStore } from "@/stores/conversationSlice"
import { useContextStore } from "@/stores/contextSlice"
import { safeJsonParse, safeJsonStringify } from "@/utils/safeJsonParse"


export interface User {
  id: string
  userId?: string
  username: string
  error?: any
  email: string
  githubId: string
  wechatId: string
  avatar?: string
  userType: string
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
  replaceSession: (user: User, token: string) => void
  logout: () => void
  updateUser: (userData: Partial<User>) => void
  openLoginModal: () => void
  closeLoginModal: () => void
  fetchUser: (tokenOverride?: string) => Promise<User | undefined>
  isLoading: boolean
}

const clearUserScopedState = () => {
  useConversationStore.getState().clearAll()
  useContextStore.getState().clearAll()
}

const userIdentity = (user: User | null | undefined) => user?.id || user?.userId
let fetchUserRequestSequence = 0

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
        fetchUserRequestSequence++
        const currentUser = get().user
        const identityChanged = Boolean(
          currentUser && user && userIdentity(currentUser) !== userIdentity(user),
        )
        if (user) {
          localStorage.setItem("user", JSON.stringify(user))
        } else {
          localStorage.removeItem("user")
          localStorage.removeItem("token")
          clearUserScopedState()
        }

        if (identityChanged) {
          localStorage.removeItem("token")
          clearUserScopedState()
        }

        set(() => ({
          user,
          token: !user || identityChanged ? null : get().token,
          isAuthenticated: Boolean(user && !identityChanged && get().token),
        }))
      },

      setToken: (token) => {
        fetchUserRequestSequence++
        const tokenChanged = Boolean(get().token && token && get().token !== token)
        const invalidatesSession = !token || tokenChanged
        if (token) {
          localStorage.setItem("token", token)
        } else {
          localStorage.removeItem("token")
          localStorage.removeItem("user")
          clearUserScopedState()
        }
        if (tokenChanged) {
          localStorage.removeItem("user")
          clearUserScopedState()
        }
        set(() => ({
          token,
          user: invalidatesSession ? null : get().user,
          isAuthenticated: invalidatesSession ? false : get().isAuthenticated,
        }))
      },

      fetchUser: async (tokenOverride) => {
        const token = tokenOverride || localStorage.getItem("token") || undefined
        if (!token) return undefined
        const requestId = ++fetchUserRequestSequence
        set(() => ({ isLoading: true }))
        try {
          const user = await authService.getUserInfo(token)
          if (requestId !== fetchUserRequestSequence) return undefined
          console.log('Fetched user info:', user)
          if (!user) {
            // tokenOverride is only a login candidate until replaceSession commits it. A failed
            // candidate must not log out the currently authenticated account.
            if (token !== get().token) return undefined
            localStorage.removeItem("user")
            localStorage.removeItem("token")
            localStorage.removeItem("rememberMe")
            localStorage.removeItem("user-storage")
            document.cookie =
            "token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; secure=true;";
            clearUserScopedState()
            set(() => ({
              user: null,
              token: null,
              isAuthenticated: false,
              rememberMe: false,
              isLoading: false,
            }))
            void fetch(apiUrl('/auth/logout'), {
                method: "POST",
                headers: {
                  "Content-Type": "application/json",
                  Authorization: `Bearer ${token}`,
                },
              }).catch(() => {})
            return undefined
          }
          // 处理字段映射：后端返回 userId，前端期望 id
          const userWithMappedFields: User = {
            ...user,
            id: user.id || user.userId,
            userType: user.userType || 'sys_user'
          };
          get().replaceSession(userWithMappedFields, token)
          return userWithMappedFields
        } catch (error) {
          if (requestId === fetchUserRequestSequence) console.error(error)
          return undefined
        } finally {
          if (requestId === fetchUserRequestSequence) {
            set(() => ({ isLoading: false }))
          }
        }
      },

      replaceSession: (user, token) => {
        fetchUserRequestSequence++
        const currentUser = get().user
        if (!get().isAuthenticated || userIdentity(currentUser) !== userIdentity(user)) {
          clearUserScopedState()
        }
        localStorage.setItem("user", JSON.stringify(user))
        localStorage.setItem("token", token)

        set(() => ({
          user,
          token,
          isAuthenticated: true,
          isLoginModalOpen: false,
          isLoading: false,
        }))
      },

      login: (user, token) => {
        get().replaceSession(user, token)
      },

      logout: () => {
        fetchUserRequestSequence++
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
        clearUserScopedState()
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
