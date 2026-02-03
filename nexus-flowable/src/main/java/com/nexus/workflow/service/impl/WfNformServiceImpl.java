package com.nexus.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.utils.MapstructUtils;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.workflow.domain.WfNform;
import com.nexus.workflow.domain.bo.WfNformBo;
import com.nexus.workflow.domain.vo.WfNformVo;
import com.nexus.workflow.mapper.WfNformMapper;
import com.nexus.workflow.service.IWfNformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 设计器单Service业务层处理
 *
 * @author nbacheng
 * @date 2025-04-29
 */
@RequiredArgsConstructor
@Service
public class WfNformServiceImpl implements IWfNformService {

    private final WfNformMapper baseMapper;

    /**
     * 查询设计器单
     */
    @Override
    public WfNform queryById(Long formId) {
        WfNform wfNform = baseMapper.selectById(formId);
        return wfNform;
    }

        /**
         * 查询设计器单列表
         */
        @Override
        public WFTableDataInfo<WfNformVo> queryPageList(WfNformBo bo, PageQuery pageQuery) {
            LambdaQueryWrapper<WfNform> lqw = buildQueryWrapper(bo);
            Page<WfNformVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
            return WFTableDataInfo.build(result);
        }

    /**
     * 查询设计器单列表
     */
    @Override
    public List<WfNformVo> queryList(WfNformBo bo) {
        LambdaQueryWrapper<WfNform> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfNform> buildQueryWrapper(WfNformBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfNform> lqw = Wrappers.lambdaQuery();
                    lqw.like(StringUtils.isNotBlank(bo.getFormName()), WfNform::getFormName, bo.getFormName());
                    lqw.eq(StringUtils.isNotBlank(bo.getFormDesc()), WfNform::getFormDesc, bo.getFormDesc());
                    lqw.eq(StringUtils.isNotBlank(bo.getFormContent()), WfNform::getFormContent, bo.getFormContent());
        return lqw;
    }

    /**
     * 新增设计器单
     */
    @Override
    public Boolean insertByBo(WfNformBo bo) {
        WfNform add = MapstructUtils.convert(bo, WfNform.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
        }
        return flag;
    }

    /**
 * 修改设计器单
 */
@Override
public Boolean updateByBo(WfNformBo bo) {
    WfNform update = MapstructUtils.convert(bo, WfNform.class);
    validEntityBeforeSave(update);

    // 更新主表
    boolean flag = baseMapper.updateById(update) > 0;


    return flag;
}

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(WfNform entity) {
        // TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除设计器单
     */
    @Transactional
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

}
