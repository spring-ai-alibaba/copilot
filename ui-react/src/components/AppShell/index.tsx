import { lazy, Suspense, useEffect, useState } from "react";
import { ToastContainer } from "react-toastify";
import { useTranslation } from "react-i18next";
import "react-toastify/dist/ReactToastify.css";
import AiChat from "@/components/AiChat";
import { Loading } from "@/components/loading";
import TopViewContainer from "@/components/TopView";
import { GlobalLimitModal } from "@/components/UserModal";
import useInit from "@/hooks/useInit";
import useChatModeStore from "@/stores/chatModeSlice";
import useUserStore from "@/stores/userSlice";
import useWorkspaceStore from "@/stores/workspaceSlice";
import { ChatMode } from "@/types/chat";
import type { SettingsTab } from "@/components/Settings";
import { ChatHeader } from "./ChatHeader";
import { NavigationSidebar } from "./NavigationSidebar";

const Login = lazy(() => import("@/components/Login"));
const Settings = lazy(() =>
  import("@/components/Settings").then((module) => ({
    default: module.Settings,
  })),
);
const WorkbenchDock = lazy(() =>
  import("./WorkbenchDock").then((module) => ({
    default: module.WorkbenchDock,
  })),
);

function readBoolean(key: string, fallback: boolean) {
  const value = localStorage.getItem(key);
  if (value === null) return fallback;
  return value === "true";
}

export function AppShell() {
  const { t } = useTranslation();
  const { mode, initOpen } = useChatModeStore();
  const {
    isLoginModalOpen,
    closeLoginModal,
    openLoginModal,
    user,
    isAuthenticated,
  } = useUserStore();
  const { fetchWorkspaceFiles } = useWorkspaceStore();
  const { isDarkMode } = useInit();
  const [sidebarOpen, setSidebarOpen] = useState(() =>
    readBoolean("arc-sidebar-open", window.innerWidth >= 1024),
  );
  const [dockOpen, setDockOpen] = useState(() =>
    readBoolean("arc-workbench-open", window.innerWidth >= 1280),
  );
  const [dockWidth, setDockWidth] = useState(() => {
    const stored = Number(localStorage.getItem("arc-workbench-width"));
    return Number.isFinite(stored) && stored >= 360 && stored <= 720
      ? stored
      : 520;
  });
  const [settingsState, setSettingsState] = useState<{
    open: boolean;
    tab: SettingsTab;
  }>({ open: false, tab: "General" });
  const [settingsMounted, setSettingsMounted] = useState(false);
  const [loginMounted, setLoginMounted] = useState(isLoginModalOpen);

  const dockAvailable = mode === ChatMode.Builder && !initOpen;
  const [dockMounted, setDockMounted] = useState(
    () => dockAvailable && dockOpen,
  );

  useEffect(() => {
    if (isAuthenticated && user) {
      void fetchWorkspaceFiles();
    }
  }, [fetchWorkspaceFiles, isAuthenticated, user]);

  useEffect(() => {
    if (dockAvailable && dockOpen) setDockMounted(true);
  }, [dockAvailable, dockOpen]);

  useEffect(() => {
    if (isLoginModalOpen) setLoginMounted(true);
  }, [isLoginModalOpen]);

  const updateSidebarOpen = (open: boolean) => {
    setSidebarOpen(open);
    localStorage.setItem("arc-sidebar-open", String(open));
    if (open && window.innerWidth < 1100) {
      setDockOpen(false);
      localStorage.setItem("arc-workbench-open", "false");
    }
  };

  const updateDockOpen = (open: boolean) => {
    setDockOpen(open);
    localStorage.setItem("arc-workbench-open", String(open));
    if (open && window.innerWidth < 1100) {
      setSidebarOpen(false);
      localStorage.setItem("arc-sidebar-open", "false");
    }
  };

  const updateDockWidth = (width: number) => {
    setDockWidth(width);
    localStorage.setItem("arc-workbench-width", String(width));
  };

  const openSettings = (tab: SettingsTab = "General") => {
    setSettingsMounted(true);
    setSettingsState({ open: true, tab });
  };

  return (
    <TopViewContainer>
      <GlobalLimitModal onLogin={openLoginModal} />
      {loginMounted ? (
        <Suspense fallback={null}>
          <Login isOpen={isLoginModalOpen} onClose={closeLoginModal} />
        </Suspense>
      ) : null}

      <div className="relative flex h-screen w-screen overflow-hidden bg-background text-foreground">
        {sidebarOpen ? (
          <button
            type="button"
            className="fixed inset-0 z-40 bg-black/25 backdrop-blur-[1px] lg:hidden"
            aria-label={t("appShell.sidebar.close", {
              defaultValue: "关闭侧栏",
            })}
            onClick={() => updateSidebarOpen(false)}
          />
        ) : null}

        <NavigationSidebar
          open={sidebarOpen}
          onClose={() => updateSidebarOpen(false)}
          onOpenSettings={openSettings}
        />

        <main className="relative flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden bg-background">
          <ChatHeader
            sidebarOpen={sidebarOpen}
            dockOpen={dockAvailable && dockOpen}
            dockAvailable={dockAvailable}
            onOpenSidebar={() => updateSidebarOpen(true)}
            onToggleDock={() => updateDockOpen(!dockOpen)}
            onOpenSettings={() => openSettings()}
          />
          <div className="relative min-h-0 min-w-0 flex-1 overflow-hidden">
            <AiChat />
          </div>
        </main>

        {dockMounted ? (
          <Suspense fallback={null}>
            <WorkbenchDock
              open={dockAvailable && dockOpen}
              width={dockWidth}
              onWidthChange={updateDockWidth}
              onClose={() => updateDockOpen(false)}
            />
          </Suspense>
        ) : null}
      </div>

      {settingsMounted ? (
        <Suspense fallback={null}>
          <Settings
            isOpen={settingsState.open}
            onClose={() =>
              setSettingsState((current) => ({ ...current, open: false }))
            }
            initialTab={settingsState.tab}
          />
        </Suspense>
      ) : null}

      <ToastContainer
        position="top-center"
        autoClose={2600}
        hideProgressBar
        newestOnTop
        closeOnClick
        pauseOnFocusLoss
        draggable
        pauseOnHover
        theme={isDarkMode ? "dark" : "light"}
        toastClassName="!rounded-xl !border !border-border !bg-popover !text-popover-foreground !shadow-xl"
        style={{ zIndex: 10020 }}
      />
      <Loading />
    </TopViewContainer>
  );
}

export default AppShell;
