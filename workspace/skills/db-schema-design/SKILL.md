---
name: db-schema-design
description: 当用户要求设计数据库表、写建表 SQL、设计表结构、数据建模，或讨论字段类型与索引设计时使用。生成 DDL 语句必须先加载本技能。查询数据或写业务代码不用本技能。
next: java-crud
related: java-crud
---

# MySQL 表结构设计规范

## 建表基线

- 存储引擎 InnoDB，字符集 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`。
- 每张表必备字段：
  ```sql
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  ```
- 逻辑删除场景加 `del_flag TINYINT NOT NULL DEFAULT 0`，不做物理删除。

## 字段规范

- 表名、字段名小写下划线；表名用单数名词（`sys_user` 而非 `sys_users`）。
- 金额用 `DECIMAL(12,2)`，禁止 FLOAT/DOUBLE；状态枚举用 `TINYINT` + COMMENT 写清枚举值。
- 字符串按实际长度选 `VARCHAR(n)`，超过 2000 字符考虑 `TEXT`。
- 所有字段必须有 COMMENT；能 NOT NULL 的一律 NOT NULL 并给默认值。

## 索引规范

- 高频查询条件建索引，命名 `idx_字段名`；唯一约束命名 `uk_字段名`。
- 联合索引把区分度高的列放前面；单表索引不超过 5 个。
- 外键关系只在字段层面表达（`xxx_id` + 索引），不建物理外键约束。

## 输出要求

- 输出完整可执行的 `CREATE TABLE` DDL，每张表附一句话用途说明。
- 多表设计时先给一段表关系说明（一对多/多对多及关联字段），再给 DDL。

## 相关技能

- 设计完表结构后若用户还需要接口代码，接着加载 `java-crud` 技能。
