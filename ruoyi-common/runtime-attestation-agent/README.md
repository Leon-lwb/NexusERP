# 签名发布与真正 premain 加载见证

## 本地开发验收范围（2026-10-04更新）

用户已明确当前只是本机开发测试、无真实用户，不要求本模块的签发/JVM防篡改体系作为业务开发前置。预检与执行transport现在默认采用 `local-dev` 服务/进程/四库一致性检查，允许IDE/classpath/调试agent，不需要部署正式根或本agent。具体见 `doc/ai/full-flow/scripts/procurement_local_runtime.py` 和本地运行简化复验报告。

本模块的NONE/UNPROVEN及最终字节证书限制仍真实存在，但只属于显式 `--runtime-check strict` 的高保证诊断，**不再据此认定本地服务不能进行功能测试**。本地结果LOCAL_DEV_CHECKED不是本模块强证明成功，不构成生产证明或业务写权限；单位、来源、额度、事务、幂等和对账仍须业务校验。

## 当前交付状态

本模块实际核验固定外部根的 Ed25519 签名发布清单，注册真正的 `ClassFileTransformer`，以回调输入作摘要，再用 `Instrumentation.getAllLoadedClasses()` 对账已经成功定义的 `Class`、真实 defining loader 对象与来源身份。不是磁盘/资源读取诊断器。

**仍不提供最终定义字节证书或业务写入授权。** 回调在校验和定义前发生，后续 Java/native transformer 能改写字节；标准 Instrumentation 没有完整链枚举、排他锁或未来注册禁令。因此，即使签名、回调摘要、成功 Class 身份都匹配，`supportedProofDomain=NONE`、`proofStatus=UNPROVEN`、`finalBytesProven=false`，system API 保持业务码 `412`。`LOAD_IDENTITY_CONFIRMED` 只表示有限见证，不是 `VERIFIED`。

隔离测试实际演示了后续 transformer 将运行行为从 `ORIGINAL` 改为 `MUTATED!`，同时早先回调仍与清单匹配。禁止以一个启动标志、JSON 域名、Git HEAD、磁盘摘要、mtime 或 classloader 资源字节消除该限制。

## 文件与信任边界

生产 `premain` 没有 `agentmain`、根路径参数、环境回退或测试根。固定公钥位置为：

- Windows：`C:/ProgramData/Nexus/RuntimeAttestation/release-root.der`
- POSIX：`/etc/nexus/runtime-attestation/release-root.der`

运营方需通过仓库外的受控部署预置 Ed25519 X.509 DER 公钥。模块绝不生成或安装生产私钥/信任锚。根、发布目录、清单、签名和 jar 及其全部父路径必须不可由当前进程写入、无符号链接、通过管理员/root 所有权与写权限检查；权限不明、Windows 本地化管理员名称等无法确认的情况也拒绝。根不是“放在任意只读目录的自签证书”。

发布目录包含独立的 agent jar、全部获准直接加载 jar、`release.manifest`、`release.sig`。签名认证完整清单字节，内存冻结后再使用；jar 摘要只是发布输入认证，绝非已加载字节证明。

测试私钥/公钥只在 JUnit 临时目录或内存生成。签名加载子 JVM 使用仅测试的隔离 premain；生产 premain 的固定根路径不受它影响。测试 premain 不打入 Maven 发布 jar。

## V2 来源边界

`ProcurementRuntimeBoundary` 是发布工具和 system 的唯一 Java 类集定义，Python 契约有对应相等性测试。当前必需类为 25 个；除原采购类外，还覆盖受控上架、出生冻结、PDA分配/写入、IQC消息及整批回写边界。原生出生及V2读回四项为：

- `com.nexus.erp.wm.service.WmReceiptIqcBirthService`
- `com.nexus.mes.qc.service.QcReceiptIqcBirthService`
- `com.nexus.erp.wm.service.WmReceiptIqcEvidenceService`
- `com.nexus.mes.qc.service.QcReceiptIqcEvidenceService`

生产发布格式为 `NEXUS-RELEASE-2`，签名覆盖 `PROCUREMENT_RECEIPT_IQC_BIRTH_V2` 和有序必需类集摘要、发布基线标签、启动类、agent jar、全部应用 jar 和全部 class 摘要。缺任一 Born/V2 读回类即拒绝生成发布清单；即使由正确根签名，缺项的 V2 清单也拒绝解析。通用 V1 清单仅用于有限加载见证，system 不允许将其复用为 V2。

