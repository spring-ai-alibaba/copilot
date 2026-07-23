import {useState} from "react";
import {FaEnvelope, FaLock} from "react-icons/fa6";
import {toast} from "react-hot-toast";
import {useTranslation} from "react-i18next";
import {authService} from "../../api/auth";
import type {TabType} from "./index";
import {LoginInput, PrimaryButton} from "./controls";

type ForgotPasswordProps = {
  onSuccess: () => void;
  onTabChange: (tab: TabType) => void;
};

const ForgotPassword = ({onTabChange}: ForgotPasswordProps) => {
  const [email, setEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [oldPassword, setOldPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const {t} = useTranslation();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      await authService.updatePassword(email, oldPassword, newPassword);
      toast.success(t("forgotPassword.success"));
      onTabChange("login");
    } catch (error) {
      toast.error(t("forgotPassword.error.updateFailed"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h2 className="mb-4 text-center text-base font-semibold text-gray-900 dark:text-white">
        {t("forgotPassword.title")}
      </h2>

      <form onSubmit={handleSubmit} className="space-y-4">
        <LoginInput
          icon={<FaEnvelope />}
          type="email"
          placeholder={t("forgotPassword.emailPlaceholder")}
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <LoginInput
          icon={<FaLock />}
          type="password"
          placeholder={t("forgotPassword.oldPasswordPlaceholder")}
          autoComplete="current-password"
          required
          value={oldPassword}
          onChange={(e) => setOldPassword(e.target.value)}
        />

        <LoginInput
          icon={<FaLock />}
          type="password"
          placeholder={t("forgotPassword.newPasswordPlaceholder")}
          autoComplete="new-password"
          required
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />

        <div className="space-y-3 pt-1">
          <PrimaryButton
            type="submit"
            loading={loading}
            loadingText={t("forgotPassword.submitting")}
          >
            {t("forgotPassword.submit")}
          </PrimaryButton>
          <button
            type="button"
            onClick={() => onTabChange("login")}
            className="w-full text-sm text-gray-500 transition-colors
              hover:text-gray-900 dark:text-[#8c8c8c] dark:hover:text-white"
          >
            {t("forgotPassword.backToLogin")}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ForgotPassword;
