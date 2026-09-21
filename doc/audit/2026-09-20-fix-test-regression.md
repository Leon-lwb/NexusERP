# 测试基建 + 全量回归修复报告（2026-09-20）

> 范围：`feature/erp-mes-iot-audit-fix` 分支全量回归从 **BUILD FAILURE** 恢复为 **BUILD SUCCESS**。
> 目标模块：`ruoyi-erp` / `ruoyi-mes` / `ruoyi-iot` / `ruoyi-station`。
> 本报告未使用任何 `git` 命令；未改动 `script/sql/ry-*.sql`、`plus-ui/**`、`application*.yml`。

## 0. 结论（最终实测）

| 模块 | Tests run | Failures | Errors | Skipped | 结果 |
|---|---|---|---|---|---|
| ruoyi-erp | 410 | 0 | 0 | 0 | SUCCESS |
| ruoyi-mes | 400 | 0 | 0 | 2 | SUCCESS |
| ruoyi-iot | 91 | 0 | 0 | 0 | SUCCESS |
| ruoyi-station | 68 | 0 | 0 | 0 | SUCCESS |
| **合计** | **969** | **0** | **0** | **2** | **BUILD SUCCESS（45.5s）** |

> 注：基线为 `erp 285 / mes 268(2 skip) / iot 15`；本分支新增了大量测试，故总量上升。
> station 基线未单独给出，现为 68 全绿。2 个 skip 为基线既有 `@Disabled`（见 §6）。

## 1. 修复清单

