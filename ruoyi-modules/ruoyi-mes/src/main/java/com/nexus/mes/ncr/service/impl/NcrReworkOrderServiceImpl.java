package com.nexus.mes.ncr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.ncr.domain.NcrReworkOrder;
import com.nexus.mes.ncr.domain.bo.NcrReworkOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrReworkOrderVo;
import com.nexus.mes.ncr.mapper.NcrReworkOrderMapper;
import com.nexus.mes.ncr.service.INcrReworkOrderService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.system.strategy.AutoCodeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * MES NCR返工单 Service 业务层实现
 * <p>
 * 返工单由 NCR 处置(REWORK)自动派生,通过 {@link IProWorkOrderService} 创建返工工单。
 * 注意: 为避免循环依赖,本实现不注入 INcrOrderService。
 * NCR 状态流转由 NcrOrderServiceImpl 在其 dispose 方法中通过 NcrOrderMapper 直调完成。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("ncrReworkOrderService")
public class NcrReworkOrderServiceImpl implements INcrReworkOrderService {

    private final NcrReworkOrderMapper baseMapper;
    private final IProWorkOrderService proWorkOrderService;
    private final ProWorkOrderMapper proWorkOrderMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询返工单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 返工单分页数据
     */
    @Override
    public TableDataInfo<NcrReworkOrderVo> selectPageList(NcrReworkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<NcrReworkOrder> lqw = buildQueryWrapper(bo);
        Page<NcrReworkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取返工单详情
     *
     * @param reworkId 返工记录ID
     * @return 返工单详情
     */
    @Override
    public NcrReworkOrderVo selectById(Long reworkId) {
        return baseMapper.selectVoById(reworkId);
    }

    /**
     * 批量删除返工单
     *
     * @param ids 返工记录ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除返工单: count={}", ids.length);
        return rows > 0;
    }

    /**
     * 派生返工工单
     * <p>
     * 1. 查询原工单信息(materialId/routingId/workshopId/bomId/unitId/materialCode/materialName);
     * 2. 构造 ProWorkOrderBo 创建返工工单(复制物料/工艺/车间,plannedQty=reworkQty);
     * 3. 确认新增方法回写的工单主键，插入并回读返工关联，核对本次来源、工单与数量。
     * 注意: 本方法不修改 NcrOrder 状态,由调用方 NcrOrderServiceImpl 负责 CAS 流转。
     * </p>
     */
    @Lock4j(keys = {"#ncrId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrReworkOrderVo deriveReworkOrder(Long ncrId, Long originalWoId, BigDecimal reworkQty, String reason) {
        if (ncrId == null || ncrId <= 0) {
            throw new ServiceException("NCR单主键必须为正数");
        }
        if (originalWoId == null || originalWoId <= 0) {
            throw new ServiceException("原工单主键必须为正数");
        }
        if (reworkQty == null || reworkQty.signum() <= 0) {
            throw new ServiceException("返工数量必须大于零");
        }

        // 1. 查询原工单(直调 Mapper 取原始实体, 避免 selectById 的 VO 富集触发 bridge 跨库查 bd_routing 在 @Transactional mes 内失效)
        ProWorkOrder originalWo = proWorkOrderMapper.selectById(originalWoId);
        if (originalWo == null) {
            throw new ServiceException("原工单不存在: " + originalWoId);
        }

        // 2. 构造返工工单BO
        String reworkWoNo = autoCodeUtil.genSerialCode("MES_REWORK_CODE", null);
        ProWorkOrderBo reworkBo = new ProWorkOrderBo();
        reworkBo.setWoNo(reworkWoNo);
        reworkBo.setSourceType("REWORK");
        reworkBo.setMaterialId(originalWo.getMaterialId());
        reworkBo.setMaterialCode(originalWo.getMaterialCode());
        reworkBo.setMaterialName(originalWo.getMaterialName());
        reworkBo.setPlannedQty(reworkQty);
        reworkBo.setUnitId(originalWo.getUnitId());
        reworkBo.setUnitName(originalWo.getUnitName());
        reworkBo.setWorkshopId(originalWo.getWorkshopId());
        reworkBo.setWorkshopName(originalWo.getWorkshopName());
        reworkBo.setBomId(originalWo.getBomId());
        reworkBo.setRoutingId(originalWo.getRoutingId());
        reworkBo.setRemark("NCR返工: " + (reason != null ? reason : ""));
        int rows = proWorkOrderService.insertByBo(reworkBo);
        if (rows != 1) {
            throw new ServiceException("返工工单创建失败：受影响行数必须为一行，实际为 " + rows);
        }

        // 3. 新增方法已回写工单主键，直接取用，避免富集查询触发跨库访问。
        Long reworkWoId = reworkBo.getMesWoId();
        if (reworkWoId == null || reworkWoId <= 0) {
            throw new ServiceException("返工工单创建后未获取到有效正主键，工单编号：" + reworkWoNo);
        }

        // 4. 构建返工关联记录
        NcrReworkOrder reworkOrder = new NcrReworkOrder();
        reworkOrder.setReworkCode(autoCodeUtil.genSerialCode("MES_REWORK_CODE", null));
        reworkOrder.setNcrId(ncrId);
        reworkOrder.setOriginalWoId(originalWoId);
        reworkOrder.setOriginalWoNo(originalWo.getWoNo());
        reworkOrder.setReworkWoId(reworkWoId);
        reworkOrder.setReworkWoNo(reworkWoNo);
        reworkOrder.setReworkQty(reworkQty);
        reworkOrder.setReworkReason(reason);
        reworkOrder.setStatus(STATUS_OPEN);
        int linkRows = baseMapper.insert(reworkOrder);
        if (linkRows != 1) {
            throw new ServiceException("返工关联创建失败：受影响行数必须为一行，实际为 " + linkRows);
        }
        Long reworkId = reworkOrder.getReworkId();
        if (reworkId == null || reworkId <= 0) {
            throw new ServiceException("返工关联创建后未获取到有效正主键");
        }
        NcrReworkOrderVo persisted = baseMapper.selectVoById(reworkId);
        if (persisted == null) {
            throw new ServiceException("返工关联创建后无法读回，关联主键：" + reworkId);
        }
        if (!reworkId.equals(persisted.getReworkId())
            || !ncrId.equals(persisted.getNcrId())
            || !originalWoId.equals(persisted.getOriginalWoId())
            || !reworkWoId.equals(persisted.getReworkWoId())
            || persisted.getReworkQty() == null
            || reworkQty.compareTo(persisted.getReworkQty()) != 0) {
            throw new ServiceException("返工关联回读身份或数量与本次派生不一致，关联主键：" + reworkId);
        }

        log.info("派生返工单: ncrId={}, originalWoId={}, reworkWoId={}, reworkWoNo={}",
            ncrId, originalWoId, reworkWoId, reworkWoNo);
        return persisted;
    }

    /**
     * 构建返工单查询条件包装器
     */
    private LambdaQueryWrapper<NcrReworkOrder> buildQueryWrapper(NcrReworkOrderBo bo) {
        return Wrappers.<NcrReworkOrder>lambdaQuery()
            .eq(bo.getNcrId() != null, NcrReworkOrder::getNcrId, bo.getNcrId())
            .eq(bo.getOriginalWoId() != null, NcrReworkOrder::getOriginalWoId, bo.getOriginalWoId())
            .eq(bo.getReworkWoId() != null, NcrReworkOrder::getReworkWoId, bo.getReworkWoId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), NcrReworkOrder::getStatus, bo.getStatus())
            .orderByDesc(NcrReworkOrder::getCreateTime);
    }
}