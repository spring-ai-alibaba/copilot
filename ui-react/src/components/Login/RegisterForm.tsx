import { motion } from "framer-motion"
import { FaUser, FaEnvelope, FaLock } from "react-icons/fa6"
import { authService } from "../../api/auth"
import { Dispatch, SetStateAction, useState } from "react"
import { toast } from "react-hot-toast"
import { useTranslation } from "react-i18next"
import { TabType } from "."


type RegisterFormProps = {
  onSuccess?: () => void
  onTabChange: Dispatch<SetStateAction<TabType>>
}

const RegisterForm = ({ onSuccess, onTabChange }: RegisterFormProps) => {
  const [formData, setFormData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [isRegistered, setIsRegistered] = useState(false)
  const { t } = useTranslation()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match")
      return
    }

    setError("")
    setLoading(true)

    try {
      const result = await authService.register(
        formData.username,
        formData.email,
        formData.password
      )

      if (result.code === 200) {
        setIsRegistered(true)
        toast.success("Registration successful!")
      } else {
        setError(result.message)
      }
    } catch (err) {
      setError(err.error || "Registration failed")
    } finally {
      setLoading(false)
    }
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData((prev) => ({
      ...prev,
      [e.target.name]: e.target.value,
    }))
  }

  if (isRegistered) {
    return (
      <div className="space-y-6 text-center">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="space-y-4"
        >
          <div className="mb-4 text-5xl text-green-500">✓</div>
          <h2 className="text-2xl font-semibold text-white">
            {t("register.register_success")}
          </h2>
          <p className="text-[#666]">
            {t("register.register_success_account")}
          </p>
          <motion.button
            className="w-full bg-gradient-to-r from-[#3B82F6] to-[#2563EB] hover:from-[#2563EB] hover:to-[#1D4ED8]
              text-white rounded-xl py-3.5 font-medium transition-all duration-300 shadow-lg shadow-blue-500/20"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => onTabChange("login")}
          >
            {t("register.process_login")}
          </motion.button>
        </motion.div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="mb-8 space-y-2 text-center">
        <p className="text-gray-600 dark:text-[#666]">{t("register.create_account")}</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="text-sm text-center text-red-500">{error}</div>
        )}
        <div className="relative group">
          <FaUser className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
          <input
            name="username"
            value={formData.username}
            onChange={handleChange}
            type="text"
            placeholder="Username"
            required
            className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
              focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
              transition-all duration-300"
          />
        </div>
        <div className="relative group">
          <FaEnvelope className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
          <input
            name="email"
            value={formData.email}
            onChange={handleChange}
            type="email"
            placeholder="Email"
            required
            className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
              focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
              transition-all duration-300"
          />
        </div>
        <div className="relative group">
          <FaLock className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
          <input
            name="password"
            value={formData.password}
            onChange={handleChange}
            type="password"
            placeholder="Password"
            required
            className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
              focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
              transition-all duration-300"
          />
        </div>
        <div className="relative group">
          <FaLock className="absolute left-4 top-1/2 -translate-y-1/2 text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
          <input
            name="confirmPassword"
            value={formData.confirmPassword}
            onChange={handleChange}
            type="password"
            placeholder="Confirm Password"
            required
            className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
            focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
            transition-all duration-300"
          />
        </div>
        <motion.button
          className="w-full bg-gradient-to-r from-[#3B82F6] to-[#2563EB] hover:from-[#2563EB] hover:to-[#1D4ED8]
            text-white rounded-xl py-3.5 font-medium transition-all duration-300 shadow-lg shadow-blue-500/20"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          type="submit"
          disabled={loading}
        >
          {loading
            ? t("register.creating_account")
            : t("register.create_account_button")}
        </motion.button>
      </form>

      <div className="text-center text-sm text-[#666]">
        <p>{t("register.already_account")} </p>
        <button
          onClick={() => onTabChange("login")}
          className="text-[#3B82F6] hover:underline"
        >
          {t("login.sign_in")}
        </button>
      </div>
    </div>
  )
}

export default RegisterForm
