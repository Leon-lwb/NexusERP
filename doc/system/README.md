# 系统级文档

本目录包含跨模块的系统级文档：升级方案、代码审计、Docker 部署、营销文档、用户手册。

## 文档清单

### 跨模块规划与审计
| 文档 | 主题 | 状态 |
|------|------|------|
| [NexusERP_三模块共同升级方案.md](./NexusERP_三模块共同升级方案.md) | ERP/MES/IoT 协同升级路线图（三波次，约 13 周） | 待审核 |
| [NexusERP_代码审计报告与修复进度.md](./NexusERP_代码审计报告与修复进度.md) | 全模块代码审计（42 问题） | 🚧 CRITICAL 已修复，37 个待修复 |
| [NexusERP_plus-ui前端优化方案.md](./NexusERP_plus-ui前端优化方案.md) | plus-ui 前端优化（5 阶段已完成 + 推广批次待开始） | ✅ 阶段 0-4 已完成，⬜ 批次 1-4 待推广 |

### 部署（deployment/）
| 文档/配置 | 路径 | 说明 |
|-----------|------|------|
| Docker 部署文档 | [deployment/docker部署文档.md](./deployment/docker部署文档.md) | v2.1，2026-07-26（主服务器 159.75.14.226） |
| docker-compose-main | [deployment/docker/docker-compose-main.yml](./deployment/docker/docker-compose-main.yml) | 主服务器编排（MySQL5.7/Redis/RabbitMQ/MinIO/Nginx/NexusServer） |
| docker-compose-assist | [../../docker-compose-assist.yml](../../docker-compose-assist.yml) | 辅助服务器编排（MonitorAdmin/SnailJobServer） |
| nginx.conf | [../../plus-ui/nginx.conf](../../plus-ui/nginx.conf) | 反向代理（实际部署用，volume 挂载） |
| redis.conf | [deployment/docker/redis/conf/redis.conf](./deployment/docker/redis/conf/redis.conf) | Redis 配置 |

### 营销（marketing/）
| 文档 | 主题 |
|------|------|
| [NexusERP1.0上线公众号文章.md](./marketing/NexusERP1.0上线公众号文章.md) | 1.0 上线宣传（六大核心模块） |
| [NexusERP1.1上线公众号文章.md](./marketing/NexusERP1.1上线公众号文章.md) | 1.1 上线宣传（MES + IoT 双引擎） |

### 资源
- [screenshots/](./screenshots/) - 18 张系统截图（登录页、各业务模块界面）
- [user-manual/](./user-manual/) - 用户操作手册（docx）

### 待开发计划（plans/）
| 文档 | 主题 | 状态 |
|------|------|------|
| [2026-07-17-so-purchase-linkage-plan.md](./plans/2026-07-17-so-purchase-linkage-plan.md) | SO 销售订单->采购联动（补全 so_sales_order_line.purchase_qty / linked_po_id，建销售->采购业务链路） | 📋 待开发 |

## 阅读顺序

1. 系统全貌：[NexusERP_三模块共同升级方案.md](./NexusERP_三模块共同升级方案.md)
2. 代码质量：[NexusERP_代码审计报告与修复进度.md](./NexusERP_代码审计报告与修复进度.md)
3. 部署运维：[deployment/docker部署文档.md](./deployment/docker部署文档.md) + 配置文件

## 已知问题

1. ✅ **docker-compose 双机部署已落地并对齐 159.75.14.226 实际配置**（已修复）：
   - [docker-compose-main.yml](./deployment/docker/docker-compose-main.yml)：6 服务，MySQL 5.7、MinIO 9005、RabbitMQ、nexus-server 镜像 `nexus-erp:fix-headless`
   - [docker-compose-assist.yml](../../docker-compose-assist.yml)：2 服务（MonitorAdmin/SnailJobServer），在辅助服务器 117.72.190.225
   - 原 docker-compose.yml 历史全量版、database.yml、doc 下 nginx.conf 副本等散落文件已清除
2. ✅ **公众号 1.0 文章图片路径已修复**（已修复）：`../script/image/` 已全部改为 `../screenshots/`
3. **代码审计进度滞后**：42问题仅修复 5 个 CRITICAL，37 个待修复无时间表
4. ✅ **顶层索引已补建**：本 README 与 doc/README.md、CONTRIBUTING.md 已建立

## 待用户决策

（暂无待决策项）
