import {motion} from "framer-motion";
import {FaEnvelope, FaLock, FaUser} from "react-icons/fa6";
import {authService} from "../../api/auth";
import {useState} from "react";
import {toast} from "react-toastify";
import {useTranslation} from "react-i18next";
import type {TabType} from ".";
import {ErrorBanner, LoginInput, PrimaryButton} from "./controls";

type RegisterFormProps = {
  onSuccess?: () => void;
  onTabChange: (tab: TabType) => void;
};

const RegisterForm = ({onSuccess, onTabChange}: RegisterFormProps) => {
  const [formData, setFormData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [isRegistered, setIsRegistered] = useState(false);
  const {t} = useTranslation();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.password !== formData.confirmPassword) {
      setError(t("register.passwords_not_match"));
      return;
    }

    setError("");
    setLoading(true);

    try {
      const result = await authService.register(
        formData.username,
        formData.email,
        formData.password
      );

      if (result.code === 200) {
        setIsRegistered(true);
        toast.success("Registration successful!");
      } else {
        setError(result.msg || result.message || "Registration failed");
      }
    } catch (err) {
      const anyErr = err as any;
      setError(
        anyErr?.msg || anyErr?.message || anyErr?.error || "Registration failed"
      );
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData((prev) => ({
      ...prev,
      [e.target.name]: e.target.value,
    }));
  };

  if (isRegistered) {
    return (
      <motion.div
        initial={{opacity: 0, y: 12}}
        animate={{opacity: 1, y: 0}}
        className="space-y-4 py-2 text-center"
      >
        <motion.div
          initial={{scale: 0.5, opacity: 0}}
          animate={{scale: 1, opacity: 1}}
          transition={{type: "spring", stiffness: 400, damping: 22}}
          className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-500/10 text-2xl text-emerald-500"
        >
          ✓
        </motion.div>
        <h2 className="text-xl font-semibold text-foreground">
          {t("register.register_success")}
        </h2>
        <p className="text-sm text-muted-foreground">
          {t("register.register_success_account")}
        </p>
        <PrimaryButton onClick={() => onTabChange("login")}>
          {t("register.process_login")}
        </PrimaryButton>
      </motion.div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <ErrorBanner message={error} />}

      <LoginInput
        icon={<FaUser />}
        name="username"
        value={formData.username}
        onChange={handleChange}
        type="text"
        placeholder={t("login.username")}
        autoComplete="username"
        required
      />

      <LoginInput
        icon={<FaEnvelope />}
        name="email"
        value={formData.email}
        onChange={handleChange}
        type="email"
        placeholder={t("login.email")}
        autoComplete="email"
        required
      />

      <LoginInput
        icon={<FaLock />}
        name="password"
        value={formData.password}
        onChange={handleChange}
        type="password"
        placeholder={t("login.password")}
        autoComplete="new-password"
        required
      />

      <LoginInput
        icon={<FaLock />}
        name="confirmPassword"
        value={formData.confirmPassword}
        onChange={handleChange}
        type="password"
        placeholder={t("register.confirm_password")}
        autoComplete="new-password"
        required
      />

      <PrimaryButton
        type="submit"
        loading={loading}
        loadingText={t("register.creating_account")}
      >
        {t("register.create_account_button")}
      </PrimaryButton>

      <p className="pt-1 text-center text-xs text-muted-foreground">
        {t("register.already_account")}{" "}
        <button
          type="button"
          onClick={() => onTabChange("login")}
          className="text-foreground underline decoration-border underline-offset-2"
        >
          {t("login.sign_in")}
        </button>
      </p>
    </form>
  );
};

export default RegisterForm;
