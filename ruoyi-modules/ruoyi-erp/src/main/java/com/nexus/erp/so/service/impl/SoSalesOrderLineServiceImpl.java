package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoSalesOrderLineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.Date;
import java.util.List;

/**
 * 销售订单行Service业务层处理
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoSalesOrderLineServiceImpl implements ISoSalesOrderLineService {

    private final SoSalesOrderLineMapper baseMapper;

    /**
     * 分页查询销售订单行列表
     */
    @Override
    public TableDataInfo<SoSalesOrderLineVo> selectPageList(SoSalesOrderLineBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoSalesOrderLine> lqw = buildQueryWrapper(bo);
        Page<SoSalesOrderLineVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售订单行列表
     */
    @Override
    public List<SoSalesOrderLineVo> selectList(SoSalesOrderLineBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售订单行详情
     */
    @Override
    public SoSalesOrderLineVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增销售订单行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoSalesOrderLineBo bo) {
        SoSalesOrderLine entity = MapstructUtils.convert(bo, SoSalesOrderLine.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改销售订单行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderLineBo bo) {
        SoSalesOrderLine entity = MapstructUtils.convert(bo, SoSalesOrderLine.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售订单行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 按条件查询销售订单行实体列表
     */
    @Override
    public List<SoSalesOrderLine> list(Wrapper<SoSalesOrderLine> wrapper) {
        return baseMapper.selectList(wrapper);
    }

    /**
     * 按销售订单ID和物料ID更新OQC状态。
     * <p>ERP-SO-02: 原先直接按 (soId, materialId) 条件更新, 会把同一订单同一物料的<b>所有行</b>
     * (不同行号/交期)一起改写。OQC 消息只带 soId+materialId, 无法区分行, 因此先确定唯一目标行
     * (未取消行中 line_id 最小者), 再按 line_id 精确更新; 命中多行时告警留痕。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOqcStatus(Long soId, Long materialId, String oqcStatus, Long oqcId, String oqcCode, Date oqcTime) {
        List<SoSalesOrderLine> candidates = baseMapper.selectList(Wrappers.<SoSalesOrderLine>lambdaQuery()
            .eq(SoSalesOrderLine::getSoId, soId)
            .eq(SoSalesOrderLine::getMaterialId, materialId)
            .ne(SoSalesOrderLine::getLineStatus, "CANCELLED")
            .orderByAsc(SoSalesOrderLine::getLineId));
        if (candidates.isEmpty()) {
            return 0;
        }
        if (candidates.size() > 1) {
            log.warn("同一销售订单同一物料存在 {} 行, OQC 结果无法按行区分, 仅回写首行: soId={}, materialId={}, lineId={}",
                candidates.size(), soId, materialId, candidates.get(0).getLineId());
        }
        return updateOqcStatusByLineId(candidates.get(0).getLineId(), oqcStatus, oqcId, oqcCode, oqcTime);
    }

    /**
     * 按销售订单行ID更新OQC状态(精确到行)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOqcStatusByLineId(Long lineId, String oqcStatus, Long oqcId, String oqcCode, Date oqcTime) {
        return baseMapper.update(null, Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .set(SoSalesOrderLine::getOqcStatus, oqcStatus)
            .set(SoSalesOrderLine::getOqcId, oqcId)
            .set(SoSalesOrderLine::getOqcCode, oqcCode)
            .set(SoSalesOrderLine::getOqcTime, oqcTime)
            .eq(SoSalesOrderLine::getLineId, lineId));
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoSalesOrderLine> buildQueryWrapper(SoSalesOrderLineBo bo) {
        LambdaQueryWrapper<SoSalesOrderLine> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getSoId() != null, SoSalesOrderLine::getSoId, bo.getSoId());
        lqw.eq(StringUtils.isNotBlank(bo.getLineNo()), SoSalesOrderLine::getLineNo, bo.getLineNo());
        lqw.eq(bo.getMaterialId() != null, SoSalesOrderLine::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), SoSalesOrderLine::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), SoSalesOrderLine::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getLineStatus()), SoSalesOrderLine::getLineStatus, bo.getLineStatus());
        return lqw;
    }
}
