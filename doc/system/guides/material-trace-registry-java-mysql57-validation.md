# Registry Java/Spring + MySQL 5.7 独立验证

本入口补充原 `tests_mysql57` 的纯SQL验证：实际编译并调用当前 `ErpSerialIdentityService`、`ErpTraceContext` 和共享发号契约，使用真实Spring动态数据源/AOP、Hikari连接池、DataSourceTransactionManager和MySQL JDBC。不会启动完整应用、Maven reactor或既有业务库。

## 运行

前提：本机Docker、已缓存镜像 `mysql:5.7`（固定镜像ID前缀 `4bc6bc963e6d`）、Java 21、Python 3.10+、本地依赖缓存和隔离classpath文件。不拉镜像、不下载依赖；缺失时明确失败。当前入口使用项目Spring Boot 3.5.12 BOM对应的缓存Connector/J 9.6.0。

在当前ERP worktree根执行：

```powershell
python -B script/tools/material_trace/tests_mysql57/registry_java_runner.py --run --output results/registry-java-review
```

`--output`只能是新建的 `tests_mysql57/results/registry-java-*` 目录，不能覆盖已有报告。省略 `--run` 时退出2且不创建容器。没有host、JDBC URL、既有container、schema或password参数。

默认classpath文件是 `ruoyi-modules/ruoyi-erp/target/wm-sn-entry-slice/classpath.txt`；可用 `--classpath-file` 指定已准备的本地classpath文件。它只包含本机已有JAR/类目录，用操作系统classpath分隔符连接；此入口绝不自行启动Maven。

纯本地安全回归，不调用Docker：

```powershell
python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p test_registry_java_runner.py -v
python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p test_harness.py -v
```

## 隔离与清理

直接复用Hume的 `DockerSandbox`：只接受本地Docker daemon、固定本机缓存镜像、唯一容器名和run/workspace标签；`network=none`、无Docker发布端口、无宿主或持久卷，MySQL数据只在tmpfs。创建后和每次SQL/转发/删除前都核验精确容器ID、镜像、名称、标签、网络与存储配置。

Java JDBC只连接任务进程创建的 `127.0.0.1` 随机端口。该短期转发通过固定 `docker exec` 程序桥接到同一个已核验容器的内部127.0.0.1:3306，不改变容器network=none，也不接受外部地址。合成用户仅授权两个run-token命名schema；凭证只传Java子进程环境，不写报告或命令行。

仅从当前安装SQL抽取白名单 `CREATE TABLE`，不导入种子INSERT、DROP或业务历史；新建schema名称由run token派生。创建的死锁探针表和触发器也仅存在于该临时schema。结束先关闭Java连接池和任务转发，再独立复核所有权并删除本次容器；失败路径同样清理。报告中的 `removed_after_ownership_verification` 必须为true。

## Maven编译及opt-in gate

IT本体保留独立main所需的JUnit Platform launcher imports。主线程统一在ERP POM加入Spring Boot BOM管理版本的 `junit-platform-launcher`、`scope=test`，同时供Hume的独立入口使用；本切片不另改POM或启动Maven。

JUnit类以 `NEXUS_REGISTRY_MYSQL57_TOKEN` 精确格式gate；Spring测试配置另有 `registry-mysql57-opt-in` profile，只由专用入口的上下文显式启用。profile关闭时真实Spring上下文不注册任何测试数据源或registry Bean，避免其他测试扫描时装配该配置。

## 验证内容

0. 专用profile未启用时，不注册数据源和registry，真实上下文中确认无Bean。

1. 两个独立AnnotationConfigApplicationContext各持有自己的路由器、连接池和事务管理器；registry是真实CGLIB代理，断言实际advisor包含DynamicDataSourceAnnotationInterceptor和TransactionInterceptor。
2. 未手动设置ERP路由的普通reserve通过代理落到真实ERP schema、非自动提交事务；身份仅REGISTERED且不绑定库存。
3. 同command跨实例重放返回相同身份，改变数量/指纹被拒绝，计数器不额外推进。
4. 实际MySQL触发器在第三件INSERT时抛SQLSTATE 45000，前两件身份、预留和sequence全部回滚；业务错误只尝试一次，解除测试触发器后同command可正常重试。
5. 三轮首次sequence并发：两物料、两规则、两个服务实例取得完整不重叠号段。
6. `JAVA`+4位与`JAVA0`+3位的不同输出namespace在两个实例并发实际重码时整段跳号；既有码、逻辑删除历史不回收，修改前缀也能继续登记且原command仍返回原身份。
7. 在registry真实事务首次锁读sequence边界安排额外探针行锁，另一个真实连接形成InnoDB死锁。没有伪造SQLException；断言原生1213、失败事务完成回滚后才出现下一次Spring事务、最终仅一条预留/一个完整号段。
8. 物理连接守卫反例：实际master连接上伪造erp标记仍拒绝，并核对具体物理库错误；只读、自动提交、无事务也拒绝。
9. 真正的Spring NEVER传播拒绝外层业务事务；真实TenantHelper.dynamic上下文拒绝跨租户登记。
10. 主线程更新后的[0-9]{6,20}租户合同：null、空白、字母、5位、21位、空格和NUL均被实际context/代理拒绝，写入与事务尝试都为0；有效990001仍通过所有注册用例。

