# 工程连接器新码立即失效：Redis 租户键修复

> 日期：2026-10-19；分支：plus，起始 HEAD：ba0fdc79。以下为源码和隔离测试记录，不代表本地既有进程已重启或生产已更新。未使用真实配对码、未连接真实业务数据库、未修改服务器。

## 故障与根因

用户报告服务器已识别 `/prod-api`、本地已识别 `/dev-api`，但配对均返回 `401 / PAIRING_INVALID`。本次沿浏览器、桌面端和后端检查，确认不是前一阶段的 API 前缀缺失。

`EngineeringDeviceController.pairing()` 在已登录用户的 `TenantHelper.dynamic(actor.tenantId())` 中生成配对码。现有 `EngineeringRedisEphemeralStore` 传入普通 Redis 键，实际由 `TenantKeyPrefixHandler` 加上租户前缀。匿名连接器兑换时尚无租户上下文，相同逻辑键不会加租户前缀，因此 Redis 原子消费返回空值。

省略部署级 Redis 前缀后，修复前的两条物理键形态为：

- 浏览器生成：`<tenantId>:ai:engineering:pair:<sha256>`。
- 匿名连接器查询：`ai:engineering:pair:<sha256>`。

此前测试夹具用普通内存 Map 替代整个短期仓储，未覆盖真实租户键映射，因而无法发现该问题。新回归保留真实 `TenantHelper`、`TenantKeyPrefixHandler`、配对服务和隔离 H2 仓储，只替换 Redis 传输和登录环境。

## 修复边界

仅当用途为 `pair` 时采用 `global:ai:engineering:pair:<sha256>`，通过现有全局键契约获得一致的生成/兑换地址。`upload` 等用途维持现有租户前缀。

配对码仍为256位随机值，Redis 键只含摘要，仍为5分钟有效并经 `getAndDelete()` 单次消费；租户、用户和允许能力仍由创建时的负载固定，兑换时重新验证所有者及权限。没有接收客户端提供的租户或用户身份，也没有关闭多租户功能。

旧键不迁移、不扫描、不清理，由原 TTL 自然过期。所有相关后端实例应更新到同一版本后再生成新码，避免新旧实例混用不同键格式。现有设备凭证、上传票据及 Windows 客户端协议无需更改。

## 验证

- 修复前，新增4项回归中2项以同一配对异常失败，精确复现“刚生成即无效”；另2项通过。
- 修复后执行 `mvn -B -pl ruoyi-modules/ruoyi-ai -am test -DskipTests=false -Dprofiles.active= -Dtest=Engineering*Test -Dsurefire.failIfNoSpecifiedTests=false`：18个测试类，65例通过，0失败、错误或跳过。
- 新增4项覆盖：浏览器生成后匿名兑换与拒绝重放；其他租户上下文不能改写固定所有者；上传票据的租户/用途隔离；仅保存摘要并保留5分钟TTL。
- `plus-ui` 的 `npm run type-check` 退出码0。JDK/Mockito动态代理和npm既有配置提示不影响本次结果。
- 前端 `npm run build:prod -- --outDir node_modules/.tmp/engineering-pairing-production-20261019-1756`：退出码0，产物保存在独立目录，未覆盖部署前端；已有 Sass、模块兼容和分包提示保留。
- 后端 `mvn -B -Pprod -pl ruoyi-admin -am package -Dmaven.test.skip=true`：51个模块构建成功；此步骤跳过测试，不能替代前述65例回归。JAR 中的配对存储类与编译产物逐字节一致，并确认包含全局配对键。
- 后端产物：`ruoyi-admin/target/ruoyi-admin.jar`，239181658字节，生产profile为prod；SHA-256：`bbf74f11feb5ff2d5e82389f6e9dae980270e7c3c18871b9da37907424caea66`，同目录提供 `.jar.sha256` 和 `engineering-pairing-artifact-verification-20261019.json`。检查确认未打入application-prod、application-secret及example配置。
- 打包后执行 `mvn -B -Pdev -pl ruoyi-admin resources:resources`，确认本机 `target/classes` 的默认profile恢复为构建前的dev，生产JAR摘要保持不变；未修改源码中的生产私有配置。
- 独立代码复核确认同版本修复覆盖根因，未发现本轮破坏所有者、权限、一次性消费及上传租户边界。复核提出新旧节点混流的P2发布风险，已明确采用全部后端实例统一更新、再生成新码的发布前提，不支持配对请求在新旧版本间混流；未增加可能破坏单次消费的凭据双写。
- 真实Redis的到期淘汰/跨节点并发、真实浏览器认证与实际环境配对未实测；TTL参数和原子操作调用方式保留，隔离回归不能替代目标环境联调。

## 使用修复

本地需要重启加载新代码的后端，前端可以继续使用当前版本。服务器需要部署包含修复的后端 JAR 或镜像并重新启动相应应用；这两项没有在本次自动执行。完成后在各自网页生成新的配对码，5分钟内使用当前 Windows 应用配对。

本次没有数据库结构变化，不需要导入 SQL 或清理 Redis。未进行真实用户绑定、真实 EDA 设计或文件上传；这些需在更新后的目标环境验证。
