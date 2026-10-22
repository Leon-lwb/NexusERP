package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.domain.bo.WmTransferBo;
import com.nexus.erp.wm.domain.vo.WmTransferVo;
import com.nexus.erp.wm.mapper.WmStorageAreaMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmTransferService;
import com.nexus.workflow.domain.WfMyBusiness;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 调拨服务实现
 * <p>
 * 实现调拨服务接口，处理调拨的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("wmTransferService")
public class WmTransferServiceImpl implements IWmTransferService {

    /** WM 公共依赖（AutoCodeUtil / 流程服务 / 流程实例查询） */
    private final WmServiceHelper wm;
    /** 基础数据访问映射器（Mapper） */
    private final WmTransferMapper baseMapper;
    /** 行数据访问映射器（Mapper） */
    private final WmTransferLineMapper lineMapper;

    private final WmStorageLocationMapper locationMapper;
    private final WmWarehouseMapper warehouseMapper;
    private final WmStorageAreaMapper areaMapper;

    /** 库存操作业务服务，提供库存操作相关能力 */
    private final WmStockOperationServiceImpl stockOperationService;

    /**
     * 分页查询调拨列表
     *
     * @param bo 调拨业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmTransferVo> selectPageList(WmTransferBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmTransfer> lqw = buildQueryWrapper(bo);
        Page<WmTransferVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询调拨列表
     *
     * @param bo 调拨业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmTransferVo> selectList(WmTransferBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询调拨
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmTransferVo selectById(Long id) {
        WmTransferVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getTransferId() != null) {
            LambdaQueryWrapper<WmTransferLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmTransferLine::getTransferId, vo.getTransferId());
            vo.setWmTransferLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增调拨
     *
     * @param bo 调拨业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmTransferBo bo) {
        WmTransfer entity = MapstructUtils.convert(bo, WmTransfer.class);
        normalizeLocations(entity, bo.getWmTransferLineList());
        if (StringUtils.isBlank(entity.getTransferNo())) {
            entity.setTransferNo(wm.autoCodeUtil.genSerialCode("WM_TRANSFER_CODE", null));
        }
        entity.setStatus(STATUS_DRAFT);
        int rows = baseMapper.insert(entity);
        if (bo.getWmTransferLineList() != null) {
            for (WmTransferLine line : bo.getWmTransferLineList()) {
                line.setTransferId(entity.getTransferId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改调拨
     *
     * @param bo 调拨业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmTransferBo bo) {
        WmTransfer entity = MapstructUtils.convert(bo, WmTransfer.class);
        // ERP-WM-08: 仅草稿可改（驳回在 afterFlowHandle 中即回到 DRAFT）。
        // 修复前无守卫：已执行调拨单明细可被删改，与 wm_material_stock_tx 的 source_doc_id 对不上，数量无法追溯。
        WmTransfer existing = baseMapper.selectById(entity.getTransferId());
        if (existing == null) {
            throw new ServiceException("调拨单不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus())) {
            throw new ServiceException("当前状态不允许修改，只有草稿状态才能修改！");
        }
        entity.setStatus(existing.getStatus());
        normalizeLocations(entity, bo.getWmTransferLineList());
        deleteLinesByTransferId(entity.getTransferId());
        if (bo.getWmTransferLineList() != null) {
            for (WmTransferLine line : bo.getWmTransferLineList()) {
                line.setTransferId(entity.getTransferId());
                lineMapper.insert(line);
            }
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 删除调拨
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            // ERP-WM-08: 仅草稿可删；删除已执行单据会让库存流水成为孤儿、库存调整无凭据
            WmTransfer existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("当前状态不允许删除，只有草稿状态才能删除！");
            }
            deleteLinesByTransferId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行调拨相关操作
     *
     * @param transferId 调拨单 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long transferId) {
        WmTransfer transfer = baseMapper.selectById(transferId);
        if (transfer == null) {
            throw new RuntimeException("调拨单不存在");
        }
        if (STATUS_EXECUTED.equals(transfer.getStatus())) {
            throw new RuntimeException("调拨单已执行");
        }
        // ERP-WM-07: 只允许「已审批(APPROVED)」或「PDA 已下架(TAKEN_OFF)」状态过账。
        // 修复前抢占条件为 ne(EXECUTED)，草稿/驳回单可直接过账 —— 既绕过审批流程，
        // 也绕过 PDA 的"下架→扫目标库位"作业步骤（与 PdaTransferServiceImpl.execute 的白名单对齐）。
        int claimed = baseMapper.update(null, Wrappers.<WmTransfer>lambdaUpdate()
            .set(WmTransfer::getStatus, "EXECUTING")
            .eq(WmTransfer::getTransferId, transferId)
            .in(WmTransfer::getStatus, STATUS_APPROVED, STATUS_TAKEN_OFF));
        if (claimed == 0) {
            throw new RuntimeException("调拨单状态不允许过账（需已审批或已下架），或已被并发执行");
        }

        // ERP-WM-14: 目标仓库必须非空，否则目标库存行会落 warehouse_id=NULL（库存维度丢失，
        // 按仓库/库位查询与 3D 库位视图都看不到这批库存）
        if (transfer.getToWarehouseId() == null) {
            throw new ServiceException("调拨单必须指定目标仓库");
        }

        LambdaQueryWrapper<WmTransferLine> lineLqw = Wrappers.lambdaQuery();
        lineLqw.eq(WmTransferLine::getTransferId, transferId);
        List<WmTransferLine> lines = lineMapper.selectList(lineLqw);

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("调拨单无明细行");
        }

        // ERP-WM-14: 每行目标库位必须非空，且不得与来源同维度（否则是无意义的原地"调拨"）
        for (WmTransferLine line : lines) {
            if (line.getToLocationId() == null) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】未指定目标库位，禁止过账");
            }
            if (transfer.getFromWarehouseId() != null && transfer.getFromWarehouseId().equals(transfer.getToWarehouseId())
                && line.getFromLocationId() != null && line.getFromLocationId().equals(line.getToLocationId())) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】来源与目标仓库/库位完全相同，禁止过账");
            }
        }

        normalizeLocations(transfer, lines);
        for (WmTransferLine line : lines) {
            // 从调拨单头填充仓库维度（行表无仓库字段，跨仓调拨时用于正确扣减/建立目标库存维度）
            line.setFromWarehouseId(transfer.getFromWarehouseId());
            line.setFromWarehouseCode(transfer.getFromWarehouseCode());
            line.setFromWarehouseName(transfer.getFromWarehouseName());
            line.setToWarehouseId(transfer.getToWarehouseId());
            line.setToWarehouseCode(transfer.getToWarehouseCode());
            line.setToWarehouseName(transfer.getToWarehouseName());
            stockOperationService.postTransfer(line);
        }

        transfer.setStatus(STATUS_EXECUTED);
        transfer.setExecuteDate(new Date());
        baseMapper.updateById(transfer);
        log.info("调拨单执行完成, transferId={}", transferId);
    }

    /** 编码输入在服务端解析；库位、库区和仓库的归属均来自本租户主数据。 */
    private void normalizeLocations(WmTransfer head, List<WmTransferLine> lines) {
        if (lines == null || lines.isEmpty()) throw new ServiceException("调拨单至少需要一条明细");
        for (WmTransferLine line : lines) {
            if (line.getMaterialId() == null || line.getQuantity() == null || line.getQuantity().signum() <= 0
                    || line.getQuantity().stripTrailingZeros().scale() > 4) {
                throw new ServiceException("调拨物料不能为空，数量必须大于0且最多4位小数");
            }
        }
        var fromWarehouse = warehouseMapper.selectById(head.getFromWarehouseId());
        var toWarehouse = warehouseMapper.selectById(head.getToWarehouseId());
        if (fromWarehouse == null || toWarehouse == null
                || !Long.valueOf(1L).equals(fromWarehouse.getStatus()) || !Long.valueOf(1L).equals(toWarehouse.getStatus())) {
            throw new ServiceException("来源和目标仓库必须真实存在且已启用");
        }
        head.setFromWarehouseCode(fromWarehouse.getWarehouseCode()); head.setFromWarehouseName(fromWarehouse.getWarehouseName());
        head.setToWarehouseCode(toWarehouse.getWarehouseCode()); head.setToWarehouseName(toWarehouse.getWarehouseName());
        BigDecimal total = BigDecimal.ZERO;
        for (WmTransferLine line : lines) {
            var from = resolveLocation(line.getFromLocationId(), line.getFromLocationCode(), head.getFromWarehouseId(), "来源");
            var to = resolveLocation(line.getToLocationId(), line.getToLocationCode(), head.getToWarehouseId(), "目标");
            if (from.getLocationId().equals(to.getLocationId()))
                throw new ServiceException("来源与目标库位不能相同");
            if (head.getFromAreaId() != null && !head.getFromAreaId().equals(from.getAreaId())
                    || head.getToAreaId() != null && !head.getToAreaId().equals(to.getAreaId())) {
                throw new ServiceException("调拨库位不属于指定库区");
            }
            var area = to.getAreaId() == null ? null : areaMapper.selectById(to.getAreaId());
            if (area == null) throw new ServiceException("目标库区不存在");
            line.setFromLocationId(from.getLocationId()); line.setFromLocationCode(from.getLocationCode()); line.setFromLocationName(from.getLocationName());
            line.setToLocationId(to.getLocationId()); line.setToLocationCode(to.getLocationCode()); line.setToLocationName(to.getLocationName());
            line.setToAreaId(area.getAreaId()); line.setToAreaCode(area.getAreaCode()); line.setToAreaName(area.getAreaName());
            line.setTenantId(null);
            total = total.add(line.getQuantity());
        }
        head.setTotalQuantity(total);
    }

