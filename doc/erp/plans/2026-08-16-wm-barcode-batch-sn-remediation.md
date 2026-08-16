# wm 条码/批次/序列号 关联修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `ruoyi-erp` wm 仓储域中条码(WmBarcode)/批次(WmBatch)/序列号(WmSn)三者的关联缺陷（分析报告问题 1-7）：SN↔条码实体贯通、SN 批量生成关联批次、库存按 SN 维度落行、批次/SN 序号并发竞态、库存事务保留 SN 维度、手工录入自动编号与基础校验。

**Architecture:** 后端在现有 wm 包内做最小改动：`DailyCodeSequenceProvider`（Redisson 锁 + Redis 原子自增）统一批次/SN 序号生成并消除初始化竞态；`IWmBarcodeService` 生成系列方法增加 `snId` 参数贯通"一条码一 SN"；入库执行器 `buildStockKey/selectOneStock` 加入 `snId` 维度使 SN 物料按 SN 独立落行；`wm_material_stock_tx` 增加 `sn_id/sn_code` 冗余字段；前端补齐条码生成弹窗（snId）与 SN 批量生成弹窗（批次）。全部按"失败测试 → 实现 → 通过 → 提交"推进，测试沿用现有 Mockito + `@Tag("dev")` 风格。

**Tech Stack:** Java 21 / Spring Boot 3 / MyBatis-Plus / Redisson(Lock4j 同源 RLock) / Mockito 5 (mockStatic) / Vue 3 + Element Plus + TypeScript / MySQL 8

**问题 → 任务映射**
| 问题 | 任务 |
|---|---|
| ③ 库存唯一键漏 snId（最危险） | Task 1 |
| ① SN↔条码实体未打通 | Task 2 |
| ② SN 批量生成不关联批次 | Task 3 |
| ④ 批次/SN 序号并发竞态（已实测 uk 冲突） | Task 4 |
| ⑥ 库存事务表缺 SN 维度 | Task 5 |
| ⑦ insertByBo 不自动编号/不校验 | Task 6 |
| ⑤ 前端生成弹窗缺字段（合并进 2/3） | Task 2、Task 3 |
| 全链路回归验证 | Task 7 |

**基线事实（已核实，勿再猜测）**
- `wm_barcode.sn_id` 列存在（实体 `WmBarcode.snId`），`WmBarcodeBo.snId` 存在，**`WmBarcodeVo` 缺 snId** —— 需补。
- `generateAndSave / generateAndSaveByCode` 生产调用点仅两处：`WmMaterialReceiveExecutorServiceImpl:176`、`WmBarcodeController:149/176`。
- `wm_sn` 已有 `batch_id/batch_code` 列（`WmSn.batchId/batchCode`），`WmSnBo` 已有 `batchId/batchCode/quantity`。
- `wm_material_stock` **无唯一索引**（数据多行同物料+同批+虚拟库位），按 SN 落行安全；`copyStock` 已复制 snId/snCode（548-550 行）。
- 虚拟库位行由 `createVirtualFallbackStock`（copyStock→含 snId）创建，`virtualCondition=copyStockCondition(stock)`（同样含 snId）——只要 `selectOneStock` 按 snId 过滤，虚拟/目标两侧一致，不会产生双计。
- 批次号 `generateBatchCode`（WmBatchServiceImpl:259-285）与 SN `batchGenerate`（WmSnServiceImpl:204-223）均有 `setObjectIfAbsent(0)` → `setCacheObject(base)` 竞态窗口。
- `WmBatchServiceImplTest` 已过期：反射访问已不存在的静态字段 `dailySequence/lastDateStr`，运行必抛 `NoSuchFieldException`（Task 4 整文件重写）。
- 前端 `batchGenerateSn` 当前只传 materialId/quantity；`generateBarcode` 已支持 batchId/stockId，缺 snId。

---

### Task 0: 数据库迁移 + 实体/VO 补字段

**Files:**
- Create: `script/sql/update/20260816_wm_sn_barcode_tx_fix.sql`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMaterialStockTx.java`（batch_code 字段后追加 sn_id/sn_code）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/WmBarcodeVo.java`（stockId 字段后追加 snId）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBarcodeServiceImpl.java:250-260`（buildQueryWrapper 增加 batchId/snId 过滤）

- [ ] **Step 1: 写迁移 SQL**

`script/sql/update/20260816_wm_sn_barcode_tx_fix.sql`:

```sql
-- 2026-08-16 wm 条码/批次/序列号关联修复（问题⑥：库存事务增加 SN 维度，按 SN 可追溯出入库流水）
ALTER TABLE `wm_material_stock_tx`
    ADD COLUMN `sn_id`   bigint      NULL COMMENT '序列号ID' AFTER `batch_code`,
    ADD COLUMN `sn_code` varchar(64) NULL COMMENT '序列号编码' AFTER `sn_id`,
    ADD INDEX `idx_wm_stock_tx_sn_id` (`sn_id`);
```

- [ ] **Step 2: 实体补字段**

`WmMaterialStockTx.java` 在 `batchCode`（第 85 行）之后插入：

```java
    /**
     * 序列号ID
     */
    private Long snId;

    /**
     * 序列号编码
     */
    @ExcelProperty("序列号编码")
    private String snCode;
```

- [ ] **Step 3: VO 补字段**

`WmBarcodeVo.java` 在 `stockId` 字段（第 83 行）之后插入：

```java
    /**
     * 序列号ID
     */
    private Long snId;
```

- [ ] **Step 4: 条码列表查询支持 batchId/snId 过滤**

`WmBarcodeServiceImpl.java` 的 `buildQueryWrapper` 在 `lqw.eq(bo.getStockId() != null, ...)` 行后追加：

```java
        lqw.eq(bo.getBatchId() != null, WmBarcode::getBatchId, bo.getBatchId());
        lqw.eq(bo.getSnId() != null, WmBarcode::getSnId, bo.getSnId());
```

- [ ] **Step 5: 编译验证**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests`
Expected: BUILD SUCCESS（无编译错误）

- [ ] **Step 6: Commit**

```bash
git add script/sql/update/20260816_wm_sn_barcode_tx_fix.sql \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMaterialStockTx.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/WmBarcodeVo.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBarcodeServiceImpl.java
git commit -m "fix(wm): 库存事务与条码VO补齐SN维度字段，条码列表支持批次/SN过滤"
```

---

### Task 1: 库存唯一键与查询加入 SN 维度（问题③）

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java:381-392`（buildStockKey）
- Modify: 同上 `:587-602`（selectOneStock）
- Create: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmMaterialReceiveStockTest.java`

**背景**：`buildStockKey` 不含 snId → 同一批次+同一库位的多个 SN 在 `putUniqueStock` 去重时被合并成一条库存（数量叠加、SN 丢失）；`selectOneStock` 不按 snId 过滤 → 转储时可能命中别的 SN 的库存行。虚拟库位行经 `copyStock` 已带 snId，两侧对称，加入过滤后自洽。

