package com.nexus.workflow.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.workflow.domain.bo.WfCopyBo;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.domain.vo.WfCopyVo;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 流程抄送Service接口
 *
 * @author wanghuan
 * @date 2022-05-19
 */
public interface IWfCopyService {

    /**
     * 查询流程抄送
     *
     * @param copyId 流程抄送主键
     * @return 流程抄送
     */
    WfCopyVo queryById(Long copyId);

    /**
     * 查询流程抄送列表
     *
     * @param wfCopy 流程抄送
     * @return 流程抄送集合
     */
    TableDataInfo<WfCopyVo> selectPageList(WfCopyBo wfCopy, PageQuery pageQuery);

    /**
     * 查询流程抄送列表
     *
     * @param wfCopy 流程抄送
     * @return 流程抄送集合
     */
    List<WfCopyVo> selectList(WfCopyBo wfCopy);

    /**
     * 抄送
     * @param taskBo
     * @return
     */
    Boolean makeCopy(WfTaskBo taskBo);

	/**
	 * 查询我的流程抄送列表
	 *
	 * @param bo 流程抄送
	 * @return 流程抄送
	 */
	TableDataInfo<WfCopyVo> selectMyPageList(WfCopyBo bo, PageQuery pageQuery);

	 /**
     * 更新为已读状态
     * @param id
     * @return
     */
	public void updateStatus(@Param("id") String id);
}
