# MES Phase 1 Week 18 开发实施计划

> **实施日期**: 2026-06-17
> **阶段**: MES Phase 1 - ERP 桥接完善 + MES 工单后端 + 只读前端
> **状态**: 待审核

> **面向代理执行者：** 必选子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用复选框语法 `- [ ]` 跟踪。

**目标：** 完善 ERP 只读桥接（车间/工作中心），完成 MES 工单 Domain/Mapper/Service/Controller 全链路，改造 `WorkOrderReleaseConsumer` 实现真正的工单创建与工序自动生成，并交付 MES 工单列表/详情前端基础页面。

**架构：** MES 工单后端沿用 ERP 工单的分层模式（Domain/BO/VO/Mapper/Service/Controller），数据源标注 `@DS("mes")`；通过 `IMesErpBridgeService` 编程式切换到 `erp` 数据源读取主数据；`WorkOrderReleaseConsumer` 消费 ERP 审批通过消息后，调用桥接服务补全物料/车间/工艺路线信息，创建 MES 工单（状态 `CREATED`）及工序步骤（状态 `PENDING`）。Week 18 暂不集成 Flowable，提供手动 `release` 接口将工单从 `CREATED` 转为 `RELEASED`；Flowable 审批流留待 Week 20 集成。

**技术栈：** Spring Boot 3.5.x、MyBatis-Plus、dynamic-datasource、RabbitMQ、Vue 3、Element Plus

---

## 一、任务清单

| 任务 | 状态 | 交付物 | 验收标准 |
|------|------|--------|---------|
| ERP 桥接完善：车间/工作中心查询 | ⬜ | `ErpWorkshopMapper`、`ErpWorkstationMapper`、`IMesErpBridgeService` 扩展方法 | MES 可通过桥接服务查询 ERP 车间和工作中心详情 |
| MES 工单 Domain/BO/VO | ⬜ | `ProWorkOrder`、`ProWorkOrderStep`、`ProWorkOrderBo`、`ProWorkOrderVo`、`ProWorkOrderStepVo` | 实体字段与 `pro_work_order`/`pro_work_order_step` 表完全对应 |
| MES 工单 Mapper | ⬜ | `ProWorkOrderMapper`、`ProWorkOrderStepMapper` | 继承 `BaseMapperPlus`，支持 Vo 查询 |
| MES 工单 Service | ⬜ | `IProWorkOrderService`、`ProWorkOrderServiceImpl` | 实现 CRUD、`createFromErpMessage`、`release`、工序自动生成 |
| MES 工单 Controller | ⬜ | `ProWorkOrderController` | 提供 list/{id}/add/edit/remove/release/steps 接口 |
| 改造 WorkOrderReleaseConsumer | ⬜ | `WorkOrderReleaseConsumer.doProcess()` | 消费消息后能创建 MES 工单及工序，幂等可重试 |
| MES MQ 发送服务骨架 | ⬜ | `MesMqSendService` | 参照 `WmMqSendService`，`@Async` + `push("master")`，为完工消息发送预留 |
| MES 工单前端基础版 | ⬜ | `plus-ui/src/views/mes/pro/workorder/index.vue`、`detail.vue` | 列表分页查询、详情查看、工序 Tab、手动下达按钮 |
| 菜单权限补充 SQL | ⬜ | `ry-vue-mes-menu-week18.sql` | 补充 MES 工单按钮权限（release 等） |
| 编译验证 | ⬜ | `mvn compile -pl ruoyi-modules/ruoyi-mes -am` | 编译通过 |

---

## 二、排期调整说明

原 `2026-06-16-mes-iot-phase-plan.md` 中 Week 18 为"ERP 桥接 + 只读前端"，Week 19 为"MES 工单后端"。由于 Week 17 已提前完成 MQ 契约迁移、DDL 建表和桥接服务骨架，现将 Week 19 的"MES 工单后端"提前至 Week 18 与"ERP 桥接完善"合并执行。调整后排期：

| 周次 | 调整后任务 |
|------|-----------|
| Week 18 | ERP 桥接完善 + MES 工单后端 + 工单前端基础版 |
| Week 19 | 只读前端完善 + Flowable 审批流准备 |
| Week 20 | Flowable 审批流集成（CREATED -> RELEASED） |
| Week 21 | Phase 1 前端完善 + 冒烟验证 |

---

## 三、详细任务分解

### 任务 1：ERP 桥接完善（车间/工作中心查询）

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mapper/ErpWorkshopMapper.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mapper/ErpWorkstationMapper.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/bridge/IMesErpBridgeService.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/bridge/MesErpBridgeServiceImpl.java`

- [ ] **Step 1: 创建 ErpWorkshopMapper**

```java
package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP车间只读Mapper（跨库查询）
 *
 * @author Nexus
 */
@Mapper
public interface ErpWorkshopMapper {

    @Select("SELECT workshop_id, workshop_code, workshop_name, workshop_desc, " +
        "manager_id, manager_name, capacity, status " +
        "FROM bd_workshop WHERE workshop_id = #{workshopId} AND del_flag = '0'")
    Map<String, Object> selectWorkshopById(@Param("workshopId") Long workshopId);
}
```

- [ ] **Step 2: 创建 ErpWorkstationMapper**

```java
package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * ERP工作站只读Mapper（跨库查询）
 *
 * @author Nexus
 */
@Mapper
public interface ErpWorkstationMapper {

    @Select("SELECT workstation_id, workstation_code, workstation_name, " +
        "workshop_id, workshop_code, workshop_name, equipment_id, " +
        "operator_count, standard_capacity, status " +
        "FROM bd_workstation WHERE workstation_id = #{workstationId} AND del_flag = '0'")
    Map<String, Object> selectWorkstationById(@Param("workstationId") Long workstationId);
}
```

- [ ] **Step 3: 扩展 IMesErpBridgeService 接口**

在 `IMesErpBridgeService.java` 末尾新增两个方法：

```java
    /**
     * 查询车间信息(从ERP)
     *
     * @param workshopId 车间ID
     * @return 车间信息Map，包含workshop_code/workshop_name等
     */
    Map<String, Object> getWorkshop(Long workshopId);

    /**
     * 查询工作站信息(从ERP)
     *
     * @param workstationId 工作站ID
     * @return 工作站信息Map，包含workstation_code/workstation_name等
     */
    Map<String, Object> getWorkstation(Long workstationId);
