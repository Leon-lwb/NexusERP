package com.nexus.workflow.service;


import com.nexus.workflow.domain.vo.WfDemoVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.workflow.domain.bo.WfDemoBo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collection;
import java.util.List;

/**
 * DEMOService接口
 *
 * @author wanghuan
 * @date 2023-10-12
 */
public interface IWfDemoService {

    /**
     * 查询DEMO
     */
    WfDemoVo queryById(Long demoId);

    /**
     * 查询DEMO列表
     */
    TableDataInfo<WfDemoVo> queryPageList(WfDemoBo bo, PageQuery pageQuery);

    /**
     * 查询DEMO列表
     */
    List<WfDemoVo> queryList(WfDemoBo bo);

    /**
     * 新增DEMO
     */
    Boolean insertByBo(WfDemoBo bo);

    /**
     * 修改DEMO
     */
    Boolean updateByBo(WfDemoBo bo);

    /**
     * 校验并批量删除DEMO信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    Page<WfDemoVo> myPage(Page<WfDemoVo> page, QueryWrapper<WfDemoVo> queryWrapper);
}
