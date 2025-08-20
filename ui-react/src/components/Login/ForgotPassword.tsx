import { useState } from "react"
import { motion } from "framer-motion"
import { FaEnvelope, FaLock, FaSpinner } from "react-icons/fa6"
import { toast } from "react-hot-toast"
import { useTranslation } from "react-i18next"
import { authService } from "../../api/auth"
import type { TabType } from "./index"

type ForgotPasswordProps = {
  onSuccess: () => void;
  onTabChange: (tab: TabType) => void;
}

const ForgotPassword = ({ onSuccess, onTabChange }: ForgotPasswordProps) => {
  const [email, setEmail] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [oldPassword, setOldPassword] = useState("")
  const [loading, setLoading] = useState(false)
  const { t } = useTranslation()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)

    try {
      await authService.updatePassword(email, oldPassword, newPassword)
      toast.success(t("forgotPassword.success"))
      onTabChange("login")
    //   onSuccess()
    } catch (error) {
      toast.error(t("forgotPassword.error.updateFailed"))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="text-center space-y-2">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
          {t("forgotPassword.title")}
        </h2>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="relative group">
          <FaEnvelope className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
          <input
            type="email"
            placeholder={t("forgotPassword.emailPlaceholder")}
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
            placeholder={t("forgotPassword.oldPasswordPlaceholder")}
            required
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
            className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
              focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
              transition-all duration-300"
          />
        </div>
        <div className="relative group">
          <FaLock className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-[#666] transition-colors group-focus-within:text-[#3B82F6]" />
          <input
            type="password"
            placeholder={t("forgotPassword.newPasswordPlaceholder")}
            required
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className="w-full bg-white dark:bg-[#222] border border-gray-200 dark:border-[#333] rounded-xl py-3.5 px-11 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-[#666]
              focus:outline-none focus:border-[#3B82F6] focus:bg-gray-50 dark:focus:bg-[#1A1A1A] focus:ring-1 focus:ring-[#3B82F6]
              transition-all duration-300"
          />
        </div>

        <div className="space-y-3">
          <motion.button
            className="w-full bg-gradient-to-r from-[#3B82F6] to-[#2563EB] hover:from-[#2563EB] hover:to-[#1D4ED8]
              text-white rounded-xl py-3.5 font-medium transition-all duration-300 shadow-lg shadow-blue-500/20"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            type="submit"
            disabled={loading}
          >
            {loading ? (
              <FaSpinner className="animate-spin mx-auto" />
            ) : (
              t("forgotPassword.submit")
            )}
          </motion.button>
          <button
            type="button"
            onClick={() => onTabChange("login")}
            className="w-full text-[#666] hover:text-[#888] transition-colors"
          >
            {t("forgotPassword.backToLogin")}
          </button>
        </div>
      </form>
    </div>
  )
}

export default ForgotPassword 