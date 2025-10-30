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
        '/api': {
          target: 'http://localhost:6039',
          changeOrigin: true,
          secure: false,
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