```

- [ ] **Step 4: 实现 MesErpBridgeServiceImpl 新增方法**

在 `MesErpBridgeServiceImpl.java` 中注入新 Mapper 并实现方法：

```java
    private final ErpWorkshopMapper erpWorkshopMapper;
    private final ErpWorkstationMapper erpWorkstationMapper;

    @Override
    public Map<String, Object> getWorkshop(Long workshopId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkshopMapper.selectWorkshopById(workshopId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    public Map<String, Object> getWorkstation(Long workstationId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkstationMapper.selectWorkstationById(workstationId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
```

- [ ] **Step 5: 编译验证**

Run: `mvn compile -pl ruoyi-modules/ruoyi-mes -am`
Expected: BUILD SUCCESS

---

### 任务 2：MES 工单 Domain/BO/VO

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProWorkOrder.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/ProWorkOrderStep.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/bo/ProWorkOrderBo.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/vo/ProWorkOrderVo.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/domain/vo/ProWorkOrderStepVo.java`

- [ ] **Step 1: 创建 ProWorkOrder 实体**

```java
package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单对象 pro_work_order
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_work_order")
public class ProWorkOrder extends TenantEntity {

    /** MES工单ID */
    @TableId(value = "mes_wo_id")
    private Long mesWoId;

    /** 关联ERP工单ID */
    private Long erpWoId;

    /** MES工单编号 */
    private String woNo;

    /** 产品物料ID */
    private Long materialId;

    /** 产品编码 */
    private String materialCode;

    /** 产品名称 */
    private String materialName;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 已完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 单位ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 计划开工日期 */
    private Date planStartDate;

    /** 计划完工日期 */
    private Date planEndDate;

    /** 实际开工时间 */
    private Date actualStartDate;

    /** 实际完工时间 */
    private Date actualEndDate;

    /** 车间ID */
    private Long workshopId;

    /** 车间名称 */
    private String workshopName;

    /** BOM ID */
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;

    /** 工单状态: CREATED/RELEASED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String woStatus;

    /** 优先级(1最高,9最低) */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
```

- [ ] **Step 2: 创建 ProWorkOrderStep 实体**

```java
package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单工序步骤对象 pro_work_order_step
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_work_order_step")
public class ProWorkOrderStep extends TenantEntity {

    /** 工序步骤ID */
    @TableId(value = "step_id")
    private Long stepId;

    /** 所属MES工单ID */
    private Long mesWoId;

    /** 工序序号(从1开始) */
    private Integer sequenceNum;

    /** ERP工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    private String workcenterCode;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 工序状态: PENDING/READY/IN_PROGRESS/COMPLETED/SKIPPED */
    private String stepStatus;

    /** 是否关键工序(0否 1是) */
    private Integer isKeyProcess;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
```

- [ ] **Step 3: 创建 ProWorkOrderBo**

```java
package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProWorkOrder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单业务对象 pro_work_order
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProWorkOrder.class, reverseConvertGenerate = false)
public class ProWorkOrderBo extends BaseEntity {

    /** MES工单ID */
    private Long mesWoId;

    /** 关联ERP工单ID */
    private Long erpWoId;

    /** MES工单编号 */
    @NotBlank(message = "工单编号不能为空")
    private String woNo;

    /** 产品物料ID */
    private Long materialId;

    /** 产品编码 */
    private String materialCode;

    /** 产品名称 */
    private String materialName;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 单位ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 计划开工日期 */
    private Date planStartDate;

    /** 计划完工日期 */
    private Date planEndDate;

    /** 车间ID */
    private Long workshopId;

    /** 车间名称 */
    private String workshopName;

    /** BOM ID */
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;

    /** 工单状态 */
    private String woStatus;

    /** 优先级 */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 批量删除ID集合 */
    private Long[] ids;
}
```

- [ ] **Step 4: 创建 ProWorkOrderVo**

```java
package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProWorkOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES工单视图对象 pro_work_order
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProWorkOrder.class)
public class ProWorkOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** MES工单ID */
    @ExcelProperty("MES工单ID")
    private Long mesWoId;

    /** 关联ERP工单ID */
    @ExcelProperty("ERP工单ID")
    private Long erpWoId;

    /** MES工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 产品物料ID */
    private Long materialId;

    /** 产品编码 */
    @ExcelProperty("产品编码")
    private String materialCode;

    /** 产品名称 */
    @ExcelProperty("产品名称")
    private String materialName;

    /** 计划数量 */
    @ExcelProperty("计划数量")
    private BigDecimal plannedQty;

    /** 已完成数量 */
    @ExcelProperty("已完成数量")
    private BigDecimal completedQty;

    /** 报废数量 */
    @ExcelProperty("报废数量")
    private BigDecimal scrapQty;

    /** 单位ID */
    private Long unitId;

    /** 单位名称 */
    @ExcelProperty("单位")
    private String unitName;

    /** 计划开工日期 */
    @ExcelProperty("计划开工")
    private Date planStartDate;

    /** 计划完工日期 */
    @ExcelProperty("计划完工")
    private Date planEndDate;

    /** 实际开工时间 */
    private Date actualStartDate;

    /** 实际完工时间 */
    private Date actualEndDate;

    /** 车间ID */
    private Long workshopId;

    /** 车间名称 */
    @ExcelProperty("车间")
    private String workshopName;

    /** BOM ID */
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;

    /** 工单状态 */
    @ExcelProperty("状态")
    private String woStatus;

    /** 优先级 */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 工序步骤列表(详情查询时填充) */
    private List<ProWorkOrderStepVo> steps;
}
```

- [ ] **Step 5: 创建 ProWorkOrderStepVo**

```java
package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProWorkOrderStep;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单工序步骤视图对象 pro_work_order_step
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProWorkOrderStep.class)
public class ProWorkOrderStepVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工序步骤ID */
    private Long stepId;

    /** 所属MES工单ID */
    private Long mesWoId;

    /** 工序序号 */
    private Integer sequenceNum;

    /** ERP工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    private String workcenterCode;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 工序状态 */
    private String stepStatus;

    /** 是否关键工序 */
    private Integer isKeyProcess;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 备注 */
    private String remark;
}
```

---

### 任务 3：MES 工单 Mapper

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/mapper/ProWorkOrderMapper.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/mapper/ProWorkOrderStepMapper.java`

- [ ] **Step 1: 创建 ProWorkOrderMapper**

```java
package com.nexus.mes.pro.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;

/**
 * MES工单Mapper接口
 *
 * @author Nexus
 */
public interface ProWorkOrderMapper extends BaseMapperPlus<ProWorkOrder, ProWorkOrderVo> {
}
```

- [ ] **Step 2: 创建 ProWorkOrderStepMapper**

```java
package com.nexus.mes.pro.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.vo.ProWorkOrderStepVo;

/**
 * MES工单工序步骤Mapper接口
 *
 * @author Nexus
 */
public interface ProWorkOrderStepMapper extends BaseMapperPlus<ProWorkOrderStep, ProWorkOrderStepVo> {
}
```

---

### 任务 4：MES 工单 Service

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/IProWorkOrderService.java`
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/service/impl/ProWorkOrderServiceImpl.java`

- [ ] **Step 1: 创建 IProWorkOrderService 接口**

```java
package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderStepVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;

import java.util.List;

/**
 * MES工单 Service 接口
 *
 * @author Nexus
 */
public interface IProWorkOrderService {

    /** 工单状态: 新建 */
    String STATUS_CREATED = "CREATED";
    /** 工单状态: 已下达 */
    String STATUS_RELEASED = "RELEASED";
    /** 工单状态: 生产中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 工单状态: 已完工 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 工单状态: 已取消 */
    String STATUS_CANCELLED = "CANCELLED";

    /** 工序状态: 待开始 */
    String STEP_PENDING = "PENDING";
    /** 工序状态: 就绪 */
    String STEP_READY = "READY";
    /** 工序状态: 进行中 */
    String STEP_IN_PROGRESS = "IN_PROGRESS";
    /** 工序状态: 已完成 */
    String STEP_COMPLETED = "COMPLETED";
    /** 工序状态: 已跳过 */
    String STEP_SKIPPED = "SKIPPED";

    /**
     * 分页查询MES工单列表
     */
    TableDataInfo<ProWorkOrderVo> selectPageList(ProWorkOrderBo bo, PageQuery pageQuery);

    /**
     * 查询MES工单列表(不分页)
     */
    List<ProWorkOrderVo> selectList(ProWorkOrderBo bo);

    /**
     * 根据ID查询MES工单详情(含工序步骤)
     */
    ProWorkOrderVo selectById(Long mesWoId);

    /**
     * 根据ERP工单ID查询MES工单(幂等校验用)
     */
    ProWorkOrderVo selectByErpWoId(Long erpWoId);

    /**
     * 查询工单的工序步骤列表
     */
    List<ProWorkOrderStepVo> selectStepsByWoId(Long mesWoId);

    /**
     * 新增MES工单
     */
    int insertByBo(ProWorkOrderBo bo);

    /**
     * 修改MES工单
     */
    int updateByBo(ProWorkOrderBo bo);

    /**
     * 批量删除MES工单
     */
    int deleteByIds(Long[] ids);

    /**
     * 下达工单(CREATED -> RELEASED)
     */
    void release(Long mesWoId);

    /**
     * 消费ERP工单下达消息，创建MES工单及工序
     * <p>
     * 幂等：通过erpWoId查重，已存在则跳过。
     *
     * @param message ERP工单下达消息
     */
    void createFromErpMessage(WorkOrderReleaseMessage message);
}
```

- [ ] **Step 2: 创建 ProWorkOrderServiceImpl 实现**

```java
package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderStepVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IProWorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MES工单 Service 业务层实现
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proWorkOrderService")
public class ProWorkOrderServiceImpl implements IProWorkOrderService {

    private final ProWorkOrderMapper baseMapper;
    private final ProWorkOrderStepMapper stepMapper;
    private final IMesErpBridgeService mesErpBridgeService;

    @Override
    public TableDataInfo<ProWorkOrderVo> selectPageList(ProWorkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProWorkOrder> lqw = buildQueryWrapper(bo);
        Page<ProWorkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<ProWorkOrderVo> selectList(ProWorkOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public ProWorkOrderVo selectById(Long mesWoId) {
        ProWorkOrderVo vo = baseMapper.selectVoById(mesWoId);
        if (vo != null) {
            vo.setSteps(selectStepsByWoId(mesWoId));
        }
        return vo;
    }

    @Override
    public ProWorkOrderVo selectByErpWoId(Long erpWoId) {
        LambdaQueryWrapper<ProWorkOrder> lqw = Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(ProWorkOrder::getErpWoId, erpWoId);
        // throwEx=false 避免极端情况下多结果抛异常，取第一条
        return baseMapper.selectVoOne(lqw, false);
    }

    @Override
    public List<ProWorkOrderStepVo> selectStepsByWoId(Long mesWoId) {
        LambdaQueryWrapper<ProWorkOrderStep> lqw = Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderStep::getSequenceNum);
        return stepMapper.selectVoList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(ProWorkOrderBo bo) {
        ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
        if (StringUtils.isBlank(entity.getWoStatus())) {
            entity.setWoStatus(STATUS_CREATED);
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(ProWorkOrderBo bo) {
        ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long mesWoId) {
        ProWorkOrder entity = baseMapper.selectById(mesWoId);
        if (entity == null) {
            throw new RuntimeException("MES工单不存在");
        }
        if (!STATUS_CREATED.equals(entity.getWoStatus())) {
            throw new RuntimeException("仅新建状态工单可下达,当前状态:" + entity.getWoStatus());
        }
        entity.setWoStatus(STATUS_RELEASED);
        baseMapper.updateById(entity);
        log.info("MES工单下达成功: mesWoId={}, woNo={}", mesWoId, entity.getWoNo());
    }

    /**
     * 消费ERP工单下达消息，创建MES工单及工序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFromErpMessage(WorkOrderReleaseMessage message) {
        log.info("开始创建MES工单: erpWoId={}, woNo={}", message.getWoId(), message.getWoNo());

        // 1. 幂等校验：通过erpWoId查重
        ProWorkOrderVo existing = selectByErpWoId(message.getWoId());
        if (existing != null) {
            log.info("MES工单已存在,跳过创建: erpWoId={}, mesWoId={}", message.getWoId(), existing.getMesWoId());
            return;
        }

        // 2. 桥接查询物料信息，补全unitId/unitName
        Long unitId = null;
        String unitName = null;
        if (message.getMaterialId() != null) {
            Map<String, Object> material = mesErpBridgeService.getMaterial(message.getMaterialId());
            if (material != null) {
                unitId = toLong(material.get("base_uom_id"));
                // unitName需通过ERP单位表查询，Week 18暂用unitId，后续扩展
            }
        }

        // 3. 桥接查询车间信息，补全workshopName
        String workshopName = null;
        if (message.getWorkshopId() != null) {
            Map<String, Object> workshop = mesErpBridgeService.getWorkshop(message.getWorkshopId());
            if (workshop != null) {
                workshopName = (String) workshop.get("workshop_name");
            }
        }

        // 4. 桥接查询默认工艺路线ID
        Long routingId = message.getRoutingId();
        if (routingId == null && message.getMaterialId() != null) {
            routingId = mesErpBridgeService.getDefaultRoutingId(message.getMaterialId());
            log.info("通过桥接服务查询默认工艺路线: materialId={}, routingId={}", message.getMaterialId(), routingId);
        }

        // 5. 创建MES工单主记录
        ProWorkOrder workOrder = new ProWorkOrder();
        workOrder.setErpWoId(message.getWoId());
        workOrder.setWoNo(message.getWoNo());
        workOrder.setMaterialId(message.getMaterialId());
        workOrder.setMaterialCode(message.getMaterialCode());
        workOrder.setMaterialName(message.getMaterialName());
        workOrder.setPlannedQty(message.getPlannedQty());
        workOrder.setUnitId(unitId);
        workOrder.setUnitName(unitName);
        workOrder.setPlanStartDate(message.getPlannedStartDate());
        workOrder.setPlanEndDate(message.getPlannedEndDate());
        workOrder.setWorkshopId(message.getWorkshopId());
        workOrder.setWorkshopName(workshopName);
        workOrder.setBomId(message.getBomId());
        workOrder.setRoutingId(routingId);
        workOrder.setWoStatus(STATUS_CREATED);
        workOrder.setPriority(5);
        baseMapper.insert(workOrder);
        log.info("MES工单创建成功: mesWoId={}, woNo={}", workOrder.getMesWoId(), workOrder.getWoNo());

        // 6. 按工艺路线自动生成工序步骤
        if (routingId != null) {
            generateStepsFromRouting(workOrder, routingId);
        } else {
            log.warn("未找到工艺路线,跳过工序生成: woNo={}, materialId={}", message.getWoNo(), message.getMaterialId());
        }

        log.info("MES工单及工序创建完成: mesWoId={}, woNo={}", workOrder.getMesWoId(), workOrder.getWoNo());
    }

    /**
     * 按ERP工艺路线步骤生成MES工单工序
     */
    private void generateStepsFromRouting(ProWorkOrder workOrder, Long routingId) {
        List<Map<String, Object>> routingSteps = mesErpBridgeService.getRoutingSteps(routingId);
        if (routingSteps == null || routingSteps.isEmpty()) {
            log.warn("工艺路线无步骤,跳过工序生成: routingId={}", routingId);
            return;
        }

        List<ProWorkOrderStep> steps = new ArrayList<>();
        for (Map<String, Object> rs : routingSteps) {
            ProWorkOrderStep step = new ProWorkOrderStep();
            step.setMesWoId(workOrder.getMesWoId());
            step.setSequenceNum(toInt(rs.get("sequence_num")));
            step.setProcessId(toLong(rs.get("process_id")));
            step.setProcessCode((String) rs.get("process_code"));
            step.setProcessName((String) rs.get("process_name"));
            step.setWorkcenterId(toLong(rs.get("workstation_id")));
            step.setWorkcenterCode((String) rs.get("workstation_code"));
            step.setPlannedQty(workOrder.getPlannedQty());
            step.setStepStatus(STEP_PENDING);
            step.setIsKeyProcess(toInt(rs.get("is_key_process")));
            steps.add(step);
        }

        // 批量插入工序步骤
        for (ProWorkOrderStep step : steps) {
            stepMapper.insert(step);
        }
        log.info("工序步骤生成完成: mesWoId={}, routingId={}, stepCount={}",
            workOrder.getMesWoId(), routingId, steps.size());
    }

    private LambdaQueryWrapper<ProWorkOrder> buildQueryWrapper(ProWorkOrderBo bo) {
        LambdaQueryWrapper<ProWorkOrder> lqw = Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getWoNo()), ProWorkOrder::getWoNo, bo.getWoNo())
            .eq(bo.getErpWoId() != null, ProWorkOrder::getErpWoId, bo.getErpWoId())
            .eq(bo.getMaterialId() != null, ProWorkOrder::getMaterialId, bo.getMaterialId())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), ProWorkOrder::getMaterialName, bo.getMaterialName())
            .eq(bo.getWorkshopId() != null, ProWorkOrder::getWorkshopId, bo.getWorkshopId())
            .eq(StringUtils.isNotBlank(bo.getWoStatus()), ProWorkOrder::getWoStatus, bo.getWoStatus())
            .orderByDesc(ProWorkOrder::getCreateTime);
        return lqw;
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

---

### 任务 5：MES 工单 Controller

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/controller/ProWorkOrderController.java`

- [ ] **Step 1: 创建 ProWorkOrderController**

```java
package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.service.IProWorkOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES工单 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/work")
public class ProWorkOrderController extends BaseController {

    private final IProWorkOrderService proWorkOrderService;

    /**
     * 分页查询MES工单列表
     */
    @SaCheckPermission("mes:pro:work:list")
    @GetMapping("/list")
    public TableDataInfo<ProWorkOrderVo> list(ProWorkOrderBo bo, PageQuery pageQuery) {
        return proWorkOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出MES工单数据到Excel
     */
    @SaCheckPermission("mes:pro:work:export")
    @Log(title = "MES工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProWorkOrderBo bo, HttpServletResponse response) {
        List<ProWorkOrderVo> list = proWorkOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "MES工单数据", ProWorkOrderVo.class, response);
    }

    /**
     * 根据ID获取MES工单详情(含工序步骤)
     */
    @SaCheckPermission("mes:pro:work:query")
    @GetMapping("/{mesWoId}")
    public R<ProWorkOrderVo> getInfo(@PathVariable Long mesWoId) {
        return R.ok(proWorkOrderService.selectById(mesWoId));
    }

    /**
     * 新增MES工单
     */
    @SaCheckPermission("mes:pro:work:add")
    @Log(title = "MES工单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ProWorkOrderVo> add(@Validated @RequestBody ProWorkOrderBo bo) {
        proWorkOrderService.insertByBo(bo);
        return R.ok(proWorkOrderService.selectById(bo.getMesWoId()));
    }

    /**
     * 修改MES工单
     */
    @SaCheckPermission("mes:pro:work:edit")
    @Log(title = "MES工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody ProWorkOrderBo bo) {
        return toAjax(proWorkOrderService.updateByBo(bo));
    }

    /**
     * 批量删除MES工单
     */
    @SaCheckPermission("mes:pro:work:remove")
    @Log(title = "MES工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{mesWoIds}")
    public R<Void> remove(@PathVariable Long[] mesWoIds) {
        return toAjax(proWorkOrderService.deleteByIds(mesWoIds));
    }

    /**
     * 下达MES工单(CREATED -> RELEASED)
     */
    @SaCheckPermission("mes:pro:work:edit")
    @Log(title = "MES工单", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{mesWoId}")
    public R<Void> release(@PathVariable Long mesWoId) {
        proWorkOrderService.release(mesWoId);
        return R.ok();
    }
}
```

---

### 任务 6：改造 WorkOrderReleaseConsumer

**Files:**
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mq/consumer/WorkOrderReleaseConsumer.java`

- [ ] **Step 1: 改造 doProcess 方法实现真正的工单创建**

将 `WorkOrderReleaseConsumer.doProcess()` 的占位实现替换为调用 `IProWorkOrderService.createFromErpMessage()`。完整文件如下：

```java
package com.nexus.mes.common.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 工单下达消费者（MES侧）
 * <p>
 * 消费ERP审批通过的工单下达消息，在MES中创建工单记录及工序步骤。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("mes")
public class WorkOrderReleaseConsumer extends BaseMessageConsumer<WorkOrderReleaseMessage> {

    private final IProWorkOrderService proWorkOrderService;

    @Override
    protected Class<WorkOrderReleaseMessage> getMessageClass() {
        return WorkOrderReleaseMessage.class;
    }

    @Override
    protected void doProcess(WorkOrderReleaseMessage message) {
        log.info("收到工单下达消息: woId={}, woNo={}, materialCode={}, plannedQty={}",
            message.getWoId(), message.getWoNo(), message.getMaterialCode(), message.getPlannedQty());

        // 设置租户上下文（消费者线程无登录态，需从消息中恢复租户）
        String tenantId = message.getTenantId() != null ? String.valueOf(message.getTenantId()) : null;
        if (tenantId != null) {
            TenantHelper.dynamic(tenantId, () -> {
                proWorkOrderService.createFromErpMessage(message);
            });
        } else {
            // 租户ID为空时直接执行（兼容未开启租户场景）
            proWorkOrderService.createFromErpMessage(message);
        }
    }

    @RabbitListener(queues = QueueConstants.MES_WORKORDER_QUEUE)
    public void onMessage(WorkOrderReleaseMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
```

**关键变更说明：**
- 移除 `IMesErpBridgeService` 依赖，改为注入 `IProWorkOrderService`
- `doProcess()` 调用 `proWorkOrderService.createFromErpMessage(message)` 完成工单创建
- 使用 `TenantHelper.dynamic(tenantId, runnable)` 设置租户上下文，确保多租户场景下数据隔离
- 幂等性由 `BaseMessageConsumer`（messageId Redis 24h）+ `createFromErpMessage` 内部 erpWoId 查重双重保障

---

### 任务 7：MES MQ 发送服务骨架

**Files:**
- Create: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/common/mq/MesMqSendService.java`

- [ ] **Step 1: 创建 MesMqSendService**

参照 `WmMqSendService` 模式，使用 `@Async` + `push("master")` 确保 `mq_message_record` 写入 master 库。

```java
package com.nexus.mes.common.mq;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.mes.common.mq.message.MesWorkOrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * MES模块MQ消息发送服务
 * <p>
 * 使用 @Async 在独立线程中发送MQ消息，脱离 @DS("mes") 的数据源上下文。
 * 因为 mq_message_record 表位于 ry-vue(master) 库而非 ry-mes 业务库，
 * 异步线程中手动 push("master") 确保消息记录写入正确库。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MesMqSendService {

    private final ReliableMessageProducer messageProducer;

    /**
     * 异步发送MES工单完工消息到ERP
     *
     * @param mesWoId      MES工单ID
     * @param erpWoId      ERP工单ID
     * @param woNo         工单编号
     * @param completedQty 完工数量
     * @param scrappedQty  报废数量
     * @param tenantId     租户ID
     */
    @Async
    public void sendWorkOrderCompleted(Long mesWoId, Long erpWoId, String woNo,
                                       java.math.BigDecimal completedQty,
                                       java.math.BigDecimal scrappedQty,
                                       String tenantId) {
        log.info("异步发送MES工单完工消息: mesWoId={}, erpWoId={}, woNo={}", mesWoId, erpWoId, woNo);
        MesWorkOrderCompletedMessage msg = new MesWorkOrderCompletedMessage();
        msg.setMesWoId(mesWoId);
        msg.setErpWoId(erpWoId);
        msg.setWoNo(woNo);
        msg.setCompletedQty(completedQty);
        msg.setScrappedQty(scrappedQty);
        msg.setSourceModule("mes");
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.ERP_WORKORDER_COMPLETED, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
```

**说明：** Week 18 仅创建骨架，完工消息发送逻辑将在 Week 19+（MES 工单完工操作实现时）调用。

---

### 任务 8：MES 工单前端基础版

**Files:**
- Create: `plus-ui/src/views/mes/pro/workorder/index.vue`
- Create: `plus-ui/src/views/mes/pro/workorder/detail.vue`
- Create: `plus-ui/src/api/mes/pro/workorder.ts`

- [ ] **Step 1: 创建 API 接口文件**

```typescript
// plus-ui/src/api/mes/pro/workorder.ts
import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProWorkOrderVO, ProWorkOrderForm, ProWorkOrderQuery } from './types';

/** 分页查询MES工单列表 */
export const listWorkOrder = (query?: ProWorkOrderQuery): AxiosPromise<ProWorkOrderVO[]> => {
  return request({
    url: '/mes/pro/work/list',
    method: 'get',
    params: query
  });
};

/** 查询MES工单详情 */
export const getWorkOrder = (mesWoId: string | number): AxiosPromise<ProWorkOrderVO> => {
  return request({
    url: `/mes/pro/work/${mesWoId}`,
    method: 'get'
  });
};

/** 新增MES工单 */
export const addWorkOrder = (data: ProWorkOrderForm) => {
  return request({
    url: '/mes/pro/work',
    method: 'post',
    data: data
  });
};

/** 修改MES工单 */
export const updateWorkOrder = (data: ProWorkOrderForm) => {
  return request({
    url: '/mes/pro/work',
    method: 'put',
    data: data
  });
};

/** 删除MES工单 */
export const delWorkOrder = (mesWoIds: string | number | string[] | number[]) => {
  return request({
    url: `/mes/pro/work/${mesWoIds}`,
    method: 'delete'
  });
};

/** 下达MES工单 */
export const releaseWorkOrder = (mesWoId: string | number) => {
  return request({
    url: `/mes/pro/work/release/${mesWoId}`,
    method: 'put'
  });
};
```

- [ ] **Step 2: 创建类型定义文件**

```typescript
// plus-ui/src/api/mes/pro/types.ts
export interface ProWorkOrderVO {
  mesWoId: number;
  erpWoId: number;
  woNo: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  plannedQty: number;
  completedQty: number;
  scrapQty: number;
  unitId: number;
  unitName: string;
  planStartDate: string;
  planEndDate: string;
  actualStartDate: string;
  actualEndDate: string;
  workshopId: number;
  workshopName: string;
  bomId: number;
  routingId: number;
  woStatus: string;
  priority: number;
  remark: string;
  steps?: ProWorkOrderStepVO[];
}

export interface ProWorkOrderStepVO {
  stepId: number;
  mesWoId: number;
  sequenceNum: number;
  processId: number;
  processCode: string;
  processName: string;
  workcenterId: number;
  workcenterCode: string;
  plannedQty: number;
  completedQty: number;
  scrapQty: number;
  stepStatus: string;
  isKeyProcess: number;
  startTime: string;
  endTime: string;
  remark: string;
}

export interface ProWorkOrderForm {
  mesWoId?: number;
  erpWoId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  plannedQty?: number;
  workshopId?: number;
  workshopName?: string;
  bomId?: number;
  routingId?: number;
  woStatus?: string;
  priority?: number;
  remark?: string;
}

export interface ProWorkOrderQuery extends PageQuery {
  woNo?: string;
  erpWoId?: number;
  materialId?: number;
  materialName?: string;
  workshopId?: number;
  woStatus?: string;
}
```

- [ ] **Step 3: 创建列表页 index.vue**

```vue
<!-- plus-ui/src/views/mes/pro/workorder/index.vue -->
<template>
  <div class="p-2">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="工单编号" prop="woNo">
          <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="产品名称" prop="materialName">
          <el-input v-model="queryParams.materialName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="woStatus">
          <el-select v-model="queryParams.woStatus" placeholder="请选择状态" clearable>
            <el-option label="新建" value="CREATED" />
            <el-option label="已下达" value="RELEASED" />
            <el-option label="生产中" value="IN_PROGRESS" />
            <el-option label="已完工" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作栏 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:pro:work:add']">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()"
            v-hasPermi="['mes:pro:work:remove']">删除</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="workOrderList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="工单编号" prop="woNo" width="160" />
        <el-table-column label="ERP工单ID" prop="erpWoId" width="120" align="center" />
        <el-table-column label="产品编码" prop="materialCode" width="140" />
        <el-table-column label="产品名称" prop="materialName" show-overflow-tooltip />
        <el-table-column label="计划数量" prop="plannedQty" width="100" align="right" />
        <el-table-column label="车间" prop="workshopName" width="120" />
        <el-table-column label="状态" prop="woStatus" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.woStatus)">{{ statusLabel(scope.row.woStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计划开工" prop="planStartDate" width="160" />
        <el-table-column label="操作" width="200" align="center">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button link type="primary" icon="Promotion" @click="handleRelease(scope.row)"
              v-if="scope.row.woStatus === 'CREATED'" v-hasPermi="['mes:pro:work:edit']">下达</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesWorkOrder">
import { listWorkOrder, delWorkOrder, releaseWorkOrder } from '@/api/mes/pro/workorder';
import type { ProWorkOrderVO, ProWorkOrderQuery } from '@/api/mes/pro/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const workOrderList = ref<ProWorkOrderVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryParams = ref<ProWorkOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  woNo: undefined,
  materialName: undefined,
  woStatus: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listWorkOrder(queryParams.value);
    workOrderList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

const handleSelectionChange = (selection: ProWorkOrderVO[]) => {
  ids.value = selection.map((item) => item.mesWoId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const handleAdd = () => {
  // Week 18 暂不实现新增弹窗，工单通过ERP消息自动创建
  proxy?.$modal.msgWarning('MES工单通过ERP工单下达自动创建，暂不支持手动新增');
};

const handleDetail = (row: ProWorkOrderVO) => {
  router.push(`/mes/pro/workorder/detail/${row.mesWoId}`);
};

const handleRelease = async (row: ProWorkOrderVO) => {
  await proxy?.$modal.confirm('确认下达工单"' + row.woNo + '"？');
  await releaseWorkOrder(row.mesWoId);
  proxy?.$modal.msgSuccess('下达成功');
  getList();
};

const handleDelete = async () => {
  await proxy?.$modal.confirm('确认删除选中的工单？');
  await delWorkOrder(ids.value);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    CREATED: '新建',
    RELEASED: '已下达',
    IN_PROGRESS: '生产中',
    COMPLETED: '已完工',
    CANCELLED: '已取消'
  };
  return map[status] || status;
};

const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    CREATED: 'info',
    RELEASED: 'success',
    IN_PROGRESS: 'warning',
    COMPLETED: 'primary',
    CANCELLED: 'danger'
  };
  return map[status] || 'info';
};

const router = useRouter();

onMounted(() => {
  getList();
});
</script>
```

- [ ] **Step 4: 创建详情页 detail.vue**

```vue
<!-- plus-ui/src/views/mes/pro/workorder/detail.vue -->
<template>
  <div class="p-2">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>MES工单详情 - {{ workOrder.woNo }}</span>
          <el-button style="float: right" type="primary" plain @click="goBack">返回</el-button>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="MES工单ID">{{ workOrder.mesWoId }}</el-descriptions-item>
        <el-descriptions-item label="ERP工单ID">{{ workOrder.erpWoId }}</el-descriptions-item>
        <el-descriptions-item label="工单编号">{{ workOrder.woNo }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ workOrder.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ workOrder.materialName }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ workOrder.plannedQty }} {{ workOrder.unitName }}</el-descriptions-item>
        <el-descriptions-item label="车间">{{ workOrder.workshopName }}</el-descriptions-item>
        <el-descriptions-item label="工艺路线ID">{{ workOrder.routingId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(workOrder.woStatus)">{{ statusLabel(workOrder.woStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划开工">{{ workOrder.planStartDate }}</el-descriptions-item>
        <el-descriptions-item label="计划完工">{{ workOrder.planEndDate }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ workOrder.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="mt-2">
      <template #header>
        <span>工序步骤</span>
      </template>
      <el-table :data="workOrder.steps" border>
        <el-table-column label="序号" prop="sequenceNum" width="80" align="center" />
        <el-table-column label="工序编码" prop="processCode" width="140" />
        <el-table-column label="工序名称" prop="processName" />
        <el-table-column label="工作中心" prop="workcenterCode" width="140" />
        <el-table-column label="关键工序" prop="isKeyProcess" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isKeyProcess === 1" type="danger">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="计划数量" prop="plannedQty" width="100" align="right" />
        <el-table-column label="状态" prop="stepStatus" width="100" align="center">
          <template #default="scope">
            <el-tag :type="stepStatusTagType(scope.row.stepStatus)">{{ stepStatusLabel(scope.row.stepStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesWorkOrderDetail">
import { getWorkOrder } from '@/api/mes/pro/workorder';
import type { ProWorkOrderVO } from '@/api/mes/pro/types';

const route = useRoute();
const router = useRouter();

const workOrder = ref<ProWorkOrderVO>({});

const loadDetail = async () => {
  const mesWoId = route.params.mesWoId as string;
  const res = await getWorkOrder(mesWoId);
  workOrder.value = res.data;
};

const goBack = () => {
  router.push('/mes/pro/workorder');
};

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    CREATED: '新建', RELEASED: '已下达', IN_PROGRESS: '生产中', COMPLETED: '已完工', CANCELLED: '已取消'
  };
  return map[status] || status;
};
const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    CREATED: 'info', RELEASED: 'success', IN_PROGRESS: 'warning', COMPLETED: 'primary', CANCELLED: 'danger'
  };
  return map[status] || 'info';
};
const stepStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待开始', READY: '就绪', IN_PROGRESS: '进行中', COMPLETED: '已完成', SKIPPED: '已跳过'
  };
  return map[status] || status;
};
const stepStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'info', READY: 'warning', IN_PROGRESS: 'warning', COMPLETED: 'success', SKIPPED: 'info'
  };
  return map[status] || 'info';
};

onMounted(() => {
  loadDetail();
});
</script>
```

---

### 任务 9：菜单权限补充 SQL

**Files:**
- Create: `doc/mes/sql/mes-week18-11-menu.sql`

- [ ] **Step 1: 生成菜单权限补充 SQL**

Week 17 已创建 3010（MES工单菜单）和 3011~3017（按钮权限）。Week 18 补充"下达"按钮权限和详情页路由。

```sql
-- =============================================
-- NexusERP - MES模块 Week 18 菜单权限补充SQL
-- 数据库: ry-vue
-- 说明: 补充MES工单下达按钮权限
-- 创建时间: 2026-06-17
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 补充MES工单按钮权限（Week 17已创建3011~3017，Week 18补充3018下达权限）
-- -----------------------------------------------------------
-- 注意: 若Week 17已包含下达权限，此SQL可跳过
-- 此处假设Week 17的3011~3017为:list/query/add/edit/remove/export/submit
-- 3018为release(下达)

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3018, 'MES工单下达', 3010, 7, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:edit', '#', 103, 1, NOW(), 'MES工单下达按钮')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- 前端路由配置提示（需在前端路由文件中手动配置或通过菜单管理界面配置）:
-- 路由路径: /mes/pro/workorder
-- 组件路径: mes/pro/workorder/index
-- 详情路由: /mes/pro/workorder/detail/:mesWoId
-- 组件路径: mes/pro/workorder/detail
```

---

### 任务 10：编译验证

- [ ] **Step 1: 编译 MES 模块**

Run: `mvn compile -pl ruoyi-modules/ruoyi-mes -am`
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动验证（待执行）**

```bash
# 1. 执行菜单权限补充SQL
mysql -u root -p ry-vue < doc/mes/sql/mes-week18-11-menu.sql

# 2. 启动应用
mvn spring-boot:run -pl ruoyi-admin

# 验证点:
# - ruoyi-admin 能正常启动
# - MES工单菜单和页面可访问
# - ERP审批工单后，MES能收到消息并创建工单
```

---

## 四、关键变更说明

### 4.1 模块结构（Week 18 新增）

```
ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/
├── common/
│   ├── bridge/
│   │   ├── IMesErpBridgeService.java       # 扩展: getWorkshop/getWorkstation
│   │   └── MesErpBridgeServiceImpl.java
│   ├── mapper/
│   │   ├── ErpMaterialMapper.java
│   │   ├── ErpBomLineMapper.java
│   │   ├── ErpRoutingStepMapper.java
│   │   ├── ErpWorkshopMapper.java          # 新增
│   │   └── ErpWorkstationMapper.java       # 新增
│   └── mq/
│       ├── MesMqSendService.java           # 新增: MQ发送服务骨架
│       └── consumer/
│           └── WorkOrderReleaseConsumer.java  # 改造: 实现真正工单创建
├── pro/                                     # 新增: 生产执行
│   ├── domain/
│   │   ├── ProWorkOrder.java
│   │   ├── ProWorkOrderStep.java
│   │   ├── bo/ProWorkOrderBo.java
│   │   └── vo/
│   │       ├── ProWorkOrderVo.java
│   │       └── ProWorkOrderStepVo.java
│   ├── mapper/
│   │   ├── ProWorkOrderMapper.java
│   │   └── ProWorkOrderStepMapper.java
│   ├── service/
│   │   ├── IProWorkOrderService.java
│   │   └── impl/ProWorkOrderServiceImpl.java
│   └── controller/
│       └── ProWorkOrderController.java
└── ...

plus-ui/src/
├── api/mes/pro/
│   ├── workorder.ts                         # 新增
│   └── types.ts                             # 新增
└── views/mes/pro/workorder/
    ├── index.vue                            # 新增: 工单列表
    └── detail.vue                           # 新增: 工单详情
```

### 4.2 MES 工单状态机（Week 18）

```
[ERP审批通过] --MQ消息--> MES工单创建(CREATED)
                              |
                              | 手动release / Week 20 Flowable审批
                              v
                          RELEASED
                              |
                              | Week 19+ 工序执行
                              v
                        IN_PROGRESS
                              |
                              | Week 19+ 完工
                              v
                        COMPLETED
```

Week 18 实现：`CREATED`（自动创建）-> `RELEASED`（手动下达）

### 4.3 WorkOrderReleaseConsumer 改造要点

| 项目 | Week 17（占位） | Week 18（正式） |
|------|----------------|----------------|
| 依赖 | `IMesErpBridgeService` | `IProWorkOrderService` |
| 行为 | 打印日志 | 创建MES工单+工序 |
| 幂等 | messageId Redis | messageId + erpWoId 双重 |
| 租户 | 未处理 | `TenantHelper.dynamic()` |

### 4.4 数据源切换策略

| 操作 | 数据源 | 机制 |
|------|--------|------|
| MES工单CRUD | mes | `@DS("mes")` 类注解 |
| 桥接查询ERP主数据 | erp | `DynamicDataSourceContextHolder.push("erp")` 编程式 |
| MQ消息记录写入 | master | `MesMqSendService` `@Async` + `push("master")` |
| Flowable流程表读写 | master | `WfMyBusinessServiceImpl` `@DS("master")`（Week 20） |

---

## 五、验证步骤

### 5.1 编译验证

```bash
mvn compile -pl ruoyi-modules/ruoyi-mes -am
# 预期: BUILD SUCCESS
```

### 5.2 功能验证

| 验证场景 | 操作 | 预期结果 |
|---------|------|---------|
| ERP工单下达 | ERP审批通过工单 | MES收到消息，创建工单(CREATED)+工序(PENDING) |
| 幂等消费 | 重发同一工单下达消息 | MES跳过创建，日志打印"已存在" |
| 工单查询 | 前端访问MES工单列表 | 显示从ERP创建的工单 |
| 工单详情 | 点击详情按钮 | 显示工单信息+工序步骤Tab |
| 手动下达 | 点击下达按钮 | 工单状态 CREATED -> RELEASED |
| 无工艺路线 | ERP物料未配工艺路线 | MES创建工单但无工序，日志告警 |

### 5.3 桥接验证

| 验证场景 | 操作 | 预期结果 |
|---------|------|---------|
| 物料查询 | 消息携带materialId | 桥接返回物料信息，补全unitId |
| 车间查询 | 消息携带workshopId | 桥接返回车间名称 |
| 工艺路线查询 | 物料有默认工艺路线 | 桥接返回routingId和步骤列表 |

---

## 六、Week 19 预告

| 任务 | 关键内容 |
|------|---------|
| 只读前端完善 | MES侧主数据只读页面（物料/BOM/工艺路线浏览） |
| Flowable审批流准备 | `IProWorkOrderService extends WfCallBackServiceI`，实现 `afterFlowHandle`/`submit`/`getProcInsId` |
| 工单完工骨架 | `complete` 接口 + `MesMqSendService.sendWorkOrderCompleted` 调用 |

---

## 七、风险与注意事项

| 风险 | 说明 | 应对 |
|------|------|------|
| 租户上下文丢失 | 消费者线程无登录态，`TenantHelper` 无法自动获取租户 | 消息携带 `tenantId`，消费时 `TenantHelper.dynamic()` 显式设置 |
| 工艺路线为空 | ERP物料未配置默认工艺路线，工序无法生成 | 记录告警日志，工单仍创建（无工序），后续可手动补工序 |
| 桥接查询失败 | ERP库不可用或数据缺失 | 桥接方法返回 null，工单使用消息中的冗余字段创建 |
| `selectVoOne` 多结果 | erpWoId 理论唯一，但若重复消费未幂等可能多条 | `createFromErpMessage` 入口先查重，已存在则跳过 |
| 前端路由未配置 | 菜单SQL仅配置按钮权限，路由需前端配置 | 在前端路由文件或菜单管理界面配置 `/mes/pro/workorder` 路由 |
| `WorkOrderReleaseMessage.tenantId` 类型 | `BaseMessage.tenantId` 为 `Long`，`TenantHelper` 需 `String` | 消费时 `String.valueOf(tenantId)` 转换，注意 "000000" 前导零问题 |
| Week 20 Flowable 集成 | Week 18 未集成 Flowable，`IProWorkOrderService` 未 extends `WfCallBackServiceI` | Week 20 再扩展接口，不影响 Week 18 已有代码 |

---

**文档生成时间**: 2026-06-17
**执行者**: AI Agent
**审核状态**: 待人工确认
