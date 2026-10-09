# 仓内模块直接依赖明细

固定提交：`5b66fbeeff5a58028f248c68705e29b0bf968dd1`。扫描 54 个受跟踪 POM；以下仅列 project/dependencies 中仓内引用，profile / management / plugin 项在 JSON 中单列。

箭头语义：依赖方 → 被依赖方；group 节点只表示成员集合，非单一 JAR。

| 模块 | 直接仓内依赖（scope / optional） | POM |
|---|---|---|
| `nexus-erp` | 无仓内直接依赖 | `pom.xml` |
| `runtime-attestation-agent` | 无仓内直接依赖 | `ruoyi-common/runtime-attestation-agent/pom.xml` |
| `ruoyi-admin` | `ruoyi-common-doc`、`ruoyi-common-social`、`ruoyi-common-ratelimiter`、`ruoyi-common-mail`、`ruoyi-system`、`ruoyi-job`、`ruoyi-generator`、`ruoyi-flowable`、`ruoyi-erp`、`ruoyi-mes`、`ruoyi-station`、`ruoyi-iot`、`ruoyi-pay`、`ruoyi-report`、`ruoyi-im`、`ruoyi-ai`、`ruoyi-flow` | `ruoyi-admin/pom.xml` |
| `ruoyi-ai` | `ruoyi-common-ai`、`ruoyi-common-flow`、`ruoyi-common-sse`、`ruoyi-common-sensitive`、`ruoyi-common-idempotent`、`ruoyi-common-log`、`ruoyi-common-web`、`ruoyi-common-doc`、`ruoyi-common-oss` | `ruoyi-modules/ruoyi-ai/pom.xml` |
| `ruoyi-common` | 无仓内直接依赖 | `ruoyi-common/pom.xml` |
| `ruoyi-common-ai` | `ruoyi-common-core`、`ruoyi-common-sse`、`ruoyi-common-mybatis`、`ruoyi-common-tenant`、`ruoyi-common-excel`、`ruoyi-common-web` (test) | `ruoyi-common/ruoyi-common-ai/pom.xml` |
| `ruoyi-common-bom` | 无仓内直接依赖 | `ruoyi-common/ruoyi-common-bom/pom.xml` |
| `ruoyi-common-core` | 无仓内直接依赖 | `ruoyi-common/ruoyi-common-core/pom.xml` |
| `ruoyi-common-cv` | `ruoyi-common-core`、`ruoyi-common-json` | `ruoyi-common/ruoyi-common-cv/pom.xml` |
| `ruoyi-common-doc` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-doc/pom.xml` |
| `ruoyi-common-encrypt` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-encrypt/pom.xml` |
| `ruoyi-common-excel` | `ruoyi-common-json` | `ruoyi-common/ruoyi-common-excel/pom.xml` |
| `ruoyi-common-flow` | `ruoyi-common-core`、`ruoyi-common-mybatis`、`ruoyi-common-tenant`、`ruoyi-common-excel` | `ruoyi-common/ruoyi-common-flow/pom.xml` |
| `ruoyi-common-flowable` | `ruoyi-common-core`、`ruoyi-common-satoken` | `ruoyi-common/ruoyi-common-flowable/pom.xml` |
| `ruoyi-common-idempotent` | `ruoyi-common-json`、`ruoyi-common-redis` | `ruoyi-common/ruoyi-common-idempotent/pom.xml` |
| `ruoyi-common-iot` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-iot/pom.xml` |
| `ruoyi-common-job` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-job/pom.xml` |
| `ruoyi-common-json` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-json/pom.xml` |
| `ruoyi-common-log` | `ruoyi-common-satoken`、`ruoyi-common-json` | `ruoyi-common/ruoyi-common-log/pom.xml` |
| `ruoyi-common-mail` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-mail/pom.xml` |
| `ruoyi-common-mq` | `ruoyi-common-core`、`ruoyi-common-redis`、`ruoyi-common-tenant`、`ruoyi-common-mybatis`、`ruoyi-common-job` | `ruoyi-common/ruoyi-common-mq/pom.xml` |
| `ruoyi-common-mybatis` | `ruoyi-common-core`、`ruoyi-common-satoken` | `ruoyi-common/ruoyi-common-mybatis/pom.xml` |
| `ruoyi-common-oss` | `ruoyi-common-json`、`ruoyi-common-redis` | `ruoyi-common/ruoyi-common-oss/pom.xml` |
| `ruoyi-common-ratelimiter` | `ruoyi-common-core`、`ruoyi-common-redis` | `ruoyi-common/ruoyi-common-ratelimiter/pom.xml` |
| `ruoyi-common-redis` | `ruoyi-common-core` | `ruoyi-common/ruoyi-common-redis/pom.xml` |
| `ruoyi-common-satoken` | `ruoyi-common-core`、`ruoyi-common-redis` | `ruoyi-common/ruoyi-common-satoken/pom.xml` |
| `ruoyi-common-security` | `ruoyi-common-satoken` | `ruoyi-common/ruoyi-common-security/pom.xml` |
| `ruoyi-common-sensitive` | `ruoyi-common-json` | `ruoyi-common/ruoyi-common-sensitive/pom.xml` |
| `ruoyi-common-sign` | `ruoyi-common-core`、`ruoyi-common-mybatis`、`ruoyi-common-tenant`、`ruoyi-common-redis`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-log`、`ruoyi-common-idempotent`、`ruoyi-common-oss`、`ruoyi-common-translation`、`ruoyi-common-sms` | `ruoyi-common/ruoyi-common-sign/pom.xml` |
| `ruoyi-common-sms` | `ruoyi-common-redis` | `ruoyi-common/ruoyi-common-sms/pom.xml` |
| `ruoyi-common-social` | `ruoyi-common-json`、`ruoyi-common-redis` | `ruoyi-common/ruoyi-common-social/pom.xml` |
| `ruoyi-common-sse` | `ruoyi-common-core`、`ruoyi-common-redis`、`ruoyi-common-satoken`、`ruoyi-common-json` | `ruoyi-common/ruoyi-common-sse/pom.xml` |
| `ruoyi-common-tenant` | `ruoyi-common-mybatis` (optional)、`ruoyi-common-redis` | `ruoyi-common/ruoyi-common-tenant/pom.xml` |
| `ruoyi-common-translation` | `ruoyi-common-json` | `ruoyi-common/ruoyi-common-translation/pom.xml` |
| `ruoyi-common-web` | `ruoyi-common-json` | `ruoyi-common/ruoyi-common-web/pom.xml` |
| `ruoyi-common-websocket` | `ruoyi-common-core`、`ruoyi-common-redis`、`ruoyi-common-satoken`、`ruoyi-common-json` | `ruoyi-common/ruoyi-common-websocket/pom.xml` |
| `ruoyi-demo` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-sms`、`ruoyi-common-mail`、`ruoyi-common-redis`、`ruoyi-common-idempotent`、`ruoyi-common-mybatis`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-ratelimiter`、`ruoyi-common-translation`、`ruoyi-common-sensitive`、`ruoyi-common-encrypt`、`ruoyi-common-tenant`、`ruoyi-common-websocket` | `ruoyi-modules/ruoyi-demo/pom.xml` |
| `ruoyi-erp` | `ruoyi-common-core`、`ruoyi-common-ai`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-translation`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-idempotent`、`ruoyi-common-sensitive`、`ruoyi-common-job`、`ruoyi-flowable`、`ruoyi-common-mq`、`ruoyi-common-cv`、`ruoyi-common-sign`、`ruoyi-system` | `ruoyi-modules/ruoyi-erp/pom.xml` |
| `ruoyi-extend` | 无仓内直接依赖 | `ruoyi-extend/pom.xml` |
| `ruoyi-flow` | `ruoyi-common-flow`、`ruoyi-common-ai`、`ruoyi-ai`、`ruoyi-common-sse`、`ruoyi-common-web`、`ruoyi-common-doc`、`ruoyi-common-mq`、`ruoyi-common-oss`、`ruoyi-common-mail`、`ruoyi-common-sms`、`ruoyi-common-job` | `ruoyi-modules/ruoyi-flow/pom.xml` |
| `ruoyi-flowable` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-translation`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-idempotent`、`ruoyi-common-flowable`、`ruoyi-common-sign`、`ruoyi-system`、`ruoyi-generator`、`ruoyi-online` | `ruoyi-modules/ruoyi-flowable/pom.xml` |
| `ruoyi-generator` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-web`、`ruoyi-common-log`、`ruoyi-common-idempotent`、`ruoyi-common-tenant` | `ruoyi-modules/ruoyi-generator/pom.xml` |
| `ruoyi-im` | `ruoyi-common-core`、`ruoyi-common-json`、`ruoyi-common-mybatis`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-doc`、`ruoyi-common-log`、`ruoyi-common-websocket`、`ruoyi-common-sse`、`ruoyi-system` | `ruoyi-modules/ruoyi-im/pom.xml` |
| `ruoyi-iot` | `ruoyi-common-core`、`ruoyi-common-iot`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-translation`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-idempotent`、`ruoyi-common-sensitive`、`ruoyi-common-redis`、`ruoyi-common-sse`、`ruoyi-common-job`、`ruoyi-system` | `ruoyi-modules/ruoyi-iot/pom.xml` |
| `ruoyi-job` | `ruoyi-common-json`、`ruoyi-common-job` | `ruoyi-modules/ruoyi-job/pom.xml` |
| `ruoyi-mes` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-translation`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-idempotent`、`ruoyi-common-sensitive`、`ruoyi-common-sign`、`ruoyi-common-job`、`ruoyi-flowable`、`ruoyi-common-mq`、`ruoyi-common-sse`、`ruoyi-common-iot`、`ruoyi-common-redis`、`ruoyi-system` | `ruoyi-modules/ruoyi-mes/pom.xml` |
| `ruoyi-modules` | 无仓内直接依赖 | `ruoyi-modules/pom.xml` |
| `ruoyi-monitor-admin` | 无仓内直接依赖 | `ruoyi-extend/ruoyi-monitor-admin/pom.xml` |
| `ruoyi-online` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-sms`、`ruoyi-common-mail`、`ruoyi-common-redis`、`ruoyi-common-idempotent`、`ruoyi-common-mybatis`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-ratelimiter`、`ruoyi-common-translation`、`ruoyi-common-sensitive`、`ruoyi-common-encrypt`、`ruoyi-common-tenant`、`ruoyi-common-websocket`、`ruoyi-generator` | `ruoyi-modules/ruoyi-online/pom.xml` |
| `ruoyi-pay` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-idempotent`、`ruoyi-common-mq` | `ruoyi-modules/ruoyi-pay/pom.xml` |
| `ruoyi-report` | `ruoyi-common-core`、`ruoyi-common-satoken`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-tenant` | `ruoyi-modules/ruoyi-report/pom.xml` |
| `ruoyi-snailjob-server` | 无仓内直接依赖 | `ruoyi-extend/ruoyi-snailjob-server/pom.xml` |
| `ruoyi-station` | `ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-log`、`ruoyi-common-tenant`、`ruoyi-common-web`、`ruoyi-system`、`ruoyi-mes` | `ruoyi-modules/ruoyi-station/pom.xml` |
| `ruoyi-system` | `runtime-attestation-agent`、`ruoyi-common-core`、`ruoyi-common-doc`、`ruoyi-common-mybatis`、`ruoyi-common-translation`、`ruoyi-common-oss`、`ruoyi-common-log`、`ruoyi-common-excel`、`ruoyi-common-sms`、`ruoyi-common-tenant`、`ruoyi-common-security`、`ruoyi-common-web`、`ruoyi-common-idempotent`、`ruoyi-common-sensitive`、`ruoyi-common-encrypt`、`ruoyi-common-websocket`、`ruoyi-common-sse`、`ruoyi-common-sign` | `ruoyi-modules/ruoyi-system/pom.xml` |

## 主应用业务模块范围

直接：`ruoyi-ai`、`ruoyi-erp`、`ruoyi-flow`、`ruoyi-flowable`、`ruoyi-generator`、`ruoyi-im`、`ruoyi-iot`、`ruoyi-job`、`ruoyi-mes`、`ruoyi-pay`、`ruoyi-report`、`ruoyi-station`、`ruoyi-system`

按声明的非 optional compile/runtime 路径可达：`ruoyi-ai`、`ruoyi-erp`、`ruoyi-flow`、`ruoyi-flowable`、`ruoyi-generator`、`ruoyi-im`、`ruoyi-iot`、`ruoyi-job`、`ruoyi-mes`、`ruoyi-online`、`ruoyi-pay`、`ruoyi-report`、`ruoyi-station`、`ruoyi-system`

这是静态声明路径，不等于执行了 Maven resolve、构建成功、实际加载或生产部署。

MES 与 ERP 的运行桥接不代表 POM 中存在 MES → ERP；AI 使用可信 ERP 端口也不代表存在 AI → ERP 的直接 Maven 依赖。
