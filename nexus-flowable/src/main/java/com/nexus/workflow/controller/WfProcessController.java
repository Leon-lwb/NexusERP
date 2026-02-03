package com.nexus.workflow.controller;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import com.nexus.common.core.domain.ProcessQuery;
import com.nexus.common.core.domain.R;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.utils.MapstructUtils;
import com.nexus.common.annotation.RepeatSubmit;
import com.nexus.common.annotation.Log;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
//import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.core.controller.BaseController;
import com.nexus.workflow.domain.bo.WfCopyBo;
import com.nexus.workflow.domain.vo.*;
import com.nexus.workflow.service.IWfCopyService;
import com.nexus.workflow.service.IWfProcessService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流流程管理
 *
 * @author nbacheng
 * @createTime 2022/3/24 18:54
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/process")
public class WfProcessController extends BaseController {

    private final IWfProcessService processService;
    private final IWfCopyService copyService;

    /**
     * 查询可发起流程列表
     *
     * @param pageQuery 分页参数
     */
    @GetMapping(value = "/list")
    @PreAuthorize("@ss.hasPermi('workflow:process:startList')")
    public WFTableDataInfo<WfDefinitionVo> startProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageStartProcessList(processQuery, pageQuery);
    }

    /**
     * 我拥有的流程
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:ownList')")
    @GetMapping(value = "/ownList")
    public WFTableDataInfo<WfTaskVo> ownProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageOwnProcessList(processQuery, pageQuery);
    }

    /**
     * 获取待办列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:todoList')")
    @GetMapping(value = "/todoList")
    public WFTableDataInfo<WfTaskVo> todoProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageTodoProcessList(processQuery, pageQuery);
    }

    /**
     * 获取待签列表
     *
     * @param processQuery 流程业务对象
     * @param pageQuery 分页参数
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:claimList')")
    @GetMapping(value = "/claimList")
    public WFTableDataInfo<WfTaskVo> claimProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageClaimProcessList(processQuery, pageQuery);
    }

    /**
     * 获取已办列表
     *
     * @param pageQuery 分页参数
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:finishedList')")
    @GetMapping(value = "/finishedList")
    public WFTableDataInfo<WfTaskVo> finishedProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageFinishedProcessList(processQuery, pageQuery);
    }

    /**
     * 获取抄送列表
     *
     * @param copyBo 流程抄送对象
     * @param pageQuery 分页参数
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:copyList')")
    @GetMapping(value = "/copyList")
    public WFTableDataInfo<WfCopyVo> copyProcessList(WfCopyBo copyBo, PageQuery pageQuery) {
        //copyBo.setUserId(LoginHelper.getUserId());
        return copyService.selectPageList(copyBo, pageQuery);
    }
    
    /**
     * 获取我的抄送列表
     *
     * @param copyBo 流程抄送对象
     * @param pageQuery 分页参数
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:myCopyList')")
    @GetMapping(value = "/myCopyList")
    public WFTableDataInfo<WfCopyVo> myCopyProcessList(WfCopyBo copyBo, PageQuery pageQuery) {
        //copyBo.setUserId(LoginHelper.getUserId());
        return copyService.selectMyPageList(copyBo, pageQuery);
    }

    /**
     *更新抄送状态为已读
     *
     * @param id 
     * 
     */
    @GetMapping(value = "/updateViewStatust")
    public R<Object> updateViewStatus(@RequestParam(name = "id",required = false) String id) {
    	copyService.updateStatus(id);
        return R.ok("更新成功!");
    }
    
    
    /**
     * 导出可发起流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:startExport')")
    @Log(title = "可发起流程", businessType = BusinessType.EXPORT)
    @PostMapping("/startExport")
    public void startExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfDefinitionVo> list = processService.selectStartProcessList(processQuery);
        List<WfDefinitionExportVo> listVo = MapstructUtils.convert(list, WfDefinitionExportVo.class);
        ExcelUtil<WfDefinitionExportVo> util = new ExcelUtil<>(WfDefinitionExportVo.class);
        util.exportExcel(response, listVo, "可发起流程", "可发起流程");
    }

    /**
     * 导出我拥有流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:ownExport')")
    @Log(title = "我拥有流程", businessType = BusinessType.EXPORT)
    @PostMapping("/ownExport")
    public void ownExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectOwnProcessList(processQuery);
        List<WfOwnTaskExportVo> listVo = MapstructUtils.convert(list, WfOwnTaskExportVo.class);
        for (WfOwnTaskExportVo exportVo : listVo) {
            exportVo.setStatus(ObjectUtil.isNull(exportVo.getFinishTime()) ? "进行中" : "已完成");
        }
        ExcelUtil<WfOwnTaskExportVo> util = new ExcelUtil<>(WfOwnTaskExportVo.class);
        util.exportExcel(response, listVo, "我拥有流程", "我拥有流程");
    }

    /**
     * 导出待办流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:todoExport')")
    @Log(title = "待办流程", businessType = BusinessType.EXPORT)
    @PostMapping("/todoExport")
    public void todoExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectTodoProcessList(processQuery);
        List<WfTodoTaskExportVo> listVo = MapstructUtils.convert(list, WfTodoTaskExportVo.class);
        ExcelUtil<WfTodoTaskExportVo> util = new ExcelUtil<>(WfTodoTaskExportVo.class);
        util.exportExcel(response, listVo, "待办流程", "待办流程");
    }

    /**
     * 导出待签流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:claimExport')")
    @Log(title = "待签流程", businessType = BusinessType.EXPORT)
    @PostMapping("/claimExport")
    public void claimExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectClaimProcessList(processQuery);
        List<WfClaimTaskExportVo> listVo = MapstructUtils.convert(list, WfClaimTaskExportVo.class);
        ExcelUtil<WfClaimTaskExportVo> util = new ExcelUtil<>(WfClaimTaskExportVo.class);
        util.exportExcel(response, listVo, "待签流程", "待签流程");
    }

    /**
     * 导出已办流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:finishedExport')")
    @Log(title = "已办流程", businessType = BusinessType.EXPORT)
    @PostMapping("/finishedExport")
    public void finishedExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectFinishedProcessList(processQuery);
        List<WfFinishedTaskExportVo> listVo = MapstructUtils.convert(list, WfFinishedTaskExportVo.class);
        ExcelUtil<WfFinishedTaskExportVo> util = new ExcelUtil<>(WfFinishedTaskExportVo.class);
        util.exportExcel(response, listVo, "已办流程", "已办流程");
    }

    /**
     * 导出抄送流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:copyExport')")
    @Log(title = "抄送流程", businessType = BusinessType.EXPORT)
    @PostMapping("/copyExport")
    public void copyExport(WfCopyBo copyBo, HttpServletResponse response) {
        //copyBo.setUserId(LoginHelper.getUserId());
        List<WfCopyVo> list = copyService.selectList(copyBo);
        List<WfCopyExportVo> listVo = MapstructUtils.convert(list, WfCopyExportVo.class);
        ExcelUtil<WfCopyExportVo> util = new ExcelUtil<>(WfCopyExportVo.class);
        util.exportExcel(response, listVo, "抄送流程", "抄送流程");
    }

    /**
     * 查询流程部署关联表单信息
     *
     * @param definitionId 流程定义id
     * @param deployId 流程部署id
     */
    @GetMapping("/getProcessForm")
    @PreAuthorize("@ss.hasPermi('workflow:process:start')")
    public R<?> getForm(@RequestParam(value = "definitionId") String definitionId,
                        @RequestParam(value = "deployId") String deployId,
                        @RequestParam(value = "procInsId", required = false) String procInsId) {
        return R.ok(processService.selectFormContent(definitionId, deployId, procInsId));
    }

    /**
     * 根据流程定义id启动流程实例
     *
     * @param processDefId 流程定义id
     * @param variables 变量集合,json对象
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:start')")
    @PostMapping("/start/{processDefId}")
    public R<Void> start(@PathVariable(value = "processDefId") String processDefId, @RequestBody Map<String, Object> variables) {
    	return processService.startProcessByDefId(processDefId, variables);

    }
    
    /**
     * 根据业务数据Id和服务名启动流程实例
     *
     * @param dataId, serviceName
     * @param variables 变量集合,json对象
     */
    @PreAuthorize("@ss.hasPermi('workflow:process:start')")
    @RepeatSubmit()
    @PostMapping("/startByDataId/{dataId}/{selectFlowId}/{serviceName}")
    public R<Void> startByDataId(@PathVariable(value = "dataId") String dataId,
    		                     @PathVariable(value = "selectFlowId") String selectFlowId,
                                 @PathVariable(value = "serviceName") String serviceName,
                                 @RequestBody Map<String, Object> variables) {
    	variables.put("dataId",dataId);
        return processService.startProcessByDataId(dataId, selectFlowId, serviceName, variables);

    }

    /**
     * 删除流程实例
     *
     * @param instanceIds 流程实例ID串
     */
    @DeleteMapping("/instance/{instanceIds}")
    public R<Void> delete(@PathVariable String[] instanceIds) {
        processService.deleteProcessByIds(instanceIds);
        return R.ok();
    }

    /**
     * 读取xml文件
     * @param processDefId 流程定义ID
     */
    @GetMapping("/bpmnXml/{processDefId}")
    public R<String> getBpmnXml(@PathVariable(value = "processDefId") String processDefId) {
        return R.ok(null, processService.queryBpmnXmlById(processDefId));
    }

    /**
     * 查询流程详情信息
     *
     * @param procInsId 流程实例ID
     * @param taskId 任务ID
     * @param dataId 业务数据ID
     */
    @GetMapping("/detail")
    public R detail(String procInsId, String taskId, String dataId) {
        return R.ok(processService.queryProcessDetail(procInsId, taskId, dataId));
    }
    
    /**
     * 查询流程详情信息
     *
     * @param dataId 任务ID
     */
    @GetMapping("/detailbydataid")
    public R detail(String dataId) {
        return R.ok(processService.queryProcessDetailByDataId(dataId));
    }
    
    /**
     * 查询流程是否结束
     *
     * @param procInsId
     * @param 
     */
    @GetMapping("/iscompleted")
    public R processIscompleted(String procInsId) {
        return R.ok(processService.processIscompleted(procInsId));
    }
    
    /**
     * 根据钉钉流程json转flowable的bpmn的xml格式
     *
     * @param ddjson json对象
     * 
     */
    @PostMapping("/ddtobpmn")
    public R<String> ddToBpmn(@RequestBody String ddjson) {
    	return processService.dingdingToBpmn(ddjson);

    }
	
	/**
     * 根据当前服务名,组织与站点查询关联流程信息
     *
     * @param serviceName
     * @param 
     */
    @GetMapping("/getProcesss")
    public R getProcesss(String serviceName) {
        return R.ok(processService.getProcesssByServiceName(serviceName));
    }
}
