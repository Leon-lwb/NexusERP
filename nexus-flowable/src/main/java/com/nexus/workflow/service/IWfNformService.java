package com.nexus.workflow.service;

import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.workflow.domain.WfNform;
import com.nexus.workflow.domain.bo.WfNformBo;
import com.nexus.workflow.domain.vo.WfNformVo;

import java.util.Collection;
import java.util.List;

/**
 * 设计器单Service接口
 *
 * @author nbacheng
 * @date 2025-04-29
 */
public interface IWfNformService {

    /**
     * 查询设计器单
     */
        WfNform queryById(Long formId);

        /**
         * 查询设计器单列表
         */
        WFTableDataInfo<WfNformVo> queryPageList(WfNformBo bo, PageQuery pageQuery);

    /**
     * 查询设计器单列表
     */
    List<WfNformVo> queryList(WfNformBo bo);

    /**
     * 新增设计器单
     */
    Boolean insertByBo(WfNformBo bo);

    /**
     * 修改设计器单
     */
    Boolean updateByBo(WfNformBo bo);

    /**
     * 校验并批量删除设计器单信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}
