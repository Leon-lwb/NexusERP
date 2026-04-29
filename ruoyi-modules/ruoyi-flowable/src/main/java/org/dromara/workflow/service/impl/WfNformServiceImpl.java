package org.dromara.workflow.service.impl;

import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.dromara.workflow.domain.WfNform;
import org.dromara.workflow.domain.bo.WfNformBo;
import org.dromara.workflow.domain.vo.WfNformVo;
import org.dromara.workflow.mapper.WfNformMapper;
import org.dromara.workflow.service.IWfNformService;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;

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
        public TableDataInfo<WfNformVo> queryPageList(WfNformBo bo, PageQuery pageQuery) {
            LambdaQueryWrapper<WfNform> lqw = buildQueryWrapper(bo);
            Page<WfNformVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
            return TableDataInfo.build(result);
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
