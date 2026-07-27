package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.ProStepGuide;
import com.nexus.mes.pro.domain.bo.ProStepGuideBo;
import com.nexus.mes.pro.domain.vo.ProStepGuideVo;
import com.nexus.mes.pro.mapper.ProStepGuideMapper;
import com.nexus.mes.pro.service.IProStepGuideService;
import com.nexus.mes.sop.domain.vo.SopProcessBindingVo;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * MES工序流程指引配置 Service 实现(P0-3)
 * <p>SOP_LINK 类型校验 sop_id 在 sop_process_binding 已绑定到同 process_id(防影子 SOP)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proStepGuideService")
public class ProStepGuideServiceImpl implements IProStepGuideService {

    private final ProStepGuideMapper baseMapper;
    private final ISopProcessBindingService sopBindingService;

    @Override
    public TableDataInfo<ProStepGuideVo> selectPageList(ProStepGuideBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProStepGuide> lqw = buildQueryWrapper(bo);
        Page<ProStepGuideVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public ProStepGuideVo selectById(Long guideId) {
        return baseMapper.selectVoById(guideId);
    }

    @Override
    public List<ProStepGuideVo> selectList(ProStepGuideBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ProStepGuideBo bo) {
        validateSopLink(bo);
        ProStepGuide guide = MapstructUtils.convert(bo, ProStepGuide.class);
        if (StringUtils.isBlank(guide.getEnabled())) {
            guide.setEnabled("1");
        }
        if (guide.getSortOrder() == null) {
            guide.setSortOrder(0);
        }
        int rows = baseMapper.insert(guide);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ProStepGuideBo bo) {
        validateSopLink(bo);
        ProStepGuide guide = MapstructUtils.convert(bo, ProStepGuide.class);
        int rows = baseMapper.updateById(guide);
        return rows > 0;
    }

    @Override
    public Boolean deleteByIds(Collection<Long> ids) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * SOP_LINK 类型校验: sop_id 须在 sop_process_binding 已绑定到同 process_id
     */
    private void validateSopLink(ProStepGuideBo bo) {
        if (!TYPE_SOP_LINK.equals(bo.getGuideType())) {
            return;
        }
        if (bo.getSopId() == null) {
            throw new RuntimeException("SOP_LINK 类型指引必须关联SOP文档");
        }
        List<SopProcessBindingVo> bindings = sopBindingService.selectByProcess(bo.getProcessId());
        boolean matched = bindings != null && bindings.stream()
            .anyMatch(b -> bo.getSopId().equals(b.getSopId()));
        if (!matched) {
            throw new RuntimeException("SOP文档未绑定到该工序,请先在SOP管理中绑定(sop_process_binding)");
        }
    }

    private LambdaQueryWrapper<ProStepGuide> buildQueryWrapper(ProStepGuideBo bo) {
        return Wrappers.<ProStepGuide>lambdaQuery()
            .eq(bo.getProcessId() != null, ProStepGuide::getProcessId, bo.getProcessId())
            .eq(StringUtils.isNotBlank(bo.getGuideType()), ProStepGuide::getGuideType, bo.getGuideType())
            .eq(StringUtils.isNotBlank(bo.getEnabled()), ProStepGuide::getEnabled, bo.getEnabled())
            .orderByAsc(ProStepGuide::getSortOrder);
    }
}
