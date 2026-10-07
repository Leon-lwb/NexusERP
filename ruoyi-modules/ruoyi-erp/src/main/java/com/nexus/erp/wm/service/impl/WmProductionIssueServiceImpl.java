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
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
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
import com.nexus.erp.wm.service.helper.WmProductionIssueReferenceAmounts;
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
import java.util.stream.Collectors;
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
    /** 原生产计划数据访问映射器 */
    private final PrdProductionPlanMapper planMapper;
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
        applyReferenceTotals(page.getRecords());
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
        List<WmProductionIssueVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        applyReferenceTotals(list);
        return list;
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
            List<WmProductionIssueLine> lines = lineMapper.selectList(lineLqw);
            vo.setWmProductionIssueLineList(lines);
            applyReferenceAmount(vo, lines);
        } else if (vo != null) {
            vo.setTotalAmount(null);
            vo.setReferenceAmountKnown(false);
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
        /** 新增来源在单头和明细持久化前核验，不能由客户端任意认领需求ID。 */
        validateNewRequirementSources(entity, bo.getWmProductionIssueLineList());
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
        syncLines(current, bo.getWmProductionIssueLineList());
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
            line.setCurrentQty(BigDecimal.ZERO);
            if (requiredQty.compareTo(BigDecimal.ZERO) > 0 && newShort.compareTo(BigDecimal.ZERO) <= 0) {
                line.setLineStatus(LINE_ISSUED);
            } else {
                line.setLineStatus(LINE_PARTIAL);
                allIssued = false;
            }
            if (lineMapper.updateById(line) != 1) {
                throw new ServiceException("领料明细回写失败，已扣库存必须回滚，请刷新后重试");
            }

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
        if (baseMapper.updateById(statusUpdate) != 1) {
            throw new ServiceException("领料单头状态回写失败，已扣库存必须回滚，请刷新后重试");
        }
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
            line.setCurrentQty(BigDecimal.ZERO);
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
        LambdaQueryWrapper<WmMaterialStock> lqw = eligibleStockQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(warehouseId != null, WmMaterialStock::getWarehouseId, warehouseId);
        /** 数量缺失无法证明可用，候选仅包含已知正 ATP；空效期放在 FEFO 最后。 */
        lqw.apply("(stock_quantity - reserved_quantity) > 0");
        lqw.last("ORDER BY expire_date IS NULL ASC, expire_date ASC, recpt_date ASC, stock_id ASC");
        List<WmMaterialStockVo> list = stockMapper.selectVoList(lqw);
        for (WmMaterialStockVo stock : list) {
            stock.setAvailableQuantity(atpQuantity(stock.getStockQuantity(), stock.getReservedQuantity()));
        }
        return list;
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
        for (Long materialId : materialIds) {
            result.put(materialId, BigDecimal.ZERO);
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = eligibleStockQuery();
        lqw.in(WmMaterialStock::getMaterialId, materialIds);
        List<WmMaterialStock> list = stockMapper.selectList(lqw);
        for (WmMaterialStock stock : list) {
            Long materialId = stock.getMaterialId();
            BigDecimal available = atpQuantity(stock.getStockQuantity(), stock.getReservedQuantity());
            /** 任一有效库存数量未知即令本物料总量未知，不返回残缺的部分汇总。 */
            if (available == null) {
                result.put(materialId, null);
            } else if (result.get(materialId) != null) {
                result.put(materialId, result.get(materialId).add(available));
            }
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
        if (woId == null) {
            return Collections.emptyList();
        }
        String tenantId = requireSourceTenant(null);
        PrdWorkOrder wo = selectActiveSourceWorkOrder(woId, tenantId);
        /** 无MRP来源的工单保留独立手工路径，计划ID或同数值来源ID不能代替需求归属。 */
        if (wo == null || !"MATERIAL_REQUIREMENT".equals(wo.getSourceType())) {
            return Collections.emptyList();
        }
        PrdMaterialRequirement requirement = resolveSourceRequirement(wo, tenantId);
        List<PrdMaterialRequirementDetail> details = detailMapper.selectList(
            Wrappers.<PrdMaterialRequirementDetail>lambdaQuery()
                .eq(PrdMaterialRequirementDetail::getRequirementId, requirement.getRequirementId())
                .eq(PrdMaterialRequirementDetail::getTenantId, tenantId)
                .eq(PrdMaterialRequirementDetail::getDelFlag, "0"));
        List<WmProductionIssueLine> lines = new ArrayList<>();
        for (PrdMaterialRequirementDetail detail : details) {
            /** 倒冲物料不进普通领料单，仍由完工倒冲处理。 */
            if (Integer.valueOf(1).equals(detail.getBackflushFlag())) {
                continue;
            }
            validateSourceDetail(requirement, detail, tenantId);
            BigDecimal required = sourceUnissuedQuantity(detail);
            WmProductionIssueLine line = new WmProductionIssueLine();
            line.setMaterialId(detail.getMaterialId());
            line.setMaterialCode(detail.getMaterialCode());
            line.setMaterialName(detail.getMaterialName());
            line.setMaterialSpec(detail.getMaterialSpec());
            line.setBaseUomId(detail.getUnit());
            line.setQuantity(required);
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
                line.setLineStatus(LINE_PENDING);
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
     *   <li>未提供明细列表（{@code null}）→ 仅编辑头表，保留全部原行，汇总由调用方重算</li>
     *   <li>显式空列表 → 删除全部未领行，存在已领行则拒绝；非空列表仍表示客户端明确编辑的行全集</li>
     *   <li>前端传来且有 lineId 的行 → 更新可编辑字段，<b>保留后端维护的</b>
     *       issued_qty/short_qty/line_status/required_qty/bom_qty/loss_rate/requirement_detail_id</li>
     *   <li>前端传来但无 lineId 的行 → 新增</li>
     *   <li>后端已有但前端未传的行 → 删除（仅当未领料 {@code issued_qty<=0} 时才删，避免误删已领料行）</li>
     * </ul>
     */
    private void syncLines(WmProductionIssue issue, List<WmProductionIssueLine> lineList) {
        Long issueId = issue.getIssueId();
        if (issueId == null || lineList == null) {
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
        /** 父单条件更新已取得既有编辑锁，全部新增来源通过后才允许明细增改删。 */
        validateNewRequirementSources(issue, lineList);
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
                    line.setTenantId(db.getTenantId());
                    line.setIssuedQty(db.getIssuedQty());
                    line.setShortQty(db.getShortQty());
                    line.setLineStatus(db.getLineStatus());
                    line.setRequiredQty(db.getRequiredQty());
                    line.setBomQty(db.getBomQty());
                    line.setLossRate(db.getLossRate());
                    line.setRequirementDetailId(db.getRequirementDetailId());
                    boolean clearPrice = Boolean.TRUE.equals(line.getClearReferencePrice());
                    if (clearPrice) {
                        line.setUnitPrice(null);
                        line.setAmount(null);
                    } else {
                        /** 省略价格只表示未编辑该字段，不得因保存批次把既有参考价清空。 */
                        if (line.getUnitPrice() == null) line.setUnitPrice(db.getUnitPrice());
                        if (line.getAmount() == null) line.setAmount(db.getAmount());
                    }
                    lineMapper.updateById(line);
                    if (clearPrice) {
                        /** 只在已通过原单状态和归属守卫的合法编辑事务内显式清价。 */
                        int cleared = lineMapper.update(null, Wrappers.<WmProductionIssueLine>lambdaUpdate()
                            .eq(WmProductionIssueLine::getIssueId, issueId)
                            .eq(WmProductionIssueLine::getLineId, db.getLineId())
                            .set(WmProductionIssueLine::getUnitPrice, null)
                            .set(WmProductionIssueLine::getAmount, null));
                        if (cleared != 1) {
                            throw new ServiceException("参考价格清空失败，请刷新领料单后重试");
                        }
                    }
                    keepIds.add(line.getLineId());
                } else {
                    /** 新增行 */
                    line.setLineId(null);
                    line.setIssueId(issueId);
                    line.setIssuedQty(BigDecimal.ZERO);
                    if (Boolean.TRUE.equals(line.getClearReferencePrice())) {
                        line.setUnitPrice(null);
                        line.setAmount(null);
                    }
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

    /** 仅认证新行的MRP来源；已有行身份仍由增量同步守卫维护，无来源手工行不走此路径。 */
    private void validateNewRequirementSources(WmProductionIssue issue, List<WmProductionIssueLine> lineList) {
        if (lineList == null || lineList.isEmpty()) {
            return;
        }
        List<WmProductionIssueLine> sourcedLines = lineList.stream()
            .filter(line -> (issue.getIssueId() == null || line.getLineId() == null)
                && line.getRequirementDetailId() != null).toList();
        if (sourcedLines.isEmpty()) {
            return;
        }
        String tenantId = requireSourceTenant(issue);
        for (WmProductionIssueLine line : sourcedLines) {
            if (StringUtils.isNotBlank(line.getTenantId()) && !tenantId.equals(line.getTenantId())) {
                throw new ServiceException("新增领料行租户与当前租户不一致");
            }
        }
        PrdWorkOrder wo = selectActiveSourceWorkOrder(issue.getWoId(), tenantId);
        PrdMaterialRequirement requirement = resolveSourceRequirement(wo, tenantId);
        for (WmProductionIssueLine line : sourcedLines) {
            PrdMaterialRequirementDetail detail = detailMapper.selectOne(
                Wrappers.<PrdMaterialRequirementDetail>lambdaQuery()
                    .eq(PrdMaterialRequirementDetail::getDetailId, line.getRequirementDetailId())
                    .eq(PrdMaterialRequirementDetail::getRequirementId, requirement.getRequirementId())
                    .eq(PrdMaterialRequirementDetail::getTenantId, tenantId)
                    .eq(PrdMaterialRequirementDetail::getDelFlag, "0"));
            validateSourceDetail(requirement, detail, tenantId);
            if (!Objects.equals(line.getRequirementDetailId(), detail.getDetailId())
                || !Objects.equals(line.getMaterialId(), detail.getMaterialId())
                || !Objects.equals(line.getBaseUomId(), detail.getUnit())) {
                throw new ServiceException("新增领料行的需求来源、物料或基本单位不一致");
            }
            BigDecimal remaining = sourceUnissuedQuantity(detail);
            BigDecimal currentQty = zeroIfNull(line.getCurrentQty());
            if (currentQty.signum() < 0 || currentQty.compareTo(remaining) > 0) {
                throw new ServiceException("新增MRP领料行的本次量不得为负数或超过来源未领余额");
            }
            /** 新行定额及欠领量由可信需求余额维护，客户端不得扩大执行上限。 */
            line.setQuantity(remaining);
            line.setRequiredQty(remaining);
            line.setShortQty(remaining);
            line.setBomQty(detail.getBomQuantity());
            line.setLossRate(detail.getLossRate());
            /** 租户由真实上下文维护，不能透传客户端提供的租户字段。 */
            line.setTenantId(tenantId);
        }
    }

    /** 来源读取不忽略租户，也不使用客户端行中的租户作为查询范围。 */
    private String requireSourceTenant(WmProductionIssue issue) {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId) || (issue != null && issue.getIssueId() != null
            && !Objects.equals(tenantId, issue.getTenantId()))) {
            throw new ServiceException("无法核验当前租户与领料单来源，请刷新后重试");
        }
        return tenantId;
    }

    /** 真实租户与有效行条件均显式限定，保留既有多租户拦截器。 */
    private PrdWorkOrder selectActiveSourceWorkOrder(Long woId, String tenantId) {
        if (woId == null) {
            throw new ServiceException("携带MRP需求来源的领料行必须关联有效工单");
        }
        return workOrderMapper.selectOne(Wrappers.<PrdWorkOrder>lambdaQuery()
            .eq(PrdWorkOrder::getWoId, woId)
            .eq(PrdWorkOrder::getTenantId, tenantId)
            .eq(PrdWorkOrder::getDelFlag, "0"));
    }

    /** 工单直接来源需求及原计划必须一致，不能以同计划其他MRP替代。 */
    private PrdMaterialRequirement resolveSourceRequirement(PrdWorkOrder wo, String tenantId) {
        if (wo == null || !tenantId.equals(wo.getTenantId()) || !"0".equals(wo.getDelFlag())
            || !"MATERIAL_REQUIREMENT".equals(wo.getSourceType()) || wo.getSourceId() == null
            || wo.getPlanId() == null || wo.getMaterialId() == null) {
            throw new ServiceException("领料工单的MRP来源不完整或无效，请核查原工单");
        }
        if (Integer.valueOf(1).equals(wo.getBackflushFlag())) {
            throw new ServiceException("倒冲工单不得认领普通MRP领料来源");
        }
        PrdProductionPlan plan = planMapper.selectOne(Wrappers.<PrdProductionPlan>lambdaQuery()
            .eq(PrdProductionPlan::getPlanId, wo.getPlanId())
            .eq(PrdProductionPlan::getTenantId, tenantId)
            .eq(PrdProductionPlan::getDelFlag, "0"));
        if (plan == null || !tenantId.equals(plan.getTenantId()) || !"0".equals(plan.getDelFlag())
            || !Objects.equals(wo.getPlanId(), plan.getPlanId())) {
            throw new ServiceException("工单原生产计划不存在、已删除或租户不一致");
        }
        PrdMaterialRequirement requirement = requirementMapper.selectOne(
            Wrappers.<PrdMaterialRequirement>lambdaQuery()
                .eq(PrdMaterialRequirement::getRequirementId, wo.getSourceId())
                .eq(PrdMaterialRequirement::getTenantId, tenantId)
                .eq(PrdMaterialRequirement::getDelFlag, "0"));
        if (requirement == null || !tenantId.equals(requirement.getTenantId()) || !"0".equals(requirement.getDelFlag())
            || !Objects.equals(wo.getSourceId(), requirement.getRequirementId())
            || !Objects.equals(wo.getPlanId(), requirement.getPlanId())
            || !Objects.equals(wo.getMaterialId(), requirement.getMaterialId())) {
            throw new ServiceException("工单来源需求不存在或与原计划、成品、租户不一致");
        }
        return requirement;
    }

    /** 有效需求头的直接明细、物料与基本单位必须完整，不为历史缺失单位伪造默认值。 */
    private void validateSourceDetail(PrdMaterialRequirement requirement, PrdMaterialRequirementDetail detail, String tenantId) {
        if (detail == null || detail.getDetailId() == null || !tenantId.equals(detail.getTenantId())
            || !"0".equals(detail.getDelFlag()) || !Objects.equals(requirement.getRequirementId(), detail.getRequirementId())
            || detail.getMaterialId() == null || StringUtils.isBlank(detail.getUnit())) {
            throw new ServiceException("工单需求明细不存在、归属无效或物料基本单位缺失");
        }
        if (Integer.valueOf(1).equals(detail.getBackflushFlag())) {
            throw new ServiceException("倒冲需求明细不得用于普通生产领料");
        }
    }

    /** 来源应领与已发数量须有效；空已发沿用原零值语义，已领满的余额为零。 */
    private BigDecimal sourceUnissuedQuantity(PrdMaterialRequirementDetail detail) {
        BigDecimal required = detail.getRequiredQuantity();
        BigDecimal issued = zeroIfNull(detail.getIssuedQuantity());
        if (required == null || required.signum() < 0 || issued.signum() < 0) {
            throw new ServiceException("MRP来源需求数量缺失或已发数量异常，请核查原需求");
        }
        return required.subtract(issued).max(BigDecimal.ZERO);
    }

    private void deleteLinesByIssueId(Long issueId) {
        if (issueId != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmProductionIssueLine::getIssueId, issueId);
            lineMapper.delete(lqw);
        }
    }

    /**
     * 汇总单头总数量与总金额：优先按正数本次领料数量汇总，反映本次计划量。
     * 执行后 current_qty 已清零，此时按已领数量 issued_qty 反映累计已发量。
     * 参考金额优先取已知明细金额，否则按已知参考价计算；任一行未知则总额为空。
     * 不使用真实库存成本追认参考价格，也不跳过未计价明细。
     *
     * @param issueId 待汇总的生产发料单头 ID
     */
    private void recalcHeaderTotals(Long issueId) {
        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = WmProductionIssueReferenceAmounts.totalAmount(lines);
        for (WmProductionIssueLine line : lines) {
            /** 优先取正数本次领料量；本次量清零或为空时取累计已领量 */
            BigDecimal qty = line.getCurrentQty() != null && line.getCurrentQty().signum() > 0
                ? line.getCurrentQty() : zeroIfNull(line.getIssuedQty());
            totalQty = totalQty.add(qty);
        }
        WmProductionIssue update = new WmProductionIssue();
        update.setIssueId(issueId);
        update.setTotalQty(totalQty);
        update.setTotalAmount(totalAmount);
        if (baseMapper.updateById(update) != 1) {
            throw new ServiceException("领料汇总回写失败，请刷新后重试");
        }
        if (totalAmount == null) {
            /** 默认非空更新策略会忽略空值，需在既有事务内显式清除旧参考总额。 */
            int affected = baseMapper.update(null, Wrappers.<WmProductionIssue>lambdaUpdate()
                .eq(WmProductionIssue::getIssueId, issueId)
                .set(WmProductionIssue::getTotalAmount, null));
            if (affected != 1) {
                throw new ServiceException("领料未计价汇总回写失败，请刷新后重试");
            }
        }
    }

    /** 列表批量读取有效明细重算参考金额，不把旧单头零视为已计价，也不写历史表。 */
    private void applyReferenceTotals(List<WmProductionIssueVo> headers) {
        if (headers.isEmpty()) {
            return;
        }
        List<Long> issueIds = headers.stream().map(WmProductionIssueVo::getIssueId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, List<WmProductionIssueLine>> linesByIssue = new HashMap<>();
        if (!issueIds.isEmpty()) {
            List<WmProductionIssueLine> lines = lineMapper.selectList(Wrappers.<WmProductionIssueLine>lambdaQuery()
                .in(WmProductionIssueLine::getIssueId, issueIds));
            linesByIssue = lines.stream().collect(Collectors.groupingBy(WmProductionIssueLine::getIssueId));
        }
        for (WmProductionIssueVo header : headers) {
            applyReferenceAmount(header, linesByIssue.getOrDefault(header.getIssueId(), Collections.emptyList()));
        }
    }

    /** 明确贯通已知状态；无有效明细或任一数量价格未知时不能证明已知零。 */
    private void applyReferenceAmount(WmProductionIssueVo header, List<WmProductionIssueLine> lines) {
        BigDecimal amount = WmProductionIssueReferenceAmounts.totalAmount(lines);
        header.setTotalAmount(amount);
        header.setReferenceAmountKnown(amount != null);
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
        LambdaQueryWrapper<WmMaterialStock> lqw = eligibleStockQuery();
        lqw.eq(WmMaterialStock::getMaterialId, materialId);
        lqw.eq(WmMaterialStock::getWarehouseId, warehouseId);
        lqw.eq(batchId != null, WmMaterialStock::getBatchId, batchId);
        lqw.eq(locationId != null, WmMaterialStock::getLocationId, locationId);
        List<WmMaterialStock> list = stockMapper.selectList(lqw);
        BigDecimal sum = BigDecimal.ZERO;
        for (WmMaterialStock stock : list) {
            BigDecimal available = atpQuantity(stock.getStockQuantity(), stock.getReservedQuantity());
            if (available == null) {
                throw new ServiceException("库存账面数量或预留数量未知，无法确认可用库存");
            }
            sum = sum.add(available);
        }
        return sum;
    }

    /** 所有领料可用库存读取统一使用数据库日期，显式限定有效未删库存。 */
    private LambdaQueryWrapper<WmMaterialStock> eligibleStockQuery() {
        return Wrappers.<WmMaterialStock>lambdaQuery()
            .eq(WmMaterialStock::getFrozenFlag, 0L)
            .eq(WmMaterialStock::getDelFlag, "0")
            .apply("(expire_date IS NULL OR expire_date >= CURDATE())");
    }

    /** ATP 逐库存截零；未知账面量或未知预留量均不能通过零兜底证明可用。 */
    private BigDecimal atpQuantity(BigDecimal stockQuantity, BigDecimal reservedQuantity) {
        return stockQuantity == null || reservedQuantity == null
            ? null : stockQuantity.subtract(reservedQuantity).max(BigDecimal.ZERO);
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
