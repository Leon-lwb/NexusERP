package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqSpareReturn;
import com.nexus.mes.eq.domain.EqSpareReturnLine;
import com.nexus.mes.eq.domain.bo.EqSpareReturnBo;
import com.nexus.mes.eq.domain.bo.EqSpareReturnLineBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.domain.vo.EqSpareReturnLineVo;
import com.nexus.mes.eq.domain.vo.EqSpareReturnVo;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnLineMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnMapper;
import com.nexus.mes.eq.service.IEqSparePartService;
import com.nexus.mes.eq.service.IEqSpareReturnService;
import com.nexus.mes.eq.service.support.EqSpareReturnNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES备件退库单 Service 业务层实现（MES-EQ-04 收口）
 * <p>
 * 数据源: {@code ry-mes} 库（类上必须标 {@code @DS("mes")}）。确认退库时逐行调
 * {@link IEqSparePartService#stockTransaction}（{@code TXN_RETURN}，ERP 侧按入库过账），
 * 由既有链路写 MES 库存流水 + ERP 过账同步台账 + 事务提交后投递 MQ，因此退库同样具备
 * 「幂等键 + 失败可见 + 可重投」的完整语义。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqSpareReturnService")
public class EqSpareReturnServiceImpl implements IEqSpareReturnService {

    private final EqSpareReturnMapper baseMapper;
    private final EqSpareReturnLineMapper lineMapper;
    private final EqSparePartMapper sparePartMapper;
    private final IEqSparePartService sparePartService;
    private final EqSpareReturnNoGenerator noGenerator;

    /**
     * 分页查询退库单列表(不填充明细)
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqSpareReturnVo> selectPageList(EqSpareReturnBo bo, PageQuery pageQuery) {
        Page<EqSpareReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 退库单详情(含明细行)
     *
     * @param returnId 退库单ID
     * @return 详情
     */
    @Override
    public EqSpareReturnVo selectById(Long returnId) {
        EqSpareReturnVo vo = baseMapper.selectVoById(returnId);
        if (vo != null) {
            vo.setLines(selectLines(returnId));
        }
        return vo;
    }

    /**
     * 新增退库单草稿(含明细)
     *
     * @param bo 业务对象
     * @return 退库单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(EqSpareReturnBo bo) {
        List<EqSpareReturnLine> lines = buildLines(bo);
        String returnNo = StringUtils.isNotBlank(bo.getReturnNo()) ? bo.getReturnNo().trim() : noGenerator.nextReturnNo();
        Long dup = baseMapper.selectCount(Wrappers.<EqSpareReturn>lambdaQuery()
            .eq(EqSpareReturn::getReturnNo, returnNo));
        if (dup != null && dup > 0) {
            throw new ServiceException("退库单号已存在: " + returnNo);
        }
        EqSpareReturn header = new EqSpareReturn();
        header.setReturnNo(returnNo);
        header.setWarehouseId(bo.getWarehouseId());
        header.setReturnDate(bo.getReturnDate() != null ? bo.getReturnDate() : new Date());
        header.setReturnType(normalizeType(bo.getReturnType()));
        header.setStatus(STATUS_DRAFT);
        header.setTotalQty(sumQty(lines));
        header.setRemark(bo.getRemark());
        header.setVersion(0);
        baseMapper.insert(header);
        for (EqSpareReturnLine line : lines) {
            line.setReturnId(header.getReturnId());
            lineMapper.insert(line);
        }
        log.info("新增备件退库单: returnId={}, returnNo={}, 行数={}, 合计={}",
            header.getReturnId(), returnNo, lines.size(), header.getTotalQty());
        return header.getReturnId();
    }

    /**
     * 修改退库单(仅 DRAFT, 明细全量替换)
     *
     * @param bo 业务对象
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(EqSpareReturnBo bo) {
        if (bo.getReturnId() == null) {
            throw new ServiceException("退库单ID不能为空");
        }
        EqSpareReturn exist = baseMapper.selectById(bo.getReturnId());
        if (exist == null) {
            throw new ServiceException("退库单不存在: " + bo.getReturnId());
        }
        if (!STATUS_DRAFT.equals(exist.getStatus())) {
            throw new ServiceException("仅草稿退库单可修改: " + exist.getReturnNo() + ", 当前状态: " + exist.getStatus());
        }
        List<EqSpareReturnLine> lines = buildLines(bo);
        EqSpareReturn update = new EqSpareReturn();
        update.setReturnId(bo.getReturnId());
        update.setWarehouseId(bo.getWarehouseId());
        update.setReturnDate(bo.getReturnDate() != null ? bo.getReturnDate() : exist.getReturnDate());
        update.setReturnType(normalizeType(bo.getReturnType()));
        update.setTotalQty(sumQty(lines));
        update.setRemark(bo.getRemark());
        update.setVersion(exist.getVersion() == null ? 1 : exist.getVersion() + 1);
        int rows = baseMapper.updateById(update);
        if (rows == 0) {
            throw new ServiceException("退库单修改失败(并发冲突, 请刷新后重试): " + bo.getReturnId());
        }
        lineMapper.delete(Wrappers.<EqSpareReturnLine>lambdaQuery()
            .eq(EqSpareReturnLine::getReturnId, bo.getReturnId()));
        for (EqSpareReturnLine line : lines) {
            line.setReturnId(bo.getReturnId());
            lineMapper.insert(line);
        }
        log.info("修改备件退库单: returnId={}, 行数={}", bo.getReturnId(), lines.size());
        return true;
    }

    /**
     * 删除退库单(仅 DRAFT)
     *
     * @param ids 退库单ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<EqSpareReturn> records = baseMapper.selectList(Wrappers.<EqSpareReturn>lambdaQuery()
            .in(EqSpareReturn::getReturnId, Arrays.asList(ids)));
        for (EqSpareReturn record : records) {
            if (!STATUS_DRAFT.equals(record.getStatus())) {
                throw new ServiceException("仅草稿退库单可删除: " + record.getReturnNo()
                    + ", 当前状态: " + record.getStatus());
            }
        }
        lineMapper.delete(Wrappers.<EqSpareReturnLine>lambdaQuery()
            .in(EqSpareReturnLine::getReturnId, Arrays.asList(ids)));
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 确认退库：CAS(DRAFT→CONFIRMED) 先占位, 再逐行 TXN_RETURN 过账, 任一行失败整单回滚
     *
     * @param returnId 退库单ID
     * @return 是否确认成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirm(Long returnId) {
        EqSpareReturn header = baseMapper.selectById(returnId);
        if (header == null) {
            throw new ServiceException("退库单不存在: " + returnId);
        }
        if (STATUS_CONFIRMED.equals(header.getStatus())) {
            // 幂等: 重复确认按成功返回, 不重复过账(库存/台账/消息均不会再产生)
            log.info("退库单已确认, 重复确认按幂等成功处理: returnId={}, returnNo={}", returnId, header.getReturnNo());
            return true;
        }
        if (STATUS_CANCELLED.equals(header.getStatus())) {
            throw new ServiceException("退库单已作废, 不可确认: " + header.getReturnNo());
        }
        // CAS 占位: 先置 CONFIRMED(同事务), 失败则整单回滚回 DRAFT, 保证并发下只过账一次
        int rows = baseMapper.update(null, Wrappers.<EqSpareReturn>lambdaUpdate()
            .set(EqSpareReturn::getStatus, STATUS_CONFIRMED)
            .set(EqSpareReturn::getConfirmBy, LoginHelper.getUserId())
            .set(EqSpareReturn::getConfirmTime, new Date())
            .setSql("version = version + 1")
            .eq(EqSpareReturn::getReturnId, returnId)
            .eq(EqSpareReturn::getStatus, STATUS_DRAFT));
        if (rows == 0) {
            throw new ServiceException("退库单状态已变更, 确认失败(请刷新后重试): " + header.getReturnNo());
        }
        List<EqSpareReturnLine> lines = lineMapper.selectList(Wrappers.<EqSpareReturnLine>lambdaQuery()
            .eq(EqSpareReturnLine::getReturnId, returnId)
            .orderByAsc(EqSpareReturnLine::getLineId));
        if (lines.isEmpty()) {
            throw new ServiceException("退库单无明细, 不可确认: " + header.getReturnNo());
        }
        for (EqSpareReturnLine line : lines) {
            EqSpareStockBo stockBo = new EqSpareStockBo();
            stockBo.setSpareId(line.getSpareId());
            stockBo.setTransactionType(IEqSparePartService.TXN_RETURN);
            stockBo.setQuantity(line.getReturnQty());
            stockBo.setRefType(REF_TYPE);
            stockBo.setRefId(returnId);
            stockBo.setRemark(StringUtils.isNotBlank(line.getRemark()) ? line.getRemark() : "备件退库单确认");
            // 逐行退库(ERP 侧按入库方向过账); 任一行失败抛异常 → 整单回滚(含上面的 CAS)并保持 DRAFT
            sparePartService.stockTransaction(stockBo);
        }
        log.info("备件退库单确认完成: returnId={}, returnNo={}, 行数={}",
            returnId, header.getReturnNo(), lines.size());
        return true;
    }

    // ==================== 私有辅助 ====================

    /**
     * 构造列表查询条件
     *
     * @param bo 查询条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<EqSpareReturn> buildQueryWrapper(EqSpareReturnBo bo) {
        return Wrappers.<EqSpareReturn>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getReturnNo()), EqSpareReturn::getReturnNo, bo.getReturnNo())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqSpareReturn::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getReturnType()), EqSpareReturn::getReturnType, bo.getReturnType())
            .ge(StringUtils.isNotBlank(bo.getBeginTime()), EqSpareReturn::getReturnDate, bo.getBeginTime())
            .le(StringUtils.isNotBlank(bo.getEndTime()), EqSpareReturn::getReturnDate, normalizeEndTime(bo.getEndTime()))
            .orderByDesc(EqSpareReturn::getReturnDate)
            .orderByDesc(EqSpareReturn::getReturnId);
    }

    /**
     * 查询明细行
     *
     * @param returnId 退库单ID
     * @return 明细列表
     */
    private List<EqSpareReturnLineVo> selectLines(Long returnId) {
        return lineMapper.selectVoList(Wrappers.<EqSpareReturnLine>lambdaQuery()
            .eq(EqSpareReturnLine::getReturnId, returnId)
            .orderByAsc(EqSpareReturnLine::getLineId));
    }

    /**
     * 校验并构造明细行：数量 &gt; 0、备件存在；同一 spare_id 重复行合并（数量累加）。
     *
     * @param bo 业务对象
     * @return 明细行（已合并、已回填冗余字段与服务端单价快照）
     */
    private List<EqSpareReturnLine> buildLines(EqSpareReturnBo bo) {
        if (bo.getLines() == null || bo.getLines().isEmpty()) {
            throw new ServiceException("退库单明细不能为空");
        }
        Map<Long, EqSpareReturnLine> merged = new LinkedHashMap<>();
        for (EqSpareReturnLineBo lineBo : bo.getLines()) {
            if (lineBo == null || lineBo.getSpareId() == null) {
                throw new ServiceException("退库明细缺少备件ID");
            }
            BigDecimal qty = lineBo.getReturnQty();
            if (qty == null || qty.signum() <= 0) {
                throw new ServiceException("退库数量必须大于0: spareId=" + lineBo.getSpareId());
            }
            EqSpareReturnLine exist = merged.get(lineBo.getSpareId());
            if (exist != null) {
                // 同单同备件重复行合并(数量累加), 避免同一备件在本单被重复过账
                exist.setReturnQty(exist.getReturnQty().add(qty));
                continue;
            }
            EqSparePartVo spare = sparePartMapper.selectVoById(lineBo.getSpareId());
            if (spare == null) {
                throw new ServiceException("备件不存在: " + lineBo.getSpareId());
            }
            EqSpareReturnLine line = new EqSpareReturnLine();
            line.setSpareId(spare.getSpareId());
            line.setSpareCode(spare.getSpareCode());
            line.setSpareName(spare.getSpareName());
            line.setReturnQty(qty);
            // 单价快照: 服务端从备件主数据读取, 不采信客户端
            line.setUnitCost(spare.getUnitCost());
            line.setRemark(lineBo.getRemark());
            merged.put(lineBo.getSpareId(), line);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 汇总明细数量
     *
     * @param lines 明细行
     * @return 合计数量
     */
    private BigDecimal sumQty(List<EqSpareReturnLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (EqSpareReturnLine line : lines) {
            total = total.add(line.getReturnQty());
        }
        return total;
    }

    /**
     * 校验并规范化退库类型（缺省 OTHER）
     *
     * @param returnType 退库类型
     * @return 规范化后的类型
     */
    private String normalizeType(String returnType) {
        if (StringUtils.isBlank(returnType)) {
            return TYPE_OTHER;
        }
        String type = returnType.trim();
        if (!TYPE_REPAIR_RETURN.equals(type) && !TYPE_EXCESS_RETURN.equals(type) && !TYPE_OTHER.equals(type)) {
            throw new ServiceException("非法退库类型: " + returnType
                + " (允许: REPAIR_RETURN/EXCESS_RETURN/OTHER)");
        }
        return type;
    }

    /**
     * 结束时间只传日期时补 23:59:59，保证当天数据被包含
     *
     * @param endTime 查询结束时间
     * @return 规范化后的结束时间
     */
    private String normalizeEndTime(String endTime) {
        if (StringUtils.isBlank(endTime)) {
            return endTime;
        }
        String trimmed = endTime.trim();
        return trimmed.length() == 10 ? trimmed + " 23:59:59" : trimmed;
    }
}
