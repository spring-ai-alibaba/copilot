import {useEffect, useState} from "react";
import {FaLock, FaUser} from "react-icons/fa6";
import {authService} from "../../api/auth";
import {toast} from "react-toastify";
import useUserStore from "../../stores/userSlice";
import {useTranslation} from "react-i18next";
import type {TabType} from ".";
import {ErrorBanner, LoginInput, PrimaryButton} from "./controls";

type LoginFormProps = {
  onSuccess?: () => void;
  onTabChange: (tab: TabType) => void;
};

const LoginForm = ({onSuccess, onTabChange}: LoginFormProps) => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const {setToken, setRememberMe, fetchUser} = useUserStore();
  const [rememberMe, setRememberMeState] = useState(true);
  const {t} = useTranslation();

  useEffect(() => {
    const handleLoginCallback = async (data: {token: string | undefined}) => {
      const token = typeof data === "object" ? data.token : data;

      if (token) {
        setToken(token);
        // 统一在登录成功后拉取一次用户完整信息（含配额等）
        try {
          await fetchUser();
        } catch {}
        toast.success("success login");
        onSuccess?.();
        // 简化方案：登录成功后整页刷新，确保所有组件按新 token 重新初始化
        setTimeout(() => {
          try {
            window.location.reload();
          } catch {}
        }, 150);
      }
    };

    if (window.electron?.ipcRenderer) {
      window.electron.ipcRenderer.on("login:callback", handleLoginCallback);
    } else {
      console.warn("electron.ipcRenderer unavailable");
    }

    return () => {
      if (window.electron?.ipcRenderer) {
        window.electron.ipcRenderer.removeListener(
          "login:callback",
          handleLoginCallback
        );
      }
    };
  }, [setToken, onSuccess]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const wrapper = await authService.login(username, password);
      if (typeof wrapper?.code === "number" && wrapper.code !== 200) {
        throw new Error(wrapper?.msg || "Login failed");
      }

      const payload = wrapper?.data ?? wrapper;
      const token = payload?.token;

      if (!token) throw new Error("Missing token");

      setToken(token);

      // 登录后主动刷新一次用户信息，保证配额等字段及时可用
      // 注意：不在登录时直接存储用户信息，避免精度丢失问题
      try {
        await fetchUser();
      } catch {}

      // 记住我
      setRememberMe(rememberMe);

      toast.success("Login successful!");
      onSuccess?.();
      // 简化方案：登录成功后整页刷新，确保所有组件按新 token 重新初始化
      setTimeout(() => {
        try {
          window.location.reload();
        } catch {}
      }, 150);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Login failed";
      setError(t(`login.${message}`, {defaultValue: message}));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <ErrorBanner message={error} />}

      <LoginInput
        icon={<FaUser />}
        type="text"
        placeholder={t("login.username")}
        autoComplete="username"
        required
        value={username}
        onChange={(e) => setUsername(e.target.value)}
      />

      <LoginInput
        icon={<FaLock />}
        type="password"
        placeholder={t("login.password")}
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />

      <div className="flex items-center justify-between text-sm">
        <label
          className="flex cursor-pointer select-none items-center gap-2 text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMeState(e.target.checked)}
            className="h-3.5 w-3.5 rounded accent-foreground"
          />
          {t("login.remember_me")}
        </label>
        {/* 没有邮箱验证码，暂时先注释，目前功能是修改密码（没问题） */}
        {/* <button type="button" onClick={() => onTabChange("forgot")}
          className="text-gray-500 transition-colors hover:text-[#3B82F6] dark:text-[#8c8c8c]">
          {t("login.forgot_password")}
        </button> */}
      </div>

      <PrimaryButton type="submit" loading={loading} loadingText={t("login.signing_in")}>
        {t("login.sign_in")}
      </PrimaryButton>

      <p className="pt-1 text-center text-[10px] leading-5 text-muted-foreground/75">
        {t("login.By_signing_in_you_agree_to_our")}{" "}
        <a href="#" className="text-foreground underline decoration-border underline-offset-2">
          {t("login.terms_of_service")}
        </a>{" "}
        {t("login.and")}{" "}
        <a href="#" className="text-foreground underline decoration-border underline-offset-2">
          {t("login.privacy_policy")}
        </a>
      </p>
    </form>
  );
};

export default LoginForm;
