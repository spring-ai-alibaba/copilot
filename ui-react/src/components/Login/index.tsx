import {useState} from "react";
import {AnimatePresence, motion} from "framer-motion";
import {FaCode} from "react-icons/fa6";
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
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
          />

          {/* Modal */}
          <motion.div
            initial={{opacity: 0, scale: 0.95, y: 20}}
            animate={{opacity: 1, scale: 1, y: 0}}
            exit={{opacity: 0, scale: 0.95, y: 20}}
            transition={{duration: 0.2}}
            className="fixed inset-0 z-50 grid place-items-center p-4"
          >
            <div
              onClick={(e) => {
                e.stopPropagation();
              }}
              className="relative w-full max-w-md overflow-hidden rounded-lg border
                border-gray-200 bg-white shadow-2xl
                dark:border-[#333333] dark:bg-[#18181a]"
            >
              {/* 品牌色渐变描边，与 Logo / Header 的蓝紫渐变一致 */}
              <div className="h-0.5 bg-gradient-to-r from-blue-500 to-purple-500" />

              {/* Close button */}
              <button
                onClick={handleClose}
                aria-label={t("common.close")}
                className="absolute right-3 top-3.5 text-gray-400 transition-colors
                  hover:text-gray-900 dark:text-[#666] dark:hover:text-white"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="h-5 w-5"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>

              <div className="px-8 pb-7 pt-9">
                {/* Brand */}
                <div className="flex flex-col items-center text-center">
                  <div
                    className="flex h-12 w-12 items-center justify-center rounded-lg
                      bg-gradient-to-br from-blue-500 to-purple-500 shadow-lg shadow-blue-500/25"
                  >
                    <FaCode className="text-xl text-white" />
                  </div>
                  <h1
                    className="mt-4 bg-gradient-to-r from-purple-500 to-purple-600 bg-clip-text
                      text-[22px] font-bold text-transparent
                      dark:from-blue-500 dark:to-purple-500"
                  >
                    Alibaba Copilot
                  </h1>
                  <p className="mt-1.5 text-sm text-gray-500 dark:text-[#8c8c8c]">
                    {t("login.AI_powered_development_platform")}
                  </p>
                </div>

                {/* 登录 / 注册 分段切换（与 Settings 侧栏选中态同色） */}
                {activeTab !== "forgot" && (
                  <div
                    className="mt-7 grid grid-cols-2 gap-1 rounded-lg bg-gray-100 p-1
                      dark:bg-[#232324]"
                  >
                    {tabs.map((tab) => (
                      <button
                        key={tab.key}
                        onClick={() => changeTab(tab.key)}
                        className={`relative rounded-md py-1.5 text-sm transition-colors duration-200
                          ${
                            activeTab === tab.key
                              ? "text-gray-900 dark:text-white"
                              : "text-gray-500 hover:text-gray-700 dark:text-[#8c8c8c] dark:hover:text-[#bbb]"
                          }`}
                      >
                        {activeTab === tab.key && (
                          <motion.span
                            layoutId="login-tab-pill"
                            transition={{type: "spring", stiffness: 500, damping: 38}}
                            className="absolute inset-0 rounded-md bg-white shadow-sm
                              dark:bg-[#333333]"
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
