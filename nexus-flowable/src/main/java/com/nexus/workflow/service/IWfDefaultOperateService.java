package com.nexus.workflow.service;

import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.workflow.domain.bo.WfDefaultOperateBo;
import com.nexus.workflow.domain.vo.WfDefaultOperateVo;

import java.util.Collection;
import java.util.List;

/**
 * 流程默认操作Service接口
 *
 * @author nbacheng
 * @date 2023-11-23
 */
public interface IWfDefaultOperateService {

    /**
     * 查询流程默认操作
     */
    WfDefaultOperateVo queryById(Long id);

    /**
     * 查询流程默认操作列表
     */
    WFTableDataInfo<WfDefaultOperateVo> queryPageList(WfDefaultOperateBo bo, PageQuery pageQuery);

    /**
     * 查询流程默认操作列表
     */
    List<WfDefaultOperateVo> queryList(WfDefaultOperateBo bo);

    /**
     * 新增流程默认操作
     */
    Boolean insertByBo(WfDefaultOperateBo bo);

    /**
     * 修改流程默认操作
     */
    Boolean updateByBo(WfDefaultOperateBo bo);

    /**
     * 校验并批量删除流程默认操作信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
