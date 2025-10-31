import {defineConfig, loadEnv} from "vite";
import react from "@vitejs/plugin-react";
import path from "path";
import {viteCommonjs} from "@originjs/vite-plugin-commonjs";

const isElectron = process.env.npm_lifecycle_event?.startsWith("electron:");

export default defineConfig(async ({ mode }) => {
  const glslPlugin = (await import("vite-plugin-glsl")).default;

  const env = loadEnv(mode, process.cwd(), "");

  process.env = { ...process.env, ...env };

  return {
    plugins: [
      viteCommonjs(),
      {
        name: "handle-dynamic-imports",
        transform(code, id) {
          if (id.includes("generateJSX.ts")) {
            return {
              code: code.replace(
                /import.*from ['"]\.\/images\/\${imageName}['"];?/g,
                "const image = await import(`./images/${imageName}`);"
              ),
              map: null,
            };
          }
        },
      },

      glslPlugin({
        include: [
          "**/*.glsl",
          "**/*.wgsl",
          "**/*.vert",
          "**/*.frag",
          "**/*.vs",
          "**/*.fs",
        ],
        exclude: undefined,
        warnDuplicatedImports: true,
        defaultExtension: "glsl",
        watch: true,
        root: "/",
      }),

      react(),
      // Temporarily disable electron plugin for web-only development
      // electron([
      //   {
      //     // Main process entry file of the Electron App
      //     entry: "electron/main.ts",
      //   },
      //   {
      //     entry: "electron/preload.ts",
      //     onstart(options) {
      //       options.reload();
      //     },
      //   },
      // ]),
    ],

    base: "./",
    build: {
      outDir: "dist",
      emptyOutDir: true,
      rollupOptions: {
        external: ["@electron/remote", "electron"],
        output: {
          manualChunks(id) {
            if (id.includes("workspace/")) {
              return null;
            }
          },
        },
      },
      copyPublicDir: true,
      assetsDir: "assets",
    },

    server: {
      headers: isElectron
        ? {}
        : {
            "Cross-Origin-Embedder-Policy": "credentialless",
            "Cross-Origin-Opener-Policy": "same-origin",
          },
      watch: {
        ignored: ["**/workspace/**"],
      },
      proxy: {
        // 统一代理目标可通过环境变量配置（优先 APP_BASE_URL，其次 VITE_PROXY_TARGET，最后默认6039）
        // 仅在开发模式下使用，用于避免跨域；生产环境不使用代理
        // 注意：如果在开发环境设置了 APP_BASE_URL，则前端会直连后端，代理配置将不会生效
        // 为保持单一入口，你也可以只设置 APP_BASE_URL，这里将自动沿用
        get '/api'() {
          const target = env.APP_BASE_URL || env.VITE_PROXY_TARGET || 'http://localhost:6039';
          return {
            target,
            changeOrigin: true,
            secure: false,
          };
        },
        // 认证等未带 /api 前缀的后端路由，开发环境也通过代理以避免跨域
        get '/auth'() {
          const target = env.APP_BASE_URL || env.VITE_PROXY_TARGET || 'http://localhost:6039';
          return {
            target,
            changeOrigin: true,
            secure: false,
          };
        },
        get '/admin'() {
          const target = env.APP_BASE_URL || env.VITE_PROXY_TARGET || 'http://localhost:6039';
          return {
            target,
            changeOrigin: true,
            secure: false,
          };
        },
      },
    },

    css: {
      postcss: {
        plugins: [require("tailwindcss"), require("autoprefixer")],
      },
    },

    define: {
      "process.env": env,
    },

    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
        "@sketch-hq/sketch-file-format-ts": "@sketch-hq/sketch-file-format-ts",
        "ag-psd": "ag-psd",
        "@electron/remote": "@electron/remote/main",
      },
    },

    optimizeDeps: {
      include: [
        "uuid",
        "@sketch-hq/sketch-file-format-ts",
        "ag-psd",
        "@codemirror/state",
        "seedrandom"
      ],
      exclude: ["@electron/remote", "electron"],
      esbuildOptions: {
        target: "esnext",
      },
    },

    publicDir: path.resolve(__dirname, "workspace"),
  };
});
