package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.domain.vo.WmStockTakingPlanVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStockTakingLineMapper;
import com.nexus.erp.wm.mapper.WmStockTakingPlanMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.IWmStockTakingPlanService;
import com.nexus.workflow.domain.WfMyBusiness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 库存盘点计划服务实现
 * <p>
 * 实现库存盘点计划服务接口，处理库存盘点计划的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("wmStockTakingPlanService")
public class WmStockTakingPlanServiceImpl implements IWmStockTakingPlanService {

    /** 基础数据访问映射器（Mapper） */
    private final WmStockTakingPlanMapper baseMapper;
    /** 行数据访问映射器（Mapper） */
    private final WmStockTakingLineMapper lineMapper;
    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    /** 库存操作业务服务，提供库存操作相关能力 */
    private final IWmStockOperationService stockOperationService;
    /** WM 公共依赖（AutoCodeUtil / 流程服务 / 流程实例查询） */
    private final WmServiceHelper wm;

    /**
     * 分页查询库存盘点计划列表
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmStockTakingPlanVo> selectPageList(WmStockTakingPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmStockTakingPlan> lqw = buildQueryWrapper(bo);
        Page<WmStockTakingPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询库存盘点计划列表
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmStockTakingPlanVo> selectList(WmStockTakingPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询库存盘点计划
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmStockTakingPlanVo selectById(Long id) {
        WmStockTakingPlanVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getPlanId() != null) {
            LambdaQueryWrapper<WmStockTakingLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmStockTakingLine::getPlanId, vo.getPlanId());
            vo.setWmStockTakingLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增库存盘点计划
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmStockTakingPlanBo bo) {
        WmStockTakingPlan entity = MapstructUtils.convert(bo, WmStockTakingPlan.class);
        if (StringUtils.isBlank(entity.getPlanCode())) {
            entity.setPlanCode(wm.autoCodeUtil.genSerialCode("WM_STOCKTAKING_CODE", null));
        }
        // ERP-WM-10: status 不由 BO 直传，服务端强制草稿，防止伪造 GENERATED/POSTED 单据
        entity.setStatus(STATUS_DRAFT);
        int rows = baseMapper.insert(entity);
        // 回写主键与单号, 供编排层(如 PDA 临时盘点)续做
        bo.setPlanId(entity.getPlanId());
        bo.setPlanCode(entity.getPlanCode());
        if (bo.getWmStockTakingLineList() != null) {
            for (WmStockTakingLine line : bo.getWmStockTakingLineList()) {
                line.setLineId(null);
                line.setPlanId(entity.getPlanId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改库存盘点计划
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmStockTakingPlanBo bo) {
        WmStockTakingPlan entity = MapstructUtils.convert(bo, WmStockTakingPlan.class);
        // ERP-WM-10: 仅草稿/已驳回可改。已生成/已过账的盘点结果会被"全删全插"抹掉，
        // 差异记录消失后再次过账即按新行重复调整库存。
        WmStockTakingPlan existing = baseMapper.selectById(entity.getPlanId());
        if (existing == null) {
            throw new com.nexus.common.core.exception.ServiceException("盘点计划不存在");
        }
        if (STATUS_GENERATED.equals(existing.getStatus())) {
            return saveGeneratedCounts(existing.getPlanId(), bo.getWmStockTakingLineList());
        }
        if (!STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new com.nexus.common.core.exception.ServiceException("当前状态不允许修改，只有草稿和已驳回状态才能修改！");
        }
        entity.setStatus(existing.getStatus());
        deleteLinesByPlanId(entity.getPlanId());
        if (bo.getWmStockTakingLineList() != null) {
            for (WmStockTakingLine line : bo.getWmStockTakingLineList()) {
                line.setLineId(null);
                line.setPlanId(entity.getPlanId());
                lineMapper.insert(line);
            }
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 删除库存盘点计划
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            // ERP-WM-10: 仅草稿可删；删除已生成/已过账单据会让盘点结果与库存调整失去凭据
            WmStockTakingPlan existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new com.nexus.common.core.exception.ServiceException("当前状态不允许删除，只有草稿状态才能删除！");
            }
            deleteLinesByPlanId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 生成库存盘点计划
     *
     * @param planId 计划 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateLines(Long planId) {
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("盘点计划不存在");
        }
        // ERP-WM-10: 仅草稿/已驳回可重建盘点行。
        // 修复前无守卫：对 POSTED 计划再点一次"生成盘点明细"会把已过账行删掉重建为 PENDING，
        // 差异记录消失后再次过账即按新行重复调整库存。
        if (!STATUS_DRAFT.equals(plan.getStatus()) && !STATUS_REJECTED.equals(plan.getStatus())) {
            throw new com.nexus.common.core.exception.ServiceException(
                "当前状态不允许生成盘点明细，只有草稿和已驳回状态才能生成！");
        }
        // 先清除已有盘点行
        deleteLinesByPlanId(planId);

        // 从库存表抓取当前库存快照生成盘点行
        LambdaQueryWrapper<WmMaterialStock> stockLqw = Wrappers.lambdaQuery();
        stockLqw.eq(plan.getWarehouseId() != null, WmMaterialStock::getWarehouseId, plan.getWarehouseId());
        stockLqw.eq(plan.getAreaId() != null, WmMaterialStock::getAreaId, plan.getAreaId());
        stockLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        stockLqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        List<WmMaterialStock> stocks = stockMapper.selectList(stockLqw);

        if (stocks == null || stocks.isEmpty()) {
            throw new RuntimeException("所选仓库/库区没有可用库存");
        }

        for (WmMaterialStock stock : stocks) {
            WmStockTakingLine line = new WmStockTakingLine();
            line.setPlanId(planId);
            line.setMaterialId(stock.getMaterialId());
            line.setMaterialCode(stock.getMaterialCode());
            line.setMaterialName(stock.getMaterialName());
            line.setMaterialSpec(stock.getMaterialSpec());
            line.setBaseUomId(stock.getBaseUomId());
            line.setBaseUomName(stock.getBaseUomName());
            line.setBatchId(stock.getBatchId());
            line.setBatchCode(stock.getBatchCode());
            line.setLocationId(stock.getLocationId());
            line.setLocationCode(stock.getLocationCode());
            line.setLocationName(stock.getLocationName());
            line.setStockId(stock.getStockId());
            line.setBookQuantity(stock.getStockQuantity());
            // ERP-WM-02: 实盘数不得默认写 0 —— 否则"未盘点"与"盘到 0"无法区分，
            // 未录入实盘数直接过账时 diff = 0 - 账面量，一次点击把整仓库存调成 0。
            // 置 NULL 表示"未录入"（wm_stock_taking_line.actual_quantity 本就是 NULL DEFAULT NULL）。
            line.setActualQuantity(null);
            line.setDifferenceQuantity(BigDecimal.ZERO);
            line.setStatus("PENDING");
            lineMapper.insert(line);
        }

        plan.setStatus("GENERATED");
        baseMapper.updateById(plan);
        log.info("盘点计划 {} 生成盘点行完成，共 {} 条", planId, stocks.size());
    }

    /**
     * 执行库存盘点计划相关操作
     *
     * @param planId 计划 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postAdjustment(Long planId) {
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("盘点计划不存在");
        }

        if (!STATUS_GENERATED.equals(plan.getStatus()) || plan.getSelectFlowId() != null) {
            throw new com.nexus.common.core.exception.ServiceException("仅未配置审批流程的已生成计划可手工过账；审批计划须审批通过后过账");
        }

        // 手工过账只接受无审批流程的GENERATED，不能抢占审批中的计划。
        int claimed = baseMapper.update(null,
            Wrappers.<WmStockTakingPlan>lambdaUpdate()
                .set(WmStockTakingPlan::getStatus, "POSTING")
                .eq(WmStockTakingPlan::getPlanId, planId)
                .eq(WmStockTakingPlan::getStatus, STATUS_GENERATED));
        if (claimed == 0) {
            throw new RuntimeException("盘点计划已过账或状态不允许过账");
        }

        List<WmStockTakingLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmStockTakingLine.class)
                .eq(WmStockTakingLine::getPlanId, planId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("盘点行不存在，请先生成盘点行");
        }

        // ERP-WM-02: 手工过账通道同样必须校验实盘数已录入。
        // 此前只有审批自动过账通道调用了 assertAllLinesCounted，手工 postAdjustment 漏掉，
        // 未盘点的行 actualQuantity 为 null 时会被当作 0，逐行把库存调整为 0（一次点击清零整仓）。
        assertAllLinesCounted(lines);

        for (WmStockTakingLine line : lines) {
            // 已过账/已平衡的行跳过，避免重复调整
            if ("POSTED".equals(line.getStatus()) || "BALANCED".equals(line.getStatus())) {
                continue;
            }
            BigDecimal actual = line.getActualQuantity() == null ? BigDecimal.ZERO : line.getActualQuantity();
            BigDecimal book = line.getBookQuantity() == null ? BigDecimal.ZERO : line.getBookQuantity();
            BigDecimal diff = actual.subtract(book);

            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                stockOperationService.postCountAdjust(line, actual, book, diff, new Date());
                line.setDifferenceQuantity(diff);
                line.setStatus("POSTED");
            } else {
                line.setDifferenceQuantity(BigDecimal.ZERO);
                line.setStatus("BALANCED");
            }
            lineMapper.updateById(line);
        }

        plan.setStatus("POSTED");
        baseMapper.updateById(plan);
        log.info("盘点计划 {} 差异过账完成", planId);
    }

    /** 已生成计划只更新实盘数；服务端账面快照、行ID和库存维度不可由PC重建。 */
    private int saveGeneratedCounts(Long planId, List<WmStockTakingLine> requested) {
        WmStockTakingPlan locked = baseMapper.selectOne(Wrappers.<WmStockTakingPlan>lambdaQuery()
            .eq(WmStockTakingPlan::getPlanId, planId).last("FOR UPDATE"));
        if (locked == null || !STATUS_GENERATED.equals(locked.getStatus())) {
            throw new com.nexus.common.core.exception.ServiceException("盘点状态已变化，请重新读取");
        }
        List<WmStockTakingLine> persisted = lineMapper.selectList(Wrappers.<WmStockTakingLine>lambdaQuery()
            .eq(WmStockTakingLine::getPlanId, planId));
        if (requested == null || requested.isEmpty() || requested.size() != persisted.size()) {
            throw new com.nexus.common.core.exception.ServiceException("实盘明细必须完整且与原计划一致");
        }
        Map<Long, WmStockTakingLine> originals = new HashMap<>();
        for (WmStockTakingLine row : persisted) originals.put(row.getLineId(), row);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (WmStockTakingLine input : requested) {
            if (input == null || input.getLineId() == null || !seen.add(input.getLineId())
                    || !originals.containsKey(input.getLineId())
                    || input.getPlanId() != null && !planId.equals(input.getPlanId())) {
                throw new com.nexus.common.core.exception.ServiceException("实盘行重复或不属于当前盘点计划");
            }
            BigDecimal actual = input.getActualQuantity();
            if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 4) {
                throw new com.nexus.common.core.exception.ServiceException("实盘数量必须非负且最多4位小数");
            }
        }
        for (WmStockTakingLine input : requested) {
            WmStockTakingLine original = originals.get(input.getLineId());
            WmStockTakingLine patch = new WmStockTakingLine();
            patch.setLineId(original.getLineId());
            patch.setActualQuantity(input.getActualQuantity());
            patch.setDifferenceQuantity(input.getActualQuantity().subtract(original.getBookQuantity() == null ? BigDecimal.ZERO : original.getBookQuantity()));
            if (lineMapper.updateById(patch) != 1) throw new com.nexus.common.core.exception.ServiceException("盘点行保存失败");
        }
        return requested.size();
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 库存盘点计划处理（submit）
     *
     * @param planIdStr 参数 planIdStr
     * @param selectFlowId selectFlow ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String planIdStr, String selectFlowId) {
        Long planId = Long.parseLong(planIdStr);
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("盘点计划不存在");
        }
        if (!STATUS_GENERATED.equals(plan.getStatus())) {
            throw new com.nexus.common.core.exception.ServiceException("只有已生成并完成实盘录入的计划才能提交审批");
        }
        assertAllLinesCounted(lineMapper.selectList(Wrappers.<WmStockTakingLine>lambdaQuery()
            .eq(WmStockTakingLine::getPlanId, planId)));
        if (StringUtils.isBlank(selectFlowId)) {
            if (plan.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(plan.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", planIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("planId", plan.getPlanId());
        variables.put("planCode", plan.getPlanCode());
        variables.put("planName", plan.getPlanName());
        variables.put("warehouseName", plan.getWarehouseName());
        R<Void> result = wm.processService.startProcessByDataId(planIdStr, selectFlowId, "wmStockTakingPlanService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取库存盘点计划
     *
     * @param planIdStr 参数 planIdStr
     * @return 结果
     */
    @Override
    public String getProcInsId(String planIdStr) {
        return wm.procInsIdHelper.resolve(planIdStr, () -> {
            WfMyBusiness business = wm.wfMyBusinessServiceImpl.getByDataId(planIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 库存盘点计划处理（afterFlowHandle）
     *
     * @param business 参数 business
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long planId = Long.parseLong(business.getDataId());
        WmStockTakingPlan plan = baseMapper.selectById(planId);
        if (plan == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            plan.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            // 审批通过：执行盘点差异调整，设置状态为已完成
            executeCountAdjust(planId);
            plan.setStatus(STATUS_COMPLETED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            plan.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            plan.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(plan);
    }

    /**
     * 获取库存盘点计划详情
     *
     * @param dataId data ID
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 库存盘点计划处理（flowValuesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 库存盘点计划处理（flowCandidateUsernamesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    // ==================== 私有方法 ====================

    /**
     * 执行盘点差异调整（审批通过后调用）
     */
    private void executeCountAdjust(Long planId) {
        // 幂等/并发防护：仅未过账（GENERATED/APPROVING）的计划允许调整，防止与 postAdjustment 双通道重复过账
        int claimed = baseMapper.update(null,
            Wrappers.<WmStockTakingPlan>lambdaUpdate()
                .set(WmStockTakingPlan::getStatus, "POSTING")
                .eq(WmStockTakingPlan::getPlanId, planId)
                .in(WmStockTakingPlan::getStatus, "GENERATED", STATUS_APPROVING));
        if (claimed == 0) {
            log.info("盘点计划 {} 已过账或状态不允许调整，跳过", planId);
            return;
        }

        List<WmStockTakingLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmStockTakingLine.class)
                .eq(WmStockTakingLine::getPlanId, planId));

        if (lines == null || lines.isEmpty()) {
            return;
        }

        // ERP-WM-02: 审批通过后的自动过账同样必须校验实盘数已录入
        // （否则"审批通过"本身就等于一次点击把整仓库存调成 0）
        assertAllLinesCounted(lines);

        for (WmStockTakingLine line : lines) {
            // 已过账/已平衡的行跳过，避免重复调整
            if ("POSTED".equals(line.getStatus()) || "BALANCED".equals(line.getStatus())) {
                continue;
            }
            BigDecimal actual = line.getActualQuantity() == null ? BigDecimal.ZERO : line.getActualQuantity();
            BigDecimal book = line.getBookQuantity() == null ? BigDecimal.ZERO : line.getBookQuantity();
            BigDecimal diff = actual.subtract(book);

            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                stockOperationService.postCountAdjust(line, actual, book, diff, new Date());
                line.setDifferenceQuantity(diff);
                line.setStatus("POSTED");
            } else {
                line.setDifferenceQuantity(BigDecimal.ZERO);
                line.setStatus("BALANCED");
            }
            lineMapper.updateById(line);
        }
        log.info("盘点计划 {} 审批通过，差异调整完成", planId);
    }

    private void deleteLinesByPlanId(Long planId) {
        if (planId != null) {
            LambdaQueryWrapper<WmStockTakingLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmStockTakingLine::getPlanId, planId);
            lineMapper.delete(lqw);
        }
    }

    /**
     * ERP-WM-02: 断言盘点计划下所有未过账行均已录入实盘数。
     * <p>实盘数为 NULL 即"未盘点"。此时若放行过账，diff 会按 {@code 0 - 账面量} 计算，
     * CountAdjustStrategy 逐行把库存调整为 0 —— 一次点击（甚至审批通过）即可清零整仓，
     * 且单据状态变为 POSTED，从界面看是"已正常过账"，账实核对无法发现是误操作。</p>
     *
     * @param lines 同一计划下的盘点行
     */
    private void assertAllLinesCounted(List<WmStockTakingLine> lines) {
        List<String> missing = new java.util.ArrayList<>();
        for (WmStockTakingLine line : lines) {
            if ("POSTED".equals(line.getStatus()) || "BALANCED".equals(line.getStatus())) {
                continue;
            }
            if (line.getActualQuantity() == null) {
                missing.add(StringUtils.isNotBlank(line.getMaterialCode())
                    ? line.getMaterialCode() : String.valueOf(line.getLineId()));
            }
        }
        if (!missing.isEmpty()) {
            int shown = Math.min(missing.size(), 5);
            String detail = String.join("、", missing.subList(0, shown));
            throw new com.nexus.common.core.exception.ServiceException(
                "存在 " + missing.size() + " 行未录入实盘数，禁止过账：" + detail
                    + (missing.size() > shown ? " 等" : ""));
        }
    }

    private LambdaQueryWrapper<WmStockTakingPlan> buildQueryWrapper(WmStockTakingPlanBo bo) {
        LambdaQueryWrapper<WmStockTakingPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getPlanCode()), WmStockTakingPlan::getPlanCode, bo.getPlanCode());
        lqw.like(StringUtils.isNotBlank(bo.getPlanName()), WmStockTakingPlan::getPlanName, bo.getPlanName());
        lqw.eq(bo.getWarehouseId() != null, WmStockTakingPlan::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmStockTakingPlan::getStatus, bo.getStatus());
        lqw.orderByDesc(WmStockTakingPlan::getCreateTime);
        return lqw;
    }
}