system 报告 `schemaVersion=2`；Python 拒绝 V1、错误边界/类集摘要、有限见证和任何伪造的未来证明域。当前根本没有可通过 JSON 启用的肯定性签发路径，也不会在缺少加载权威时借用数据库连接。

## 发布工具

先由主线程完成受控构建。运行以下显式命令；私钥必须是运营方已有 PKCS#8 DER 文件，输出目录必须存在且不含既有清单/签名：

```text
java -cp <agent.jar> com.nexus.runtime.attestation.ReleaseManifestTool sign-procurement-v2 <既有私钥.der> <40位发布基线标签> <启动类二进制名> <agent.jar> <输出目录> <应用.jar> <其余直接加载jar...>
```

工具只写 `release.manifest` 与 `release.sig`，使用 `CREATE_NEW`，不覆盖既有文件，不输出/安装私钥或根。生成清单不表示部署、加载证明、审批或业务写权限。

## 必要候选启动限制

允许 HotSpot JDK 17/21 的 system loader 直接 jar 模式；当前实际子 JVM 验证环境为 JDK 21，使用 `javac --release 17`。JDK 17 实际执行仍需另验。

必要参数与条件如下；以下只是候选启动形式，**不会启用最终字节证明**：

```text
java -XX:+DisableAttachMechanism -javaagent:<绝对发布目录>/<批准agent.jar>=<同一绝对发布目录> -cp <签名清单中的全部直接加载jar> <签名清单中的启动类>
```

同时通过 `HotSpotDiagnosticMXBean` 核对实际 attach 禁用值。仅允许这一批准的 agent、`-Xms`/`-Xmx` 及显式 `-Dspring.profiles.active=dev`；额外 Java/native agent、调试、bootclasspath/module 覆盖、任意额外 JVM 参数、注入型环境变量、应用参数/类路径覆盖均拒绝。启动属性和 CodeSource 仅用于必要一致性检查，不是不可伪造的来源证明。

不支持 loose classes、Spring Boot 嵌套 jar/custom loader、隐式 `Class-Path`、multi-release jar、重复类/版本覆盖、动态业务类或热重载；现有 Spring/CGLIB 动态定义等不符合模式时也会粘性失效。不会自动重打包或重启当前应用。

见证 transformer 支持观察重转换以拒绝它，本身始终返回 null、不主动重转换/重定义。`classBeingRedefined`、重复定义、错误 loader、未知来源、回调字节不匹配等都会粘性失效。抛异常不能阻止 JVM 定义，因此不将异常伪装成拦截。必需类没有成功加载即为 `PENDING_DEFINITIONS`，不调用 `Class.forName` 强迫加载。

## 真正肯定性来源认证的剩余前置

必须另行实现并审核仓库外可信启动器或 JVM/native 强制机制，认证 agent/JVM 自身及完整加载链，禁止所有未批准 transformer、JVMTI/native hook、native load、attach 和未来重定义/注册，提供不可由应用伪造且绑定该进程/启动身份的证明。它需与固定发布根、不可替换发布文件及加载后身份对账闭合。不能仅新增一个布尔值、域字符串或模块打开参数。

本模块没有该外部强制器，没有生产根部署、JDK17 真机/完整 Spring 应用验证、native hook 验证或业务端到端验收；绝不宣称正式环境已可写。

## 隔离验证

从仓库根目录运行，不触碰共享 target，不调用 Maven，不启动端口或连接真实数据库：

```text
python -B ruoyi-common/runtime-attestation-agent/src/test/run-isolated-tests.py
```

脚本仅使用本地缓存的 JUnit 5.12.2/Platform 1.12.2 依赖，所有编译和 jar/测试根均在独立临时目录。代理单测与子 JVM 测试，以及 system 的加载见证/记录约束/实际 premain 桥接测试均参与；无用例或任一失败均非零退出。

主线程负责全量 Maven 和正式写前复核；建议按仓库规范显式清空 Surefire 环境 Tag 过滤并核对非零 `Tests run`。模块依赖声明不会自动启用 javaagent。

## 实现一次源

实现细节核对 Oracle JDK17 的 `ClassFileTransformer`、`Instrumentation`、`java.lang.instrument` 包说明及 OpenJDK17 启动器一次源。所用 `sun.java.command` 为启动器设置的可变伪属性，只作必要限制，不能作为已加载证明：

```text
https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html
https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html
https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/package-summary.html
https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/native/libjli/java.c
```
