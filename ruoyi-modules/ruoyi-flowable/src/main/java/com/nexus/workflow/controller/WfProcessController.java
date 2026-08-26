package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.flowable.core.domain.ProcessQuery;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.bo.WfCopyBo;
import com.nexus.workflow.domain.vo.*;
import com.nexus.workflow.service.IWfCopyService;
import com.nexus.workflow.service.IWfProcessService;

import java.util.List;
import java.util.Map;

/**
 * 工作流流程管理
 * <p>提供流程定义/实例相关的各类列表查询、导出、启动、详情、流程图读取及钉钉转换等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/24 18:54
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/process")
public class WfProcessController extends BaseController {

    /** 流程业务服务，提供流程列表、启动、详情、导出等能力。 */
    private final IWfProcessService processService;
    /** 流程抄送业务服务，提供抄送列表查询与已读状态更新。 */
    private final IWfCopyService copyService;

    /**
     * 查询可发起流程列表。
     *
     * @param processQuery 流程查询条件
     * @param pageQuery    分页参数（页码、每页大小、排序）
     * @return 分页结果，包含可发起流程定义列表及分页元信息
     */
    @GetMapping(value = "/list")
    @SaCheckPermission("workflow:process:startList")
    public TableDataInfo<WfDefinitionVo> startProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageStartProcessList(processQuery, pageQuery);
    }

    /**
     * 查询我拥有的流程列表。
     *
     * @param processQuery 流程查询条件
     * @param pageQuery    分页参数（页码、每页大小、排序）
     * @return 分页结果，包含当前用户拥有的流程任务列表及分页元信息
     */
    @SaCheckPermission("workflow:process:ownList")
    @GetMapping(value = "/ownList")
    public TableDataInfo<WfTaskVo> ownProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageOwnProcessList(processQuery, pageQuery);
    }

    /**
     * 获取待办列表。
     *
     * @param processQuery 流程查询条件
     * @param pageQuery    分页参数（页码、每页大小、排序）
     * @return 分页结果，包含当前用户待办任务列表及分页元信息
     */
    @SaCheckPermission("workflow:process:todoList")
    @GetMapping(value = "/todoList")
    public TableDataInfo<WfTaskVo> todoProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageTodoProcessList(processQuery, pageQuery);
    }

    /**
     * 获取待签列表。
     *
     * @param processQuery 流程业务对象
     * @param pageQuery    分页参数（页码、每页大小、排序）
     * @return 分页结果，包含当前用户待签收任务列表及分页元信息
     */
    @SaCheckPermission("workflow:process:claimList")
    @GetMapping(value = "/claimList")
    public TableDataInfo<WfTaskVo> claimProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageClaimProcessList(processQuery, pageQuery);
    }

    /**
     * 获取已办列表。
     *
     * @param processQuery 流程查询条件
     * @param pageQuery    分页参数（页码、每页大小、排序）
     * @return 分页结果，包含当前用户已办任务列表及分页元信息
     */
    @SaCheckPermission("workflow:process:finishedList")
    @GetMapping(value = "/finishedList")
    public TableDataInfo<WfTaskVo> finishedProcessList(ProcessQuery processQuery, PageQuery pageQuery) {
        return processService.selectPageFinishedProcessList(processQuery, pageQuery);
    }

    /**
     * 获取抄送列表。
     *
     * @param copyBo    流程抄送对象
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含抄送任务列表及分页元信息
     */
    @SaCheckPermission("workflow:process:copyList")
    @GetMapping(value = "/copyList")
    public TableDataInfo<WfCopyVo> copyProcessList(WfCopyBo copyBo, PageQuery pageQuery) {
        copyBo.setUserId(LoginHelper.getUserId());
        return copyService.selectPageList(copyBo, pageQuery);
    }

    /**
     * 获取我的抄送列表。
     *
     * @param copyBo    流程抄送对象
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含我的抄送任务列表及分页元信息
     */
    @SaCheckPermission("workflow:process:myCopyList")
    @GetMapping(value = "/myCopyList")
    public TableDataInfo<WfCopyVo> myCopyProcessList(WfCopyBo copyBo, PageQuery pageQuery) {
        copyBo.setUserId(LoginHelper.getUserId());
        return copyService.selectMyPageList(copyBo, pageQuery);
    }

    /**
     * 更新抄送状态为已读（仅限本人抄送记录，归属校验在服务层）。
     *
     * @param id 抄送记录 ID
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:process:copyList")
    @GetMapping(value = "/updateViewStatust")
    public R<Object> updateViewStatus(@RequestParam(name = "id",required = false) String id) {
    	copyService.updateStatus(id);
        return R.ok("更新成功!");
    }


    /**
     * 导出可发起流程列表。
     *
     * @param processQuery 流程查询条件
     * @param response     HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:process:startExport")
    @Log(title = "可发起流程", businessType = BusinessType.EXPORT)
    @PostMapping("/startExport")
    public void startExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfDefinitionVo> list = processService.selectStartProcessList(processQuery);
        List<WfDefinitionExportVo> listVo = MapstructUtils.convert(list, WfDefinitionExportVo.class);
        ExcelUtil.exportExcel(listVo, "可发起流程", WfDefinitionExportVo.class, response);
    }

    /**
     * 导出我拥有流程列表。
     *
     * @param processQuery 流程查询条件
     * @param response     HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:process:ownExport")
    @Log(title = "我拥有流程", businessType = BusinessType.EXPORT)
    @PostMapping("/ownExport")
    public void ownExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectOwnProcessList(processQuery);
        List<WfOwnTaskExportVo> listVo = MapstructUtils.convert(list, WfOwnTaskExportVo.class);
        for (WfOwnTaskExportVo exportVo : listVo) {
            exportVo.setStatus(ObjectUtil.isNull(exportVo.getFinishTime()) ? "进行中" : "已完成");
        }
        ExcelUtil.exportExcel(listVo, "我拥有流程", WfOwnTaskExportVo.class, response);
    }

    /**
     * 导出待办流程列表。
     *
     * @param processQuery 流程查询条件
     * @param response     HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:process:todoExport")
    @Log(title = "待办流程", businessType = BusinessType.EXPORT)
    @PostMapping("/todoExport")
    public void todoExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectTodoProcessList(processQuery);
        List<WfTodoTaskExportVo> listVo = MapstructUtils.convert(list, WfTodoTaskExportVo.class);
        ExcelUtil.exportExcel(listVo, "待办流程", WfTodoTaskExportVo.class, response);
    }

    /**
     * 导出待签流程列表。
     *
     * @param processQuery 流程查询条件
     * @param response     HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:process:claimExport")
    @Log(title = "待签流程", businessType = BusinessType.EXPORT)
    @PostMapping("/claimExport")
    public void claimExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectClaimProcessList(processQuery);
        List<WfClaimTaskExportVo> listVo = MapstructUtils.convert(list, WfClaimTaskExportVo.class);
        ExcelUtil.exportExcel(listVo, "待签流程", WfClaimTaskExportVo.class, response);
    }

    /**
     * 导出已办流程列表。
     *
     * @param processQuery 流程查询条件
     * @param response     HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:process:finishedExport")
    @Log(title = "已办流程", businessType = BusinessType.EXPORT)
    @PostMapping("/finishedExport")
    public void finishedExport(@Validated ProcessQuery processQuery, HttpServletResponse response) {
        List<WfTaskVo> list = processService.selectFinishedProcessList(processQuery);
        List<WfFinishedTaskExportVo> listVo = MapstructUtils.convert(list, WfFinishedTaskExportVo.class);
        ExcelUtil.exportExcel(listVo, "已办流程", WfFinishedTaskExportVo.class, response);
    }

    /**
     * 导出抄送流程列表。
     *
     * @param copyBo   流程抄送对象
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:process:copyExport")
    @Log(title = "抄送流程", businessType = BusinessType.EXPORT)
    @PostMapping("/copyExport")
    public void copyExport(WfCopyBo copyBo, HttpServletResponse response) {
        copyBo.setUserId(LoginHelper.getUserId());
        List<WfCopyVo> list = copyService.selectList(copyBo);
        List<WfCopyExportVo> listVo = MapstructUtils.convert(list, WfCopyExportVo.class);
        ExcelUtil.exportExcel(listVo, "抄送流程", WfCopyExportVo.class, response);
    }

    /**
     * 查询流程部署关联表单信息。
     *
     * @param definitionId 流程定义 ID
     * @param deployId     流程部署 ID
     * @param procInsId    流程实例 ID（可选）
     * @return 流程表单内容（JSON 对象）
     */
    @GetMapping("/getProcessForm")
    @SaCheckPermission("workflow:process:start")
    public R<?> getForm(@RequestParam(value = "definitionId") String definitionId,
                        @RequestParam(value = "deployId") String deployId,
                        @RequestParam(value = "procInsId", required = false) String procInsId) {
        return R.ok(processService.selectFormContent(definitionId, deployId, procInsId));
    }

    /**
     * 根据流程定义 ID 启动流程实例。
     *
     * @param processDefId 流程定义 ID
     * @param variables    流程变量集合（JSON 对象）
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:process:start")
    @RepeatSubmit()
    @PostMapping("/start/{processDefId}")
    public R<Void> start(@PathVariable(value = "processDefId") String processDefId, @RequestBody Map<String, Object> variables) {
    	return processService.startProcessByDefId(processDefId, variables);

    }

    /**
     * 根据业务数据 ID 与服务名启动流程实例。
     *
     * @param dataId      业务数据 ID
     * @param selectFlowId 选择的流程 ID
     * @param serviceName 业务服务名称
     * @param variables   流程变量集合（JSON 对象）
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:process:start")
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
     * 删除流程实例。
     *
     * @param instanceIds 流程实例 ID 串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:process:remove")
    @DeleteMapping("/instance/{instanceIds}")
    public R<Void> delete(@PathVariable String[] instanceIds) {
        processService.deleteProcessByIds(instanceIds);
        return R.ok();
    }

    /**
     * 读取流程 BPMN XML 文件。
     *
     * @param processDefId 流程定义 ID
     * @return 流程 BPMN XML 字符串
     */
    @SaCheckPermission("workflow:process:query")
    @GetMapping("/bpmnXml/{processDefId}")
    public R<String> getBpmnXml(@PathVariable(value = "processDefId") String processDefId) {
        return R.ok(null, processService.queryBpmnXmlById(processDefId));
    }

    /**
     * 查询流程详情信息。
     *
     * @param procInsId 流程实例 ID
     * @param taskId    任务 ID
     * @param dataId    业务数据 ID
     * @return 流程详情信息
     */
    @SaCheckPermission("workflow:process:query")
    @GetMapping("/detail")
    public R detail(String procInsId, String taskId, String dataId) {
        return R.ok(processService.queryProcessDetail(procInsId, taskId, dataId));
    }

    /**
     * 根据业务数据 ID 查询流程详情信息。
     *
     * @param dataId 业务数据 ID
     * @return 流程详情信息
     */
    @SaCheckPermission("workflow:process:query")
    @GetMapping("/detailbydataid")
    public R detail(String dataId) {
        return R.ok(processService.queryProcessDetailByDataId(dataId));
    }

    /**
     * 查询流程是否已完成。
     *
     * @param procInsId 流程实例 ID
     * @return 是否已完成的布尔结果
     */
    @SaCheckPermission("workflow:process:query")
    @GetMapping("/iscompleted")
    public R processIscompleted(String procInsId) {
        return R.ok(processService.processIscompleted(procInsId));
    }

    /**
     * 将钉钉流程 JSON 转换为 Flowable 的 BPMN XML 格式。
     *
     * @param ddjson 钉钉流程 JSON 字符串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:model:edit")
    @PostMapping("/ddtobpmn")
    public R<Void> ddToBpmn(@RequestBody String ddjson) {
    	return processService.dingdingToBpmn(ddjson);

    }

	/**
     * 根据当前服务名查询关联流程信息。
     *
     * @param serviceName 业务服务名称
     * @return 关联流程信息
     */
    @SaCheckPermission("workflow:process:query")
    @GetMapping("/getProcesss")
    public R getProcesss(String serviceName) {
        return R.ok(processService.getProcesssByServiceName(serviceName));
    }
}
