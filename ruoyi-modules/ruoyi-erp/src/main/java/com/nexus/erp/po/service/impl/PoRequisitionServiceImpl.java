package com.nexus.erp.po.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.po.domain.PoRequisition;
import com.nexus.erp.po.domain.PoRequisitionDetail;
import com.nexus.erp.po.domain.bo.PoRequisitionBo;
import com.nexus.erp.po.domain.vo.PoRequisitionVo;
import com.nexus.erp.po.mapper.PoRequisitionDetailMapper;
import com.nexus.erp.po.mapper.PoRequisitionMapper;
import com.nexus.erp.po.service.IPoRequisitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门请购单Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class PoRequisitionServiceImpl implements IPoRequisitionService {

    private final PoRequisitionMapper baseMapper;
    private final PoRequisitionDetailMapper detailMapper;

    /**
     * 分页查询部门请购单列表
     */
    @Override
    public TableDataInfo<PoRequisitionVo> selectPageList(PoRequisitionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PoRequisition> lqw = buildQueryWrapper(bo);
        Page<PoRequisitionVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询部门请购单列表（不分页）
     */
    @Override
    public List<PoRequisitionVo> selectList(PoRequisitionBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询部门请购单（含明细行）
     */
    @Override
    public PoRequisitionVo selectById(Long id) {
        PoRequisitionVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<PoRequisitionDetail> lqw = Wrappers.lambdaQuery();
            lqw.eq(PoRequisitionDetail::getRequisitionId, vo.getRequisitionId());
            lqw.orderByAsc(PoRequisitionDetail::getDetailId);
            vo.setPoRequisitionDetailList(detailMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增部门请购单（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PoRequisitionBo bo) {
        PoRequisition entity = MapstructUtils.convert(bo, PoRequisition.class);
        int rows = baseMapper.insert(entity);
        // 插入明细行
        if (bo.getPoRequisitionDetailList() != null && !bo.getPoRequisitionDetailList().isEmpty()) {
            for (PoRequisitionDetail detail : bo.getPoRequisitionDetailList()) {
                detail.setRequisitionId(entity.getRequisitionId());
                detailMapper.insert(detail);
            }
        }
        return rows;
    }

    /**
     * 修改部门请购单（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PoRequisitionBo bo) {
        PoRequisition entity = MapstructUtils.convert(bo, PoRequisition.class);
        int rows = baseMapper.updateById(entity);
        // 删除原有明细行并重新插入
        if (bo.getPoRequisitionDetailList() != null) {
            detailMapper.delete(Wrappers.lambdaQuery(PoRequisitionDetail.class)
                .eq(PoRequisitionDetail::getRequisitionId, entity.getRequisitionId()));
            for (PoRequisitionDetail detail : bo.getPoRequisitionDetailList()) {
                detail.setRequisitionId(entity.getRequisitionId());
                detailMapper.insert(detail);
            }
        }
        return rows;
    }

    /**
     * 批量删除部门请购单（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            detailMapper.delete(Wrappers.lambdaQuery(PoRequisitionDetail.class)
                .eq(PoRequisitionDetail::getRequisitionId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<PoRequisition> buildQueryWrapper(PoRequisitionBo bo) {
        LambdaQueryWrapper<PoRequisition> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getRequisitionNo()), PoRequisition::getRequisitionNo, bo.getRequisitionNo());
        lqw.like(StringUtils.isNotBlank(bo.getDepartmentName()), PoRequisition::getDepartmentName, bo.getDepartmentName());
        lqw.like(StringUtils.isNotBlank(bo.getApplicantName()), PoRequisition::getApplicantName, bo.getApplicantName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PoRequisition::getStatus, bo.getStatus());
        lqw.orderByDesc(PoRequisition::getCreateTime);
        return lqw;
    }
}
