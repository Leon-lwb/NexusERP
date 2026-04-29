package org.dromara.workflow.service;

import org.dromara.workflow.domain.WfNform;
import org.dromara.workflow.domain.bo.WfNformBo;
import org.dromara.workflow.domain.vo.WfNformVo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
    import org.dromara.common.mybatis.core.page.PageQuery;

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
        TableDataInfo<WfNformVo> queryPageList(WfNformBo bo, PageQuery pageQuery);

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
