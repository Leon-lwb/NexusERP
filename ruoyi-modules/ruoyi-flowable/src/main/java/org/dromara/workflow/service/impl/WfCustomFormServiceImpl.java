package org.dromara.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.stereotype.Service;

import org.dromara.workflow.domain.bo.WfCustomFormBo;
import org.dromara.workflow.domain.vo.CustomFormVo;
import org.dromara.workflow.domain.vo.WfCustomFormVo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.utils.ModelUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.workflow.domain.WfCustomForm;
import org.dromara.workflow.mapper.WfCustomFormMapper;
import org.dromara.workflow.service.IWfCustomFormService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 流程业务单Service业务层处理
 *
 * @author nbacheng
 * @date 2023-10-09
 */
@RequiredArgsConstructor
@Service
public class WfCustomFormServiceImpl implements IWfCustomFormService {

	private final RepositoryService repositoryService;
    private final WfCustomFormMapper baseMapper;

    /**
     * 查询流程业务单
     */
    @Override
    public WfCustomFormVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询流程业务单列表
     */
    @Override
    public TableDataInfo<WfCustomFormVo> queryPageList(WfCustomFormBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfCustomForm> lqw = buildQueryWrapper(bo);
        Page<WfCustomFormVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询流程业务单列表
     */
    @Override
    public List<WfCustomFormVo> queryList(WfCustomFormBo bo) {
        LambdaQueryWrapper<WfCustomForm> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfCustomForm> buildQueryWrapper(WfCustomFormBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfCustomForm> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getBusinessName()), WfCustomForm::getBusinessName, bo.getBusinessName());
        lqw.eq(StringUtils.isNotBlank(bo.getBusinessService()), WfCustomForm::getBusinessService, bo.getBusinessService());
        lqw.like(StringUtils.isNotBlank(bo.getFlowName()), WfCustomForm::getFlowName, bo.getFlowName());
        lqw.eq(StringUtils.isNotBlank(bo.getDeployId()), WfCustomForm::getDeployId, bo.getDeployId());
        lqw.like(StringUtils.isNotBlank(bo.getRouteName()), WfCustomForm::getRouteName, bo.getRouteName());
        lqw.eq(StringUtils.isNotBlank(bo.getComponent()), WfCustomForm::getComponent, bo.getComponent());
        return lqw;
    }

    /**
     * 新增流程业务单
     */
    @Override
    public Boolean insertByBo(WfCustomFormBo bo) {
        WfCustomForm add = BeanUtil.toBean(bo, WfCustomForm.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改流程业务单
     */
    @Override
    public Boolean updateByBo(WfCustomFormBo bo) {
        WfCustomForm update = BeanUtil.toBean(bo, WfCustomForm.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(WfCustomForm entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除流程业务单
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteBatchIds(ids) > 0;
    }

	@Override
	public void updateCustom(CustomFormVo customFormVo) {
		baseMapper.updateCustom(customFormVo);
	}

	@Override
	public List<WfCustomForm> selectSysCustomFormByServiceName(String serviceName) {
		return baseMapper.selectSysCustomFormByServiceName(serviceName);
	}

	@Override
	public boolean saveCustomDeployForm(String procDefKey, String deployId, String deployName, BpmnModel bpmnModel) {
        // 获取开始节点
        StartEvent startEvent = ModelUtils.getStartEvent(bpmnModel);
        if (ObjectUtil.isNull(startEvent)) {
            throw new RuntimeException("开始节点不存在，请检查流程设计是否有误！");
        }
        // 更新开始节点表单信息与流程信息到自定义业务关联表
        WfCustomFormBo customFormBo = buildCustomForm(procDefKey, deployId, deployName, startEvent);
        if (ObjectUtil.isNotNull(customFormBo)) {
        	updateByBo(customFormBo);
        	return true;
        }
		return false;
	}
	/**
     * 构建部署表单关联信息对象
     * @param procDefKey 流程定义Key
     * @param deployId 部署ID
     * @param deployName 部署名称ID
     * @param node 节点信息
     * @return 部署表单关联对象。若无表单信息（formKey），则返回null
     */
    private WfCustomFormBo buildCustomForm(String procDefKey, String deployId, String deployName, FlowNode node) {
        
    	//获取业务流程关联表信息,以便更新
    	// 创建流程查询条件
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(procDefKey)
            .orderByProcessDefinitionVersion()
            .desc();
        long defTotal = processDefinitionQuery.count();
        if (defTotal > 1L) {//有老版本,搜索业务流程关联表
        	List<ProcessDefinition> definitionList = processDefinitionQuery.list();
        	//获取上一个版本的流程发布id
        	String preDeployId = definitionList.get(1).getDeploymentId();
        	WfCustomFormBo wfCustomFormbo = selectSysCustomFormByDeployId(preDeployId);
        	if (ObjectUtil.isNotEmpty(wfCustomFormbo)) {//找到有就用最新的deployId进行更新
        		wfCustomFormbo.setDeployId(deployId);
        		updateByBo(wfCustomFormbo);
        	}
        }
    	//获取流程模型的表单信息
    	String formKey = ModelUtils.getFormKey(node);
        if (StringUtils.isEmpty(formKey)) {
            return null;
        }
        Long formId = Convert.toLong(StringUtils.substringAfter(formKey, "key_"));
        WfCustomFormVo customFormVo = queryById(formId);
        if (ObjectUtil.isNull(customFormVo)) {
            throw new ServiceException("表单信息查询错误");
        }
        WfCustomFormBo customFormBo = new WfCustomFormBo();
        customFormBo.setId(formId);
        customFormBo.setBusinessName(customFormVo.getBusinessName());
        customFormBo.setBusinessService(customFormVo.getBusinessService());
        customFormBo.setCreateBy(customFormBo.getCreateBy());
        customFormBo.setRouteName(customFormVo.getRouteName());
        customFormBo.setDeployId(deployId);
        customFormBo.setFlowName(deployName);
        return customFormBo;
    }

	@Override
	public WfCustomFormBo selectSysCustomFormByDeployId(String deployId) {
		return baseMapper.selectSysCustomFormByDeployId(deployId);
	}
}
