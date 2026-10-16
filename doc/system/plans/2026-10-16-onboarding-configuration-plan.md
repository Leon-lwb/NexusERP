# 项目初始化配置与脱敏示例实施计划

> 使用 writing-plans、test-driven-development 和 verification-before-completion。前一轮审查方案已由用户确认，在当前工作区实施，不自动提交/推送或启停服务，保留既有报表与AI修改。

**Goal:** 提供无真实凭证的prod/secret示例，补齐local，整理dev，并使README及Docker部署说明与配置加载/打包行为一致。

**Architecture:** dev共享基础连接和可选集成契约；local显式导入dev并在后续文档收紧本地开关，保持Spring实际profile为local。私有密钥可选加载，可选短信/社交厂商按额外profile装配；支付、监控、调度默认关闭。生产示例必需凭证使用无默认值的环境变量，真实文件外置，admin构建排除私有文件与示例，Compose挂载config目录。

**Tech Stack:** Spring Boot 3.5配置数据、YAML、Maven资源/打包规则、JUnit 5、Docker Compose、Markdown。

## Task 1: 回归与基线

- [x] 记录真实prod/secret哈希、当前已有修改和受影响配置消费测试。
- [x] 新增ConfigurationBootstrapTest，隔离宿主机环境及私有配置，覆盖干净dev/local、显式可选集成、外部覆盖优先级和生产必需值。
- [x] 执行 `mvn -pl ruoyi-admin -am test -DskipTests=false "-Dprofiles.active=" -Dtest=ConfigurationBootstrapTest -Dsurefire.failIfNoSpecifiedTests=false`，确认当前缺失配置导致预期失败。

## Task 2: 配置与构建

**Files:** application-dev.yml、application-local.yml、application-prod.example.yml、application-secret.example.yml、ruoyi-admin/pom.xml、docker-compose-main.yml；按需调整DecisionDemoProfileTest、McpLocalEndpointConfigurationTest的配置契约。

- [x] dev使用可覆盖的本地连接入口，移除个人密码密文及硬编码token，secret改为可选；基础数据源/Redis/RabbitMQ仍保留。
- [x] local导入dev并提供后续本地覆盖；不激活dev、不继承开发MCP白名单或自动改表。
- [x] 完整生产示例以环境变量提供必需凭证，secret示例保留完整字段契约及启用方法；真实prod/secret原文件不改。
- [x] admin资源复制和JAR均排除私有/示例文件，Compose使用外部config目录和明确prod profile。
- [x] 更新已有相关测试，执行新旧配置、演示护栏、MCP和短信登录回归。

## Task 3: 文档与证据

**Files:** README.md、doc/system/deployment/docker部署文档.md、AGENTS.md的配置现状、当前计划及验证记录。

- [x] 写明本地基础依赖、本人凭证、私有配置覆盖、Maven/Spring profile和可选集成的启用方式。
- [x] 写明生产配置模板准备、环境变量、外部挂载、构建产物规则与首次迁移要求。
- [x] 构建admin并检查JAR无真实prod/secret及示例；检查有效Maven资源规则、Compose语法、文档链接与diff空白。
- [x] 比较真实prod/secret哈希保持不变，报告实际用例数和未做的外部环境验证。

实际结果：最终40项相关回归全部通过，生产构建及两份JAR清单、Compose合成配置、有效POM与私有文件哈希检查通过。残留classpath入口已以先失败后通过的新增用例修复；详细结果见同目录2026-10-16-onboarding-configuration-verification.md。
