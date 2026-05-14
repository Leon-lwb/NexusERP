package org.dromara.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdMaterialRequirementDetail;
import org.dromara.erp.prd.domain.bo.PrdMaterialRequirementBo;
import org.dromara.erp.prd.domain.vo.PrdMaterialRequirementVo;
import org.dromara.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import org.dromara.erp.prd.mapper.PrdMaterialRequirementMapper;
import org.dromara.erp.prd.service.IPrdMaterialRequirementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料清单Service业务层处理
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdMaterialRequirementServiceImpl implements IPrdMaterialRequirementService {

    private final PrdMaterialRequirementMapper baseMapper;
    private final PrdMaterialRequirementDetailMapper detailMapper;

    @Override
    public TableDataInfo<PrdMaterialRequirementVo> selectPageList(PrdMaterialRequirementBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildQueryWrapper(bo);
        Page<PrdMaterialRequirementVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdMaterialRequirementVo> selectList(PrdMaterialRequirementBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdMaterialRequirementVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId) {
        return baseMapper.selectById(requirementId);
    }

    @Override
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildEntityQueryWrapper(prdMaterialRequirement);
        return baseMapper.selectList(lqw);
    }

    @Override
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementListWithDetails(PrdMaterialRequirement prdMaterialRequirement) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildEntityQueryWrapper(prdMaterialRequirement);
        List<PrdMaterialRequirement> list = baseMapper.selectList(lqw);
        for (PrdMaterialRequirement main : list) {
            if (main.getRequirementId() != null) {
                LambdaQueryWrapper<PrdMaterialRequirementDetail> detailLqw = Wrappers.lambdaQuery();
                detailLqw.eq(PrdMaterialRequirementDetail::getRequirementId, main.getRequirementId());
                List<PrdMaterialRequirementDetail> details = detailMapper.selectList(detailLqw);
                main.setPrdMaterialRequirementDetailList(details);
            }
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
        int rows = baseMapper.insert(prdMaterialRequirement);
        insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdMaterialRequirementBo bo) {
        PrdMaterialRequirement entity = MapstructUtils.convert(bo, PrdMaterialRequirement.class);
        int rows = baseMapper.insert(entity);
        if (bo.getPrdMaterialRequirementDetailList() != null && !bo.getPrdMaterialRequirementDetailList().isEmpty()) {
            for (PrdMaterialRequirementDetail detail : bo.getPrdMaterialRequirementDetailList()) {
                detail.setRequirementId(entity.getRequirementId());
            }
            insertPrdMaterialRequirementDetails(bo.getPrdMaterialRequirementDetailList());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdMaterialRequirementBo bo) {
        PrdMaterialRequirement entity = MapstructUtils.convert(bo, PrdMaterialRequirement.class);
        // Delete existing details
        deleteDetailByRequirementId(entity.getRequirementId());
        // Insert new details
        if (bo.getPrdMaterialRequirementDetailList() != null && !bo.getPrdMaterialRequirementDetailList().isEmpty()) {
            for (PrdMaterialRequirementDetail detail : bo.getPrdMaterialRequirementDetailList()) {
                detail.setRequirementId(entity.getRequirementId());
            }
            insertPrdMaterialRequirementDetails(bo.getPrdMaterialRequirementDetailList());
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
        deleteDetailByRequirementId(prdMaterialRequirement.getRequirementId());
        insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return baseMapper.updateById(prdMaterialRequirement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteDetailByRequirementId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds) {
        for (Long id : requirementIds) {
            deleteDetailByRequirementId(id);
        }
        return baseMapper.deleteBatchIds(List.of(requirementIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdMaterialRequirementByRequirementId(Long requirementId) {
        deleteDetailByRequirementId(requirementId);
        return baseMapper.deleteById(requirementId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdMaterialRequirementDetails(List<PrdMaterialRequirementDetail> detailList) {
        int count = 0;
        for (PrdMaterialRequirementDetail detail : detailList) {
            count += detailMapper.insert(detail);
        }
        return count;
    }

    /**
     * Insert detail records for a requirement
     */
    private void insertPrdMaterialRequirementDetail(PrdMaterialRequirement prdMaterialRequirement) {
        List<PrdMaterialRequirementDetail> detailList = prdMaterialRequirement.getPrdMaterialRequirementDetailList();
        Long requirementId = prdMaterialRequirement.getRequirementId();
        if (detailList != null && !detailList.isEmpty()) {
            List<PrdMaterialRequirementDetail> list = new ArrayList<>();
            for (PrdMaterialRequirementDetail detail : detailList) {
                detail.setRequirementId(requirementId);
                list.add(detail);
            }
            if (!list.isEmpty()) {
                insertPrdMaterialRequirementDetails(list);
            }
        }
    }

    /**
     * Delete details by requirement ID
     */
    private void deleteDetailByRequirementId(Long requirementId) {
        if (requirementId != null) {
            LambdaQueryWrapper<PrdMaterialRequirementDetail> lqw = Wrappers.lambdaQuery();
            lqw.eq(PrdMaterialRequirementDetail::getRequirementId, requirementId);
            detailMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<PrdMaterialRequirement> buildQueryWrapper(PrdMaterialRequirementBo bo) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRequirementNo()), PrdMaterialRequirement::getRequirementNo, bo.getRequirementNo());
        lqw.eq(bo.getPlanId() != null, PrdMaterialRequirement::getPlanId, bo.getPlanId());
        lqw.eq(StringUtils.isNotBlank(bo.getPlanNo()), PrdMaterialRequirement::getPlanNo, bo.getPlanNo());
        lqw.eq(bo.getBomId() != null, PrdMaterialRequirement::getBomId, bo.getBomId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdMaterialRequirement::getStatus, bo.getStatus());
        lqw.eq(bo.getPlannerId() != null, PrdMaterialRequirement::getPlannerId, bo.getPlannerId());
        lqw.like(StringUtils.isNotBlank(bo.getPlannerName()), PrdMaterialRequirement::getPlannerName, bo.getPlannerName());
        lqw.eq(bo.getDepartmentId() != null, PrdMaterialRequirement::getDepartmentId, bo.getDepartmentId());
        lqw.like(StringUtils.isNotBlank(bo.getDepartmentName()), PrdMaterialRequirement::getDepartmentName, bo.getDepartmentName());
        return lqw;
    }

    private LambdaQueryWrapper<PrdMaterialRequirement> buildEntityQueryWrapper(PrdMaterialRequirement entity) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getRequirementNo()), PrdMaterialRequirement::getRequirementNo, entity.getRequirementNo());
        lqw.eq(entity.getPlanId() != null, PrdMaterialRequirement::getPlanId, entity.getPlanId());
        lqw.eq(StringUtils.isNotBlank(entity.getPlanNo()), PrdMaterialRequirement::getPlanNo, entity.getPlanNo());
        lqw.eq(entity.getBomId() != null, PrdMaterialRequirement::getBomId, entity.getBomId());
        lqw.eq(StringUtils.isNotBlank(entity.getStatus()), PrdMaterialRequirement::getStatus, entity.getStatus());
        lqw.eq(entity.getPlannerId() != null, PrdMaterialRequirement::getPlannerId, entity.getPlannerId());
        lqw.like(StringUtils.isNotBlank(entity.getPlannerName()), PrdMaterialRequirement::getPlannerName, entity.getPlannerName());
        lqw.eq(entity.getDepartmentId() != null, PrdMaterialRequirement::getDepartmentId, entity.getDepartmentId());
        lqw.like(StringUtils.isNotBlank(entity.getDepartmentName()), PrdMaterialRequirement::getDepartmentName, entity.getDepartmentName());
        return lqw;
    }
}
