package com.nexus.workflow.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.workflow.domain.WfMyBusiness;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *业务模块调用API的集合
 *@author wanghuan,wanghuan
 *@date 2022/11/02
 *@version 1.0
 */
@Slf4j
@Service
public class WfCommonService {

	// 统一按接口注入：getByDataId/delete 等方法均已声明在接口上，
	// 避免业务方依赖具体实现类（原先注入 Impl 类导致实现替换/代理事务受限）
	@Autowired
    IWfMyBusinessService wfMyBusinessService;

	@Autowired
	IWfInstanceService wfInstanceService;

    /**
     * 初始生成或修改业务与流程的关联信息<br/>
     * 当业务模块新增一条数据后调用，此时业务数据关联一个流程定义，以备后续流程使用
     * @return 是否成功
     * @param title 必填。流程业务简要描述。例：2021年11月26日xxxxx申请
     * @param dataId 必填。业务数据Id，如果是一对多业务关系，传入主表的数据Id
     * @param serviceImplName 必填。业务service注入spring容器的名称。
*                        例如：@Service("demoService")则传入 demoService
     * @param processDefinitionKey 必填。流程定义Key，传入此值，未来启动的会是该类流程的最新一个版本
     * @param processDefinitionId 选填。流程定义Id，传入此值，未来启动的为指定版本的流程
     */
    public boolean initActBusiness(String title,String dataId, String serviceImplName, String processDefinitionKey, String processDefinitionId,String routeName){
        boolean hasBlank = StrUtil.hasBlank(title,dataId, serviceImplName, processDefinitionKey);
        if (hasBlank) throw new ServiceException("流程关键参数未填完全！dataId, serviceImplName, processDefinitionKey");
        LambdaQueryWrapper<WfMyBusiness> flowMyBusinessLambdaQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessLambdaQueryWrapper.eq(WfMyBusiness::getDataId, dataId);
        WfMyBusiness wfMyBusiness = new WfMyBusiness();
        WfMyBusiness business = wfMyBusinessService.getOne(flowMyBusinessLambdaQueryWrapper);
        if (business!=null){
        	wfMyBusiness = business;
        }
        if (processDefinitionId==null){
            // 流程定义ID为空则初始化失败（原实现调用 R.fail(...) 无返回值，既不返回也不抛异常，属逻辑漏洞）
            throw new ServiceException("自定义表单未关联流程定义表或流程定义未关联自定义表单");
        }
        wfMyBusiness.setTitle(title);
        wfMyBusiness.setDataId(dataId);
        wfMyBusiness.setServiceImplName(serviceImplName);
        wfMyBusiness.setProcessDefinitionKey(processDefinitionKey);
        wfMyBusiness.setProcessDefinitionId(processDefinitionId);
        wfMyBusiness.setRouteName(routeName);
        if (business!=null){
            return wfMyBusinessService.updateById(wfMyBusiness);
        } else {
            return wfMyBusinessService.save(wfMyBusiness);
        }
    }

    /**
     * 删除流程
     * @param dataId
     * @return
     */
    public boolean delActBusiness(String dataId){
        boolean hasBlank = StrUtil.hasBlank(dataId);
        if (hasBlank) throw new ServiceException("流程关键参数未填完全！dataId");
        LambdaQueryWrapper<WfMyBusiness> flowMyBusinessQueryWrapper = new LambdaQueryWrapper<>();
        flowMyBusinessQueryWrapper.eq(WfMyBusiness::getDataId,dataId);
        // 脏数据下同一 dataId 可能存在多行，getOne 会抛异常；无行时返回 null 需跳过实例删除
        WfMyBusiness one = wfMyBusinessService.getOne(flowMyBusinessQueryWrapper, false);
        if (one != null && one.getProcessInstanceId()!=null){
            try {
                wfInstanceService.delete(one.getProcessInstanceId(),"删除流程",dataId);
            } catch (Exception e) {
                // 实例删除失败不阻断业务关联删除，但必须留痕，便于补偿清理孤立实例
                log.error("删除流程实例失败, dataId={}, processInstanceId={}", dataId, one.getProcessInstanceId(), e);
            }
        }
        return wfMyBusinessService.remove(flowMyBusinessQueryWrapper);
    }

}
