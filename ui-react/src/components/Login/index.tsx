import {useState} from "react";
import {AnimatePresence, motion} from "framer-motion";
import {X} from "lucide-react";
import {AppLogo} from "@/components/AppLogo";
import LoginForm from "./LoginForm";
import RegisterForm from "./RegisterForm";
import ForgotPassword from "./ForgotPassword";
import useUserStore from "../../stores/userSlice";
import {useTranslation} from "react-i18next";

export type TabType = "login" | "register" | "forgot";

type LoginProps = {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
};

const TAB_ORDER: Record<TabType, number> = {login: 0, register: 1, forgot: 2};

const formVariants = {
  enter: (dir: number) => ({opacity: 0, x: dir >= 0 ? 24 : -24}),
  center: {opacity: 1, x: 0},
  exit: (dir: number) => ({opacity: 0, x: dir >= 0 ? -24 : 24}),
};

const Login = ({isOpen, onClose}: LoginProps) => {
  const [activeTab, setActiveTab] = useState<TabType>("login");
  const [direction, setDirection] = useState(0);
  const {closeLoginModal} = useUserStore();
  const {t} = useTranslation();

  // Handle unified close logic
  const handleClose = () => {
    onClose();
    closeLoginModal();
  };

  // Handle login success
  const handleSuccess = () => {
    handleClose();
  };

  const changeTab = (tab: TabType) => {
    setDirection(TAB_ORDER[tab] - TAB_ORDER[activeTab]);
    setActiveTab(tab);
  };

  const tabs: Array<{key: TabType; label: string}> = [
    {key: "login", label: t("login.sign_in")},
    {key: "register", label: t("login.register")},
  ];

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop - click to close */}
          <motion.div
            initial={{opacity: 0}}
            animate={{opacity: 1}}
            exit={{opacity: 0}}
            onClick={handleClose}
            className="fixed inset-0 z-[10005] bg-black/35 backdrop-blur-sm"
          />

          {/* Modal */}
          <motion.div
            initial={{opacity: 0, scale: 0.95, y: 20}}
            animate={{opacity: 1, scale: 1, y: 0}}
            exit={{opacity: 0, scale: 0.95, y: 20}}
            transition={{duration: 0.2}}
            className="fixed inset-0 z-[10006] grid place-items-center p-4"
          >
            <div
              onClick={(e) => {
                e.stopPropagation();
              }}
              className="arc-dialog-panel relative w-full max-w-md overflow-hidden"
            >
              <button
                onClick={handleClose}
                aria-label={t("common.close")}
                className="arc-icon-button absolute right-3 top-3 z-20"
              >
                <X className="h-4 w-4" />
              </button>

              <div className="px-8 pb-7 pt-8 max-sm:px-5">
                <div className="flex flex-col items-center text-center">
                  <AppLogo className="h-11 w-11 rounded-2xl" />
                  <h1 className="mt-4 text-xl font-semibold tracking-tight text-foreground">
                    {activeTab === "forgot" ? t("login.forgot_password") : "Alibaba Copilot"}
                  </h1>
                  <p className="mt-1.5 text-xs leading-5 text-muted-foreground">
                    {t("login.AI_powered_development_platform")}
                  </p>
                </div>

                {activeTab !== "forgot" && (
                  <div className="mt-7 grid grid-cols-2 gap-1 rounded-lg bg-muted/75 p-1">
                    {tabs.map((tab) => (
                      <button
                        key={tab.key}
                        onClick={() => changeTab(tab.key)}
                        className={`relative rounded-md py-1.5 text-xs font-medium transition-colors duration-200
                          ${
                            activeTab === tab.key
                              ? "text-foreground"
                              : "text-muted-foreground hover:text-foreground"
                          }`}
                      >
                        {activeTab === tab.key && (
                          <motion.span
                            layoutId="login-tab-pill"
                            transition={{type: "spring", stiffness: 500, damping: 38}}
                            className="absolute inset-0 rounded-md bg-background shadow-sm"
                          />
                        )}
                        <span className="relative">{tab.label}</span>
                      </button>
                    ))}
                  </div>
                )}

                {/* Forms */}
                <AnimatePresence mode="wait" custom={direction} initial={false}>
                  <motion.div
                    key={activeTab}
                    custom={direction}
                    variants={formVariants}
                    initial="enter"
                    animate="center"
                    exit="exit"
                    transition={{duration: 0.22, ease: "easeOut"}}
                    className="mt-6"
                  >
                    {activeTab === "login" && (
                      <LoginForm onSuccess={handleSuccess} onTabChange={changeTab} />
                    )}
                    {activeTab === "register" && (
                      <RegisterForm onSuccess={handleSuccess} onTabChange={changeTab} />
                    )}
                    {activeTab === "forgot" && (
                      <ForgotPassword onSuccess={handleSuccess} onTabChange={changeTab} />
                    )}
                  </motion.div>
                </AnimatePresence>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default Login;
