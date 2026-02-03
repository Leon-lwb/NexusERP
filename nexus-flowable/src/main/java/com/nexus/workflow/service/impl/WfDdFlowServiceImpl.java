package com.nexus.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.workflow.domain.WfDdFlow;
import com.nexus.workflow.domain.bo.WfDdFlowBo;
import com.nexus.workflow.domain.vo.WfDdFlowVo;
import com.nexus.workflow.mapper.WfDdFlowMapper;
import com.nexus.workflow.service.IWfDdFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 钉钉流程Service业务层处理
 *
 * @author nbacheng
 * @date 2023-11-29
 */
@RequiredArgsConstructor
@Service
public class WfDdFlowServiceImpl implements IWfDdFlowService {

    private final WfDdFlowMapper baseMapper;

    /**
     * 查询钉钉流程
     */
    @Override
    public WfDdFlowVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询钉钉流程列表
     */
    @Override
    public WFTableDataInfo<WfDdFlowVo> queryPageList(WfDdFlowBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfDdFlow> lqw = buildQueryWrapper(bo);
        Page<WfDdFlowVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return WFTableDataInfo.build(result);
    }

    /**
     * 查询钉钉流程列表
     */
    @Override
    public List<WfDdFlowVo> queryList(WfDdFlowBo bo) {
        LambdaQueryWrapper<WfDdFlow> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfDdFlow> buildQueryWrapper(WfDdFlowBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfDdFlow> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getName()), WfDdFlow::getName, bo.getName());
        lqw.eq(StringUtils.isNotBlank(bo.getFlowData()), WfDdFlow::getFlowData, bo.getFlowData());
        return lqw;
    }

    /**
     * 新增钉钉流程
     */
    @Override
    public Boolean insertByBo(WfDdFlowBo bo) {
        WfDdFlow add = BeanUtil.toBean(bo, WfDdFlow.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改钉钉流程
     */
    @Override
    public Boolean updateByBo(WfDdFlowBo bo) {
        WfDdFlow update = BeanUtil.toBean(bo, WfDdFlow.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(WfDdFlow entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除钉钉流程
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteBatchIds(ids) > 0;
    }
}