- [ ] **Step 1: 写失败测试**

`WmMaterialReceiveStockTest.java`（完整文件）：

```java
package com.nexus.erp.wm;

import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.impl.WmMaterialReceiveExecutorServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 入库执行器：库存唯一键 / 库存查询 的 SN 维度单元测试（问题③回归）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmMaterialReceiveStockTest {

    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private WmMaterialStockTxMapper materialStockTxMapper;

    @InjectMocks
    private WmMaterialReceiveExecutorServiceImpl executor;

    /** 同批次同库位、不同 SN 的库存必须落成不同的行（唯一键含 snId） */
    @Test
    void buildStockKey_differsBySnId() throws Exception {
        WmMaterialStock s1 = stockWithSn(100L, 9L, 2L, 77L);
        WmMaterialStock s2 = stockWithSn(100L, 9L, 2L, 78L);
        WmMaterialStock s3 = stockWithSn(100L, 9L, 2L, null);

        String k1 = buildStockKey(s1);
        String k2 = buildStockKey(s2);
        String k3 = buildStockKey(s3);

        assertNotEquals(k1, k2, "不同SN必须生成不同库存唯一键");
        assertNotEquals(k1, k3, "有SN与无SN必须生成不同库存唯一键");
        assertNotNull(k1);
    }

    /** selectOneStock 必须按 snId 过滤，避免命中其他 SN 的库存行 */
    @Test
    void selectOneStock_filtersBySnId() throws Exception {
        WmMaterialStock condition = stockWithSn(100L, 9L, 2L, 77L);
        when(materialStockMapper.selectOne(any())).thenReturn(new WmMaterialStock());

        Method m = WmMaterialReceiveExecutorServiceImpl.class
            .getDeclaredMethod("selectOneStock", WmMaterialStock.class);
        m.setAccessible(true);
        m.invoke(executor, condition);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<WmMaterialStock>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(materialStockMapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("sn_id"), "查询条件必须包含 sn_id，实际: " + sql);
    }

    private WmMaterialStock stockWithSn(Long materialId, Long batchId, Long warehouseId, Long snId) {
        WmMaterialStock s = new WmMaterialStock();
        s.setMaterialId(materialId);
        s.setBatchId(batchId);
        s.setWarehouseId(warehouseId);
        s.setSnId(snId);
        s.setStockQuantity(new BigDecimal("1"));
        return s;
    }

    private String buildStockKey(WmMaterialStock stock) throws Exception {
        Method m = WmMaterialReceiveExecutorServiceImpl.class.getDeclaredMethod("buildStockKey", WmMaterialStock.class);
        m.setAccessible(true);
        return (String) m.invoke(executor, stock);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=WmMaterialReceiveStockTest -DfailIfNoTests=false`
Expected: FAIL —— `buildStockKey_differsBySnId`（当前唯一键不含 snId，k1==k2）；`selectOneStock_filtersBySnId`（sql 段无 `sn_id`）

- [ ] **Step 3: buildStockKey 增加 snId 段**

`buildStockKey` 方法体改为（在 batchId 段后追加 snId 段）：

```java
    private String buildStockKey(WmMaterialStock stock) {
        return String.join("|",
            String.valueOf(stock.getMaterialId()),
            stock.getBatchId() != null ? stock.getBatchId().toString() : "",
            stock.getSnId() != null ? stock.getSnId().toString() : "",
            stock.getWarehouseId() != null ? stock.getWarehouseId().toString() : "",
            stock.getAreaId() != null ? stock.getAreaId().toString() : "",
            stock.getLocationId() != null ? stock.getLocationId().toString() : "",
            stock.getVendorId() != null ? stock.getVendorId().toString() : "",
            stock.getWoId() != null ? stock.getWoId().toString() : "",
            stock.getProductionDate() != null ? stock.getProductionDate().toString() : ""
        );
    }
```

- [ ] **Step 4: selectOneStock 增加 snId 过滤**

在 `selectOneStock` 的 `lqw.eq(condition.getBatchId() != null, ...)` 行后追加：

```java
        lqw.eq(condition.getSnId() != null, WmMaterialStock::getSnId, condition.getSnId());
```

- [ ] **Step 5: 运行测试确认通过**

Run: 同 Step 2 命令
Expected: PASS（2 个测试绿）

- [ ] **Step 6: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmMaterialReceiveStockTest.java
git commit -m "fix(wm): 库存唯一键与查询加入SN维度，SN物料按SN独立落行(问题③)"
```

---

### Task 2: 条码生成链路贯通 snId（问题① + 问题⑤条码前端）

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmBarcodeService.java:70,77`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBarcodeServiceImpl.java:136-197`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmBarcodeController.java:139-177`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java:174-186`
- Create: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmBarcodeServiceImplTest.java`
- Modify: `plus-ui/src/api/erp/wm/barcode/index.ts:107-117`
- Modify: `plus-ui/src/views/erp/wm/barcode/index.vue`（生成弹窗/查看详情/查询参数）

- [ ] **Step 1: 写失败测试（后端）**

`WmBarcodeServiceImplTest.java`（完整文件）：

```java
package com.nexus.erp.wm;

import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.wm.barcode.BarcodeGenerator;
import com.nexus.erp.wm.barcode.BarcodeRule;
import com.nexus.erp.wm.barcode.BarcodeSequenceProvider;
import com.nexus.erp.wm.domain.WmBarcode;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import com.nexus.erp.wm.mapper.WmBarcodeMapper;
import com.nexus.erp.wm.service.impl.WmBarcodeServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 条码生成：生成的条码实例必须携带 snId（问题①回归）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmBarcodeServiceImplTest {

    @Mock
    private WmBarcodeMapper baseMapper;
    @Mock
    private WmBarcodeConfigMapper barcodeConfigMapper;
    @Mock
    private BarcodeGenerator barcodeGenerator;
    @Mock
    private BarcodeSequenceProvider barcodeSequenceProvider;

    @InjectMocks
    private WmBarcodeServiceImpl wmBarcodeService;

    private WmBarcodeConfig config() {
        WmBarcodeConfig config = new WmBarcodeConfig();
        config.setConfigId(1L);
        config.setConfigCode("WM_RECEIVE_DEFAULT");
        config.setPrefix("RC");
        config.setDatePattern("yyyyMMdd");
        config.setSequenceLength(6);
        config.setWithCheckDigit(0L);
        config.setEnableFlag(1L);
        return config;
    }

    @Test
    void generateAndSaveByCode_persistsSnId() {
        when(barcodeConfigMapper.selectOne(any())).thenReturn(config());
        when(barcodeSequenceProvider.nextSequence("WM_RECEIVE_DEFAULT")).thenReturn(1L);
        when(barcodeGenerator.generate(any(BarcodeRule.class), any(Date.class), anyLong()))
            .thenReturn("RC20260816000001");
        when(baseMapper.insert(any(WmBarcode.class))).thenReturn(1);

        wmBarcodeService.generateAndSaveByCode(
            "WM_RECEIVE_DEFAULT", "RECEIVE_IN", 1L, null,
            100L, 9L, 5L, 77L, new Date());

        ArgumentCaptor<WmBarcode> captor = ArgumentCaptor.forClass(WmBarcode.class);
        verify(baseMapper).insert(captor.capture());
        WmBarcode saved = captor.getValue();
        assertEquals(77L, saved.getSnId(), "条码实例必须落 snId");
        assertEquals(9L, saved.getBatchId());
        assertEquals(5L, saved.getStockId());
        assertEquals(100L, saved.getMaterialId());
    }
}
```

