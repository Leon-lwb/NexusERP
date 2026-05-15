package org.dromara.workflow.service.impl;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.common.constant.ProcessConstants;
import org.dromara.common.flowable.common.enums.FormType;
import org.dromara.common.flowable.factory.FlowServiceFactory;
import org.dromara.common.flowable.utils.ModelUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.workflow.domain.bo.WfFlowConfigBo;
import org.dromara.workflow.domain.bo.WfModelBo;
import org.dromara.workflow.domain.dto.WfMetaInfoDto;
import org.dromara.workflow.domain.vo.WfAppTypeVo;
import org.dromara.workflow.domain.vo.WfFlowConfigVo;
import org.dromara.workflow.domain.vo.WfFormVo;
import org.dromara.workflow.domain.vo.WfModelVo;
import org.dromara.workflow.mapper.WfCategoryMapper;
import org.dromara.workflow.service.IWfCustomFormService;
import org.dromara.workflow.service.IWfDeployFormService;
import org.dromara.workflow.service.IWfFlowConfigService;
import org.dromara.workflow.service.IWfFormService;
import org.dromara.workflow.service.IWfModelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ObjectUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ModelQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.bpmn.model.Process;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author wanghuan
 * @createTime 2022/6/21 9:11
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class WfModelServiceImpl extends FlowServiceFactory implements IWfModelService {

    private final RepositoryService repositoryService;

    private final IWfFormService formService;
    private final IWfDeployFormService deployFormService;
    private final WfCategoryMapper categoryMapper;
    private final IWfCustomFormService customFormService;
    private final IWfFlowConfigService flowConfigService;

    @Override
    public TableDataInfo<WfModelVo> list(WfModelBo modelBo, PageQuery pageQuery) {
        ModelQuery modelQuery = repositoryService.createModelQuery()
        		.modelTenantId(TenantHelper.getTenantId())
        		.latestVersion().orderByCreateTime().desc();
        // 构建查询条件
        if (StringUtils.isNotBlank(modelBo.getModelKey())) {
            modelQuery.modelKey(modelBo.getModelKey());
        }
        if (StringUtils.isNotBlank(modelBo.getModelName())) {
            modelQuery.modelNameLike("%" + modelBo.getModelName() + "%");
        }
        if (StringUtils.isNotBlank(modelBo.getCategory())) {
            modelQuery.modelCategory(modelBo.getCategory());
        }
        // 执行查询
        long pageTotal = modelQuery.count();
        if (pageTotal <= 0) {
            return TableDataInfo.build();
        }
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<Model> modelList = modelQuery.listPage(offset, pageQuery.getPageSize());
        List<WfModelVo> modelVoList = new ArrayList<>(modelList.size());
        modelList.forEach(model -> {
            WfModelVo modelVo = new WfModelVo();
            modelVo.setModelId(model.getId());
            modelVo.setModelName(model.getName());
            modelVo.setModelKey(model.getKey());
            modelVo.setCategory(model.getCategory());
            modelVo.setCreateTime(model.getCreateTime());
            modelVo.setVersion(model.getVersion());
            WfMetaInfoDto metaInfo = JsonUtils.parseObject(model.getMetaInfo(), WfMetaInfoDto.class);
            if (metaInfo != null) {
                modelVo.setDescription(metaInfo.getDescription());
                modelVo.setFormType(metaInfo.getFormType());
                modelVo.setFormId(metaInfo.getFormId());
            }
            modelVoList.add(modelVo);
        });
        Page<WfModelVo> page = new Page<>();
        page.setRecords(modelVoList);
        page.setTotal(pageTotal);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WfModelVo> list(WfModelBo modelBo) {
        ModelQuery modelQuery = repositoryService.createModelQuery()
        		.modelTenantId(TenantHelper.getTenantId())
        		.latestVersion().orderByCreateTime().desc();
        // 构建查询条件
        if (StringUtils.isNotBlank(modelBo.getModelKey())) {
            modelQuery.modelKey(modelBo.getModelKey());
        }
        if (StringUtils.isNotBlank(modelBo.getModelName())) {
            modelQuery.modelNameLike("%" + modelBo.getModelName() + "%");
        }
        if (StringUtils.isNotBlank(modelBo.getCategory())) {
            modelQuery.modelCategory(modelBo.getCategory());
        }
        List<Model> modelList = modelQuery.list();
        List<WfModelVo> modelVoList = new ArrayList<>(modelList.size());
        modelList.forEach(model -> {
            WfModelVo modelVo = new WfModelVo();
            modelVo.setModelId(model.getId());
            modelVo.setModelName(model.getName());
            modelVo.setModelKey(model.getKey());
            modelVo.setCategory(model.getCategory());
            modelVo.setCreateTime(model.getCreateTime());
            modelVo.setVersion(model.getVersion());
            WfMetaInfoDto metaInfo = JsonUtils.parseObject(model.getMetaInfo(), WfMetaInfoDto.class);
            if (metaInfo != null) {
                modelVo.setDescription(metaInfo.getDescription());
                modelVo.setFormType(metaInfo.getFormType());
                modelVo.setFormId(metaInfo.getFormId());
            }
            modelVoList.add(modelVo);
        });
        return modelVoList;
    }

    @Override
    public TableDataInfo<WfModelVo> historyList(WfModelBo modelBo, PageQuery pageQuery) {
        ModelQuery modelQuery = repositoryService.createModelQuery()
        	.modelTenantId(TenantHelper.getTenantId())
            .modelKey(modelBo.getModelKey())
            .orderByModelVersion()
            .desc();
        // 执行查询（不显示最新版，-1）
        long pageTotal = modelQuery.count() - 1;
        if (pageTotal <= 0) {
            return TableDataInfo.build();
        }
        // offset+1，去掉最新版
        int offset = 1 + pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<Model> modelList = modelQuery.listPage(offset, pageQuery.getPageSize());
        List<WfModelVo> modelVoList = new ArrayList<>(modelList.size());
        modelList.forEach(model -> {
            WfModelVo modelVo = new WfModelVo();
            modelVo.setModelId(model.getId());
            modelVo.setModelName(model.getName());
            modelVo.setModelKey(model.getKey());
            modelVo.setCategory(model.getCategory());
            modelVo.setCreateTime(model.getCreateTime());
            modelVo.setVersion(model.getVersion());
            WfMetaInfoDto metaInfo = JsonUtils.parseObject(model.getMetaInfo(), WfMetaInfoDto.class);
            if (metaInfo != null) {
                modelVo.setDescription(metaInfo.getDescription());
                modelVo.setFormType(metaInfo.getFormType());
                modelVo.setFormId(metaInfo.getFormId());
            }
            modelVoList.add(modelVo);
        });
        Page<WfModelVo> page = new Page<>();
        page.setRecords(modelVoList);
        page.setTotal(pageTotal);
        return TableDataInfo.build(page);
    }

    @Override
    public WfModelVo getModel(String modelId) {
        // 获取流程模型
        Model model = repositoryService.getModel(modelId);
        if (ObjectUtil.isNull(model)) {
            throw new RuntimeException("流程模型不存在！");
        }
        // 获取流程图
        String bpmnXml = queryBpmnXmlById(modelId);
        WfModelVo modelVo = new WfModelVo();
        modelVo.setModelId(model.getId());
        modelVo.setModelName(model.getName());
        modelVo.setModelKey(model.getKey());
        modelVo.setCategory(model.getCategory());
        modelVo.setCreateTime(model.getCreateTime());
        modelVo.setVersion(model.getVersion());
        modelVo.setBpmnXml(bpmnXml);
        WfMetaInfoDto metaInfo = JsonUtils.parseObject(model.getMetaInfo(), WfMetaInfoDto.class);
        if (metaInfo != null) {
            modelVo.setDescription(metaInfo.getDescription());
            modelVo.setFormType(metaInfo.getFormType());
            modelVo.setFormId(metaInfo.getFormId());
            if (FormType.PROCESS.getType().equals(metaInfo.getFormType())) {
                WfFormVo wfFormVo = formService.queryById(metaInfo.getFormId());
                modelVo.setContent(wfFormVo.getContent());
            }
        }
        return modelVo;
    }

    @Override
    public String queryBpmnXmlById(String modelId) {
        byte[] bpmnBytes = repositoryService.getModelEditorSource(modelId);
        return StrUtil.utf8Str(bpmnBytes);
    }

    @Override
    public void insertModel(WfModelBo modelBo) {
        Model model = repositoryService.newModel();
        model.setName(modelBo.getModelName());
        model.setKey(modelBo.getModelKey());
        model.setCategory(modelBo.getCategory());
        String metaInfo = buildMetaInfo(new WfMetaInfoDto(), modelBo.getDescription());
        model.setMetaInfo(metaInfo);
        model.setTenantId(TenantHelper.getTenantId());
        // 保存流程模型
        repositoryService.saveModel(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(WfModelBo modelBo) {
        // 根据模型Key查询模型信息
        Model model = repositoryService.getModel(modelBo.getModelId());
        if (ObjectUtil.isNull(model)) {
            throw new RuntimeException("流程模型不存在！");
        }
        model.setCategory(modelBo.getCategory());
        WfMetaInfoDto metaInfoDto = JsonUtils.parseObject(model.getMetaInfo(), WfMetaInfoDto.class);
        String metaInfo = buildMetaInfo(metaInfoDto, modelBo.getDescription());
        model.setMetaInfo(metaInfo);
        // 保存流程模型
        repositoryService.saveModel(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveModel(WfModelBo modelBo) {
        // 查询模型信息
        Model model = repositoryService.getModel(modelBo.getModelId());
        if (ObjectUtil.isNull(model)) {
            throw new RuntimeException("流程模型不存在！");
        }
        // 检查 BPMN XML 是否为空
        if (StrUtil.isBlank(modelBo.getBpmnXml())) {
            throw new RuntimeException("BPMN XML 不能为空！");
        }
        BpmnModel bpmnModel;
        try {
            bpmnModel = ModelUtils.getBpmnModel(modelBo.getBpmnXml());
        } catch (Exception e) {
            log.error("解析 BPMN XML 失败: {}", e.getMessage());
            log.error("BPMN XML 内容: {}", modelBo.getBpmnXml());
            throw new RuntimeException("Error reading XML: " + e.getMessage());
        }
        if (ObjectUtil.isEmpty(bpmnModel)) {
            throw new RuntimeException("获取模型设计失败！");
        }
        String processName = bpmnModel.getMainProcess().getName();
        // 获取开始节点
        StartEvent startEvent = ModelUtils.getStartEvent(bpmnModel);
        if (ObjectUtil.isNull(startEvent)) {
            throw new RuntimeException("开始节点不存在，请检查流程设计是否有误！");
        }
        WfAppTypeVo appTypeVo = categoryMapper.selectAppTypeVoByCode(model.getCategory(),TenantHelper.getTenantId());
        String appType = "";
        if(ObjectUtil.isNotEmpty(appTypeVo)) {
        	appType =  appTypeVo.getId();
        }
        // 获取开始节点配置的表单Key
        if (StrUtil.equalsAnyIgnoreCase(appType, "OA") && StrUtil.isBlank(startEvent.getFormKey())) {
            throw new RuntimeException("应用类型为OA必须配置流程表单");
        }
      //查看开始节点的后一个任务节点出口
        List<SequenceFlow> outgoingFlows = startEvent.getOutgoingFlows();
		if(Objects.isNull(outgoingFlows)) {
			throw new RuntimeException("导入失败，流程配置错误！");
        }
        //遍历返回下一个节点信息
        for (SequenceFlow outgoingFlow : outgoingFlows) {
            //类型自己判断（获取下个节点是任务节点）
            FlowElement targetFlowElement = outgoingFlow.getTargetFlowElement();
            //下个是节点
           if(targetFlowElement instanceof UserTask){// 下个出口是用户任务，而且是要发起人节点才让保存

        	   if(StringUtils.equals(((UserTask) targetFlowElement).getAssignee(), "${initiator}"))
        	   {
        		   break;
        	    }
        	   else {
        		   throw new RuntimeException("导入失败，流程第一个用户任务节点必须是发起人节点");
        	   }
            }
        }
        Model newModel;
        if (Boolean.TRUE.equals(modelBo.getNewVersion())) {
            newModel = repositoryService.newModel();
            newModel.setName(processName);
            newModel.setKey(model.getKey());
            newModel.setCategory(model.getCategory());
            newModel.setMetaInfo(model.getMetaInfo());
            newModel.setVersion(model.getVersion() + 1);
            newModel.setTenantId(TenantHelper.getTenantId());
        } else {
            newModel = model;
            // 设置流程名称
            newModel.setName(processName);
        }
        // 保存流程模型
        repositoryService.saveModel(newModel);
        // 保存 BPMN XML
        byte[] bpmnXmlBytes = StringUtils.getBytes(modelBo.getBpmnXml(), StandardCharsets.UTF_8);
        repositoryService.addModelEditorSource(newModel.getId(), bpmnXmlBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void latestModel(String modelId) {
        // 获取流程模型
        Model model = repositoryService.getModel(modelId);
        if (ObjectUtil.isNull(model)) {
            throw new RuntimeException("流程模型不存在！");
        }
        Integer latestVersion = repositoryService.createModelQuery()
            .modelKey(model.getKey())
            .latestVersion()
            .singleResult()
            .getVersion();
        if (model.getVersion().equals(latestVersion)) {
            throw new RuntimeException("当前版本已是最新版！");
        }
        // 获取 BPMN XML
        byte[] bpmnBytes = repositoryService.getModelEditorSource(modelId);
        Model newModel = repositoryService.newModel();
        newModel.setName(model.getName());
        newModel.setKey(model.getKey());
        newModel.setCategory(model.getCategory());
        newModel.setMetaInfo(model.getMetaInfo());
        newModel.setVersion(latestVersion + 1);
        newModel.setTenantId(model.getTenantId());
        // 保存流程模型
        repositoryService.saveModel(newModel);
        // 保存 BPMN XML
        repositoryService.addModelEditorSource(newModel.getId(), bpmnBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<String> ids) {
        ids.forEach(id -> {
            Model model = repositoryService.getModel(id);
            if (ObjectUtil.isNull(model)) {
                throw new RuntimeException("流程模型不存在！");
            }
            repositoryService.deleteModel(id);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deployModel(String modelId) {
        // 获取流程模型
        Model model = repositoryService.getModel(modelId);
        if (ObjectUtil.isNull(model)) {
            throw new RuntimeException("流程模型不存在！");
        }
        // 获取流程图
        byte[] bpmnBytes = repositoryService.getModelEditorSource(modelId);
        if (ArrayUtil.isEmpty(bpmnBytes)) {
            throw new RuntimeException("请先设计流程图！");
        }
        String bpmnXml = StringUtils.toEncodedString(bpmnBytes, StandardCharsets.UTF_8);
        BpmnModel bpmnModel = ModelUtils.getBpmnModel(bpmnXml);
        String processName = model.getName() + ProcessConstants.SUFFIX;
        // 部署流程
        Deployment deployment = repositoryService.createDeployment()
            .name(model.getName())
            .key(model.getKey())
            .tenantId(TenantHelper.getTenantId())
            .category(model.getCategory())
            .addBytes(processName, bpmnBytes)
            .deploy();
        ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
            .deploymentId(deployment.getId())
            .singleResult();
        // 修改流程定义的分类，便于搜索流程
        repositoryService.setProcessDefinitionCategory(procDef.getId(), model.getCategory());
        // 保存部署表单
        WfAppTypeVo appTypeVo = categoryMapper.selectAppTypeVoByCode(model.getCategory(),TenantHelper.getTenantId());
        String appType = "";
        if(ObjectUtil.isNotEmpty(appTypeVo)) {
        	appType =  appTypeVo.getId();
        }
        if(StrUtil.equalsAnyIgnoreCase(appType, "OA")) {
        	return deployFormService.saveInternalDeployForm(deployment.getId(), bpmnModel);
        } else if(StrUtil.equalsAnyIgnoreCase(appType, "ZDYYW")) {
        	return customFormService.saveCustomDeployForm(procDef.getKey(), deployment.getId(), deployment.getName(), bpmnModel);
        }
        else {//对于其它流程应用类型，可以不挂接表单，通过其它方式挂接
        	return true;
        }
    }

    /**
     * 构建模型扩展信息
     * @return
     */
    private String buildMetaInfo(WfMetaInfoDto metaInfo, String description) {
        // 只有非空，才进行设置，避免更新时的覆盖
        if (StringUtils.isNotEmpty(description)) {
            metaInfo.setDescription(description);
        }
        if (StringUtils.isNotEmpty(metaInfo.getCreateUser())) {
            metaInfo.setCreateUser(LoginHelper.getUsername());
        }
        return JsonUtils.toJsonString(metaInfo);
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String configModel(String modelId, String appType) {
		// 获取流程模型
        Model model = repositoryService.getModel(modelId);
        if (ObjectUtil.isNull(model)) {
            throw new RuntimeException("流程模型不存在！");
        }
        // 获取流程图
        byte[] bpmnBytes = repositoryService.getModelEditorSource(modelId);
        if (ArrayUtil.isEmpty(bpmnBytes)) {
            throw new RuntimeException("请先设计流程图！");
        }
        String bpmnXml = StringUtils.toEncodedString(bpmnBytes, StandardCharsets.UTF_8);
        BpmnModel bpmnModel = ModelUtils.getBpmnModel(bpmnXml);
        String processName = model.getName();
        Process process = bpmnModel.getMainProcess();
        Collection<FlowElement> flowElements = process.getFlowElements();
        for (FlowElement flowElement : flowElements) {
        	WfFlowConfigBo flowConfigBo = new WfFlowConfigBo();
        	if (flowElement instanceof StartEvent) {
        		StartEvent startEvent =  (StartEvent)flowElement;
        		WfFlowConfigVo flowConfigVo = new WfFlowConfigVo();
        		flowConfigVo.setModelId(modelId);
        		flowConfigVo.setNodeKey(startEvent.getId());
            	if(ObjectUtils.isEmpty(flowConfigService.selectByModelIdAndNodeKey(flowConfigVo))) {
            		flowConfigBo.setModelId(modelId);
        		    flowConfigBo.setAppType(appType);
        		    flowConfigBo.setFormKey(startEvent.getFormKey());
        		    flowConfigBo.setNodeName(startEvent.getName());
        		    flowConfigBo.setNodeKey(startEvent.getId());
        		    flowConfigService.insertByBo(flowConfigBo);
            	}
            	else {
            		flowConfigVo.setAppType(appType);
            		flowConfigVo.setFormKey(startEvent.getFormKey());
            		flowConfigVo.setNodeName(startEvent.getName());
          		    flowConfigService.updateFlowConfig(flowConfigVo);
            	}
        	}
            if (flowElement instanceof UserTask) {
                UserTask userTask = (UserTask)flowElement;
                WfFlowConfigVo flowConfigVo = new WfFlowConfigVo();
        		flowConfigVo.setModelId(modelId);
        		flowConfigVo.setNodeKey(userTask.getId());
            	if(ObjectUtils.isEmpty(flowConfigService.selectByModelIdAndNodeKey(flowConfigVo))) {
            		 flowConfigBo.setModelId(modelId);
            		 flowConfigBo.setAppType(appType);
            		 flowConfigBo.setFormKey(userTask.getFormKey());
            		 flowConfigBo.setNodeName(userTask.getName());
            		 flowConfigBo.setNodeKey(userTask.getId());
            		 flowConfigService.insertByBo(flowConfigBo);
            	}
            	else {
            		 flowConfigVo.setAppType(appType);
            		 flowConfigVo.setFormKey(userTask.getFormKey());
            		 flowConfigVo.setNodeName(userTask.getName());
          		     flowConfigService.updateFlowConfig(flowConfigVo);
            	}
            }
        }
        return modelId;
	}
}