| # | 现象 | 根因 | 处置 | 改动文件 |
|---|---|---|---|---|
| R1 | ~80 例 `ExceptionInInitializerError` / `NoClassDefFoundError: Could not initialize class MapstructUtils`，后跑测试类雪崩 | 纯 Mockito 单测无 Spring 容器，`MapstructUtils.<clinit>`（`SpringUtils.getBean(Converter.class)`）失败；JVM 内失败的 `<clinit>` 是**粘性**的，同一 surefire fork 后续全部报 `NoClassDefFoundError` | 测试基建：新增 ERP 模块静态工具预热（JUnit 5 自动发现，`@BeforeAll` 之前执行，**预热后立即关闭**静态桩） | `ruoyi-erp/src/test/.../testsupport/SpringStaticBootstrap.java`、`SpringStaticBootstrapExtension.java`、`src/test/resources/junit-platform.properties`、`src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`（均新增） |
| R2 | `PuPurchaseRequisitionServiceImplTest.generateFromLowStock_materialStatusComparedNumerically:75` 断言"状态条件应为数值 1L"失败 | MyBatis-Plus 的 `eq/gt` 条件值只在生成 SQL 片段时才物化进 `paramNameValuePairs`，直接读取得到空 Map | 测试：断言前先 `wrapper.getSqlSegment()` 物化；**生产代码 `.eq(BdMaterial::getStatus, 1L)` 本身正确，未改** | `ruoyi-erp/src/test/.../pu/PuPurchaseRequisitionServiceImplTest.java` |
| R3 | `WmStockTakingPlanServiceImplTest.postAdjustment_rejectsWhenActualQuantityNotEntered:89` 期望抛 `ServiceException` 却未抛 | **生产代码缺陷**：ERP-WM-02 的 `assertAllLinesCounted` 守卫只加在"审批自动过账"通道（`afterFlowHandle` 第 446 行），**手工过账 `postAdjustment` 漏加**。未盘点行 `actualQuantity=null` 被当作 0，逐行把库存调整为 0 | **改生产代码**（唯一一处）：在 `postAdjustment` 的明细非空校验后补 `assertAllLinesCounted(lines)` | `ruoyi-erp/src/main/.../wm/service/impl/WmStockTakingPlanServiceImpl.java` |
| R4 | `SoQuotationServiceImplTest.convertToSalesOrder_success...:290` 抛"报价单已被其他用户转换为销售订单" | 该批次把转单改为状态 CAS（`CONFIRMED→CONVERTED`，`baseMapper.update(null, lambdaUpdate)`），测试未桩条件更新返回 1 行，且仍 `verify(updateById)` | 测试：补 `when(baseMapper.update(any(), any())).thenReturn(1)`，断言改为 `verify(baseMapper).update(any(), any())` + `verify(baseMapper, never()).updateById(...)` | `ruoyi-erp/src/test/.../so/SoQuotationServiceImplTest.java` |
| R5 | `SoSalesOrderHeaderServiceImplTest.fulfill_...:325` / `cancel_...:394` `TooManyActualInvocations`（wanted 1, was 2） | ERP-SO-01 新增 `clearLineReservedQty(soId)`：释放预留后额外发一条整单批量条件更新清零 `reserved_qty`。测试的 `times(1)` 期望已过期 | 测试：改为 `times(2)` 并**加强断言**——用 `ArgumentCaptor<Wrapper>` 校验其中一次更新的 `getSqlSet()` 含 `reserved_qty`（不是单纯放宽次数） | `ruoyi-erp/src/test/.../so/SoSalesOrderHeaderServiceImplTest.java` |
| R6 | `PuPurchaseReceiveWritebackServiceImplTest.initStatics:64` `Mockito Cannot instrument class RedisUtils because it or one of its supertypes could not be initialized` | `RedisUtils.CLIENT = SpringUtils.getBean(RedissonClient.class)`，静态初始化同样依赖 Spring 容器；而 Mockito 无法静态打桩一个初始化失败的类 | 测试基建：预热清单加入 `RedisUtils`，并在预热窗口内桩 `SpringUtil.getBean(RedissonClient.class)` 返回 mock | `ruoyi-erp/src/test/.../testsupport/SpringStaticBootstrap.java` |
| R7 | R6 修复后该类 6 个用例开始真正执行，`writeBack_exactQuantity_succeedsAndWritesIdempotentKey:127` 报"zero interactions" | 潜伏的**用例隔离缺陷**：`mockStatic(RedisUtils)` 建在 `@BeforeAll`，`writeBack_alreadyWritten_skips` 的 `getCacheObject → "1"` 桩**跨用例泄漏**，使后续用例被误判为"已回写"而提前返回 | 测试：RedisUtils 静态桩改为 `@BeforeEach` 开启 / `@AfterEach` 关闭，逐用例隔离 | `ruoyi-erp/src/test/.../pu/PuPurchaseReceiveWritebackServiceImplTest.java` |
| R8 | MES `TgMaintenanceDueTaskTest.fallbackSkipsWhenSnailJobEnabled:69` / `...LockHeldByAnotherInstance:88` 报 `static mocking is already registered in the current thread` | MES 既有 `SpringStaticBootstrap` **故意常驻不关** `mockStatic(SpringUtil.class)`；Mockito 不允许同线程对同一类重复注册静态桩，导致自建 `mockStatic(SpringUtil.class)` 的测试直接报错（`EqSchedulerFallbackTest` 是同类潜伏地雷，只是执行顺序恰好躲过） | 测试基建：改造为与 ERP 一致的"**预热后立即关闭**"；预热完成后各工具类静态字段已持有可用实例，不再需要常驻桩 | `ruoyi-mes/src/test/.../testsupport/SpringStaticBootstrap.java` |

### 1.1 关键设计说明：毒化机制与"静态桩必须用完即关"

三个工具类都是同一种危险写法：

```java
public class MapstructUtils { private final static Converter CONVERTER = SpringUtils.getBean(Converter.class); }
public class JsonUtils      { private static final ObjectMapper OBJECT_MAPPER = SpringUtils.getBean(ObjectMapper.class); }
public class RedisUtils     { private static final RedissonClient CLIENT = SpringUtils.getBean(RedissonClient.class); }
```

两个性质叠加，决定了正确解法：

1. **失败的 `<clinit>` 在 JVM 内粘性**：一旦某个测试类在没有 Spring 容器（也没有静态桩）的窗口里首次触碰它们，`ExceptionInInitializerError` 之后同 fork 内所有访问都变成 `NoClassDefFoundError`——这就是"一个测试踩雷、整模块雪崩"的成因。
2. **Mockito 禁止同线程重复注册同一类的静态桩**（`static mocking is already registered in the current thread`）。因此"常驻一个 SpringUtil 静态桩"这种看似省事的做法，会反过来打死那些需要自建 `mockStatic(SpringUtil.class)` 的测试——这正是 MES R8 的成因。