- [ ] **Step 2: 运行测试确认失败（编译失败 = 预期）**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=WmBarcodeServiceImplTest -DfailIfNoTests=false`
Expected: FAIL（`generateAndSaveByCode` 尚无 snId 参数，编译错误 "实际参数列表和形式参数列表长度不同"）

- [ ] **Step 3: 接口增加 snId 参数**

`IWmBarcodeService.java` 两方法签名改为（在 `stockId` 后、`bizDate` 前插入 `Long snId`）：

```java
    String generateAndSave(Long configId, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate);

    String generateAndSaveByCode(String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate);
```

- [ ] **Step 4: 实现类透传并落 snId**

`WmBarcodeServiceImpl.java`：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateAndSave(Long configId, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate) {
        WmBarcodeConfig config = configId == null ? null : barcodeConfigMapper.selectById(configId);
        return generateAndSaveInternal(config, null, bizType, bizId, bizLineId, materialId, batchId, stockId, snId, bizDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateAndSaveByCode(String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate) {
        WmBarcodeConfig config = StringUtils.isBlank(configCode) ? null : barcodeConfigMapper.selectOne(Wrappers.<WmBarcodeConfig>lambdaQuery()
            .eq(WmBarcodeConfig::getConfigCode, configCode));
        return generateAndSaveInternal(config, configCode, bizType, bizId, bizLineId, materialId, batchId, stockId, snId, bizDate);
    }
```

`generateAndSaveInternal` 签名改为 `(WmBarcodeConfig config, String configCode, String bizType, Long bizId, Long bizLineId, Long materialId, Long batchId, Long stockId, Long snId, Date bizDate)`，并在 `barcode.setStockId(stockId);` 后追加：

```java
        barcode.setSnId(snId);
```

- [ ] **Step 5: 控制器增加 snId 参数**

`WmBarcodeController.java` 两个生成方法各增加一行参数并在调用处传入：

```java
                              @RequestParam(required = false) Long stockId,
                              @RequestParam(required = false) Long snId) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把条码误塞进 msg
        return R.ok("查询成功", wmBarcodeService.generateAndSave(configId, bizType, bizId, bizLineId, materialId, batchId, stockId, snId, new Date()));
```

`generateByCode` 同理（`generateAndSaveByCode(... stockId, snId, new Date())`）。

- [ ] **Step 6: 入库执行器传 stock.snId**

`WmMaterialReceiveExecutorServiceImpl.java` 条码生成循环（174-186 行）的调用改为：

```java
            wmBarcodeService.generateAndSaveByCode(
                "WM_RECEIVE_DEFAULT",
                "RECEIVE_IN",
                receiveId,
                null,
                stock.getMaterialId(),
                stock.getBatchId(),
                stock.getStockId(),
                stock.getSnId(),
                new Date()
            );
```

- [ ] **Step 7: 运行后端测试确认通过**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=WmBarcodeServiceImplTest -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 8: 前端 API 增加 snId**

`plus-ui/src/api/erp/wm/barcode/index.ts` 的 `generateBarcode` params 类型追加：

```ts
  batchId?: number;
  stockId?: number;
  snId?: number;
```

- [ ] **Step 9: 前端条码页支持 SN 输入/展示/筛选**

`plus-ui/src/views/erp/wm/barcode/index.vue` 三处修改：

(1) 生成弹窗 `generateForm`（物料ID 字段后追加）：

```vue
        <el-form-item label="序列号ID" prop="snId">
          <el-input v-model="generateForm.snId" placeholder="序列号物料生成的条码绑定SN" style="width: 100%" />
        </el-form-item>
```

`generateForm` 定义与 `handleGenerate` 重置均加 `snId: undefined`。

(2) 查看详情 `viewForm` 加 `snId: undefined`，descriptions 中"批次ID"后追加：

```vue
        <el-descriptions-item label="序列号ID">{{ viewForm.snId }}</el-descriptions-item>
```

(3) 查询参数 `queryParams` 加 `snId: null`。

- [ ] **Step 10: 前端静态检查**

Run: `npm run lint:eslint`（工作目录 `plus-ui`，仅确认新增代码无错误；报装饰性警告可忽略）
Expected: ESLint 通过（或仅既有装饰性警告）

- [ ] **Step 11: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmBarcodeService.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBarcodeServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmBarcodeController.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmBarcodeServiceImplTest.java \
        plus-ui/src/api/erp/wm/barcode/index.ts \
        plus-ui/src/views/erp/wm/barcode/index.vue
git commit -m "feat(wm): 条码生成贯通snId(一SN一条码)，前端生成弹窗/详情/筛选支持SN(问题①⑤)"
```

---

### Task 3: SN 批量生成关联批次（问题② + 问题⑤SN前端）

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmSnService.java:103`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSnServiceImpl.java:192-245`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmSnController.java:100-105`
- Modify: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmSnServiceImplTest.java`（batchGenerate 相关用例）
- Modify: `plus-ui/src/api/erp/wm/sn/index.ts:29-38`
- Modify: `plus-ui/src/views/erp/wm/sn/index.vue`（批量生成弹窗加批次下拉）

- [ ] **Step 1: 更新失败测试**

`WmSnServiceImplTest.java`：
- 类声明上方新增两个 mock：

```java
    @Mock
    private com.nexus.erp.wm.mapper.WmBatchMapper batchMapper;
    @Mock
    private com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider dailyCodeSequenceProvider;
```

- `setUp()` 增加默认桩（否则其它用例在 `next` 处 NPE）：

```java
        when(dailyCodeSequenceProvider.next(anyString(), any())).thenReturn(1L);
