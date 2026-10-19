package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.trace.SerialReservation;
import com.nexus.common.trace.SerialReservationCommand;
import com.nexus.erp.wm.trace.ErpSerialIdentityService;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.bo.WmSnBo;
import com.nexus.erp.wm.domain.vo.WmSnVo;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.IWmSnService;
import com.nexus.erp.wm.trace.ErpTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 序列号旧入口适配：正式号码仅由ERP注册，库存与生命周期事实只能通过业务单据改变。 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmSnServiceImpl implements IWmSnService {
    private final WmSnMapper baseMapper;
    private final ErpSerialIdentityService identities;
    private final ErpTraceContext context;

    @Override
    public TableDataInfo<WmSnVo> selectPageList(WmSnBo bo, PageQuery pageQuery) {
        Page<WmSnVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmSnVo> selectList(WmSnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmSnVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /** 原新增URL注册一条正式身份；不自造命令，不直接插SN，更不声明已入库。 */
    @Override
    public int insertByBo(WmSnBo bo) {
        if (bo != null && bo.getQuantity() != null && bo.getQuantity() != 1) {
            throw new ServiceException("单个新增只允许注册一条SN，请使用带commandId的批量入口");
        }
        SerialReservation reservation = register(bo, 1);
        var identity = reservation.identities().get(0);
        bo.setSnId(identity.identityId());
        bo.setSnCode(identity.snCode());
        return 1;
    }

    /** 原批量URL按调用方持久命令一次预留完整号段，规则及批次由注册服务冻结。 */
    @Override
    public void batchGenerate(WmSnBo bo) {
        if (bo == null || bo.getQuantity() == null || bo.getQuantity() <= 0) {
            throw new ServiceException("生成数量必须大于0");
        }
        register(bo, bo.getQuantity());
    }

    /** 无命令旧签名不能生成可靠指纹，明确停用而不替调用方生成随机幂等号。 */
    @Deprecated
    @Override
    public void batchGenerate(Long materialId, Integer quantity, Long batchId, Long ruleId,
                              Long sourceDocId, String sourceDocType, String sourceDocNo) {
        throw new ServiceException("旧批量接口缺少commandId，请使用带commandId的注册请求");
    }

    private SerialReservation register(WmSnBo bo, int quantity) {
        if (bo == null || StringUtils.isBlank(bo.getCommandId()) || bo.getCommandId().length() > 128) {
            throw new ServiceException("注册必须提供有效commandId，长度不得超过128位");
        }
        if (bo.getSnCode() != null && bo.getSnCode().length() > 64) {
            throw new ServiceException("新SN编码不得超过64位；历史号码不可截断重写");
        }
        if (StringUtils.isNotBlank(bo.getSnCode()) || bo.getSnId() != null) {
            throw new ServiceException("正式SN及身份ID只能由ERP统一注册生成，外部号码请走别名维护");
        }
        if (bo.getStockId() != null || bo.getWarehouseId() != null || bo.getLocationId() != null
            || StringUtils.isNotBlank(bo.getWarehouseCode()) || StringUtils.isNotBlank(bo.getWarehouseName())
            || StringUtils.isNotBlank(bo.getLocationCode())
            || (StringUtils.isNotBlank(bo.getStatus()) && !"REGISTERED".equals(bo.getStatus()))) {
            throw new ServiceException("注册仅产生REGISTERED身份，库存和状态请通过业务单据处理");
        }
        if (bo.getBatchId() == null && StringUtils.isNotBlank(bo.getBatchCode())) {
            throw new ServiceException("指定批次必须提供batchId，由注册服务校验租户及物料");
        }
        try {
            var command = new SerialReservationCommand(bo.getCommandId(), bo.getMaterialId(), quantity,
                bo.getRuleId(), StringUtils.isBlank(bo.getSourceDocType()) ? "ERP_MANUAL" : bo.getSourceDocType(),
                bo.getSourceDocId(), bo.getBatchId());
            return identities.reserveManual(context.tenant(), command);
        } catch (IllegalArgumentException error) {
            throw new ServiceException(error.getMessage());
        }
    }

    /** 只允许备注及预留元数据；完整表单可回传相同事实，但不能改写或清空事实列。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmSnBo bo) {
        if (bo == null || bo.getSnId() == null) throw new ServiceException("序列号ID不能为空");
        WmSn existing = baseMapper.selectById(bo.getSnId());
        if (existing == null) throw new ServiceException("序列号不存在或不可访问");
        rejectFactChange(bo.getSnCode(), existing.getSnCode());
        rejectFactChange(bo.getMaterialId(), existing.getMaterialId());
        rejectFactChange(bo.getMaterialCode(), existing.getMaterialCode());
        rejectFactChange(bo.getMaterialName(), existing.getMaterialName());
        rejectFactChange(bo.getMaterialSpec(), existing.getMaterialSpec());
        rejectFactChange(bo.getBatchId(), existing.getBatchId());
        rejectFactChange(bo.getBatchCode(), existing.getBatchCode());
        rejectFactChange(bo.getStockId(), existing.getStockId());
        rejectFactChange(bo.getWarehouseId(), existing.getWarehouseId());
        rejectFactChange(bo.getWarehouseCode(), existing.getWarehouseCode());
        rejectFactChange(bo.getWarehouseName(), existing.getWarehouseName());
        rejectFactChange(bo.getLocationId(), existing.getLocationId());
        rejectFactChange(bo.getLocationCode(), existing.getLocationCode());
        rejectFactChange(bo.getStatus(), existing.getStatus());
        rejectFactChange(bo.getSourceDocId(), existing.getSourceDocId());
        rejectFactChange(bo.getSourceDocType(), existing.getSourceDocType());
        rejectFactChange(bo.getSourceDocNo(), existing.getSourceDocNo());
        rejectFactChange(bo.getRuleId(), existing.getRuleId());
        rejectFactChange(bo.getProductionDate(), existing.getProductionDate());
        rejectFactChange(bo.getExpiryDate(), existing.getExpiryDate());
        WmSn patch = new WmSn();
        patch.setSnId(existing.getSnId());
        patch.setRemark(bo.getRemark());
        patch.setAttr1(bo.getAttr1());
        patch.setAttr2(bo.getAttr2());
        patch.setAttr3(bo.getAttr3());
        patch.setAttr4(bo.getAttr4());
        return baseMapper.updateById(patch);
    }

    private void rejectFactChange(Object requested, Object current) {
        if (requested != null && !Objects.equals(requested, current)) {
            throw new ServiceException("正式SN号码、物料、批次、库存、来源及状态等事实不可直接修改，请通过业务单据处理");
        }
    }

    /** 正式号码永久占用，删除不能成为回收号码的旁路。 */
    @Override
    public int deleteByIds(Long[] ids) {
        throw new ServiceException("正式SN不可删除或回收，请通过业务单据作废或报废并保留追溯身份");
    }

    @Override
    public List<WmSnVo> selectByMaterialAndStatus(Long materialId, String status) {
        LambdaQueryWrapper<WmSn> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmSn::getMaterialId, materialId);
        lqw.eq(StringUtils.isNotBlank(status), WmSn::getStatus, status);
        return baseMapper.selectVoList(lqw);
    }

    /** 库存绑定只允许由受控业务单据过账。 */
    @Override
    public void bindStock(Long snId, Long stockId, Long warehouseId, Long locationId) {
        throw new ServiceException("不支持直接绑定SN库存，请通过收货、入库或调拨业务单据过账");
    }

    /** 库存解绑只允许由受控业务单据过账。 */
    @Override
    public void unbindStock(Long snId) {
        throw new ServiceException("不支持直接解绑SN库存，请通过出库、退料或调拨业务单据过账");
    }

    /** 生命周期状态由对应业务单据驱动，维护接口不接受任意状态。 */
    @Override
    public void changeStatus(Long snId, String status) {
        throw new ServiceException("不支持直接变更SN状态，请通过对应业务单据执行");
    }

    /** 保留原查询过滤条件与排序。 */
    private LambdaQueryWrapper<WmSn> buildQueryWrapper(WmSnBo bo) {
        LambdaQueryWrapper<WmSn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getSnCode()), WmSn::getSnCode, bo.getSnCode());
        lqw.eq(bo.getMaterialId() != null, WmSn::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmSn::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmSn::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmSn::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(bo.getBatchId() != null, WmSn::getBatchId, bo.getBatchId());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmSn::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmSn::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmSn::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmSn::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getLocationId() != null, WmSn::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmSn::getLocationCode, bo.getLocationCode());
        lqw.eq(bo.getStockId() != null, WmSn::getStockId, bo.getStockId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmSn::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocType()), WmSn::getSourceDocType, bo.getSourceDocType());
        lqw.eq(bo.getSourceDocId() != null, WmSn::getSourceDocId, bo.getSourceDocId());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocNo()), WmSn::getSourceDocNo, bo.getSourceDocNo());
        lqw.orderByDesc(WmSn::getCreateTime);
        return lqw;
    }
}
