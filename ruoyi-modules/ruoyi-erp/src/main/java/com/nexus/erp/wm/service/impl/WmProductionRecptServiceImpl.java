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
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.WmProductionRecptWriteGuard;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import java.util.Objects;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
/**
 * 生产收货服务实现
 * <p>
 * 实现生产收货服务接口，处理生产收货的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionRecptServiceImpl implements IWmProductionRecptService {

    /** 生产完工入库单编码规则编码（sys_auto_code_rule.rule_code） */
    private static final String PRODUCTION_RECPT_CODE_RULE = "WM_PRODUCTION_RECPT_CODE";
    /** 完工入库为成品自动生成批次的来源单据类型 */
    private static final String BATCH_SOURCE_DOC_TYPE = "PRD_WORK_ORDER";

    /** 基础数据访问映射器（Mapper） */
    private final WmProductionRecptMapper baseMapper;
    /** 库存操作业务服务，提供库存操作相关能力 */
    private final IWmStockOperationService stockOperationService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;
    /** 批次业务服务，提供批次相关能力 */
    private final IWmBatchService batchService;
    /** 所有同源写入口共同使用原工单父锁与完整原结果守卫。 */
    private final WmProductionRecptWriteGuard writeGuard;

    /**
     * 分页查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = buildQueryWrapper(bo);
        Page<WmProductionRecptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmProductionRecptVo> selectList(WmProductionRecptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询生产收货
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmProductionRecptVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmProductionRecptBo bo) {
        require(bo != null && bo.getRecptId() == null && (bo.getStatus() == null || "DRAFT".equals(bo.getStatus())),
            "普通新建生产入库只允许未编号的草稿");
        WmProductionRecpt entity = MapstructUtils.convert(bo, WmProductionRecpt.class);
        PrdWorkOrder wo = writeGuard.lockWorkOrder(entity.getWoId());
        require(writeGuard.receipts(wo).isEmpty(), "同工单已有生产入库单，不能另建重复草稿");
        writeGuard.requireNoEffects(wo);
        canonicalSource(entity, wo);
        entity.setStatus("DRAFT");
        writeGuard.validateDraft(wo, entity);
        if (StringUtils.isBlank(entity.getRecptCode())) {
            entity.setRecptCode(autoCodeUtil.genSerialCode(PRODUCTION_RECPT_CODE_RULE, null));
        }
        require(StringUtils.isNotBlank(entity.getRecptCode()), "生产入库编码规则未返回有效编号");
        require(baseMapper.insert(entity) == 1, "生产入库草稿写入失败");
        bo.setRecptId(entity.getRecptId());
        return 1;
    }

    /** 普通编辑只能修改原草稿的数量、仓储、日期和备注，不能换来源或伪造终态。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionRecptBo bo) {
        require(bo != null && (bo.getStatus() == null || "DRAFT".equals(bo.getStatus())), "普通编辑不能改变生产入库状态");
        var locked = writeGuard.lockReceipt(bo.getRecptId());
        WmProductionRecpt old = locked.receipt();
        writeGuard.requireDraft(old); writeGuard.requireNoEffects(locked.workOrder());
        require((bo.getWoId() == null || Objects.equals(bo.getWoId(), old.getWoId()))
            && (bo.getMaterialId() == null || Objects.equals(bo.getMaterialId(), old.getMaterialId()))
            && (bo.getWoNo() == null || Objects.equals(bo.getWoNo(), old.getWoNo()))
            && (bo.getMaterialCode() == null || Objects.equals(bo.getMaterialCode(), old.getMaterialCode()))
            && Objects.equals(bo.getRecptCode(), old.getRecptCode()), "原生产入库工单、物料或编号不能改换");
        WmProductionRecpt entity = new WmProductionRecpt();
        entity.setRecptId(old.getRecptId()); entity.setRecptCode(old.getRecptCode());
        entity.setWoId(old.getWoId()); entity.setWoNo(old.getWoNo());
        entity.setMaterialId(old.getMaterialId()); entity.setMaterialCode(old.getMaterialCode());
        entity.setQuantity(bo.getQuantity() == null ? old.getQuantity() : bo.getQuantity());
        entity.setWarehouseId(bo.getWarehouseId() == null ? old.getWarehouseId() : bo.getWarehouseId());
        entity.setLocationId(bo.getLocationId() == null ? old.getLocationId() : bo.getLocationId());
        entity.setRecptDate(bo.getRecptDate() == null ? old.getRecptDate() : bo.getRecptDate());
        entity.setBatchId(old.getBatchId()); entity.setBatchCode(old.getBatchCode());
        entity.setStatus("DRAFT"); entity.setRemark(bo.getRemark());
        writeGuard.validateDraft(locked.workOrder(), entity);
        int changed = baseMapper.update(entity, Wrappers.<WmProductionRecpt>lambdaUpdate()
            .eq(WmProductionRecpt::getRecptId, old.getRecptId()).eq(WmProductionRecpt::getTenantId, writeGuard.tenant())
            .eq(WmProductionRecpt::getStatus, "DRAFT").eq(WmProductionRecpt::getDelFlag, "0"));
        require(changed == 1, "生产入库草稿状态已变化，编辑未提交");
        return changed;
    }

    /** 全部原单在删除前通过守卫；不先删可删部分再发现已执行凭据。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        writeGuard.validateDelete(ids);
        int deleted = baseMapper.deleteBatchIds(List.of(ids));
        require(deleted == ids.length, "生产入库草稿删除数量不完整");
        return deleted;
    }

    /** 原工单锁下执行唯一草稿；完整原结果可恢复，不把策略静默跳过当成新单成功。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long recptId) {
        var locked = writeGuard.lockReceipt(recptId);
        WmProductionRecpt recpt = locked.receipt();
        if ("EXECUTED".equals(recpt.getStatus())) {
            writeGuard.verifyExecuted(locked.workOrder(), writeGuard.readyQuantity(locked.workOrder()), recpt);
            return;
        }
        writeGuard.validateReady(locked.workOrder(), recpt);
        executeLocked(locked.workOrder(), recpt);
    }

    private void executeLocked(PrdWorkOrder wo, WmProductionRecpt recpt) {
        // 使用最小实体参与条件更新，保留既有操作人和更新时间自动填充。
        WmProductionRecpt claim = new WmProductionRecpt(); claim.setStatus("EXECUTING");
        require(baseMapper.update(claim, Wrappers.<WmProductionRecpt>lambdaUpdate()
            .eq(WmProductionRecpt::getRecptId, recpt.getRecptId())
            .eq(WmProductionRecpt::getTenantId, writeGuard.tenant()).eq(WmProductionRecpt::getStatus, "DRAFT")
            .eq(WmProductionRecpt::getDelFlag, "0")) == 1, "生产入库单状态已变化，不能重复执行");
        Long batchId = recpt.getBatchId(); String batchCode = recpt.getBatchCode();
        if (batchId == null) {
            WmBatchVo batch = batchService.createBatchByMaterial(recpt.getMaterialId(), BATCH_SOURCE_DOC_TYPE,
                recpt.getWoId(), recpt.getWoNo(), recpt.getWarehouseId(), null);
            require(batch != null && batch.getBatchId() != null && StringUtils.isNotBlank(batch.getBatchCode()), "生产入库未生成有效来源批次");
            batchId = batch.getBatchId(); batchCode = batch.getBatchCode();
        }
        Date now = new Date();
        stockOperationService.postProductionCompletion(recpt.getWoId(), recpt.getWoNo(), recpt.getMaterialId(),
            recpt.getMaterialCode(), recpt.getMaterialName(), recpt.getQuantity(), recpt.getWarehouseId(),
            recpt.getLocationId(), batchId, batchCode, now);
        WmProductionRecpt completed = new WmProductionRecpt(); completed.setStatus("EXECUTED");
        completed.setBatchId(batchId); completed.setBatchCode(batchCode);
        require(baseMapper.update(completed, Wrappers.<WmProductionRecpt>lambdaUpdate()
            .eq(WmProductionRecpt::getRecptId, recpt.getRecptId())
            .eq(WmProductionRecpt::getTenantId, writeGuard.tenant()).eq(WmProductionRecpt::getStatus, "EXECUTING")
            .eq(WmProductionRecpt::getDelFlag, "0")) == 1, "生产入库终态回写失败，必须回滚");
        recpt.setBatchId(batchId); recpt.setBatchCode(batchCode); recpt.setStatus("EXECUTED");
        writeGuard.verifyExecuted(wo, recpt.getQuantity(), recpt);
        log.info("生产入库原凭据已核验, recptId={}", recpt.getRecptId());
    }

    /** 原自动完成入口沿用一次终态数量；重放只返回完整原结果，不支持累计补差额。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndExecute(Long woId, String woNo, Long materialId, String materialCode, String materialName,
                                 String materialSpec, BigDecimal quantity, Long warehouseId, String warehouseCode,
                                 String warehouseName, Long locationId, String locationCode, String locationName, Date recptDate) {
        PrdWorkOrder wo = writeGuard.lockWorkOrder(woId);
        require(Objects.equals(wo.getWoNo(), woNo) && Objects.equals(wo.getMaterialId(), materialId)
            && Objects.equals(wo.getMaterialCode(), materialCode) && quantity != null
            && quantity.compareTo(writeGuard.readyQuantity(wo)) == 0, "自动生产入库参数不属于原工单本次完工事实");
        List<WmProductionRecpt> old = writeGuard.receipts(wo);
        if (!old.isEmpty()) {
            WmProductionRecpt result = writeGuard.verifyExecuted(wo, quantity, null);
            require(Objects.equals(result.getWarehouseId(), warehouseId) && Objects.equals(result.getLocationId(), locationId),
                "自动生产入库重放仓储与原结果不一致");
            return result.getRecptId();
        }
        writeGuard.requireNoEffects(wo);
        WmProductionRecpt entity = new WmProductionRecpt();
        entity.setWoId(woId); entity.setWoNo(woNo); entity.setMaterialId(materialId); entity.setMaterialCode(materialCode);
        entity.setQuantity(quantity); entity.setWarehouseId(warehouseId); entity.setLocationId(locationId);
        entity.setRecptDate(recptDate); entity.setStatus("DRAFT"); entity.setRemark("工单完工自动入库");
        writeGuard.validateReady(wo, entity);
        entity.setRecptCode(autoCodeUtil.genSerialCode(PRODUCTION_RECPT_CODE_RULE, null));
        require(StringUtils.isNotBlank(entity.getRecptCode()), "生产入库编码规则未返回有效编号");
        require(baseMapper.insert(entity) == 1 && entity.getRecptId() != null, "自动生产入库草稿写入失败");
        executeLocked(wo, entity);
        return entity.getRecptId();
    }

    private void canonicalSource(WmProductionRecpt entity, PrdWorkOrder wo) {
        require((entity.getMaterialId() == null || Objects.equals(entity.getMaterialId(), wo.getMaterialId()))
            && (entity.getWoNo() == null || Objects.equals(entity.getWoNo(), wo.getWoNo()))
            && (entity.getMaterialCode() == null || Objects.equals(entity.getMaterialCode(), wo.getMaterialCode())),
            "生产入库工单与请求物料来源冲突");
        entity.setWoId(wo.getWoId()); entity.setWoNo(wo.getWoNo());
        entity.setMaterialId(wo.getMaterialId()); entity.setMaterialCode(wo.getMaterialCode());
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new ServiceException(message);
    }

    private LambdaQueryWrapper<WmProductionRecpt> buildQueryWrapper(WmProductionRecptBo bo) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRecptCode()), WmProductionRecpt::getRecptCode, bo.getRecptCode());
        lqw.eq(bo.getWoId() != null, WmProductionRecpt::getWoId, bo.getWoId());
        lqw.like(StringUtils.isNotBlank(bo.getWoNo()), WmProductionRecpt::getWoNo, bo.getWoNo());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmProductionRecpt::getStatus, bo.getStatus());
        lqw.orderByDesc(WmProductionRecpt::getCreateTime);
        return lqw;
    }
}