```

- 既有 `batchGenerate` 调用全部改为 7 参（batchId 传 null）：`wmSnService.batchGenerate(100L, 3, null, 1L, 200L, "PO", "PO20260601001")`、`(100L, 2, null, 1L, 200L, "PO", "PO20260601001")`、`(100L, 0, null, ...)`、`(100L, -1, null, ...)`。
- `testBatchGenerate_SequenceIncrement`：改为 `when(dailyCodeSequenceProvider.next(anyString(), any())).thenReturn(6L);`（替代对 DB 最大序号的模拟，语义一致：首条 SN 尾号 0006）。
- 新增两个用例：

```java
    /** 批量生成关联批次：SN 应写入 batchId/batchCode */
    @Test
    void testBatchGenerate_WithBatch() {
        com.nexus.erp.wm.domain.WmBatch batch = new com.nexus.erp.wm.domain.WmBatch();
        batch.setBatchId(9L);
        batch.setBatchCode("B202608160001");
        batch.setMaterialId(100L);
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(baseMapper.insert(any(WmSn.class))).thenReturn(1);

        wmSnService.batchGenerate(100L, 2, 9L, 1L, 200L, "PO", "PO20260601001");

        ArgumentCaptor<WmSn> captor = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper, times(2)).insert(captor.capture());
        for (WmSn sn : captor.getAllValues()) {
            assertEquals(9L, sn.getBatchId());
            assertEquals("B202608160001", sn.getBatchCode());
        }
    }

    /** 批量生成关联批次：批次物料不匹配应报错 */
    @Test
    void testBatchGenerate_BatchMaterialMismatch() {
        com.nexus.erp.wm.domain.WmBatch batch = new com.nexus.erp.wm.domain.WmBatch();
        batch.setBatchId(9L);
        batch.setBatchCode("B202608160001");
        batch.setMaterialId(999L);
        when(batchMapper.selectById(9L)).thenReturn(batch);

        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmSnService.batchGenerate(100L, 1, 9L, 1L, 200L, "PO", "PO001")
        );
        assertTrue(exception.getMessage().contains("不属于该物料"));
    }
```

- [ ] **Step 2: 运行测试确认失败（编译失败 = 预期）**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=WmSnServiceImplTest -DfailIfNoTests=false`
Expected: FAIL（接口 6 参 → 7 参，编译错误）

- [ ] **Step 3: 接口与实现增加 batchId 并回填批次**

`IWmSnService.java`（注意：`DailyCodeSequenceProvider` 引用在 Task 4 才创建，本任务先改接口/控制器/前端，Task 4 统一接 provider —— 参见 Task 4 Step 5 的合并说明）：

```java
    void batchGenerate(Long materialId, Integer quantity, Long batchId, Long ruleId, Long sourceDocId, String sourceDocType, String sourceDocNo);
```

