---
name: java-crud
description: 当用户要求编写 Java 后端接口、增删改查、CRUD、Controller/Service/Mapper 代码，或提到 Spring Boot / MyBatis-Plus 后端开发时使用。涉及数据库表对应的实体和接口生成必须先加载本技能。前端页面任务不用本技能。
requires: db-schema-design（需要新建表时先加载它设计 DDL）
related: vue-element-page
---

# Java CRUD 后端代码规范

## 技术基线

- Spring Boot 3.x + MyBatis-Plus + Lombok。
- 分层：Controller → Service（接口 + Impl）→ Mapper → Entity。
- 统一返回 `R<T>` 包装类；分页用 MyBatis-Plus 的 `Page<T>`。

## 各层规范

**Entity**
- `@Data` + `@TableName("表名")`；主键 `@TableId(type = IdType.AUTO)`。
- 时间字段用 `LocalDateTime`，命名 `createTime` / `updateTime`。

**Mapper**
- 继承 `BaseMapper<Entity>`，能用 Wrapper 解决的不写 XML。

**Service**
- 接口继承 `IService<Entity>`，实现类继承 `ServiceImpl<Mapper, Entity>`。
- 业务校验放 Service 层（如唯一性检查、状态流转合法性），校验失败抛 `ServiceException`。

**Controller**
- REST 风格：`GET /list`（分页查询）、`GET /{id}`、`POST`（新增）、`PUT`（修改）、`DELETE /{id}`。
- 入参校验用 `@Validated` + jakarta validation 注解，禁止在 Controller 写业务逻辑。

## 命名约定

- 包名按模块划分：`controller` / `service` / `service.impl` / `mapper` / `domain.entity` / `domain.dto`。
- 查询条件封装为 `XxxQueryDto`，新增修改用 `XxxDto`，避免直接暴露 Entity。

## 相关技能

- 需要先建表时，先加载 `db-schema-design` 设计 DDL 再写代码。
- 用户同时要管理界面时，接着加载 `vue-element-page`。