于是统一采用：**在 SpringUtil 静态桩窗口内主动触发这些工具类的静态初始化，然后立即关闭静态桩**。
预热完成后 `CONVERTER` / `OBJECT_MAPPER` / `CLIENT` 已持有可用实例（BeanUtil 同名属性拷贝 / 真实 Jackson / mock RedissonClient），静态初始化不会再失败，也不再需要 SpringUtil 桩。

ERP 侧通过 `SpringStaticBootstrapExtension`（`BeforeAllCallback`）+ `junit.jupiter.extensions.autodetection.enabled=true` 自动注册，
使预热**与测试类执行顺序无关**——彻底消除"surefire 文件系统顺序变化就再次雪崩"的脆弱性。

> 未采用 `forkCount=1 reuseForks=false`：那是用"每类一个新 JVM"掩盖根因，代价是测试时间成倍增长，且不解决任何真实问题。

### 1.2 生产代码改动（仅 1 处）

| 文件 | 改动 | 理由 |
|---|---|---|
| `ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockTakingPlanServiceImpl.java` | `postAdjustment` 中明细非空校验后新增 `assertAllLinesCounted(lines);` | 测试暴露的是**真实生产缺陷**：ERP-WM-02 的守卫只覆盖审批自动过账通道，手工过账通道漏加。未盘点（`actualQuantity=null`）被当作 0，一次点击即可把整仓库存逐行清零，且单据状态变为 POSTED，账实核对无法发现误操作。守卫方法 `assertAllLinesCounted` 已存在且已在另一通道使用，此处为补齐调用，无新增逻辑。 |

## 2. 未修复 / 部分修复项（含理由与建议）

| 项 | 说明与建议 |
|---|---|
| MES 侧未加自动发现扩展 | ERP 已用 `SpringStaticBootstrapExtension` 做到顺序无关；MES 仍靠 2 个测试类显式调用 `ensureActive()`。当前 MES 全绿，未扩大改动面。**建议**：后续给 `ruoyi-mes`（及 `ruoyi-iot`）补同样的 `junit-platform.properties` + Extension，消除顺序脆弱性。 |
| `ruoyi-iot` 未做静态桩治理 | IoT 测试未出现毒化（91 全绿），但 `IotSchedulerFallbackTest` 同样自建 `mockStatic(SpringUtil.class)`，属同类脆弱点，建议后续统一。 |
| `NcrOrderServiceImplTest` 2 个 `@Disabled` | 基线既有，本次未启用（见 §6）。预热已让 `JsonUtils` 可安全初始化，**理论上具备启用条件**，建议后续单开一次验证后启用。 |
| 无 DDL / SQL / 前端改动 | 本次为测试基建与测试断言修复，§3、§4 不适用。 |

## 3. 主脚本合并清单

**不适用**：本次未改动任何 `script/sql/ry-*.sql`。

## 4. DDL 登记

**不适用**：无数据库结构变更。

## 5. 毒化源定位过程

**步骤 1 — 证据收集（surefire 报告）**：按报告文件写入时间排序，找最早出现 `ExceptionInInitializerError` 的类。

```
com.nexus.erp.bd.BdBomDetailServiceImplTest.txt     2026/9/20 21:25:22  HasEIIE=True  HasNCDF=False   ← 最早，且只此一类
com.nexus.erp.bd.BdRoutingServiceImplTest.txt       2026/9/20 21:25:23  HasEIIE=True  HasNCDF=True
com.nexus.erp.fin.arap.FinReceiptServiceImplTest.txt 2026/9/20 21:25:23 HasEIIE=True  HasNCDF=True
...（其余 17 个类均为 EIIE+NCDF 混合，属雪崩受害者）
```

`BdBomDetailServiceImplTest` 是**唯一**只出现 `ExceptionInInitializerError`（无 `NoClassDefFoundError`）的类 ⇒ 它就是首次触发者。

**步骤 2 — 读堆栈确认真实触发点**：

```
java.lang.ExceptionInInitializerError
    at com.nexus.erp.bd.service.impl.BdBomDetailServiceImpl.lambda$saveBatch$0(BdBomDetailServiceImpl.java:167)
    at com.nexus.erp.bd.BdBomDetailServiceImplTest.saveBatch_noWorkOrderReference_succeeds(BdBomDetailServiceImplTest.java:94)
Caused by: cn.hutool.core.exceptions.UtilException: No ConfigurableListableBeanFactory or ApplicationContext injected...
    at cn.hutool.extra.spring.SpringUtil.getBeanFactory(SpringUtil.java:76)
    at cn.hutool.extra.spring.SpringUtil.getBean(SpringUtil.java:122)
    at com.nexus.common.core.utils.MapstructUtils.<clinit>(MapstructUtils.java:23)
```

