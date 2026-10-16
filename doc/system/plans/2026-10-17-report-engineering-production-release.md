# 报表与工程连接器生产发布计划

> 基线：plus/b154279b；目标：43.138.173.168；日期：2026-10-17。
> 本文是执行清单。未勾选项目不代表已完成或已上线。

## 发布范围与顺序

- [x] 核对 Git、运行容器、内存和生产 MySQL 版本。工作区初始干净，MySQL 5.7.44，无 rpt_* 与 ai_engineering_* 表。
- [x] 重绘报表菜单所用 SVG，使用 currentColor，覆盖浅色、深色、选中状态；保留现有菜单图标名，新装和生产共用。
- [x] 更新第19章为自研报表，补充第14章工程设备、任务、预览、DRC与本地连接器；生成 Word 和 HTML。
- [x] 运行前端报表/工程单测、类型检查、连接器测试、文档测试和后端相关测试。
- [ ] 审查差异与敏感信息，提交并推送 Gitee plus；记录发布提交。
- [ ] 从已推送提交构建 ERP JAR、前端 dist 和 Windows 连接器分发包。
- [ ] 备份生产系统库、当前镜像/Compose/配置/前端；查询表结构和菜单编号后执行经审查的追加迁移。
- [ ] 外置现有有效配置，沿用 Corretto 17；配置持久化报表目录、独立查询凭据和工程私有对象存储。
- [ ] 创建新 ERP 镜像，切换 ERP 与静态资源；保留旧镜像和目录以便回滚。
- [ ] 验证健康检查、验证码、报表初始化/预览/导出、工程设备/任务入口、手册及监控入口。

## 实施文件

- 图标：`plus-ui/src/assets/icons/svg/` 中报表使用的13个语义图标。
- 菜单图标名称保持与现有新装 SQL 一致；生产追加迁移登记同名图标，不修改历史快照。
- 文档源：`doc/flowcharts/scripts/manual_current_report.py`、`manual_current_engineering.py`、`gen_manual_v4.py`、`gen_manual_html.py`。
- 交付：`doc/system/user-manual/NexusERP_用户操作手册.docx`、`plus-ui/public/manual/`。
- 迁移：`script/sql/update/2026-10-17-report-engineering-prod-additive.sql`；包含已核对的新结构/菜单和本轮用户授权的旧积木清理，不复制本地业务数据、不导入主安装 SQL。
- 私有运行时材料：仓库外 `E:/Nexus/nexus-release-staging-20261017/`；凭据和备份不得提交。

## 验证命令

```powershell
python -X utf8 -m unittest discover -s doc/flowcharts/scripts -p "test_*.py" -v
cd plus-ui
npm run test:report
npm run test:engineering
npm run type-check
cd ../ruoyi-extend/nexus-engineering-connector
npm test
npm run type-check
```

Maven 在仓库根执行，显式 `-DskipTests=false -Dprofiles.active=`，以非零用例数验收。构建和部署仅在推送完成后执行。

## 数据与运行时约束

2026-10-17用户追加明确授权删除生产旧积木物理表和菜单。先完成生产备份，再按实际查询确认的19张积木表（含3张演示表）和旧菜单清单清理；保留其他业务表。只移除相应角色/套餐引用，不给普通角色自动新增功能授权。

配置取自现有运行容器的有效配置；不得用本地示例覆盖。工程私有桶与公共附件桶隔离。当前无证书，不放宽连接器HTTPS限制；可通过SSH本地环回隧道使用其已支持的显式开发地址。

回滚使用旧镜像、旧Compose及前端备份；本次追加的空表/新元数据可保留，不因回滚删除生产数据。部署成功与Windows EDA真机端到端验收分别报告。

## 发布前验证结果

文档30项、前端报表142项、前端工程81项、连接器49项、后端相关431项通过，前端/连接器类型检查通过。13个SVG在三种背景39实例可见；手册390px手机无横向溢出。生产增量隔离MySQL5.7.44验证通过；生产库尚未执行。

二次隔离验证已覆盖最终19表清理和新增订阅调度任务：新任务初始停用，原任务状态不变。独立代码审查通过；审查提醒的最终SQL/记录重新暂存已纳入提交步骤。生产五个库的在线备份、旧积木专用备份、Compose/环境/Nginx/前端备份已完成，位于`/nexus/backups/20261017-pre-release/`，备份校验通过，服务尚未切换。

发布前追加修复：工程私桶的@PostConstruct原先先于SystemApplicationRunner读取OSS缓存，新配置/冷缓存会阻断启动。现通过既有OSS服务先初始化主库配置，再校验专用PRIVATE策略；不回落公共桶。新增冷缓存回归先观测失败，再修复通过；SysEngineeringStorageTest全部12项通过，独立复核无其他阻断。该修复另行提交推送后才构建。
