---
name: frontend-style
description: 当用户要求创建、修改或美化 HTML 页面、静态网页、前端界面、落地页时使用。涉及页面样式、布局、配色、响应式设计时必须先加载本技能。纯后端 Java 代码任务不需要本技能。
related: vue-element-page（管理后台类界面改用它）
---

# 前端页面样式规范

生成或修改任何 HTML 页面时，严格遵守以下规范。

## 强制规范

1. **禁止手写大量自定义 CSS**，必须使用 Tailwind CSS 工具类。
2. 每个 HTML 页面的 `<head>` 中必须引入 Tailwind CDN：
   ```html
   <script src="https://cdn.tailwindcss.com"></script>
   ```
3. 自定义样式只允许出现在少量无法用 Tailwind 表达的场景（如复杂动画），且必须写在 `<style>` 块内并加注释说明原因。

## 设计风格

- 简洁专业：留白充足，层级清晰，避免花哨装饰。
- 配色：以中性色（slate/gray/zinc）为底，单一主题色点缀；正文用 `text-slate-700`，标题用 `text-slate-900`。
- 响应式：默认移动优先，使用 `sm:` `md:` `lg:` 断点；容器用 `max-w-*` + `mx-auto`。
- 圆角与阴影统一：卡片 `rounded-xl shadow-sm`，按钮 `rounded-lg`。
- 图标优先用内联 SVG（heroicons 风格），不引入图标字体库。

## 输出要求

- 单文件 HTML（样式、脚本内联），保存到工作目录，可直接双击打开预览。
- 页面必须包含 `<meta name="viewport" content="width=device-width, initial-scale=1.0">`。
- 中文页面 `<html lang="zh-CN">`，并设置 `<meta charset="UTF-8">`。
