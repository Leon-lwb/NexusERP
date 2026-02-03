package com.nexus.workflow.service.impl;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.core.domain.ProcessQuery;
import com.nexus.common.utils.ProcessUtils;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
//import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfDeployForm;
import com.nexus.workflow.domain.vo.WfDeployVo;
import com.nexus.workflow.mapper.WfCategoryMapper;
import com.nexus.workflow.mapper.WfDeployFormMapper;
import com.nexus.workflow.service.IWfDeployService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author nbacheng
 * @createTime 2022/6/30 9:04
 */
@RequiredArgsConstructor
@Service
public class WfDeployServiceImpl implements IWfDeployService {

    private final RepositoryService repositoryService;
    private final WfDeployFormMapper deployFormMapper;
    private final WfCategoryMapper categoryMapper;

    @Override
    public WFTableDataInfo<WfDeployVo> queryPageList(ProcessQuery processQuery, PageQuery pageQuery) {
        // 流程定义列表数据查询
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery()
        	//.processDefinitionTenantId(TenantHelper.getTenantId())
            .latestVersion()
            .orderByProcessDefinitionKey()
            .asc();
        // 构建搜索条件
        ProcessUtils.buildProcessSearch(processDefinitionQuery, processQuery);
        long pageTotal = processDefinitionQuery.count();
        if (pageTotal <= 0) {
            return WFTableDataInfo.build();
        }
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<ProcessDefinition> definitionList = processDefinitionQuery.listPage(offset, pageQuery.getPageSize());

        List<WfDeployVo> deployVoList = new ArrayList<>(definitionList.size());
        for (ProcessDefinition processDefinition : definitionList) {
        	if( StringUtils.isNotBlank(processQuery.getAppType())) {
        		if ( processQuery.getAppType().equalsIgnoreCase(categoryMapper.selectAppTypeByCode(processDefinition.getCategory(),""))) {
	        		String deploymentId = processDefinition.getDeploymentId();
		            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
		            WfDeployVo vo = new WfDeployVo();
		            vo.setDefinitionId(processDefinition.getId());
		            vo.setProcessKey(processDefinition.getKey());
		            vo.setProcessName(processDefinition.getName());
		            vo.setVersion(processDefinition.getVersion());
		            vo.setCategory(processDefinition.getCategory());
		            //vo.setAppType(categoryMapper.selectAppTypeByCode(processDefinition.getCategory(),TenantHelper.getTenantId()));
		            vo.setDeploymentId(processDefinition.getDeploymentId());
		            vo.setSuspended(processDefinition.isSuspended());
		            // 流程部署信息
		            vo.setCategory(deployment.getCategory());
		            vo.setDeploymentTime(deployment.getDeploymentTime());
		            deployVoList.add(vo);
        		}	
        	}
        	else {
	            String deploymentId = processDefinition.getDeploymentId();
	            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
	            WfDeployVo vo = new WfDeployVo();
	            vo.setDefinitionId(processDefinition.getId());
	            vo.setProcessKey(processDefinition.getKey());
	            vo.setProcessName(processDefinition.getName());
	            vo.setVersion(processDefinition.getVersion());
	            vo.setCategory(processDefinition.getCategory());
	            //vo.setAppType(categoryMapper.selectAppTypeByCode(processDefinition.getCategory(),TenantHelper.getTenantId()));
	            vo.setDeploymentId(processDefinition.getDeploymentId());
	            vo.setSuspended(processDefinition.isSuspended());
	            // 流程部署信息
	            vo.setCategory(deployment.getCategory());
	            vo.setDeploymentTime(deployment.getDeploymentTime());
	            deployVoList.add(vo);
        	}
            
        }
        Page<WfDeployVo> page = new Page<>();
        page.setRecords(deployVoList);
        page.setTotal(pageTotal);
        return WFTableDataInfo.build(page);
    }

    @Override
    public WFTableDataInfo<WfDeployVo> queryPublishList(String processKey, PageQuery pageQuery) {
        // 创建查询条件
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery()
        	//.processDefinitionTenantId(TenantHelper.getTenantId())
            .processDefinitionKey(processKey)
            .orderByProcessDefinitionVersion()
            .desc();
        long pageTotal = processDefinitionQuery.count();
        if (pageTotal <= 0) {
            return WFTableDataInfo.build();
        }
        // 根据查询条件，查询所有版本
        int offset = pageQuery.getPageSize() * (pageQuery.getPageNum() - 1);
        List<ProcessDefinition> processDefinitionList = processDefinitionQuery
            .listPage(offset, pageQuery.getPageSize());
        List<WfDeployVo> deployVoList = processDefinitionList.stream().map(item -> {
            WfDeployVo vo = new WfDeployVo();
            vo.setDefinitionId(item.getId());
            vo.setProcessKey(item.getKey());
            vo.setProcessName(item.getName());
            vo.setVersion(item.getVersion());
            vo.setCategory(item.getCategory());
            vo.setDeploymentId(item.getDeploymentId());
            vo.setSuspended(item.isSuspended());
            return vo;
        }).collect(Collectors.toList());
        Page<WfDeployVo> page = new Page<>();
        page.setRecords(deployVoList);
        page.setTotal(pageTotal);
        return WFTableDataInfo.build(page);
    }

    /**
     * 激活或挂起流程
     *
     * @param state 状态
     * @param definitionId 流程定义ID
     */
    @Override
    public void updateState(String definitionId, String state) {
        if (SuspensionState.ACTIVE.toString().equals(state)) {
            // 激活
            repositoryService.activateProcessDefinitionById(definitionId, true, null);
        } else if (SuspensionState.SUSPENDED.toString().equals(state)) {
            // 挂起
            repositoryService.suspendProcessDefinitionById(definitionId, true, null);
        }
    }

    @Override
    public String queryBpmnXmlById(String definitionId) {
        InputStream inputStream = repositoryService.getProcessModel(definitionId);
        try {
            return IoUtil.readUtf8(inputStream);
        } catch (IORuntimeException exception) {
            throw new RuntimeException("加载xml文件异常");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<String> deployIds) {
        for (String deployId : deployIds) {
            repositoryService.deleteDeployment(deployId, true);
            deployFormMapper.delete(new LambdaQueryWrapper<WfDeployForm>().eq(WfDeployForm::getDeployId, deployId));
        }
    }
}
