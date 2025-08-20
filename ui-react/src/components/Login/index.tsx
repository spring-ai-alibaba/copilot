import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import LoginForm from "./LoginForm";
import RegisterForm from "./RegisterForm";
import useUserStore from "../../stores/userSlice";
import ForgotPassword from "./ForgotPassword";

export type TabType = "login" | "register" | "forgot";
type LoginProps = {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
};

const Login = ({ isOpen, onClose }: LoginProps) => {
  const [activeTab, setActiveTab] = useState<TabType>("login");
  const { closeLoginModal } = useUserStore();

  // Handle unified close logic
  const handleClose = () => {
    onClose();
    closeLoginModal();
  };

  // Handle login success
  const handleSuccess = () => {
    handleClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop - click to close */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40"
          />

          {/* Modal */}
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ duration: 0.2 }}
            onClick={() => {
              handleClose();
            }}
            className="fixed inset-0 grid place-items-center z-50"
          >
            <div
              onClick={(e) => {
                e.stopPropagation();
              }}
              className="w-full max-w-md bg-white dark:bg-[#1A1A1A] rounded-2xl p-8 shadow-2xl border border-gray-200 dark:border-[#333]"
            >
              {/* Close button - click to close modal */}
              <button
                onClick={handleClose}
                className="absolute right-4 top-4 text-gray-400 dark:text-[#666] hover:text-gray-600 dark:hover:text-[#888] transition-colors"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="h-6 w-6"
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

              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
              >
                {activeTab === "login" && (
                  <LoginForm
                    onSuccess={handleSuccess}
                    onTabChange={setActiveTab}
                  />
                )}
                {activeTab === "register" && (
                  <RegisterForm
                    onSuccess={handleSuccess}
                    onTabChange={setActiveTab}
                  />
                )}
                {activeTab === "forgot" && (
                  <ForgotPassword
                    onSuccess={handleSuccess}
                    onTabChange={setActiveTab}
                  />
                )}
              </motion.div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default Login;
