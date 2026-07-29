---
name: vue-element-page
description: 当用户要求开发 Vue 页面、Vue 组件、管理后台界面，或明确提到 Vue / Element Plus / 后台管理系统时使用。生成列表页、表单页、弹窗等管理端界面必须先加载本技能。纯静态 HTML 页面用 frontend-style 技能，不用本技能。
related: java-crud
---

# Vue + Element Plus 页面开发规范

## 技术基线

- Vue 3 组合式 API（`<script setup>`），禁止 Options API。
- 组件库统一使用 Element Plus，禁止混用其他 UI 库。
- 单文件组件（.vue），样式块使用 `scoped`。

## 页面模板结构

管理端页面统一三段式：

1. **搜索区**：`el-form`（inline）+ 查询/重置按钮；
2. **表格区**：`el-table` + `el-pagination`，操作列固定在右侧（编辑/删除）；
3. **弹窗区**：新增/编辑共用一个 `el-dialog` + `el-form`，通过 `formData.id` 是否为空区分模式。

## 代码规范

- 接口调用统一封装在 `api/` 目录，页面内不直接写 axios。
- 列表加载状态用 `v-loading`；删除操作必须 `ElMessageBox.confirm` 二次确认。
- 表单必须带 `rules` 校验；提交按钮需 loading 防重复提交。
- 分页参数命名统一：`pageNum` / `pageSize` / `total`。

## 典型代码骨架

```vue
<script setup>
import { ref, reactive, onMounted } from 'vue'
const loading = ref(false)
const queryParams = reactive({ pageNum: 1, pageSize: 10 })
const tableData = ref([])
const total = ref(0)
async function getList() { /* 调用 api，设置 loading */ }
onMounted(getList)
</script>
```
