---
name: excel-report
description: 当用户要求生成 Excel 报表、导出 xlsx 文件、制作数据透视或表格数据导出时使用本技能。只是查询数据、做网页表格展示或写 SQL 不用本技能。
---

# Excel 报表规范

## 技术选型
- 前端导出用 SheetJS（xlsx 包）；后端导出用 EasyExcel

## 格式要求
1. 表头加粗、背景浅灰、冻结首行
2. 金额列右对齐、保留两位小数、千分位分隔
3. 日期列统一 yyyy-MM-dd 格式
4. 文件名格式：报表名_yyyyMMdd.xlsx

## 大数据量
- 超过 1 万行用流式写入（EasyExcel write + sheet 分页）
