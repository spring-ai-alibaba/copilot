import { useState, useEffect, Dispatch, SetStateAction } from "react"
import { motion } from "framer-motion"
import {
  FaWeixin,
  FaEnvelope,
  FaLock,
  FaGithub,
  FaCode,
  FaSpinner,
} from "react-icons/fa6"
import { authService } from "../../api/auth"
import { toast } from "react-hot-toast"
import useUserStore from "../../stores/userSlice"
import { useTranslation } from "react-i18next"
import { TabType } from "."


type LoginFormProps = {
  onSuccess?: () => void
  onTabChange: Dispatch<SetStateAction<TabType>>
}

const LoginForm = ({ onSuccess, onTabChange }: LoginFormProps) => {
  const [loginMethod, setLoginMethod] = useState<"email" | "github" | "wechat">(
    "email"
  )
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [loading, setLoading] = useState(false)
  const [oauthLoading, setOauthLoading] = useState(false)
  const [error, setError] = useState("")
  const { setUser, setToken, setRememberMe, fetchUser } = useUserStore()
  const [rememberMe, setRememberMeState] = useState(true)
  const { t } = useTranslation()

  useEffect(() => {
    const handleLoginCallback = async (data: { token: string | undefined }) => {
      const token = typeof data === "object" ? data.token : data

      if (token) {
        setToken(token)
        const user = await authService.getUserInfo(token)
        setUser(user)
        toast.success("success login")
        onSuccess?.()
      } else {
      }
    }

    if (window.electron?.ipcRenderer) {
      window.electron.ipcRenderer.on("login:callback", handleLoginCallback)
    } else {
      console.warn("electron.ipcRenderer unavailable")
    }

    return () => {
      if (window.electron?.ipcRenderer) {
        window.electron.ipcRenderer.removeListener(
          "login:callback",
          handleLoginCallback
        )
      }
    };
  }, [setToken, onSuccess]);


  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    setLoading(true)
    try {
      const response = await fetch(
        `${process.env.APP_BASE_URL}/api/auth/login`,
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ email, password, language: localStorage.getItem('language')  }),
        }
      )

      const data = await response.json()
       if(data.code !== 200){
        throw new Error(data.message || "Login failed")
      }
      if(data.status && data.status !== 200){
        throw new Error(data.message || "Login failed")
      }

      setToken(data.token)

      // TODO 这里最好加一个loading
      // 拿到了 token 之后，再去fetchUser
      const user = await fetchUser()

      if (!response.ok) {
        throw new Error(data.error || "Login failed")
      }

      // Set remember me state
      setRememberMe(rememberMe)

      // After successful login, use login action to set user info and token at once
      setUser(user)

      toast.success("Login successful!")
      onSuccess?.()
    } catch (err) {
      
      setError(t(`login.${err.message}`) || "Login failed")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="text-center space-y-2 mb-8">
        <div className="flex items-center justify-center gap-3">
          <div className="w-12 h-12 bg-gradient-to-br from-blue-500 to-purple-500 rounded-xl flex items-center justify-center">
            <FaCode className="text-2xl text-white" />
          </div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-500 to-purple-500 bg-clip-text text-transparent">
            Alibaba Copilot
          </h1>
        </div>
        <p className="text-[#666]">{t("login.AI_powered_development_platform")}</p>
      </div>


      {loginMethod === "email" && (
        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="text-red-500 text-sm text-center">{error}</div>
          )}
          <div className="relative group">
            <FaEnvelope className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
            <input
              type="email"
              placeholder="Email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
                focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
                transition-all duration-300"
            />
          </div>
          <div className="relative group">
            <FaLock className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
            <input
              type="password"
              placeholder="Password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
                focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
                transition-all duration-300"
            />
          </div>
          <div className="flex items-center justify-between text-sm">
            <label className="flex items-center gap-2 text-[#666] hover:text-[#888] cursor-pointer">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMeState(e.target.checked)}
                className="rounded-md border-[#333] bg-[#222] text-[#3B82F6] focus:ring-[#3B82F6]"
              />
              {t("login.remember_me")}
            </label>
            {/* 没有邮箱验证码，暂时先注释，目前功能是修改密码（没问题） */}
            {/* <button onClick={() => onTabChange("forgot")} className="text-[#666] hover:text-[#3B82F6] transition-colors">
              {t("login.forgot_password")}
            </button> */}
          </div>
          <motion.button
            className="w-full bg-gradient-to-r from-[#3B82F6] to-[#2563EB] hover:from-[#2563EB] hover:to-[#1D4ED8]
              text-white rounded-xl py-3.5 font-medium transition-all duration-300 shadow-lg shadow-blue-500/20"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="submit"
            disabled={loading}
          >
            {loading ? t("login.signing_in") : t("login.sign_in")}
          </motion.button>
        </form>
      )}

      <div className="text-center text-sm text-[#666]">
        <p>{t("login.By_signing_in_you_agree_to_our")}</p>
        <div className="space-x-2">
          <a href="#" className="text-[#3B82F6] hover:underline">
            {t("login.terms_of_service")}
          </a>
          <span>{t("login.and")}</span>
          <a href="#" className="text-[#3B82F6] hover:underline">
            {t("login.privacy_policy")}
          </a>
        </div>
        <div className="mt-4">
          <span>{t("login.need_an_account")}? </span>
          <button
            onClick={() => onTabChange("register")}
            className="text-[#3B82F6] hover:underline"
          >
            {t("login.register")}
          </button>
        </div>
      </div>
    </div>
  )
}

export default LoginForm
