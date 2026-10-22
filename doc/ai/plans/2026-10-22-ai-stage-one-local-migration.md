# AI 一阶段本地权限迁移记录

- 执行时间：2026-10-22T22:39:51.802393700+08:00
- 基线：plus / b0a37949
- 目标：localhost:3306/ry-vue；MySQL 5.7.14
- 执行：原增量四条INSERT，单连接SERIALIZABLE事务，提交前验证父页面及四项唯一启用按钮。异常回滚。
- 首次新增：4；提交后四项核查通过；重复新增：0。
- 菜单：1725→1702 ai:model:export；1734→1703 ai:provider:export；1745→1704 ai:knowledge:export；1843→1831 ai:flow:query。
- 角色、租户套餐和业务库未修改。未导入主初始化SQL。
- 本机备份：C:/Users/admin/AppData/Local/Codex/db-backups/nexus-erp/2026-10-22-ai-stage-one/2026-10-22T14-39-50-782Z，含before.tsv、after.tsv、result.json；只含相关菜单元数据，不进入提交。
- 静态保护测试：node --test scripts/test-ai-stage-one-permissions.mjs，6项通过、0失败、0跳过。
- 此记录仅证明上述本机菜单迁移，不代表生产部署或业务权限人工验收。
