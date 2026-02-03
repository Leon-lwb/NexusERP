package com.nexus.workflow.service;

import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.workflow.domain.WfCustomForm;
import com.nexus.workflow.domain.bo.WfCustomFormBo;
import com.nexus.workflow.domain.vo.CustomFormVo;
import com.nexus.workflow.domain.vo.WfCustomFormVo;
import org.flowable.bpmn.model.BpmnModel;

import java.util.Collection;
import java.util.List;

/**
 * 流程业务单Service接口
 *
 * @author nbacheng
 * @date 2023-10-09
 */
public interface IWfCustomFormService {

    /**
     * 查询流程业务单
     */
    WfCustomFormVo queryById(Long id);

    /**
     * 查询流程业务单列表
     */
    WFTableDataInfo<WfCustomFormVo> queryPageList(WfCustomFormBo bo, PageQuery pageQuery);

    /**
     * 查询流程业务单列表
     */
    List<WfCustomFormVo> queryList(WfCustomFormBo bo);

    /**
     * 新增流程业务单
     */
    Boolean insertByBo(WfCustomFormBo bo);

    /**
     * 修改流程业务单
     */
    Boolean updateByBo(WfCustomFormBo bo);

    /**
     * 校验并批量删除流程业务单信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    void updateCustom(CustomFormVo customFormVo);

	List<WfCustomForm> selectSysCustomFormByServiceName(String serviceName);
	WfCustomFormBo selectSysCustomFormByDeployId(String deployId);
	/**
     * 保存流程实例关联自定义业务表单
     * @param procDefId 流程定义ID
     * @param deployId 部署ID
     * @param deployName 部署名称
     * @param bpmnModel bpmnModel对象
     * @return
     */
    boolean saveCustomDeployForm(String procDefKey, String deployId, String deployName, BpmnModel bpmnModel);
}
