// 统一的 API URL 构造函数
const BASE = process.env.APP_BASE_URL || ""

// 规则：
// - path 必须按后端真实映射传入（例如 /api/appInfo 或 /auth/login）
// - 开发环境不拼接任何前缀，直接返回 path，由 Vite 代理匹配 /api 和 /auth
// - 生产/直连模式拼接 BASE + path
export const apiUrl = (path: string) => (BASE ? `${BASE}${path}` : path)