`WmSnServiceImpl.java` 本任务先完成"batchId 校验与回填"（序号部分沿用旧 Redis 逻辑，Task 4 再替换）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerate(Long materialId, Integer quantity, Long batchId, Long ruleId,
                              Long sourceDocId, String sourceDocType, String sourceDocNo) {
        if (quantity == null || quantity <= 0) {
            throw new ServiceException("生成数量必须大于0");
        }
        if (materialId == null) {
            throw new ServiceException("物料ID不能为空");
        }

        String prefix = "SN";
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());

        // 关联批次：校验存在且物料一致，回填批次编码
        String batchCode = null;
        if (batchId != null) {
            WmBatch batch = batchMapper.selectById(batchId);
            if (batch == null) {
                throw new ServiceException("关联批次不存在");
            }
            if (!materialId.equals(batch.getMaterialId())) {
                throw new ServiceException("批次[" + batch.getBatchCode() + "]不属于该物料");
            }
            batchCode = batch.getBatchCode();
        }

        // 序号使用 Redis 原子自增（跨实例唯一、重启不重置），首次访问当天键时按当日最大已有序号初始化
        String seqKey = "erp:sn:seq:" + dateStr;
        long startSeq;
        if (RedisUtils.setObjectIfAbsent(seqKey, 0L, Duration.ofHours(48))) {
            startSeq = loadMaxSnSeq(prefix + dateStr);
            RedisUtils.setCacheObject(seqKey, startSeq, Duration.ofHours(48));
        }
        startSeq = RedisUtils.incrAtomicValue(seqKey);

        long seq = startSeq;
        List<WmSn> batchList = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            WmSn sn = new WmSn();
            sn.setSnCode(prefix + dateStr + String.format("%04d", seq + i));
            sn.setMaterialId(materialId);
            sn.setBatchId(batchId);
            sn.setBatchCode(batchCode);
            sn.setRuleId(ruleId);
            sn.setSourceDocId(sourceDocId);
            sn.setSourceDocType(sourceDocType);
            sn.setSourceDocNo(sourceDocNo);
            sn.setStatus("IN_STOCK");
            batchList.add(sn);
        }

        for (WmSn sn : batchList) {
            baseMapper.insert(sn);
        }

        log.info("批量生成序列号完成，物料ID：{}，数量：{}，批次：{}", materialId, quantity, batchCode);
    }

    /** 查询当日最大 SN 序号（likeRight 前缀 + 截取 4 位序号） */
    private long loadMaxSnSeq(String likeRightPrefix) {
        List<WmSn> existingList = baseMapper.selectList(Wrappers.<WmSn>lambdaQuery()
            .likeRight(WmSn::getSnCode, likeRightPrefix)
            .orderByDesc(WmSn::getSnCode)
            .last("LIMIT 1"));
        if (existingList == null || existingList.isEmpty()) {
            return 0L;
        }
        String lastCode = existingList.get(0).getSnCode();
        try {
            return Long.parseLong(lastCode.substring(likeRightPrefix.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
```

新增 import：`com.nexus.erp.wm.domain.WmBatch`、`com.nexus.erp.wm.mapper.WmBatchMapper`；字段新增 `private final WmBatchMapper batchMapper;`（`@RequiredArgsConstructor` 自动注入）。

- [ ] **Step 4: 控制器透传 batchId**

`WmSnController.java`：

```java
        wmSnService.batchGenerate(bo.getMaterialId(), bo.getQuantity(), bo.getBatchId(),
            bo.getRuleId(), bo.getSourceDocId(), bo.getSourceDocType(), bo.getSourceDocNo());
```

- [ ] **Step 5: 运行测试确认已用用例通过**

Run: 同 Step 2
Expected: PASS（含新增 2 个用例）

- [ ] **Step 6: 前端 API 与页面**

`plus-ui/src/api/erp/wm/sn/index.ts`：

```ts
export function batchGenerateSn(data: {
  materialId?: number;
  quantity: number;
  batchId?: number;
  ruleId?: number;
  sourceDocId?: number;
  sourceDocType?: string;
  sourceDocNo?: string;
}) {
  return request({ url: '/erp/wm/sn/batchGenerate', method: 'post', data });
}
```

`plus-ui/src/views/erp/wm/sn/index.vue`：
- import 追加 `listBatch`：`import { listBatch } from '@/api/erp/wm/batch';`
- 批量生成弹窗在"生成数量"后追加：

```vue
        <el-form-item label="关联批次" prop="batchId">
          <el-select v-model="batchGenerateForm.batchId" placeholder="请选择批次（可空）" clearable filterable style="width: 100%">
            <el-option v-for="item in batchOptions" :key="item.batchId"
              :label="item.batchCode + '（' + (item.materialName || item.materialCode || '') + '）'"
              :value="item.batchId" />
          </el-select>
        </el-form-item>
```

- 数据与处理：

```ts
const batchOptions = ref<any[]>([]);

const handleBatchGenerate = () => {
  batchGenerateForm.value = { materialId: undefined, quantity: 1, batchId: undefined };
  batchOptions.value = [];
  batchGenerateVisible.value = true;
};

const loadBatchOptions = (materialId?: number) => {
  listBatch({ pageNum: 1, pageSize: 200, materialId }).then((res: any) => {
    batchOptions.value = res.rows || [];
  });
};

// 物料ID变化时刷新批次下拉；有物料ID才加载
watch(
  () => batchGenerateForm.value.materialId,
  (val) => {
    if (val) loadBatchOptions(val);
    else batchOptions.value = [];
  }
);
```

`batchGenerateForm` 定义加 `batchId: undefined as number | undefined`，`onMounted`/`onActivated` 无需改动（弹窗打开时为空，输入物料后联动）。

- [ ] **Step 7: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/IWmSnService.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSnServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmSnController.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmSnServiceImplTest.java \
        plus-ui/src/api/erp/wm/sn/index.ts \
        plus-ui/src/views/erp/wm/sn/index.vue
git commit -m "feat(wm): SN批量生成支持关联批次并落batchId/batchCode，前端批次下拉(问题②⑤)"
```

---

### Task 4: 批次/SN 序号并发竞态修复 + 过期测试重写（问题④）

**Files:**
- Create: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/DailyCodeSequenceProvider.java`
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBatchServiceImpl.java:259-285`（generateBatchCode 改用 provider）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSnServiceImpl.java:203-223`（batchGenerate 序号段改用 provider，替换 Task 3 的旧 Redis 逻辑）
- Rewrite: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmBatchServiceImplTest.java`（整文件，旧文件反射静态字段已失效）
- Create: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/DailyCodeSequenceProviderTest.java`

**根因**：`setObjectIfAbsent(key,0)`（SETNX 成功）与 `setCacheObject(key, base)` 之间，并发线程可能已 incr 拿到 1，随后 A 用历史最大值覆盖 key，序号回退撞库内唯一键（`uk_batch_code` / `uk_sn_code`，`sys_oper_log` 已有 `Duplicate entry 'B202607060001'` 实据）。

- [ ] **Step 1: 写失败测试（provider）**

`DailyCodeSequenceProviderTest.java`（完整文件）：

```java
package com.nexus.erp.wm;

import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 按日自增序号提供器：初始化与自增必须处于同一把锁内（问题④回归）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class DailyCodeSequenceProviderTest {

    private static final String KEY = "erp:batch:seq:20260816";
    private static final String LOCK_KEY = "erp:code:seq:lock:erp:batch:seq:20260816";

    private final DailyCodeSequenceProvider provider = new DailyCodeSequenceProvider();

    /** 缓存未命中：锁内初始化(取库内最大序号)后再自增，返回 max+1 */
    @Test
    void next_initializesFromDbWhenCacheMiss() {
        try (MockedStatic<RedisUtils> utils = mockStatic(RedisUtils.class)) {
            RedissonClient client = mock(RedissonClient.class);
            RLock lock = mock(RLock.class);
            when(RedisUtils.getClient()).thenReturn(client);
            when(client.getLock(LOCK_KEY)).thenReturn(lock);
            when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
            when(RedisUtils.getCacheObject(KEY)).thenReturn(null);

            LongSupplier loader = mock(LongSupplier.class);
            when(loader.getAsLong()).thenReturn(5L);
            when(RedisUtils.incrAtomicValue(KEY)).thenReturn(6L);

            long seq = provider.next(KEY, loader);

            assertEquals(6L, seq);
            utils.verify(() -> RedisUtils.setCacheObject(eq(KEY), eq(5L), any()));
            utils.verify(() -> RedisUtils.incrAtomicValue(KEY));
            verify(loader).getAsLong();
            verify(lock).unlock();
        }
    }

    /** 缓存命中：不再初始化，也不再查询库内最大序号 */
    @Test
    void next_skipsInitWhenCacheHit() {
        try (MockedStatic<RedisUtils> utils = mockStatic(RedisUtils.class)) {
            RedissonClient client = mock(RedissonClient.class);
            RLock lock = mock(RLock.class);
            when(RedisUtils.getClient()).thenReturn(client);
            when(client.getLock(LOCK_KEY)).thenReturn(lock);
            when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
            when(RedisUtils.getCacheObject(KEY)).thenReturn(5L);

            LongSupplier loader = mock(LongSupplier.class);
            when(RedisUtils.incrAtomicValue(KEY)).thenReturn(6L);

            long seq = provider.next(KEY, loader);

            assertEquals(6L, seq);
            utils.verify(() -> RedisUtils.setCacheObject(anyString(), anyLong(), any()), never());
            verify(loader, never()).getAsLong();
            verify(lock).unlock();
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败（编译失败 = 预期）**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=DailyCodeSequenceProviderTest -DfailIfNoTests=false`
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 DailyCodeSequenceProvider**

`DailyCodeSequenceProvider.java`（完整文件）：

```java
package com.nexus.erp.wm.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 按日自增序号提供器（批次号/序列号共用）。
 *
 * <p>修复旧实现 generateBatchCode()/batchGenerate() 的初始化竞态：
 * 旧代码先 setObjectIfAbsent(key, 0) 再 setCacheObject(key, base)，两步之间若有并发线程已 incr，
 * 后执行的覆盖会把序号打回历史最大值，导致新序号与库内批次号/序列号重复
 * （uk_batch_code / uk_sn_code 唯一键冲突，sys_oper_log 曾有 Duplicate entry 'B202607060001' 实据）。</p>
 *
 * <p>本提供器把"读缓存 → 初始化(查库最大序号) → 原子自增"放进同一把 Redisson 分布式锁，
 * 双机集群跨实例安全；锁粒度按 seqKey（含日期）隔离，锁内仅做 Redis 读写，不持有数据库事务。</p>
 */
@Slf4j
@Component
public class DailyCodeSequenceProvider {

    private static final Duration SEQ_TTL = Duration.ofHours(48);

    /**
     * 取下一个序号。
     *
     * @param seqKey       含日期的 Redis 序号键，如 erp:batch:seq:20260816
     * @param maxSeqLoader 当天键不存在时，从库中读取当日最大序号（作为 self 起点）
     * @return 下一个序号（= max + 1）
     */
    public long next(String seqKey, LongSupplier maxSeqLoader) {
        RLock lock = RedisUtils.getClient().getLock("erp:code:seq:lock:" + seqKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("序号生成繁忙，请稍后重试");
            }
            Long cached = RedisUtils.getCacheObject(seqKey);
            if (cached == null) {
                RedisUtils.setCacheObject(seqKey, maxSeqLoader.getAsLong(), SEQ_TTL);
            }
            return RedisUtils.incrAtomicValue(seqKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("序号生成被中断，请重试");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
```

- [ ] **Step 4: 运行 provider 测试确认通过**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=DailyCodeSequenceProviderTest -DfailIfNoTests=false`
Expected: PASS（若报 mockito-inline 缺失，在 `ruoyi-modules/ruoyi-erp/pom.xml` 的 test 依赖中显式加 `org.mockito:mockito-core` 最新 5.x 版本后重跑，mockStatic 需要 inline mock maker —— Spring Boot 全家桶通常已内置）

- [ ] **Step 5: WmBatchServiceImpl 改用 provider**

字段追加：

```java
    /** 按日自增序号提供器（批次号并发安全） */
    private final DailyCodeSequenceProvider dailyCodeSequenceProvider;
```

`generateBatchCode` 方法体整体替换：

```java
    private String generateBatchCode() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String currentDateStr = sdf.format(new Date());
        String key = "erp:batch:seq:" + currentDateStr;
        long seq = dailyCodeSequenceProvider.next(key, () -> loadMaxBatchSeq("B" + currentDateStr));
        return "B" + currentDateStr + String.format("%04d", seq);
    }

    /** 查询当日最大批次序号（likeRight 前缀 + 截取 4 位序号），无历史批次返回 0 */
    private long loadMaxBatchSeq(String likeRightPrefix) {
        WmBatch last = baseMapper.selectOne(
            Wrappers.<WmBatch>lambdaQuery()
                .likeRight(WmBatch::getBatchCode, likeRightPrefix)
                .orderByDesc(WmBatch::getBatchCode)
                .last("LIMIT 1"));
        if (last == null || last.getBatchCode() == null || last.getBatchCode().length() <= likeRightPrefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(last.getBatchCode().substring(likeRightPrefix.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
```

删除不再使用的 import：`com.nexus.common.redis.utils.RedisUtils`、`java.time.Duration`。

- [ ] **Step 6: WmSnServiceImpl 序号段改用 provider**

将 Task 3 Step 3 中 batchGenerate 的序号初始化块整体替换为：

```java
        // 序号使用锁内初始化 + Redis 原子自增（跨实例唯一、并发安全），首次访问当天键时按当日最大已有序号初始化
        long startSeq = dailyCodeSequenceProvider.next("erp:sn:seq:" + dateStr, () -> loadMaxSnSeq(prefix + dateStr));
```

字段追加（与 batchMapper 并列）：

```java
    /** 按日自增序号提供器（序列号并发安全） */
    private final DailyCodeSequenceProvider dailyCodeSequenceProvider;
```

删除不再使用的 import：`com.nexus.common.redis.utils.RedisUtils`、`java.time.Duration`。

- [ ] **Step 7: 重写 WmBatchServiceImplTest（旧文件反射已失效）**

整文件替换为 `WmBatchServiceImplTest.java`（注意：`createBatch` 内部调用私有 `generateBatchCode` → provider；测试全部 mock provider，不触碰 Redis）：

```java
package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;
import com.nexus.erp.wm.service.impl.WmBatchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 批次管理Service单元测试（重写：旧版反射已不存在的静态计数器字段，随并发修复整体替换）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmBatchServiceImplTest {

    @Mock
    private WmBatchMapper baseMapper;
    @Mock
    private DailyCodeSequenceProvider dailyCodeSequenceProvider;

    @InjectMocks
    private WmBatchServiceImpl wmBatchService;

    @BeforeEach
    void setUp() {
        when(dailyCodeSequenceProvider.next(anyString(), any())).thenReturn(6L);
    }

    /** createBatch：自动生成批次编码（provider 提供序号），格式 B{yyyyMMdd}{4位} 且默认值补齐 */
    @Test
    void testCreateBatch_AutoCodeAndDefaults() {
        WmBatchBo bo = new WmBatchBo();
        bo.setMaterialId(100L);
        bo.setMaterialCode("MAT001");

        when(baseMapper.insert(any(WmBatch.class))).thenReturn(1);
        WmBatchVo vo = new WmBatchVo();
        vo.setBatchId(1L);
        vo.setBatchCode("B202608160006");
        vo.setMaterialId(100L);
        vo.setStatus("ACTIVE");
        when(baseMapper.selectVoById(anyLong())).thenReturn(vo);

        WmBatchVo result = wmBatchService.createBatch(bo);

        assertEquals("B202608160006", result.getBatchCode());
        assertEquals("ACTIVE", result.getStatus());
        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).insert(captor.capture());
        WmBatch inserted = captor.getValue();
        assertTrue(inserted.getBatchCode().startsWith("B"));
        assertTrue(inserted.getBatchCode().matches("B\\d{8}\\d{4}"));
        assertEquals(0, inserted.getReceivedQty().compareTo(BigDecimal.ZERO));
        assertEquals(0, inserted.getIssuedQty().compareTo(BigDecimal.ZERO));
    }

    /** insertByBo：批次编码为空时自动生成（问题⑦回归，见 Task 6） —— 此用例在 Task 6 完成前标记忽略 */
    @Test
    void testInsertByBo_AutoCode() {
        // 由 Task 6 补全：此处先占位保证编译
        WmBatchBo bo = new WmBatchBo();
        bo.setMaterialId(100L);
        assertNotNull(bo.getMaterialId());
    }

    /** 更新批次数量 - 收货时 received_qty 增加 */
    @Test
    void testUpdateQuantity_Receive() {
        WmBatch existingBatch = batch("B202606010001");
        when(baseMapper.selectById(1L)).thenReturn(existingBatch);
        when(baseMapper.updateById(any(WmBatch.class))).thenReturn(1);

        wmBatchService.updateQuantity(1L, new BigDecimal("20"), true);

        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("70").compareTo(captor.getValue().getReceivedQty())); // 50 + 20
    }

    /** 更新数量 - 批次不存在应抛出异常 */
    @Test
    void testUpdateQuantity_BatchNotFound() {
        when(baseMapper.selectById(anyLong())).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmBatchService.updateQuantity(999L, BigDecimal.ONE, true)
        );
        assertTrue(exception.getMessage().contains("不存在"));
    }

    /** 检查过期批次 - 过期批次状态应变为 EXPIRED */
    @Test
    void testCheckExpiry() {
        WmBatch expiredBatch = batch("B202606010001");
        expiredBatch.setExpiryDate(new Date(System.currentTimeMillis() - 86400000L));
        when(baseMapper.selectList(any())).thenReturn(List.of(expiredBatch));
        when(baseMapper.updateById(any(WmBatch.class))).thenReturn(1);

        wmBatchService.checkExpiry();

        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals("EXPIRED", captor.getValue().getStatus());
    }

    /** 变更批次状态 */
    @Test
    void testChangeStatus() {
        WmBatch existingBatch = batch("B202606010001");
        when(baseMapper.selectById(1L)).thenReturn(existingBatch);
        when(baseMapper.updateById(any(WmBatch.class))).thenReturn(1);

        wmBatchService.changeStatus(1L, "CLOSED");

        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals("CLOSED", captor.getValue().getStatus());
    }

    private WmBatch batch(String code) {
        WmBatch b = new WmBatch();
        b.setBatchId(1L);
        b.setBatchCode(code);
        b.setMaterialId(100L);
        b.setQuantity(new BigDecimal("100"));
        b.setReceivedQty(new BigDecimal("50"));
        b.setIssuedQty(new BigDecimal("10"));
        b.setStatus("ACTIVE");
        return b;
    }
}
```

> 说明：`testInsertByBo_AutoCode` 占位，Task 6 替换为真实断言。

- [ ] **Step 8: 运行批次+SN+provider 全量测试确认通过**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=DailyCodeSequenceProviderTest,WmBatchServiceImplTest,WmSnServiceImplTest -DfailIfNoTests=false`
Expected: PASS（3 个测试类全绿）

- [ ] **Step 9: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/DailyCodeSequenceProvider.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBatchServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSnServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/DailyCodeSequenceProviderTest.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmBatchServiceImplTest.java
git commit -m "fix(wm): 批次/SN序号生成加分布式锁消除初始化竞态，重写过期批次测试(问题④)"
```

---

### Task 5: 库存事务记录 SN 维度（问题⑥）

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java:616-658`（recordStockTransaction）
- Modify: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmMaterialReceiveStockTest.java`（追加用例）

- [ ] **Step 1: 写失败测试（追加到 WmMaterialReceiveStockTest）**

```java
    /** 库存事务必须记录 SN（问题⑥回归） */
    @Test
    void recordStockTransaction_persistsSn() throws Exception {
        com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo receive =
            new com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo();
        receive.setReceiveId(1L);
        receive.setReceiveCode("CGRK20260816001");
        receive.setReceiveDate(new Date());

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(100L);
        stock.setMaterialCode("MAT001");
        stock.setBatchId(9L);
        stock.setBatchCode("B202608160001");
        stock.setSnId(77L);
        stock.setSnCode("SN202608160001");
        stock.setWarehouseId(2L);
        stock.setLocationId(6L);
        stock.setRecptDate(new Date());

        Method m = WmMaterialReceiveExecutorServiceImpl.class.getDeclaredMethod(
            "recordStockTransaction",
            com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo.class,
            WmMaterialStock.class, int.class, BigDecimal.class,
            BigDecimal.class, BigDecimal.class, Long.class, String.class);
        m.setAccessible(true);
        m.invoke(executor, receive, stock, 1, new BigDecimal("1"),
            BigDecimal.ZERO, new BigDecimal("1"), 5L, "TARGET_IN");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.nexus.erp.wm.domain.WmMaterialStockTx> captor =
            ArgumentCaptor.forClass(com.nexus.erp.wm.domain.WmMaterialStockTx.class);
        verify(materialStockTxMapper).insert(captor.capture());
        com.nexus.erp.wm.domain.WmMaterialStockTx tx = captor.getValue();
        assertEquals(77L, tx.getSnId(), "库存事务必须落 sn_id");
        assertEquals("SN202608160001", tx.getSnCode(), "库存事务必须落 sn_code");
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=WmMaterialReceiveStockTest -DfailIfNoTests=false`
Expected: FAIL（`WmMaterialStockTx` 尚无 setSnId/setSnCode —— Task 0 Step 2 已加，若仍未加则先补；此处失败原因为 `tx.getSnId()`/`getSnCode()` 编译错误时先确认 Task 0 已合入）

- [ ] **Step 3: recordStockTransaction 复制 SN**

在 `stockTx.setBatchCode(stock.getBatchCode());` 后追加：

```java
        stockTx.setSnId(stock.getSnId());
        stockTx.setSnCode(stock.getSnCode());
```

- [ ] **Step 4: 运行测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmMaterialReceiveExecutorServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmMaterialReceiveStockTest.java
git commit -m "fix(wm): 库存事务记录SN维度，出入库流水可按SN追溯(问题⑥)"
```

---

### Task 6: insertByBo 自动编号与基础校验（问题⑦）

**Files:**
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBatchServiceImpl.java:86-91`（insertByBo）
- Modify: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSnServiceImpl.java:86-91`（insertByBo）
- Modify: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmBatchServiceImplTest.java`（替换占位用例）
- Modify: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmSnServiceImplTest.java`（追加 insertByBo 用例）

- [ ] **Step 1: 写失败测试**

替换 `WmBatchServiceImplTest.testInsertByBo_AutoCode` 为真实断言：

```java
    /** insertByBo：批次编码为空时自动生成，并补齐默认状态与数量（问题⑦回归） */
    @Test
    void testInsertByBo_AutoCodeAndDefaults() {
        WmBatchBo bo = new WmBatchBo();
        bo.setMaterialId(100L);
        bo.setMaterialCode("MAT001");
        when(baseMapper.insert(any(WmBatch.class))).thenReturn(1);

        wmBatchService.insertByBo(bo);

        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).insert(captor.capture());
        WmBatch inserted = captor.getValue();
        assertTrue(inserted.getBatchCode().matches("B\\d{8}\\d{4}"));
        assertEquals("ACTIVE", inserted.getStatus());
        assertEquals(0, inserted.getQuantity().compareTo(BigDecimal.ZERO));
    }

    /** insertByBo：物料ID为空应报错 */
    @Test
    void testInsertByBo_RequiresMaterial() {
        WmBatchBo bo = new WmBatchBo();
        ServiceException exception = assertThrows(ServiceException.class, () ->
            wmBatchService.insertByBo(bo)
        );
        assertTrue(exception.getMessage().contains("物料ID不能为空"));
    }
```

`WmSnServiceImplTest` 追加：

```java
    /** insertByBo：SN编码为空时自动生成，关联批次自动回填批次编码（问题⑦回归） */
    @Test
    void testInsertByBo_AutoCodeAndBatchBackfill() {
        com.nexus.erp.wm.domain.WmBatch batch = new com.nexus.erp.wm.domain.WmBatch();
        batch.setBatchId(9L);
        batch.setBatchCode("B202608160001");
        batch.setMaterialId(100L);
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(baseMapper.insert(any(WmSn.class))).thenReturn(1);

        WmSnBo bo = new WmSnBo();
        bo.setMaterialId(100L);
        bo.setBatchId(9L);
        // snCode 留空，期望自动生成
        wmSnService.insertByBo(bo);

        ArgumentCaptor<WmSn> captor = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper).insert(captor.capture());
        WmSn inserted = captor.getValue();
        assertTrue(inserted.getSnCode().matches("SN\\d{8}\\d{4}"));
        assertEquals("B202608160001", inserted.getBatchCode());
    }
```

（`WmSnBo` import 需加入测试文件：`com.nexus.erp.wm.domain.bo.WmSnBo`）

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest=WmBatchServiceImplTest,WmSnServiceImplTest -DfailIfNoTests=false`
Expected: FAIL（insertByBo 仍为纯 convert+insert，无自动编号 / 无校验）

- [ ] **Step 3: 批次 insertByBo**

`WmBatchServiceImpl.java`：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBatchBo bo) {
        if (bo.getMaterialId() == null) {
            throw new ServiceException("物料ID不能为空");
        }
        if (StringUtils.isBlank(bo.getBatchCode())) {
            bo.setBatchCode(generateBatchCode());
        }
        if (StringUtils.isBlank(bo.getStatus())) {
            bo.setStatus("ACTIVE");
        }
        if (bo.getQuantity() == null) {
            bo.setQuantity(BigDecimal.ZERO);
        }
        if (bo.getReceivedQty() == null) {
            bo.setReceivedQty(BigDecimal.ZERO);
        }
        if (bo.getIssuedQty() == null) {
            bo.setIssuedQty(BigDecimal.ZERO);
        }
        WmBatch entity = MapstructUtils.convert(bo, WmBatch.class);
        return baseMapper.insert(entity);
    }
```

- [ ] **Step 4: SN insertByBo**

`WmSnServiceImpl.java`：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmSnBo bo) {
        if (bo.getMaterialId() == null) {
            throw new ServiceException("物料ID不能为空");
        }
        if (StringUtils.isBlank(bo.getSnCode())) {
            String prefix = "SN";
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            long seq = dailyCodeSequenceProvider.next("erp:sn:seq:" + dateStr, () -> loadMaxSnSeq(prefix + dateStr));
            bo.setSnCode(prefix + dateStr + String.format("%04d", seq));
        }
        if (bo.getBatchId() != null && StringUtils.isBlank(bo.getBatchCode())) {
            WmBatch batch = batchMapper.selectById(bo.getBatchId());
            if (batch == null) {
                throw new ServiceException("关联批次不存在");
            }
            bo.setBatchCode(batch.getBatchCode());
        }
        WmSn entity = MapstructUtils.convert(bo, WmSn.class);
        return baseMapper.insert(entity);
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: 同 Step 2 命令
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmBatchServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmSnServiceImpl.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmBatchServiceImplTest.java \
        ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmSnServiceImplTest.java
git commit -m "feat(wm): 手工新增批次/SN自动编号与基础校验(问题⑦)"
```

---

### Task 7: 全链路验证（手工回归清单）

**Files:** 无（仅执行验证）

- [ ] **Step 1: 执行全部 wm 单测**

Run: `mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dtest='DailyCodeSequenceProviderTest,WmBatchServiceImplTest,WmSnServiceImplTest,WmBarcodeServiceImplTest,WmMaterialReceiveStockTest' -DfailIfNoTests=false`
Expected: PASS（5 个测试类全绿）

- [ ] **Step 2: 应用迁移 SQL 并重启后端**

Run: 在 `ry-erp` 库执行 `script/sql/update/20260816_wm_sn_barcode_tx_fix.sql`，重启 `ruoyi-admin`（`mvn spring-boot:run -P local`）

- [ ] **Step 3: 并发验证（批次/SN 序号）**

并发调用 50 次 POST `/erp/wm/batch/createBatch`（materialId 同一物料）与 `/erp/wm/sn/batchGenerate`（quantity=1），随后校验：
`SELECT batch_code, COUNT(*) c FROM wm_batch WHERE batch_code LIKE 'B2026%' GROUP BY batch_code HAVING c>1;` 应为 0 行；`SELECT sn_code, COUNT(*) c FROM wm_sn WHERE sn_code LIKE 'SN2026%' GROUP BY sn_code HAVING c>1;` 应为 0 行。

- [ ] **Step 4: SN 入库全链路（问题①③⑥ 联动）**

1. 序列号物料建入库单 → 上架确认（明细含 SN）→ 执行入库；
2. 断言：`wm_material_stock` 每个 SN 独立一行（`sn_id` 非空、`quantity=1`、库位为目标库位）；`wm_barcode` 每 SN 一条且 `sn_id` 已落；`wm_material_stock_tx` 的 TARGET_IN/VIRTUAL_OUT 两条流水均含 `sn_id/sn_code`；
3. 前端条码页查看该条码详情可见"序列号ID"；SN 管理页搜索批次号能过滤。

- [ ] **Step 5: SN 批量生成关联批次（问题②）**

1. 先建一个批次（如物料 100）；
2. SN 管理 → 批量生成 → 输入物料ID 自动带出批次下拉 → 选批次 → 生成；
3. 断言：`wm_sn` 新记录 `batch_id/batch_code` 已填；选不存在/其它物料的批次返回"批次不存在/不属于该物料"。

- [ ] **Step 6: 前端回归**

1. 条码页"生成条码"弹窗现含 序列号ID 输入框，生成后列表可查；
2. SN 页列表含批次号列与筛选（已有）；批量生成弹窗多批次下拉；
3. 手工"新增批次/新增序列号"不需手工填编码，保存后自动生成。

- [ ] **Step 7: 数据说明（不自动执行，写入 commit message / 文档备注）**

- 历史已合并的 SN 库存行（修复前同批次多 SN 合一行）**不会自动拆分**；如需拆分需按 `wm_material_stock_tx` 中带 SN 的 TARGET_IN 流水重建库存行（二期数据修复，不在本计划范围）。
- seed 数据中 `wm_barcode.material_id=1` 的 12 条测试痕迹与 `batch_id=NULL` 的 2026-07-31 条码属测试/缺省场景产物，无业务影响，可保留。
- 知识点：`wm_material_receive` 相关 MES 侧 `pro_sn` 与 ERP `wm_sn` 刻意分离，本修复不动 MES。

- [ ] **Step 8: 更新 FIX-LIST（约定产物）**

`doc/code-review/FIX-LIST.md` 追加一行（"已修"）：
`- [x] wm 条码/批次/SN：snId贯通条码、batchGenerate关联批次、库存按SN落行、序号并发锁、库存事务SN维度、insertByBo自动编号（2026-08-16，见 doc/erp/plans/2026-08-16-wm-barcode-batch-sn-remediation.md）`

- [ ] **Step 9: Commit**

```bash
git add doc/code-review/FIX-LIST.md
git commit -m "docs: 记录wm条码/批次/SN关联修复完成项"
```

---

## 自检记录（Self-Review）

- **覆盖度**：问题①→Task2，②→Task3，③→Task1，④→Task4，⑤→Task2/3 前端段，⑥→Task0+Task5，⑦→Task6；无遗漏。
- **命名一致性**：全计划统一 `generateAndSaveByCode(..., batchId, stockId, snId, bizDate)`、`batchGenerate(materialId, quantity, batchId, ruleId, sourceDocId, sourceDocType, sourceDocNo)`、provider 签名 `next(seqKey, LongSupplier)`；测试类名与既有 `@Tag("dev")` 风格一致。
- **已知依赖顺序**：Task3 引用的 `DailyCodeSequenceProvider` 在 Task4 创建（Step 3 已合并说明：Task3 先保持旧 Redis 序号逻辑，Task4 Step 6 替换）；Task5 依赖 Task0 的实体字段；Task4 Step 7 重写的批次测试含 Task6 占位用例，Task6 替换之。
- **无占位符**：所有代码步骤均为完整可粘贴文本；唯一例外是 Task4 Step 7 中 `testInsertByBo_AutoCode` 的占位（已在 Task6 Step 1 提供完整替换），且注释明确标注由 Task 6 补全。