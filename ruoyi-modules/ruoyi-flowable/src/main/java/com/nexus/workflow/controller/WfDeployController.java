package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.common.flowable.core.domain.ProcessQuery;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.workflow.domain.vo.WfDeployVo;
import com.nexus.workflow.domain.vo.WfFormVo;
import com.nexus.workflow.service.IWfDeployFormService;
import com.nexus.workflow.service.IWfDeployService;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * 流程部署
 * <p>提供流程部署列表、版本列表、激活/挂起、BPMN XML 读取、删除及关联表单查询等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/24 20:57
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/deploy")
public class WfDeployController extends BaseController {

    /** 流程部署业务服务，提供部署分页、版本、状态变更及 BPMN XML 读取等能力。 */
    private final IWfDeployService deployService;
    /** 部署表单关联服务，用于查询部署关联的业务表单。 */
    private final IWfDeployFormService deployFormService;

    /**
     * 查询流程部署列表。
     *
     * @param processQuery 流程查询条件
     * @param pageQuery    分页参数（页码、每页大小、排序）
     * @return 分页结果，包含流程部署列表及分页元信息
     */
    @SaCheckPermission("workflow:deploy:list")
    @GetMapping("/list")
    public TableDataInfo<WfDeployVo> list(ProcessQuery processQuery, PageQuery pageQuery) {
        return deployService.queryPageList(processQuery, pageQuery);
    }

    /**
     * 查询流程部署版本列表。
     *
     * @param processKey 流程定义 KEY
     * @param pageQuery  分页参数（页码、每页大小、排序）
     * @return 分页结果，包含指定流程的版本部署列表及分页元信息
     */
    @SaCheckPermission("workflow:deploy:list")
    @GetMapping("/publishList")
    public TableDataInfo<WfDeployVo> publishList(@RequestParam String processKey, PageQuery pageQuery) {
        return deployService.queryPublishList(processKey, pageQuery);
    }

    /**
     * 激活或挂起流程。
     *
     * @param state       状态（active：激活，suspended：挂起）
     * @param definitionId 流程定义 ID
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:deploy:state")
    @PutMapping(value = "/changeState")
    public R<Void> changeState(@RequestParam String state, @RequestParam String definitionId) {
        deployService.updateState(definitionId, state);
        return R.ok();
    }

    /**
     * 读取流程定义的 BPMN XML 内容。
     *
     * @param definitionId 流程定义 ID
     * @return 流程 BPMN XML 字符串
     */
    @SaCheckPermission("workflow:deploy:query")
    @GetMapping("/bpmnXml/{definitionId}")
    public R<String> getBpmnXml(@PathVariable(value = "definitionId") String definitionId) {
        return R.ok(null, deployService.queryBpmnXmlById(definitionId));
    }

    /**
     * 删除流程部署。
     *
     * @param deployIds 流程部署 ID 串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:deploy:remove")
    @Log(title = "删除流程部署", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deployIds}")
    public R<String> remove(@NotEmpty(message = "主键不能为空") @PathVariable String[] deployIds) {
        deployService.deleteByIds(Arrays.asList(deployIds));
        return R.ok();
    }

    /**
     * 查询流程部署关联表单信息。
     *
     * @param deployId 流程部署 ID
     * @return 部署关联的业务表单内容（JSON 对象）
     */
    @GetMapping("/form/{deployId}")
    public R<?> start(@PathVariable(value = "deployId") String deployId) {
        WfFormVo formVo = deployFormService.selectDeployFormByDeployId(deployId);
        if (Objects.isNull(formVo)) {
            return R.fail("请先配置流程表单");
        }
        return R.ok(JsonUtils.parseObject(formVo.getContent(), Map.class));
    }
}