**步骤 3 — 排除"静态桩窗口被抢占"假设**：`BdBomDetailServiceImplTest` 中**根本没有** `mockStatic(SpringUtil.class)`
（grep ERP 测试树，17 个类有该桩，不含本类）。所以它不是在桩窗口内失败，而是**在完全无桩的窗口里首次触碰 `MapstructUtils`**，
导致真实 `SpringUtil.getBeanFactory()` 抛 `UtilException`。

**步骤 4 — 确认雪崩方向**：`saveBatch` 的调用点 `BdBomDetailServiceImpl.java:167` 是 `list.stream().map(bo -> MapstructUtils.convert(...))`。
即该测试类一旦先跑，`MapstructUtils` 的 `<clinit>` 即告失败并粘住；后续 17 个类即使正确地在自己的桩窗口里初始化，也只会拿到 `NoClassDefFoundError`。

**步骤 5 — 对照可用范式**：`BdRoutingServiceImplTest` / `SoQuotationServiceImplTest` 等 17 个类的做法是
"在 `@BeforeAll` 里 `mockStatic(SpringUtil.class)` + 桩 `Converter` 为 `BeanUtil.copyProperties`"，
与 MES 模块 `com.nexus.mes.testsupport.SpringStaticBootstrap` 思路一致 —— 但这仍**依赖执行顺序**（谁是第一个触碰者）。

**步骤 6 — 定根因并修复**：根因是"纯单测 JVM 内 `MapstructUtils` 无法安全完成静态初始化"，
而修复必须同时满足两个约束：① 必须在任何测试类之前完成；② 不能常驻静态桩（Mockito 禁止重复注册，见 R8）。
故采用 **自动发现的 `BeforeAllCallback` + 预热后立即关闭**（§1.1）。

**步骤 7 — 验证**：修复毒化源后重跑 ERP：`405 / 24F / 60E` → `405 / 2F / 1E`，
证明 24F/60E 中约 80 例确为毒化雪崩，仅剩 3 例为独立真失败（即 R2/R3/R4）。

## 6. @Disabled 清单

全量回归中 `Skipped: 2`，来源唯一，均为**基线既有**（本次未新增任何 `@Disabled`）：

| 文件 | 用例 | 原因（原文） |
|---|---|---|
| `ruoyi-mes/src/test/java/com/nexus/mes/ncr/service/impl/NcrOrderServiceImplTest.java:124` | defectInfo 解析用例 | `JsonUtils 需 Spring 上下文; defectInfo 解析+defectCodeId null 回归由 E2E 覆盖(28/28)` |
| `ruoyi-mes/src/test/java/com/nexus/mes/ncr/service/impl/NcrOrderServiceImplTest.java:130` | 畸形 JSON 安全用例 | `JsonUtils 需 Spring 上下文; 畸形 JSON 安全回归由 E2E 覆盖` |

> `ruoyi-erp` / `ruoyi-iot` / `ruoyi-station` 测试树内无 `@Disabled` / `@Ignore`。
> 无"因跨批次业务缺陷而新标记 `@Disabled`"的用例；R3 已按任务要求改生产代码而非跳过测试。

## 7. 验证记录

### 7.1 执行命令

全量（四模块）：

```cmd
mvn -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-iot,ruoyi-modules/ruoyi-station ^
    -am test -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false"
```

单模块定位用 `-pl ruoyi-modules/ruoyi-<mod> -am test -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false"`。

> **环境坑（已规避）**：本机 pwsh 包装层会在 `.` 处拆分参数，直接执行上述命令会得到
> `Unknown lifecycle phase ".failIfNoSpecifiedTests=false"`。
> 规避方式：把命令写进 `.cmd` 文件后用 `cmd /c` 执行（与 `doc/audit/2026-09-20-fix-sch-cv.md` §5.5 记载一致）。

### 7.2 执行结果（实测通过）

