package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueShortageBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
/**
 * 生产发料服务实现
 * <p>
 * 实现生产发料服务接口，处理生产发料的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionIssueServiceImpl implements IWmProductionIssueService {

    /** 领料单/行状态常量 */
    private static final String STATUS_CREATED = "CREATED";
    /** 常量：STATUSPARTIAL */
    private static final String STATUS_PARTIAL = "PARTIAL";
    /** 常量：STATUSCOMPLETED */
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_EXECUTED = "EXECUTED";   /** 历史值，等价于已完成 */
    /** 常量：行PENDING */
    private static final String LINE_PENDING = "PENDING";
    /** 常量：行PARTIAL */
    private static final String LINE_PARTIAL = "PARTIAL";
    /** 常量：行ISSUED */
    private static final String LINE_ISSUED = "ISSUED";

    /** 行状态: 缺货关闭 (未领满但经缺货逃生通道完成) */
    private static final String LINE_SHORT_CLOSED = "SHORT_CLOSED";

    /** 基础数据访问映射器（Mapper） */
    private final WmProductionIssueMapper baseMapper;
    /** 行数据访问映射器（Mapper） */
    private final WmProductionIssueLineMapper lineMapper;
    /** 库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper stockMapper;
    private final WmWarehouseMapper warehouseMapper;
    /** 解耦：注入库存操作接口而非实现 */
    private final IWmStockOperationService stockOperationService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;
    /** 生产发料数据访问映射器（Mapper） */
    private final PrdWorkOrderMapper workOrderMapper;
    /** 生产发料数据访问映射器（Mapper） */
    private final PrdMaterialRequirementMapper requirementMapper;
    /** 明细数据访问映射器（Mapper） */
    private final PrdMaterialRequirementDetailMapper detailMapper;

    /**
     * 分页查询生产发料列表
     *
     * @param bo 生产发料业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmProductionIssueVo> selectPageList(WmProductionIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionIssue> lqw = buildQueryWrapper(bo);
        Page<WmProductionIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产发料列表
     *
     * @param bo 生产发料业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmProductionIssueVo> selectList(WmProductionIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询生产发料
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmProductionIssueVo selectById(Long id) {
        WmProductionIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getIssueId() != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmProductionIssueLine::getIssueId, vo.getIssueId());
            vo.setWmProductionIssueLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增生产发料
     *
     * @param bo 生产发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmProductionIssueBo bo) {
        if (bo == null || bo.getIssueId() != null) {
            throw new ServiceException("新建领料单不得指定已有ID");
        }
        if (StringUtils.isNotBlank(bo.getStatus()) && !STATUS_CREATED.equals(bo.getStatus()) && !"DRAFT".equals(bo.getStatus())) {
            throw new ServiceException("新建领料单只能是未领料状态");
        }
        if (bo.getWmProductionIssueLineList() != null && bo.getWmProductionIssueLineList().stream()
            .anyMatch(line -> line == null || zeroIfNull(line.getIssuedQty()).signum() != 0)) {
            throw new ServiceException("新建领料单不得填写已领数量");
        }
        WmProductionIssue entity = MapstructUtils.convert(bo, WmProductionIssue.class);
        entity.setStatus(STATUS_CREATED);
        if (StringUtils.isBlank(entity.getIssueCode())) {
            entity.setIssueCode(autoCodeUtil.genSerialCode("WM_PROD_ISSUE_CODE", null));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_CREATED);
        }
        if (StringUtils.isBlank(entity.getIssueType())) {
            entity.setIssueType("NORMAL");
        }
        /** 未指定领料人时默认取当前操作人 */
        if (entity.getIssuerId() == null) {
            entity.setIssuerId(entity.getCreateBy());
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getIssueId(), bo.getWmProductionIssueLineList());
        recalcHeaderTotals(entity.getIssueId());
        return rows;
    }

    /**
     * 修改生产发料
     *
     * @param bo 生产发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionIssueBo bo) {
        if (bo == null || bo.getIssueId() == null) {
            throw new ServiceException("领料单ID不能为空");
        }
        WmProductionIssue current = baseMapper.selectById(bo.getIssueId());
        if (current == null || (!STATUS_CREATED.equals(current.getStatus()) && !STATUS_PARTIAL.equals(current.getStatus()))) {
            throw new ServiceException("仅未完成的领料单可编辑，已过账完成的单据不可重开");
        }
        if (StringUtils.isNotBlank(bo.getStatus()) && !current.getStatus().equals(bo.getStatus())) {
            throw new ServiceException("领料状态由执行流程维护，不可通过编辑修改");
        }
        if ((bo.getWoId() != null && !Objects.equals(bo.getWoId(), current.getWoId()))
            || (StringUtils.isNotBlank(bo.getIssueCode()) && !Objects.equals(bo.getIssueCode(), current.getIssueCode()))) {
            throw new ServiceException("领料单号及来源工单不可改绑");
        }
        WmProductionIssue entity = MapstructUtils.convert(bo, WmProductionIssue.class);
        entity.setStatus(current.getStatus());
        entity.setWoId(current.getWoId());
        entity.setWoNo(current.getWoNo());
        entity.setIssueCode(current.getIssueCode());
        entity.setTotalQty(null);
        entity.setTotalAmount(null);
        /** 条件更新锁定父单，阻止读后并发执行完成的单据被编辑重开。 */
        int rows = baseMapper.update(entity, Wrappers.<WmProductionIssue>lambdaUpdate()
            .eq(WmProductionIssue::getIssueId, current.getIssueId())
            .eq(WmProductionIssue::getStatus, current.getStatus()));
        if (rows != 1) {
            throw new ServiceException("领料状态已变化，请刷新后重试");
        }
        /** 增量同步行保留原ID；总额最后由后端重算，不接受页面回传总额。 */
        syncLines(entity.getIssueId(), bo.getWmProductionIssueLineList());
        recalcHeaderTotals(entity.getIssueId());
        return rows;
    }

    /**
     * 删除生产发料
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            WmProductionIssue current = baseMapper.selectById(id);
            if (current == null || !STATUS_CREATED.equals(current.getStatus())) {
                throw new ServiceException("仅未领料的新建单据可删除，已领料单据须保留库存追溯");
            }
        }
        for (Long id : ids) {
            List<WmProductionIssueLine> lines = lineMapper.selectList(Wrappers.<WmProductionIssueLine>lambdaQuery()
                .eq(WmProductionIssueLine::getIssueId, id));
            if (lines.stream().anyMatch(line -> zeroIfNull(line.getIssuedQty()).signum() > 0)) {
                throw new ServiceException("已有领料流水的单据不可删除");
            }
            int claimed = baseMapper.update(null, Wrappers.<WmProductionIssue>lambdaUpdate()
                .set(WmProductionIssue::getStatus, "DELETING")
                .eq(WmProductionIssue::getIssueId, id)
                .eq(WmProductionIssue::getStatus, STATUS_CREATED));
            if (claimed != 1) {
                throw new ServiceException("领料状态已变化，请刷新后重试");
            }
            deleteLinesByIssueId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行生产领料过账 — 支持多次部分领料。
     * <p>
     * 逐行按本次领料数量({@code current_qty})扣减库存（复用 {@link IWmStockOperationService#postProductionIssue}
     * 的 FEFO 跨批次扣减与流水记录），累计已领、重算欠领，current_qty 清零等待下次。
     * 扣减前对每行做可用量(ATP)校验，防止超扣；整个过程置于单事务，任一行失败则整体回滚。
     * <p>
     * 并发说明：同一领料单不建议并发执行；库存扣减走 {@code WmMaterialStock} 的 {@code @Version}
     * 乐观锁，若多张领料单同时扣减同一批次库存可能抛 {@code OptimisticLockingFailureException}
     * 导致本单回滚，调用方可重试。事务内重试无效（失败已标记回滚），故未在此内置重试。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long issueId) {
        WmProductionIssue issue = baseMapper.selectById(issueId);
        if (issue == null) {
            throw new RuntimeException("生产领料单不存在");
        }
        String status = issue.getStatus();
        if (STATUS_COMPLETED.equals(status) || STATUS_EXECUTED.equals(status)) {
            throw new RuntimeException("生产领料单已完成，不可再次领料");
        }
        if (!STATUS_CREATED.equals(status) && !STATUS_PARTIAL.equals(status)) {
            throw new RuntimeException("当前状态不允许执行领料：" + status);
        }
        /**
         * ERP-WM-05: 单据级 CAS 抢占（CREATED/PARTIAL → EXECUTING）。
         * 修复前只做读判断，双击或 PC+PDA 并发时两次请求都读到 CREATED，各自按同一 current_qty 扣一次库存。
         */
        int claimed = baseMapper.update(null, Wrappers.<WmProductionIssue>lambdaUpdate()
            .set(WmProductionIssue::getStatus, "EXECUTING")
            .eq(WmProductionIssue::getIssueId, issueId)
            .in(WmProductionIssue::getStatus, STATUS_CREATED, STATUS_PARTIAL));
        if (claimed == 0) {
            throw new ServiceException("生产领料单状态已变更（可能已被并发执行），请刷新后重试");
        }

        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("生产领料单无明细行");
        }

        Map<Long, Map<Long, List<WmProductionIssueLine>>> groupedPicks = new HashMap<>();
        /**
         * 任何库存过账前，先预检每个正数领料量；发现仓库无效或缺货时，事务也会回滚
         * CAS 抢占。
         */
        for (WmProductionIssueLine line : lines) {
            BigDecimal currentQty = line.getCurrentQty();
            if (currentQty == null || currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Long warehouseId = line.getWarehouseId() != null ? line.getWarehouseId() : issue.getWarehouseId();
            if (warehouseId == null) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】请选择领料仓库");
            }
            WmWarehouse warehouse = warehouseMapper.selectById(warehouseId);
            if (warehouse == null || !Long.valueOf(1L).equals(warehouse.getStatus())) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】领料仓库不存在或未启用：" + warehouseId);
            }
            line.setWarehouseId(warehouseId);
            line.setWarehouseCode(warehouse.getWarehouseCode());
            line.setWarehouseName(warehouse.getWarehouseName());
            groupedPicks.computeIfAbsent(line.getMaterialId(), ignored -> new HashMap<>())
                .computeIfAbsent(warehouseId, ignored -> new ArrayList<>()).add(line);
            BigDecimal available = sumAvailable(line.getMaterialId(), warehouseId,
                line.getBatchId(), line.getLocationId());
            if (available.compareTo(currentQty) < 0) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】仓库【" + warehouse.getWarehouseName()
                    + "】库存不足，可用量：" + available);
            }
        }

        /** 逐行独立检查无法发现两次领料正在竞争同一仓库的库存。 */
        for (Map.Entry<Long, Map<Long, List<WmProductionIssueLine>>> material : groupedPicks.entrySet()) {
            for (Map.Entry<Long, List<WmProductionIssueLine>> warehouse : material.getValue().entrySet()) {
                List<WmProductionIssueLine> picks = warehouse.getValue();
                if (picks.size() <= 1) {
                    continue;
                }
                BigDecimal demand = BigDecimal.ZERO;
                for (WmProductionIssueLine pick : picks) {
                    demand = demand.add(pick.getCurrentQty());
                }
                BigDecimal available = sumAvailable(material.getKey(), warehouse.getKey(), null, null);
                if (available.compareTo(demand) < 0) {
                    WmProductionIssueLine first = picks.get(0);
                    throw new ServiceException("物料【" + first.getMaterialName() + "】仓库【" + first.getWarehouseName()
                        + "】库存不足，可用量：" + available);
                }
            }
        }
        Date now = new Date();
        boolean hasPick = false;

        boolean allIssued = true;

        for (WmProductionIssueLine line : lines) {
            BigDecimal currentQty = line.getCurrentQty();
            /** 本次不领料的行：仅判断是否已领满以决定单头状态 */
            if (currentQty == null || currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                if (!isLineFullyIssued(line)) {
                    allIssued = false;
                }
                continue;
            }
            hasPick = true;

            BigDecimal requiredQty = zeroIfNull(line.getRequiredQty());
            BigDecimal issuedQty = zeroIfNull(line.getIssuedQty());
            /** 欠领 = 应领 - 已领；应领为空（手工行）时不设上限，以可用量为准 */
            BigDecimal shortQty = requiredQty.compareTo(BigDecimal.ZERO) > 0
                ? requiredQty.subtract(issuedQty) : null;
            if (line.getShortQty() != null) {
                shortQty = line.getShortQty();
            }

            /**
             * ERP-WM-04：超领守卫。修复前写成 {@code shortQty > 0 && currentQty > shortQty}，
             * 该行已领满时 {@code shortQty == 0} 导致整个校验被短路，仍可继续领料，仅受可用库存限制。
             * 改为上限 {@code max(shortQty, 0)}：已领满时任何正数本次领料量都必须拒绝。
             */
            if (shortQty != null) {
                BigDecimal allowed = shortQty.max(BigDecimal.ZERO);
                if (currentQty.compareTo(allowed) > 0) {
                    throw new RuntimeException("物料【" + line.getMaterialName() + "】本次领料数量(" + currentQty
                        + ")超过欠领数量(" + allowed + ")");
                }
            }

            /**
             * 策略读取 line.getQuantity() 扣减；为不污染 quantity 的应领语义，
             * 备份原值，临时令 quantity=本次领料量，扣减后恢复。
             */
            BigDecimal originQuantity = line.getQuantity();
            line.setQuantity(currentQty);
            stockOperationService.postProductionIssue(line, now);
            line.setQuantity(originQuantity);

            /** 累计已领、重算欠领、本次清零 */
            BigDecimal newIssued = issuedQty.add(currentQty);
            BigDecimal newShort = requiredQty.compareTo(BigDecimal.ZERO) > 0
                ? requiredQty.subtract(newIssued) : BigDecimal.ZERO;
            if (newShort.compareTo(BigDecimal.ZERO) < 0) {
                newShort = BigDecimal.ZERO;
            }
            line.setIssuedQty(newIssued);
            line.setShortQty(newShort);
            line.setCurrentQty(null);
            if (requiredQty.compareTo(BigDecimal.ZERO) > 0 && newShort.compareTo(BigDecimal.ZERO) <= 0) {
                line.setLineStatus(LINE_ISSUED);
            } else {
                line.setLineStatus(LINE_PARTIAL);
                allIssued = false;
            }
            lineMapper.updateById(line);

            /** 回写工单物料需求明细的已发量/欠料量 */
            updateRequirementDetail(line.getRequirementDetailId(), currentQty);
        }

        if (!hasPick) {
            throw new RuntimeException("没有需要领料的行（本次领料数量均为0），请填写本次领料数量后再执行");
        }

        issue.setStatus(allIssued ? STATUS_COMPLETED : STATUS_PARTIAL);
        WmProductionIssue statusUpdate = new WmProductionIssue();
        statusUpdate.setIssueId(issueId);
        statusUpdate.setStatus(issue.getStatus());
        baseMapper.updateById(statusUpdate);
        recalcHeaderTotals(issueId);
        log.info("生产领料单执行完成, issueId={}, status={}", issueId, issue.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeWithShortage(Long issueId, List<PdaIssueShortageBo> shortages) {
        WmProductionIssue issue = baseMapper.selectById(issueId);
        if (issue == null) {
            throw new ServiceException("生产领料单不存在");
        }
        String status = issue.getStatus();
        if (STATUS_COMPLETED.equals(status) || STATUS_EXECUTED.equals(status)) {
            return; /** 幂等: 已完成直接返回 */
        }
        if (!STATUS_CREATED.equals(status) && !STATUS_PARTIAL.equals(status)) {
            throw new ServiceException("当前状态不允许缺货完成：" + status);
        }

        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("生产领料单无明细行");
        }

        Map<Long, PdaIssueShortageBo> declared = new HashMap<>();
        if (shortages != null) {
            for (PdaIssueShortageBo s : shortages) {
                if (s.getLineId() != null) {
                    declared.put(s.getLineId(), s);
                }
            }
        }

        for (WmProductionIssueLine line : lines) {
            if (isLineFullyIssued(line)) {
                continue;
            }
            PdaIssueShortageBo s = declared.remove(line.getLineId());
            if (s == null) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】未领满且未声明缺货");
            }
            BigDecimal gap = zeroIfNull(line.getRequiredQty()).subtract(zeroIfNull(line.getIssuedQty()));
            if (gap.compareTo(BigDecimal.ZERO) < 0) {
                gap = BigDecimal.ZERO;
            }
            line.setShortQty(gap);
            line.setShortageReason(StringUtils.isNotBlank(s.getReason()) ? s.getReason() : "缺货");
            line.setLineStatus(LINE_SHORT_CLOSED);
            line.setCurrentQty(null);
            lineMapper.updateById(line);
        }
        if (!declared.isEmpty()) {
            throw new ServiceException("缺货声明包含不存在或已领满的行");
        }

        WmProductionIssue statusUpdate = new WmProductionIssue();
        statusUpdate.setIssueId(issueId);
        statusUpdate.setStatus(STATUS_COMPLETED);
        baseMapper.updateById(statusUpdate);
        recalcHeaderTotals(issueId);
        log.info("生产领料单缺货关闭完成, issueId={}", issueId);
    }

    /**
     * 查询生产发料
     *
     * @param materialId 物料 ID
     * @param warehouseId 仓库 ID
     * @return 结果对象
     */
    @Override
    public List<WmMaterialStockVo> selectAvailableStock(Long materialId, Long warehouseId) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        /** 仅查未冻结库存：frozen_flag 字典 sys_true_or_false，0=否(未冻结/可用)，1=是(已冻结) */
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        lqw.orderByAsc(WmMaterialStock::getExpireDate, WmMaterialStock::getRecptDate, WmMaterialStock::getStockId);
        return stockMapper.selectVoList(lqw);
    }

    /**
     * 批量查询多个物料的总可用库存（汇总所有仓库/批次/库位），
     * 供领料明细列表展示每行物料的"可用库存"参考列。
     */
    @Override
    public Map<Long, BigDecimal> sumAvailableStockByMaterials(List<Long> materialIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (materialIds == null || materialIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.in(WmMaterialStock::getMaterialId, materialIds);
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
        List<WmMaterialStock> list = stockMapper.selectList(lqw);
        for (WmMaterialStock s : list) {
            result.merge(s.getMaterialId(), zeroIfNull(s.getStockQuantity()), BigDecimal::add);
        }
        return result;
    }

    /**
     * 构建生产发料相关对象。
     *
     * @param woId 工单 ID
     * @return 结果对象
     */
    @Override
    public List<WmProductionIssueLine> buildLinesFromWorkOrder(Long woId) {
        PrdWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null || wo.getPlanId() == null) {
            return Collections.emptyList();
        }
        List<PrdMaterialRequirement> reqs = requirementMapper.selectList(
            Wrappers.lambdaQuery(PrdMaterialRequirement.class)
                .eq(PrdMaterialRequirement::getPlanId, wo.getPlanId()));
        if (reqs.isEmpty()) {
            return Collections.emptyList();
        }
        List<WmProductionIssueLine> lines = new ArrayList<>();
        for (PrdMaterialRequirement req : reqs) {
            List<PrdMaterialRequirementDetail> details = detailMapper.selectList(
                Wrappers.lambdaQuery(PrdMaterialRequirementDetail.class)
                    .eq(PrdMaterialRequirementDetail::getRequirementId, req.getRequirementId()));
            for (PrdMaterialRequirementDetail detail : details) {
                /** 倒冲物料不进领料单（完工时自动倒冲） */
                if (detail.getBackflushFlag() != null && detail.getBackflushFlag() == 1) {
                    continue;
                }
                BigDecimal required = zeroIfNull(detail.getRequiredQuantity());
                WmProductionIssueLine line = new WmProductionIssueLine();
                line.setMaterialId(detail.getMaterialId());
                line.setMaterialCode(detail.getMaterialCode());
                line.setMaterialName(detail.getMaterialName());
                line.setMaterialSpec(detail.getMaterialSpec());
                line.setBaseUomId(detail.getUnit());
                line.setBomQty(detail.getBomQuantity());
                line.setLossRate(detail.getLossRate());
                line.setRequiredQty(required);
                line.setIssuedQty(BigDecimal.ZERO);
                line.setCurrentQty(required);
                line.setShortQty(required);
                line.setRequirementDetailId(detail.getDetailId());
                line.setLineStatus(LINE_PENDING);
                lines.add(line);
            }
        }
        return lines;
    }

    /** 私有辅助方法。 */

    private void insertOrUpdateLines(Long issueId, List<WmProductionIssueLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmProductionIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
                /** 兜底初始化新列，避免旧数据/手工新增行出现 null */
                if (line.getIssuedQty() == null) {
                    line.setIssuedQty(BigDecimal.ZERO);
                }
                if (StringUtils.isBlank(line.getLineStatus())) {
                    line.setLineStatus(LINE_PENDING);
                }
                if (line.getRequiredQty() != null && line.getShortQty() == null) {
                    line.setShortQty(line.getRequiredQty().subtract(line.getIssuedQty()));
                }
                lineMapper.insert(line);
            }
        }
    }

    /**
     * 增量同步领料单行（保留 line_id，保证库存流水 source_doc_line_id 可追溯）：
     * <ul>
     *   <li>前端传来且有 lineId 的行 → 更新可编辑字段，<b>保留后端维护的</b>
     *       issued_qty/short_qty/line_status/required_qty/bom_qty/loss_rate/requirement_detail_id</li>
     *   <li>前端传来但无 lineId 的行 → 新增</li>
     *   <li>后端已有但前端未传的行 → 删除（仅当未领料 {@code issued_qty<=0} 时才删，避免误删已领料行）</li>
     * </ul>
     */
    private void syncLines(Long issueId, List<WmProductionIssueLine> lineList) {
        if (issueId == null) {
            return;
        }
        /** 后端现有行 */
        List<WmProductionIssueLine> existLines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        Map<Long, WmProductionIssueLine> existMap = new HashMap<>();
        for (WmProductionIssueLine l : existLines) {
            existMap.put(l.getLineId(), l);
        }

        Set<Long> keepIds = new HashSet<>();
        /** 核对所有来源行后再更新，不能把其他单据行 ID 当作新增并删掉本单原行。 */
        Set<Long> requestedIds = new HashSet<>();
        if (lineList != null) {
            for (WmProductionIssueLine line : lineList) {
                if (line == null) {
                    throw new ServiceException("领料行不能为空");
                }
                if (line.getLineId() != null) {
                    WmProductionIssueLine original = existMap.get(line.getLineId());
                    if (original == null || !requestedIds.add(line.getLineId())) {
                        throw new ServiceException("领料行不属于当前单据或重复提交");
                    }
                    if (!Objects.equals(line.getMaterialId(), original.getMaterialId())
                        || !Objects.equals(line.getBaseUomId(), original.getBaseUomId())
                        || (line.getRequirementDetailId() != null && !Objects.equals(line.getRequirementDetailId(), original.getRequirementDetailId()))) {
                        throw new ServiceException("领料行的物料、单位及需求来源不可改绑");
                    }
                } else if (zeroIfNull(line.getIssuedQty()).signum() != 0) {
                    throw new ServiceException("新增领料行不得填写已领数量");
                }
            }
        }
        for (WmProductionIssueLine existing : existLines) {
            if (zeroIfNull(existing.getIssuedQty()).signum() > 0 && !requestedIds.contains(existing.getLineId())) {
                throw new ServiceException("已领料行不可通过编辑删除");
            }
        }
        if (lineList != null) {
            for (WmProductionIssueLine line : lineList) {
                if (line.getLineId() != null && existMap.containsKey(line.getLineId())) {
                    /** 更新：以后端现值为准保留领料执行结果字段，以前端为准更新可编辑字段 */
                    WmProductionIssueLine db = existMap.get(line.getLineId());
                    line.setIssueId(issueId);
                    line.setIssuedQty(db.getIssuedQty());
                    line.setShortQty(db.getShortQty());
                    line.setLineStatus(db.getLineStatus());
                    line.setRequiredQty(db.getRequiredQty());
                    line.setBomQty(db.getBomQty());
                    line.setLossRate(db.getLossRate());
                    line.setRequirementDetailId(db.getRequirementDetailId());
                    lineMapper.updateById(line);
                    keepIds.add(line.getLineId());
                } else {
                    /** 新增行 */
                    line.setLineId(null);
                    line.setIssueId(issueId);
                    line.setIssuedQty(BigDecimal.ZERO);
                    line.setLineStatus(LINE_PENDING);
                    if (line.getRequiredQty() != null && line.getShortQty() == null) {
                        line.setShortQty(line.getRequiredQty().subtract(line.getIssuedQty()));
                    }
                    lineMapper.insert(line);
                }
            }
        }
        /** 删除前端未保留且未领料的旧行（已领料的行不允许删，防止流水孤立） */
        for (WmProductionIssueLine db : existLines) {
            if (!keepIds.contains(db.getLineId())
                && zeroIfNull(db.getIssuedQty()).compareTo(BigDecimal.ZERO) <= 0) {
                lineMapper.deleteById(db.getLineId());
            }
        }
    }

    private void deleteLinesByIssueId(Long issueId) {
        if (issueId != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmProductionIssueLine::getIssueId, issueId);
            lineMapper.delete(lqw);
        }
    }

    /**
     * 汇总单头总数量与总金额：按本次领料数量汇总，未执行或待执行时反映本次计划量。
     * 执行后 current_qty 已清零，此时按已领数量 issued_qty 反映累计已发量。
     * 金额同理，取 amount 与单价乘已领数量的较大者。
     *
     * @param issueId 待汇总的生产发料单头 ID
     */
    private void recalcHeaderTotals(Long issueId) {
        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (WmProductionIssueLine line : lines) {
            /** 优先取本次领料量；执行后为空则取已领量 */
            BigDecimal qty = line.getCurrentQty() != null ? line.getCurrentQty() : zeroIfNull(line.getIssuedQty());
            BigDecimal price = zeroIfNull(line.getUnitPrice());
            BigDecimal amt = line.getAmount() != null ? line.getAmount() : qty.multiply(price);
            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(amt);
        }
        WmProductionIssue update = new WmProductionIssue();
        update.setIssueId(issueId);
        update.setTotalQty(totalQty);
        update.setTotalAmount(totalAmount);
        baseMapper.updateById(update);
    }

    /** 行是否已领满：行状态为 ISSUED，或有应领定额且已领 >= 应领 */
    private boolean isLineFullyIssued(WmProductionIssueLine line) {
        if (LINE_ISSUED.equals(line.getLineStatus())) {
            return true;
        }
        BigDecimal required = zeroIfNull(line.getRequiredQty());
        /** 无应领定额的手工行：只要未处于 ISSUED，视为未完成（避免误判单头已完成） */
        if (required.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal issued = zeroIfNull(line.getIssuedQty());
        return required.compareTo(issued) <= 0;
    }

    /** 按物料+仓库(必选)+批次(可空)+库位(可空)汇总可用库存，与策略扣减范围一致 */
    private BigDecimal sumAvailable(Long materialId, Long warehouseId, Long batchId, Long locationId) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(batchId != null, WmMaterialStock::getBatchId, batchId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        /** 仅查未冻结库存：frozen_flag 0=未冻结/可用，1=已冻结 */
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        lqw.apply("(stock_quantity - IFNULL(reserved_quantity, 0)) > 0");
        lqw.apply("(expire_date IS NULL OR expire_date >= CURDATE())");
        List<WmMaterialStock> list = stockMapper.selectList(lqw);
        BigDecimal sum = BigDecimal.ZERO;
        for (WmMaterialStock s : list) {
            sum = sum.add(zeroIfNull(s.getStockQuantity()).subtract(zeroIfNull(s.getReservedQuantity())).max(BigDecimal.ZERO));
        }
        return sum;
    }

    /**
     * 回写工单物料需求明细的已发量/欠料量。
     * <p>ERP-WM-05: 改为原子累加 + 同语句重算欠料量，替换原先的读-改-写。
     * 并发执行两张领料单时，后写会覆盖先写，导致 {@code issued_quantity} 少计、{@code shortage_quantity} 虚高，
     * 物料需求侧看不到真实欠料。MySQL 的 SET 子句从左到右求值，故第二条 setSql 直接读已更新的 issued_quantity。</p>
     */
    private void updateRequirementDetail(Long detailId, BigDecimal issuedDelta) {
        if (detailId == null || issuedDelta == null || issuedDelta.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        detailMapper.update(null, Wrappers.<PrdMaterialRequirementDetail>lambdaUpdate()
            .setSql("issued_quantity = IFNULL(issued_quantity, 0) + " + issuedDelta.toPlainString())
            .setSql("shortage_quantity = GREATEST(IFNULL(required_quantity, 0) - IFNULL(issued_quantity, 0), 0)")
            .eq(PrdMaterialRequirementDetail::getDetailId, detailId));
    }

    private BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private LambdaQueryWrapper<WmProductionIssue> buildQueryWrapper(WmProductionIssueBo bo) {
        LambdaQueryWrapper<WmProductionIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmProductionIssue::getIssueCode, bo.getIssueCode());
        lqw.eq(bo.getWoId() != null, WmProductionIssue::getWoId, bo.getWoId());
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), WmProductionIssue::getWoNo, bo.getWoNo());
        lqw.eq(bo.getWarehouseId() != null, WmProductionIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmProductionIssue::getStatus, bo.getStatus());
        lqw.orderByDesc(WmProductionIssue::getCreateTime);
        return lqw;
    }
}
