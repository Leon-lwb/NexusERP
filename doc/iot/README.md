# IoT 模块文档

本目录包含 NexusERP IoT 物联网平台的设计文档、阶段一验收报告、学习指南及实施计划。

## 文档清单

### 主文档
| 文档 | 主题 |
|------|------|
| [NexusERP_IoT文档.md](./NexusERP_IoT文档.md) | IoT 综合设计文档（21 章，含表设计/MQTT/Redis Streams/SSE/MES 集成边界） |
| [NexusERP_IoT阶段一验收报告.md](./NexusERP_IoT阶段一验收报告.md) | 阶段一（Week 33-36）验收报告 |
| [NexusERP_IoT学习指南.md](./NexusERP_IoT学习指南.md) | 面向新开发者的入门教程 |

### 周计划（plans/）
| 文档 | Week | 主题 |
|------|------|------|
| [2026-06-12-iot-week33-plan.md](./plans/archive/2026-06-12-iot-week33-plan.md) | Week 33 | 模块可编译 + 3 表 DDL + Moquette Broker + 产品/设备 CRUD |
| [2026-06-13-iot-week34-plan.md](./plans/archive/2026-06-13-iot-week34-plan.md) | Week 34 | 数据管道（MQTT→Redis Stream→MySQL+缓存+告警） |
| [2026-06-14-iot-week35-plan.md](./plans/archive/2026-06-14-iot-week35-plan.md) | Week 35 | REST 端点 + SSE + 前端 CRUD + 4 字典 |
| [2026-06-15-iot-week36-plan.md](./plans/archive/2026-06-15-iot-week36-plan.md) | Week 36 | ECharts 仪表盘 + SnailJob + 真实设备联调 |

### SQL 脚本（sql/）
| 脚本 | 用途 |
|------|------|
| [iot-week33-01-init.sql](./sql/archive/iot-week33-01-init.sql) | 建库 + 3 张核心表（iot_product/iot_device/iot_device_data） |
| [iot-week33-02-menu.sql](./sql/archive/iot-week33-02-menu.sql) | IoT 平台菜单权限 |
| [iot-week35-03-dict.sql](./sql/archive/iot-week35-03-dict.sql) | 4 个 IoT 字典 |

## 阅读顺序

1. 新开发者：[NexusERP_IoT学习指南.md](./NexusERP_IoT学习指南.md) 入门
2. 完整设计：[NexusERP_IoT文档.md](./NexusERP_IoT文档.md)（重点读第一、二、三章）
3. 阶段一成果：[NexusERP_IoT阶段一验收报告.md](./NexusERP_IoT阶段一验收报告.md)
4. 周计划按 Week 33→34→35→36 顺序阅读

## 已知问题

1. ✅ **Week 编号与文件名日期时序已修正**（已修复）：
   - 文件名日期已按 Week 顺序连续递增：week33=06-12、week34=06-13、week35=06-14、week36=06-15
   - 各 plan 内部"上周计划"与"计划制定"引用已同步更新
2. ✅ **路径引用已修复**（已修复）：主文档与 plan 文件中 `../superpowers/plans/`、`script/superpowers/plans/` 已全部改为 `./plans/`
3. ✅ **SQL 文件名引用已修复**（已修复）：验收报告 2.4 节已改为 `iot-week33-01-init.sql`/`iot-week33-02-menu.sql`/`iot-week35-03-dict.sql`
4. ✅ **DDL 已与 SQL 对齐**（已修复）：主文档 §2.1/2.2 的 3 张表（iot_product/iot_device/iot_device_data）已同步 `tenant_id BIGINT`→`VARCHAR(20)`，补 `del_flag`/审计列/`remark`，已加「DDL 已与 sql/archive/iot-week33-01-init.sql 对齐」备注
5. ✅ **字典 SQL 注释已修复**（已修复）：[iot-week35-03-dict.sql](./sql/archive/iot-week35-03-dict.sql) 头部注释由"600200~600211 / 12 条"已改为"600200~600209 / 10 条"
6. **学习指南与主文档内容重复**：架构图、MQTT 概念、表设计等重叠 — 信息项，可不处理（学习指南面向新人，重复可接受）
