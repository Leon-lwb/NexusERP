package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.common.flowable.common.enums.ProcessStatus;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.service.WmCertifiedArrivalWorkflowPort;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Objects;

/**
 * 仅在干净上下文读取实际 master 关联及 Flowable 状态；不信关联行可被错配写入的 actStatus。
 * 引擎不可变 businessKey、tenant、定义及源变量均匹配后才给出状态；master 读取结束后再进入 ERP。
 * 不修改公共任务/流程发布器，不替代原办理人/签章门禁，不读取完整变量或用户凭证。
 */
@DS("master")
@Service
public class WmCertifiedArrivalWorkflowReader implements WmCertifiedArrivalWorkflowPort {
    private final WfMyBusinessServiceImpl business;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final TaskService tasks;
    public WmCertifiedArrivalWorkflowReader(WfMyBusinessServiceImpl business,RuntimeService runtime,HistoryService history,TaskService tasks){
        this.business=business;this.runtime=runtime;this.history=history;this.tasks=tasks;
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public Proof read(String tenantId,Long noticeId){
        need(TenantHelper.isEnable()&&tenantId!=null&&tenantId.matches("[0-9]{6,20}")&&tenantId.equals(TenantHelper.getTenantId())&&noticeId!=null&&noticeId>0,"通知流程租户/身份无效");
        String id=noticeId.toString();var relation=business.getByDataId(id);
        need(relation!=null&&id.equals(relation.getDataId())&&"wmCertifiedArrivalNoticeService".equals(relation.getServiceImplName())
            &&text(relation.getProcessInstanceId())&&text(relation.getProcessDefinitionId()),"缺少本通知实际流程关联");
        String process=relation.getProcessInstanceId();
        var archived=history.createHistoricProcessInstanceQuery().processInstanceId(process).singleResult();
        need(archived!=null&&process.equals(archived.getId())&&tenantId.equals(archived.getTenantId())
            &&id.equals(archived.getBusinessKey())&&relation.getProcessDefinitionId().equals(archived.getProcessDefinitionId()),"历史实例并非本通知/租户/定义");
        var active=runtime.createProcessInstanceQuery().processInstanceId(process).singleResult();
        if(active!=null)need(process.equals(active.getId())&&tenantId.equals(active.getTenantId())&&id.equals(active.getBusinessKey())
            &&relation.getProcessDefinitionId().equals(active.getProcessDefinitionId())&&archived.getEndTime()==null,"活动实例与本通知关联不一致");
        String dataId=value(process,"dataId",active!=null);
        String sourceNotice=value(process,"noticeId",active!=null);
        String code=value(process,"noticeCode",active!=null);
        need(id.equals(dataId)&&id.equals(sourceNotice)&&text(code),"本通知源 dataId/noticeId/businessCode 不一致");
        String status=value(process,ProcessConstants.PROCESS_STATUS_KEY,active!=null);String next;
        if(active!=null){
            need(ProcessStatus.RUNNING.getStatus().equals(status),"活动实例不能伪装完成/取消结果");next="APPROVING";
        }else{
            need(archived.getEndTime()!=null&&tasks.createTaskQuery().processInstanceId(process).count()==0,"原实例尚未实际结束或仍有待办");
            if(ProcessStatus.COMPLETED.getStatus().equals(status)){
                need(!text(archived.getDeleteReason()),"删除/异常结束不构成正常批准");next="APPROVED";
            }else if(ProcessStatus.TERMINATED.getStatus().equals(status))next="REJECTED";
            else if(ProcessStatus.CANCELED.getStatus().equals(status))next="DRAFT";
            else throw failure("缺少真实引擎最终状态，不能按关联行 pass 推断批准");
        }
        return new Proof(tenantId,id,code,process,relation.getProcessDefinitionId(),dataId,sourceNotice,next);
    }
    /** 只读必要源变量，不抓取 formData/完整变量或签章凭证。 */
    private String value(String process,String name,boolean active){
        Object value;
        if(active)value=runtime.getVariable(process,name);
        else{var variable=history.createHistoricVariableInstanceQuery().processInstanceId(process).variableName(name).singleResult();value=variable==null?null:variable.getValue();}
        return value==null?null:Objects.toString(value);
    }
    private static boolean text(String s){return s!=null&&!s.isBlank();}
    private static ServiceException failure(String reason){return new ServiceException("认证通知流程拒绝："+reason);}
    private static void need(boolean allowed,String reason){if(!allowed)throw failure(reason);}
}
