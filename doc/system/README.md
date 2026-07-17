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
| Docker 部署文档 | [deployment/docker部署文档.md](./deployment/docker部署文档.md) | v2.0，2026-06-12 |
| docker-compose | [deployment/docker/docker-compose.yml](./deployment/docker/docker-compose.yml) | 主编排配置 |
| database.yml | [deployment/docker/database.yml](./deployment/docker/database.yml) | ⚠️ 框架默认模板，非实际部署 |
| nginx.conf | [deployment/docker/nginx/conf/nginx.conf](./deployment/docker/nginx/conf/nginx.conf) | 反向代理 |
| redis.conf | [deployment/docker/redis/conf/redis.conf](./deployment/docker/redis/conf/redis.conf) | Redis 配置 |

### 营销（marketing/）
| 文档 | 主题 |
|------|------|
| [NexusERP1.0上线公众号文章.md](./marketing/NexusERP1.0上线公众号文章.md) | 1.0 上线宣传（六大核心模块） |
| [NexusERP1.1上线公众号文章.md](./marketing/NexusERP1.1上线公众号文章.md) | 1.1 上线宣传（MES + IoT 双引擎） |

### 资源
- [screenshots/](./screenshots/) — 18 张系统截图（登录页、各业务模块界面）
- [user-manual/](./user-manual/) — 用户操作手册（docx）

## 阅读顺序

1. 系统全貌：[NexusERP_三模块共同升级方案.md](./NexusERP_三模块共同升级方案.md)
2. 代码质量：[NexusERP_代码审计报告与修复进度.md](./NexusERP_代码审计报告与修复进度.md)
3. 部署运维：[deployment/docker部署文档.md](./deployment/docker部署文档.md) + 配置文件

## 已知问题

1. ✅ **docker-compose 已按部署文档拆分为双机部署**（已修复）：
   - 新增 [docker-compose-main.yml](./deployment/docker/docker-compose-main.yml)：6 服务（MySQL/Redis/RabbitMQ/MinIO/Nginx/NexusServer），密码 `MIS@5mis@5`，MinIO 凭证 `nexus/nexus123456`，RabbitMQ 已补齐
   - 新增 [docker-compose-assist.yml](./deployment/docker/docker-compose-assist.yml)：2 服务（MonitorAdmin/SnailJobServer）
   - 保留原 [docker-compose.yml](./deployment/docker/docker-compose.yml) 作为历史归档，文件头注释已说明拆分关系
   - **遗留**：`nginx/conf/nginx.conf` 中 `upstream` 段仍为旧名 `ruoyi-server`，需同步为 `nexus-server`（不影响主流程，待后续维护）
2. **database.yml 与实际部署无关**：含 Oracle/SQLServer/PG，实际用 MySQL，属框架默认模板
3. **部署文档引用的部分文件仍缺失**：`script/deploy.sh`、`application-prod.yml`、各模块 `Dockerfile` 等（docker-compose-main/assist 已补齐）
4. ✅ **公众号 1.0 文章图片路径已修复**（已修复）：`../script/image/` 已全部改为 `../screenshots/`
5. **代码审计进度滞后**：42问题仅修复 5 个 CRITICAL，37 个待修复无时间表
6. ✅ **顶层索引已补建**：本 README 与 doc/README.md、CONTRIBUTING.md 已建立

## 待用户决策

- nginx.conf 的 upstream 段是否需要立即同步为 `nexus-server`（当前不影响双机部署主流程）？
