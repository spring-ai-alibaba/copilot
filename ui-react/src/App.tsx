import useUserStore from "./stores/userSlice";
import useChatModeStore from "./stores/chatModeSlice";
import useWorkspaceStore from "./stores/workspaceSlice";
import {GlobalLimitModal} from "./components/UserModal";
import Header from "./components/Header";
import AiChat from "./components/AiChat";
import Login from "./components/Login";
import EditorPreviewTabs from "./components/EditorPreviewTabs";
import "./utils/i18";
import classNames from "classnames";
import {ChatMode} from "./types/chat";
import {ToastContainer} from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import {UpdateTip} from "./components/UpdateTip"
import useInit from "./hooks/useInit";
import {Loading} from "./components/loading";
import TopViewContainer from "./components/TopView";
import { useEffect } from "react";

function App() {
    const {mode, initOpen} = useChatModeStore();

    const {isLoginModalOpen, closeLoginModal, openLoginModal, user, isAuthenticated} = useUserStore();

    const {fetchWorkspaceFiles} = useWorkspaceStore();

    const {isDarkMode} = useInit();

    // 获取工作区文件
    useEffect(() => {
        console.log('App useEffect triggered:', { isAuthenticated, user: user ? { id: user.id, userType: user.userType } : null });
        if (isAuthenticated && user && user.userType && user.id) {
            const workspacePath = `${user.userType}_${user.id}`;
            console.log('Fetching workspace files for path:', workspacePath);
            fetchWorkspaceFiles(workspacePath);
        } else if (isAuthenticated && user) {
            console.log('User authenticated but missing userType or id:', { userType: user.userType, id: user.id });
        }
    }, [isAuthenticated, user, fetchWorkspaceFiles]);

    return (
        <TopViewContainer>
            <GlobalLimitModal onLogin={openLoginModal}/>
            <Login isOpen={isLoginModalOpen} onClose={closeLoginModal}/>
            <div
                className={classNames(
                    "h-screen w-screen flex flex-col overflow-hidden",
                    {
                        dark: isDarkMode,
                    }
                )}
            >
                <Header/>
                <div
                    className="flex flex-row w-full h-full max-h-[calc(100%-48px)] bg-white dark:bg-[#111]"
                >
                    <AiChat/>
                    {mode === ChatMode.Builder && !initOpen && <EditorPreviewTabs/>}
                </div>
            </div>
            <UpdateTip/>
            <ToastContainer
                position="top-center"
                autoClose={2000}
                hideProgressBar={false}
                newestOnTop={false}
                closeOnClick
                rtl={false}
                pauseOnFocusLoss
                draggable
                pauseOnHover
                theme="colored"
                style={{
                    zIndex: 100000,
                }}
            />
            <Loading/>
        </TopViewContainer>
    );
}

export default App;
