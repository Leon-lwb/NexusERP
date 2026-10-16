# 项目初始化配置、脱敏示例与部署验证记录

日期：2026-10-16（Asia/Shanghai）；分支：plus；开始核对HEAD：183667d1。用户确认实施：新增prod/secret示例、补齐local、整理dev，并更新README/部署说明。本次没有自动提交、推送、启动服务或连接/改写业务数据库。

## 最终行为

- 新增 `application-prod.example.yml`、`application-secret.example.yml`、`application-local.yml`。生产示例不提供真实凭证，必需项显式使用环境变量；secret示例覆盖现有secret字段结构。
- dev的四个数据源、Redis、RabbitMQ改为环境变量或外部profile覆盖，移除原个人密码密文、硬编码工位密钥及调度token。支付、邮件、监控/调度、MQTT、联网搜索、视觉和WebSocket默认关闭，默认回环监听。
- local导入共享开发配置，实际profile仍为local；后续文档收紧连接池、关闭MCP开发白名单，禁止自动修改Flowable结构。安装SQL中的引擎版本元数据为7.0.0.0，存量环境仍需单独核对。
- 短信厂商仅在额外sms profile装配，第三方登录供应商仅在social profile装配；实际启用前需完整配置厂商凭证与回调。
- 私有secret仅从 `NEXUS_CONFIG_DIR` 对应的外部文件可选加载，公共dev/生产示例不再主动导入classpath密钥，避免未clean输出目录的旧文件干扰。
- admin两组资源规则及JAR规则均排除真实prod/secret和两个example；额外JAR排除防止历史target/classes残留进入产物。Maven spring-boot:run工作目录固定为聚合根。
- 根Compose显式选择生产profile、只读挂载config目录，并要求外部application-prod.yml存在；补充必需API/运维变量映射和数据源覆盖。没有执行任何Compose部署命令。
- 原有真实ignored prod/secret原文件SHA-256前后相同，继续保持私有。根config/application*.yml已纳入忽略。

## 改动文件

| 范围 | 文件 |
| --- | --- |
| 配置 | ruoyi-admin/src/main/resources/application-dev.yml、application-local.yml、application-prod.example.yml、application-secret.example.yml |
| 构建及运行 | ruoyi-admin/pom.xml、docker-compose-main.yml、.gitignore |
| 回归 | ruoyi-admin/src/test/java/com/nexus/test/ConfigurationBootstrapTest.java、DecisionDemoProfileTest.java、McpLocalEndpointConfigurationTest.java |
| 使用与部署 | README.md、doc/system/deployment/docker部署文档.md、AGENTS.md |
| 实施证据 | 当前验证记录及2026-10-16-onboarding-configuration-plan.md |

此前报表README/数据库记录及工作区并行AI修改保留，没有清理或提交。

## 测试证据

1. 先编写隔离配置回归，使用真实ConfigDataEnvironmentPostProcessor、Spring占位符及条件装配。测试只复制公开配置，屏蔽父classpath里的真实私有文件，移除宿主系统环境，使用合成值；不会启动NexusApplication或外部资源。
2. 原配置执行新增7项：Maven确认2失败、4错误、1通过，跳过0。失败覆盖必需secret导入、缺少local、缺少生产示例、可选集成装配契约。
3. 修改后执行下列Maven回归，5个测试类39项全部通过，失败/错误/跳过均0：

```text
mvn -pl ruoyi-admin -am test -DskipTests=false "-Dprofiles.active=" "-Dtest=ConfigurationBootstrapTest,DecisionDemoProfileTest,McpLocalEndpointConfigurationTest,AppAuthSmsCodeTest,SmsAuthStrategyTest" -Dsurefire.failIfNoSpecifiedTests=false
```

4. 审查发现残留classpath密钥入口后，新增第8项初始化测试，先复现8项中的1项失败，再移除公共配置的classpath导入，8项通过。
5. 最终生产构建后，使用Maven产生的admin测试/运行classpath，经JUnit Platform再次执行同5个测试类：**40项启动、40项通过，失败0、跳过0、中止0**。此最终40项为JUnit直接执行结果，不把前一次Maven39项或打包中的跳过测试混作新增用例。

最终用例分布：初始化配置8、演示护栏26、MCP白名单4、App短信认证1、短信登录策略1。测试覆盖外部覆盖优先级、local不隐式激活dev、可选厂商门控、生产凭证解析及陈旧classpath隔离。

标准Maven命令可用于后续复跑当前40项；本地最终运行器为忽略目录中的 `final-runner-java.args`，验证日志与summary见下方。

## 构建与分发验证

```text
mvn -pl ruoyi-admin -am package -P prod -DskipTests
mvn -pl ruoyi-admin -P prod help:effective-pom -Doutput=<本机忽略目录>/effective-pom.xml
```

两次生产构建均成功；最后一次在残留classpath修正后重新构建。打包显式跳过测试，测试结论来自上节的非零用例执行。

对最终 `ruoyi-admin.jar` 及 `.jar.original` 实际读取ZIP目录，均仅含以下四份公共application配置：

- application.yml
- application-dev.yml
- application-local.yml
- application-decision-demo.yml

真实prod/secret及两个example均不在两个产物中；过滤后的默认profile为prod；打包dev不再存在classpath:application-secret.yml导入。

有效POM确认只有两组admin资源规则，过滤组明确排除私有与示例文件，JAR再次排除；Spring Boot运行目录解析为当前仓库根。配置整理未改动公共application.yml原有的前端配套开发加密材料，生产示例通过必需环境变量覆盖；不据此宣称整个代码库已完成密钥治理。

Compose使用单独的合成env文件执行：

```text
docker compose --env-file <合成夹具> -f docker-compose-main.yml config --quiet
```

退出码0；没有使用真实.env、输出解析后的凭证或联系Docker引擎启动容器。文档新增本地链接检查通过，Git差异空白检查通过。

## 独立审查

按requesting-code-review进行只读审查，覆盖四份配置、打包/挂载、忽略规则、文档与三份相关测试，未发现仍未解决的确定P1/P2问题。主线程已补上首次上传前的远端config目录准备说明，并以新增回归消除残留classpath入口。审查没有编辑文件、运行构建或读取真实prod/secret内容。

## 本地证据与边界

证据保存在Git忽略目录 `.codex/tmp/onboarding-config-20261016/`：初始red-tests.log、configuration-regression.log、stale-classpath-red/green.log、final-configuration-tests.log、final-test-summary.json、final-package-prod.log、effective-build-validation.json、compose-validation.json、final-validation.json及私有文件哈希比较。

本次没有部署、启停服务或执行数据库迁移，也没有用真实数据库/Redis/RabbitMQ/第三方SDK做完整启动或业务验收。未改Vue/TS源码，未重复前端全量检查。首次本地使用仍需配置自己的基础依赖及凭证；生产按新示例迁移到外部config目录，不能直接假定旧源码私有prod的classpath导入可继续使用。既有私有源文件保留未覆盖。