    private WmStorageLocation resolveLocation(
            Long id, String code, Long warehouseId, String label) {
        var location =
                id != null
                        ? locationMapper.selectById(id)
                        : StringUtils.isBlank(code)
                                ? null
                                : locationMapper.selectOne(
                                        Wrappers.<WmStorageLocation>lambdaQuery()
                                                .eq(WmStorageLocation::getWarehouseId, warehouseId)
                                                .eq(WmStorageLocation::getLocationCode, code));
        if (location == null || !Objects.equals(warehouseId, location.getWarehouseId())) {
            throw new ServiceException(label + "库位不存在或不属于指定仓库");
        }
        if (!Long.valueOf(1L).equals(location.getEnableFlag()) || Long.valueOf(1L).equals(location.getFrozenFlag())) {
            throw new ServiceException(label + "库位未启用或已冻结");
        }
        if (StringUtils.isNotBlank(code) && !code.equals(location.getLocationCode())) {
            throw new ServiceException(label + "库位编码与ID不一致");
        }
        return location;
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 调拨处理（submit）
     *
     * @param transferIdStr 参数 transferIdStr
     * @param selectFlowId selectFlow ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String transferIdStr, String selectFlowId) {
        Long transferId = Long.parseLong(transferIdStr);
        WmTransfer entity = baseMapper.selectById(transferId);
        if (entity == null) {
            throw new RuntimeException("调拨单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的调拨单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", transferIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("transferId", entity.getTransferId());
        variables.put("transferNo", entity.getTransferNo());
        R<Void> result = wm.processService.startProcessByDataId(transferIdStr, selectFlowId, "wmTransferService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取调拨
     *
     * @param transferIdStr 参数 transferIdStr
     * @return 结果
     */
    @Override
    public String getProcInsId(String transferIdStr) {
        return wm.procInsIdHelper.resolve(transferIdStr, () -> {
            WfMyBusiness business = wm.wfMyBusinessServiceImpl.getByDataId(transferIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 调拨处理（afterFlowHandle）
     *
     * @param business 参数 business
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long transferId = Long.parseLong(business.getDataId());
        WmTransfer entity = baseMapper.selectById(transferId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(entity);
    }

    /**
     * 获取调拨详情
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
     * 调拨处理（flowValuesOfTask）
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
     * 调拨处理（flowCandidateUsernamesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void deleteLinesByTransferId(Long transferId) {
        if (transferId != null) {
            LambdaQueryWrapper<WmTransferLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmTransferLine::getTransferId, transferId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmTransfer> buildQueryWrapper(WmTransferBo bo) {
        LambdaQueryWrapper<WmTransfer> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getTransferNo()), WmTransfer::getTransferNo, bo.getTransferNo());
        lqw.eq(bo.getFromWarehouseId() != null, WmTransfer::getFromWarehouseId, bo.getFromWarehouseId());
        lqw.eq(bo.getToWarehouseId() != null, WmTransfer::getToWarehouseId, bo.getToWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getTransferType()), WmTransfer::getTransferType, bo.getTransferType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmTransfer::getStatus, bo.getStatus());
        lqw.orderByDesc(WmTransfer::getCreateTime);
        return lqw;
    }
}
