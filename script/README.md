# Nexus-ERP 脚本目录

## 保留内容及用途

| 路径 | 用途与边界 |
| --- | --- |
| `bin/ry.bat`、`bin/ry.sh` | Windows / Linux 裸 JAR 启停工具；当前 Maven 产物仍为 `ruoyi-admin.jar`。仅供人工选择，不自动启停服务。 |
| `deploy.sh` | 根目录双机 Docker Compose 的部署辅助工具。执行前核对实际目录、配置和依赖；本次清理不代表脚本已在目标环境验收。 |
| `docker/milvus/` | 可选 Milvus / RAG 本地部署模板；含持久化配置，不作为普通缓存清理。 |
| `mcp/manufacturing-documents/` | 制造资料 MCP 服务及单测；`node_modules/` 是运行依赖，`.npm-cache/` 是可重新下载的本地 npm 缓存。 |
| `sql/ry-vue.sql` | 系统库全新安装定义与种子。 |
| `sql/ry-erp.sql` | ERP 库全新安装定义与种子。 |
| `sql/ry-mes.sql` | MES 库全新安装定义与种子。 |
| `sql/ry-iot.sql` | IoT 库全新安装定义与种子。 |
| `sql/app.sql` | 移动端相关全新安装定义。 |
| `sql/tests/` | 主安装 SQL 的离线契约测试，不连接真实数据库。 |
| `tools/` | 权限与安装校验、SOP、数据重建、商城资料等维护工具。历史工具可能有机器路径或一次性基线，使用前单独审查。 |


## 离线验证

在仓库根目录运行：

```powershell
python -X utf8 -m unittest discover -s script/sql/tests -p 'test_*.py' -v
```

该命令只读取主安装 SQL；实际测试数须大于 0。全新建库验证工具涉及数据库连接，应另行确认隔离目标与授权，本次未执行。
