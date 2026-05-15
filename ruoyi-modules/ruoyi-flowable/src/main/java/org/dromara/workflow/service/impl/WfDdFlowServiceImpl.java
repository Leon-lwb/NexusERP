package org.dromara.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.workflow.domain.WfDdFlow;
import org.dromara.workflow.domain.bo.WfDdFlowBo;
import org.dromara.workflow.domain.vo.WfDdFlowVo;
import org.dromara.workflow.mapper.WfDdFlowMapper;
import org.dromara.workflow.service.IWfDdFlowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 钉钉流程Service业务层处理
 *
 * @author wanghuan
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
    public TableDataInfo<WfDdFlowVo> queryPageList(WfDdFlowBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfDdFlow> lqw = buildQueryWrapper(bo);
        Page<WfDdFlowVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
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
