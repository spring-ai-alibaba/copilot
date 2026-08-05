# 技能匹配 Golden Set（评测基准 v1）

用途：衡量技能匹配准确率。每条 case 给出用户 query 与期望行为（应加载哪个技能，或不应加载任何技能）。
每次修改技能 description 或新增技能后，抽样跑一遍并记录命中情况。

指标定义：
- **命中**：agent 在动手前调用了 `load_skill_through_path` 加载期望技能；
- **误触发**：加载了不该加载的技能；
- **漏触发**：应加载而未加载。

## A. 应命中 frontend-style

| # | 用户 query | 期望 |
|---|---|---|
| A1 | 帮我做一个产品介绍落地页 | frontend-style |
| A2 | 写一个个人简历网页，好看一点 | frontend-style |
| A3 | 给我生成一个倒计时页面 | frontend-style |
| A4 | 把刚才那个页面配色改成深色主题 | frontend-style |
| A5 | 做一个手机端也能看的活动宣传页 | frontend-style |

## B. 应命中 vue-element-page

| # | 用户 query | 期望 |
|---|---|---|
| B1 | 用 Vue 写一个用户管理页面 | vue-element-page |
| B2 | 做一个后台管理系统的订单列表页，要能搜索和分页 | vue-element-page |
| B3 | 写一个 Element Plus 的新增用户弹窗表单 | vue-element-page |
| B4 | 帮我写个 Vue 组件展示商品卡片 | vue-element-page |

## C. 应命中 java-crud

| # | 用户 query | 期望 |
|---|---|---|
| C1 | 帮我写一个商品管理的增删改查接口 | java-crud |
| C2 | 用 Spring Boot 写个用户注册的后端接口 | java-crud |
| C3 | 给 order 表生成 Controller、Service、Mapper | java-crud |
| C4 | 写一个分页查询员工列表的接口 | java-crud |

## D. 应命中 db-schema-design

| # | 用户 query | 期望 |
|---|---|---|
| D1 | 帮我设计一个电商订单的表结构 | db-schema-design |
| D2 | 写一下用户表和角色表的建表 SQL | db-schema-design |
| D3 | 博客系统需要哪些表？给出 DDL | db-schema-design |

## E. 不应命中任何技能（负样本）

| # | 用户 query | 期望 |
|---|---|---|
| E1 | 你好，你能做什么 | 无 |
| E2 | 解释一下什么是依赖注入 | 无 |
| E3 | 帮我把这段 Python 代码翻译成 Java | 无 |
| E4 | 读一下工作目录里有哪些文件 | 无 |

## F. 边界样本（组合/易混淆，记录实际行为）

| # | 用户 query | 期望 | 说明 |
|---|---|---|---|
| F1 | 做一个图书管理系统，前后端都要 | java-crud + vue-element-page（顺序加载） | 组合任务 |
| F2 | 把数据库查询结果做成一个展示页面 | java-crud 或 frontend-style（视上下文） | 易混淆 |
| F3 | 设计好表以后把 CRUD 接口也写了 | db-schema-design → java-crud | 链式 |

## 评测记录

| 日期 | 版本 | A 命中 | B 命中 | C 命中 | D 命中 | E 误触发 | 备注 |
|---|---|---|---|---|---|---|---|
| 2026-07-27 | 技能 v1（4 技能初版 description） | 2/2（A1、A3） | 1/1（B1） | 1/1（C1） | 2/2（D1、D2） | 0/1（E1 无误触发） | F1 未测；F3 链式通过（db-schema-design→java-crud）；C1 出现自发组合加载。抽样 recall 7/7，误触发 0 |
| 2026-07-27（全量） | 技能 v1 | 5/5（A4 为多轮同会话，技能已在上下文中，直接 edit_file 修改，判定合理通过） | 4/4 | 4/4（C2/C4 自发组合 db-schema-design） | 3/3 | 0/4（E1-E4 全部零误触发，E4 正确仅用 list_files） | **F1 三技能链式通过**（db-schema-design→java-crud→vue-element-page，56 文件全栈图书管理系统）；F2 模糊请求 → 模型探索工作区后向用户澄清（要展示哪个库/Vue 还是静态页），行为合理。全量 recall 16/16，误触发 0/4 |
