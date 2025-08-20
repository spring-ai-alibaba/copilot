import { create } from "zustand";
import { X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { Settings } from "../Settings";

interface LimitModalStore {
  isVisible: boolean;
  type: "login" | "limit";
  openModal: (type: "login" | "limit") => void;
  closeModal: () => void;
}

// Create global state management
export const useLimitModalStore = create<LimitModalStore>((set) => ({
  isVisible: false,
  type: "login",
  openModal: (type) => set({ isVisible: true, type }),
  closeModal: () => set({ isVisible: false }),
}));

// Global Modal component
export const GlobalLimitModal = ({ onLogin }: { onLogin: () => void }) => {
  const { t } = useTranslation();

  const { isVisible, type, closeModal } = useLimitModalStore();

  const [openSetting, setOpenSetting] = useState(false);

  if (!isVisible) return null;

  return (
    <>
      <Settings
        isOpen={openSetting}
        onClose={() => {
          closeModal();
          setOpenSetting(false);
        }}
        initialTab={"Quota"}
      />
      {!openSetting && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-[9999]">
          <div className="bg-[#1c1c1c] rounded-lg w-[400px] relative">
            <button
              onClick={closeModal}
              className="absolute right-4 top-4 text-gray-400 hover:text-gray-200 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="p-6">
              <h3 className="text-xl font-medium text-white mb-2">
                {type === "login"
                  ? t("login.chat_limit_reached")
                  : t("login.usage_limit_reached")}
              </h3>
              <p className="text-gray-400 text-sm mb-6">
                {type === "login"
                  ? t("login.chat_limit_reached_tips")
                  : t("login.usage_limit_reached_tips")}
              </p>

              <div className="flex gap-3">
                {type === "limit" ? (
                  <button
                    onClick={() => {
                      setOpenSetting(true);
                    }}
                    className="flex-1 bg-blue-500 hover:bg-blue-600 text-white rounded-lg py-2 text-sm font-medium transition-colors"
                  >
                    {t("common.open_directory_quota")}
                  </button>
                ) : (
                  <button
                    onClick={() => {
                      onLogin();
                      closeModal();
                    }}
                    className="flex-1 bg-blue-500 hover:bg-blue-600 text-white rounded-lg py-2 text-sm font-medium transition-colors"
                  >
                    {t("common.login")}
                  </button>
                )}
                <button
                  onClick={closeModal}
                  className="flex-1 bg-[#333] hover:bg-[#444] text-white rounded-lg py-2 text-sm font-medium transition-colors"
                >
                  {t("common.close")}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
