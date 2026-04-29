package org.dromara.workflow.service;


import org.dromara.workflow.domain.vo.WfDemoVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.workflow.domain.bo.WfDemoBo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collection;
import java.util.List;

/**
 * DEMOService接口
 *
 * @author nbacheng
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