| 阶段 | erp | mes | iot | station | 整体 |
|---|---|---|---|---|---|
| 修复前（原始基线报告） | 405 / **24F / 60E** | 未跑 | 未跑 | 未跑 | BUILD FAILURE |
| 修复毒化源 + R2/R3/R4 后 | 405 / 2F / 1E | 未跑 | 未跑 | 未跑 | BUILD FAILURE |
| 修复 R5/R6 后 | 410 / 0F / 1E | 未跑 | 未跑 | 未跑 | BUILD FAILURE |
| 修复 R7/R8 后（最终全量） | **410 / 0 / 0** | **400 / 0 / 0（2 skip）** | **91 / 0 / 0** | **68 / 0 / 0** | **BUILD SUCCESS** |

最终日志尾部：

```
[INFO] ruoyi-erp .......................................... SUCCESS [  9.855 s]
[INFO] ruoyi-mes .......................................... SUCCESS [ 16.962 s]
[INFO] ruoyi-station ...................................... SUCCESS [  3.530 s]
[INFO] ruoyi-iot .......................................... SUCCESS [  3.019 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  45.536 s
```

### 7.3 变更文件清单（11 个）

**新增（测试基建，4）**
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/testsupport/SpringStaticBootstrap.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/testsupport/SpringStaticBootstrapExtension.java`
- `ruoyi-modules/ruoyi-erp/src/test/resources/junit-platform.properties`
- `ruoyi-modules/ruoyi-erp/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`

**修改（测试，6）**
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/bd/BdBomDetailServiceImplTest.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/pu/PuPurchaseRequisitionServiceImplTest.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/pu/PuPurchaseReceiveWritebackServiceImplTest.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/so/SoQuotationServiceImplTest.java`
- `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/so/SoSalesOrderHeaderServiceImplTest.java`
- `ruoyi-modules/ruoyi-mes/src/test/java/com/nexus/mes/testsupport/SpringStaticBootstrap.java`

**修改（生产，1）**
- `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockTakingPlanServiceImpl.java`

## 8. 风险与回滚说明

### 8.1 风险

| 风险 | 评估 | 缓解 |
|---|---|---|
| 预热用的 `Converter` 是"同名属性拷贝"通用实现，与 MapStruct 生成器行为在**复杂/嵌套映射**上可能不同 | 低。ERP 原有 17 个测试类早已使用完全相同的实现；且 `MapstructUtils` 一旦完成初始化即固定，预热不改变既有测试的桩语义 | 若未来有测试需要真实 MapStruct 映射，应改为注入真实 `Converter` 实现（MapStruct Plus 的 `ConverterImpl`），而非在预热里放宽 |
| 预热后静态桩立即关闭，若某测试**运行期**依赖 `SpringUtil.getBean(X)` 返回 null | 低。预热前后 ERP 410 全绿、MES 400 全绿（含改造前就依赖常驻桩的场景）实测无回归 | 已用全量回归实测覆盖；MES R8 的失败恰好证明"常驻桩"才是风险源 |
| `junit.jupiter.extensions.autodetection.enabled=true` 会启用 ServiceLoader 发现 | 低。ERP 测试类路径下仅注册了本扩展；实测 410 用例全绿 | 如需收敛，可改为在受影响测试类上显式 `@ExtendWith(SpringStaticBootstrapExtension.class)` |
| 新增测试资源文件可能与既有 surefire 配置冲突 | 无。根 pom 仅配置 `<groups>` / `<excludedGroups>`，未覆盖 `junit-platform.properties` | 实测 BUILD SUCCESS |

### 8.2 回滚

- 测试基建（4 个新增文件）：删除即可；删除后 ERP 会回到"surefire 文件顺序决定是否雪崩"的状态，**不建议**。
- 测试断言（6 个文件）：逐文件还原即可，但会重新引入 R2/R4/R5/R7 失败。
- 生产代码（1 处，`WmStockTakingPlanServiceImpl.postAdjustment` 的 `assertAllLinesCounted(lines);`）：
  **不建议回滚**——回滚即恢复"未盘点也能一键把整仓库存清零"的 P0 缺陷（ERP-WM-02）。
- 全量回滚到修复前状态等价于恢复 `erp 405 / 24F / 60E / BUILD FAILURE`。
