# CONTROL 隔离验收资源

本目录 `.env` 为忽略文件，含独立 DML 账号凭据，不提交/分享；`provision-evidence.json`不含凭据。正式/日常dev运行不加载这些测试工具。

## 已保存的2026-09-27验收

见报告 `doc/ai/benchmark/delivery-risk/reports/2026-09-27-summary-control-http-acceptance.md`及同名evidence目录。这里的四库已有真实写入/补偿和修复前失败，不能重导或清空“恢复黄金”，准备器检测存在即拒绝。

## 首次全新隔离环境执行顺序

1. 审阅 `scripts/prepare-control-test.py`，先无参数dry run，再显式`--apply`。只允许已验证13307实例及新control四库，原ERP只读复制；新Redis6386、私有MQvhost，已有目标拒绝。当前固定20260927命名已用，应另行审阅新命名/护栏，不能删除旧库重跑。
2. 编译/测试：`mvn -pl ruoyi-admin -am test -DskipTests=false -Dtest=*Decision*,*Industrial*,*Delivery* -Dsurefire.failIfNoSpecifiedTests=false`；再package。保留target/test-classes，生产JAR不能有`com/nexus/decisioncontrol`。
3. `pwsh -NoProfile -File scripts/run-control-test.ps1`启动隔离8081。它只用命令覆盖DS/Redis/MQ；启用decision-control-test，故障配置在非controlDS、prod Profile、混合库组或共享Redis上拒绝启动。
4. 根据用户授权，仅该测试启动器暂时`--captcha.enable=false`，基础application.yml仍为true。启动`node scripts/run-control-http.mjs`，它从本地前端读取已知测试登录默认值并RSA/AES登录；密码、token仅内存。无验证码时自动登录，不需截图。
5. 通过 `%TEMP%/nexus-control-live-command.txt`一次写一个命令：`full`产生固定四分片并调用既有90秒Runner；等待终态，再`login control_reader`、`login control_tenant_b`，最后`matrix`。只有在新副本还未执行CONTROL时运行matrix（它要求先有40基线并创建明确租户B查询fixture）。`quit`关闭登录测试客户端。
6. marker仅`%TEMP%/nexus-control-faults/fault.txt`：`actionId:EXECUTE_FAIL/VERIFY_FAIL/VERIFY_PAUSE_FAIL`。真实执行/核验后注入错误用于验证补偿；补偿始终委托真实ERP。没有HTTP故障/状态修改接口。离线SQL helper只允许四个明确control库的单条SELECT/INSERT/UPDATE/SHOW/CHECKSUM，任何DROP/DELETE/跨库/OUTFILE等拒绝。
7. 保存原始报告、所有失败及数据库对账，检查原ERP checksum、库存、执行实例与幂等键。停止自己启动的8081，保留数据库/证据；不关闭原8080、不发布排产/自动采购审批/扣库存。

已有dev库的node_type扩容需按`doc/system/数据库增量变更记录.md`手工预检/备份后处理，仅control库已迁移。临时免验证码不要写回application.yml或生产配置。
