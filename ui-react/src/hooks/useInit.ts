import {authService} from "@/api/auth";
import useThemeStore from "@/stores/themeSlice";
import useUserStore from "@/stores/userSlice";
import i18n from "@/utils/i18";
import {useEffect} from "react"
import useMCPStore from "@/stores/useMCPSlice";
const electron = window.electron;
const useInit = (): { isDarkMode: boolean } => {
    const {isDarkMode, setTheme} = useThemeStore()
    const {setUser,fetchUser} = useUserStore()
    const {closeLoginModal} = useUserStore();
    const servers = useMCPStore(state => state.servers)
    useEffect(() => {
        if (electron) {
            electron.ipcRenderer.send('mcp:servers-from-renderer', servers)
        }
    }, []) 

    // Copy optimization
    useEffect(() => {
        // Add page view tracking
        const fetchUserInfo = async () => {
            let userInToken = ''
            try{
                userInToken =  JSON.parse(localStorage.getItem("user-storage"))?.state?.token
            }catch(e){
                userInToken = JSON.parse(localStorage.getItem("user-storage"))?.state?.rememberMe
            }
            const token = localStorage.getItem("token") || userInToken
            if (token) {
                fetchUser()
            }
        }
        fetchUserInfo()

        // Create broadcast channel
        const channel = new BroadcastChannel('login_channel');

        // Check isLogin parameter in URL
        const urlParams = new URLSearchParams(window.location.search);
        const isLogin = urlParams.get('isLogin') === 'true';

        // Check if it's the main page
        const isMainPage = !window.location.search.includes('isLogin=true');

        if (isLogin) {
            // If it's the login page, first check if main page exists
            channel.postMessage({type: 'CHECK_MAIN_PAGE'});

            // Set timeout to wait for main page response
            const timeout = setTimeout(() => {
                // If no response received within timeout, main page doesn't exist
                channel.postMessage({type: 'LOGIN_SUCCESS'});
            }, 1000);

            // Listen for main page response
            channel.onmessage = (event) => {
                if (event.data.type === 'MAIN_PAGE_EXISTS') {
                    clearTimeout(timeout);
                    // Main page exists, send login success message and close current page
                    channel.postMessage({type: 'LOGIN_SUCCESS'});
                    window.close();
                }
            };
        } else {
            // If it's the main page, listen for messages
            channel.onmessage = (event) => {
                if (event.data.type === 'CHECK_MAIN_PAGE') {
                    // Respond to check request
                    channel.postMessage({type: 'MAIN_PAGE_EXISTS'});
                } else if (event.data.type === 'LOGIN_SUCCESS') {
                    // Execute user info fetch operation
                    closeLoginModal();
                    fetchUserInfo();
                }
            };
        }

        window?.electron?.ipcRenderer.invoke(
            "node-container:set-now-path",
            ""
        )

        const settingsConfig = JSON.parse(
            localStorage.getItem("settingsConfig") || "{}"
        )
        if (settingsConfig.language) {
            i18n.changeLanguage(settingsConfig.language)
        } else {
            // Get browser language settings
            const browserLang = navigator.language.toLowerCase()
            // If Chinese environment (including simplified and traditional), set to Chinese, otherwise set to English
            const defaultLang = browserLang.startsWith("zh") ? "zh" : "en"

            i18n.changeLanguage(defaultLang)
            // Save to local settings
        }

        // Apply stored proxy settings (if any)
        if (window.electron && settingsConfig.proxyType) {
            console.log('Applying proxy settings on startup:', settingsConfig.proxyType, settingsConfig.customProxy);
            
            // Apply settings based on different proxy types
            if (settingsConfig.proxyType === 'system') {
                window.electron.ipcRenderer.send('set-proxy', {
                    type: 'system',
                    customProxy: undefined
                });
            } else if (settingsConfig.proxyType === 'custom' && settingsConfig.customProxy) {
                window.electron.ipcRenderer.send('set-proxy', {
                    type: 'custom',
                    customProxy: settingsConfig.customProxy
                });
            } else if (settingsConfig.proxyType === 'none') {
                window.electron.ipcRenderer.send('set-proxy', {
                    type: 'none',
                    customProxy: undefined
                });
            }
        }

        // Initialize user info to ensure user-related tables are created

        const callback = (event: ClipboardEvent) => {
            try {
                navigator.clipboard
                    .writeText(window.getSelection().toString().trim())
                    .then(() => {
                    })
                event.preventDefault()
            } catch (e) {
            }
        }
        document.addEventListener("copy", callback)

        return () => document.removeEventListener("copy", callback)
    }, [])


    useEffect(() => {
        if (isDarkMode) {
            document.documentElement.classList.add("dark")
        } else {
            document.documentElement.classList.remove("dark")
        }

        const mql = window.matchMedia("(prefers-color-scheme: dark)")
        const savedTheme = localStorage.getItem("theme") || "system"
        if (savedTheme !== "system") {
            setTheme(savedTheme === "dark")
        } else {
            setTheme(mql.matches)
        }

        const handleStorageChange = () => {
            if (savedTheme === "system") {
                setTheme(mql.matches)
            }
        }
        mql.addEventListener("change", handleStorageChange)
        return () => {
            mql.removeEventListener("change", handleStorageChange)
        }
    }, [isDarkMode])

    return {isDarkMode}

}

export default useInit