事务证据中的 `completion=0` 为COMMITTED，`completion=1` 为ROLLED_BACK；`completedEvent < 下一次 startedEvent` 证明回滚先完成。Hikari可以在新事务中复用同一个物理serverConnectionId，因此“新事务”不要求更换池中物理连接；每次记录独立的Spring ConnectionHolder事务样本。

## Registry与主guard只读复核

- 主guard仅统计 `RESERVED` 且仍有有效 `REGISTERED` 身份的预留。PC/manual适配继续保留RESERVED，因此未使用意图不被提前CONFIRMED绕过；已消费成OUT等状态后不再永久阻塞。
- 手工退休CAS为RETIRED，只有完整原集合全部退休才CANCELLED。部分使用、部分退休的预留可以保持RESERVED，实际剩余REGISTERED存在性决定pending。
- BdMaterialServiceImpl.updateByBo使用lockTracking的FOR UPDATE；registry新登记和退休同样锁物料，维度编辑与登记共用物料行串行化。原command重放不发新号，不把历史身份当新库存。
- MES确认继续原Inbox/outbox握手。CONFIRMED来源的在制使用由MaterialTrackingUsagePort负责，主guard在usage缺失时失败关闭。本验证不把该跨MES判断宣称为已运行的集成用例。
- 未发现当前有效数据路径中与上述配合相冲突的新增阻断问题。本轮不编辑registry生产逻辑、guard、Kant服务或任何既有DDL。

## 明确边界

这是同一JVM中的两个独立Spring上下文，不是两个已部署服务器。代理、JDBC、事务与物理库检查均真实，但未启动完整Spring Boot组件扫描/自动配置、HTTP/Sa-Token/Redis、RabbitMQ或MES工作流，也未调用Kant库存服务。没有生产迁移、真实业务数据或跨主机数据库验收。高并发长时间压力、跨JVM雪花配置冲突、HTTP权限及完整库存生命周期仍属独立集成范围。

每轮目录保留javac/java参数与日志、输入SHA256、java-evidence.json（逐用例事务与原生错误码）、innodb-status.txt、容器启动/清理记录和report.json。只有Java退出0、所有发现测试成功且零失败/零跳过、输入未变化、所有权清理确认成功才报告PASS。

## 最终实测结果（2026-10-19）

最终目录：`script/tools/material_trace/tests_mysql57/results/registry-java-tenant-final/`。独立javac成功；Java/Spring/MySQL实际14/14通过，0失败、0跳过；runner安全回归6/6、原harness安全回归13/13通过。运行时为MySQL 5.7.44、Java 21.0.8、Spring 6.2.17、项目BOM对应Connector/J 9.6.0。

已纳入主线程数字租户合同的新ErpTraceContext快照，SHA256为 `e34a7e078cf9ebedf6a9f81d9fbcbc603f753cdc96b47720837270a028555e45`。最终report.json记录输入哈希未变及本轮容器已在所有权核验后删除；所有本切片开发/验证轮次创建的容器ID也逐个只读确认不存在。

首次sequence并发初始化和独立锁竞争用例均保留真实MySQL 1213，事务样本显示ROLLED_BACK完成后才开始下一次COMMITTED事务。详细原生码和连接/事务样本见java-evidence.json；Hikari复用物理连接不改变新事务结论。

四个新增文件为IT、runner、安全回归和本指南；未改生产registry、主guard、Kant服务或既有DDL。ERP test-scope launcher依赖由主线程统一添加，本切片确认已存在，保留入口，未修改POM或开启Maven。主线程可继续统一reactor验证。
