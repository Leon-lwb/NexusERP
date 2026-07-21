package com.nexus.workflow.controller;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.constant.UserConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.query.QueryGenerator;
import com.nexus.workflow.domain.vo.WfDemoVo;
import com.nexus.workflow.domain.bo.WfDemoBo;
import com.nexus.workflow.service.IWfDemoService;

/**
 * DEMO
 * <p>流程表单 DEMO 数据的增删改查与导出 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @date 2023-10-12
 */
@Validated
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/workflow/demo")
public class WfDemoController extends BaseController {

    /** DEMO 业务服务，提供分页、导出、增删改等能力。 */
    private final IWfDemoService iWfDemoService;

    /**
     * 查询 DEMO 列表（分页）。
     *
     * @param vo         DEMO 查询条件
     * @param pageQuery  分页参数（页码、每页大小、排序）
     * @param req        请求对象，用于修正排序字段避免列歧义
     * @return 分页结果，包含 DEMO 列表及分页元信息
     */
    @SaCheckPermission("workflow:demo:list")
    @GetMapping("/list")
    public TableDataInfo<WfDemoVo> list(WfDemoVo vo, PageQuery pageQuery, HttpServletRequest req) {
   	//by wanghuan for java.sql.SQLSyntaxErrorException: ORA-00918: 未明确定义列
		Map<String, String[]> ParameterMap = new HashMap<String, String[]>(req.getParameterMap());
		String[] column = new String[]{""};
		if(ParameterMap!=null&&  ParameterMap.containsKey("column")) {
			column[0] = ParameterMap.get("column")[0];
			column[0] = "t."+ column[0];
			ParameterMap.replace("column", column);
			log.info("修改的排序规则>>列:" + ParameterMap.get("column")[0]);
		}
		QueryWrapper<WfDemoVo> queryWrapper = QueryGenerator.initQueryWrapper(vo, ParameterMap);
		queryWrapper.eq("t.del_flag", UserConstants.USER_NORMAL);
		queryWrapper.orderByDesc("t.create_time");

		Page<WfDemoVo> page = new Page<WfDemoVo>(pageQuery.getPageNum(), pageQuery.getPageSize());
	    Page<WfDemoVo> result = iWfDemoService.myPage(page, queryWrapper);
	    return TableDataInfo.build(result);
    }

    /**
     * 导出 DEMO 列表。
     *
     * @param bo       DEMO 查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("workflow:demo:export")
    @Log(title = "DEMO", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDemoBo bo, HttpServletResponse response) {
        List<WfDemoVo> list = iWfDemoService.queryList(bo);
        ExcelUtil.exportExcel(list, "DEMO", WfDemoVo.class, response);
    }

    /**
     * 获取 DEMO 详细信息。
     *
     * @param demoId 主键
     * @return DEMO 详细信息
     */
    @SaCheckPermission("workflow:demo:query")
    @GetMapping("/{demoId}")
    public R<WfDemoVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long demoId) {
        return R.ok(iWfDemoService.queryById(demoId));
    }

    /**
     * 新增 DEMO。
     *
     * @param bo DEMO 新增数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:demo:add")
    @Log(title = "DEMO", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDemoBo bo) {
        return toAjax(iWfDemoService.insertByBo(bo));
    }

    /**
     * 修改 DEMO。
     *
     * @param bo DEMO 修改数据
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:demo:edit")
    @Log(title = "DEMO", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDemoBo bo) {
        return toAjax(iWfDemoService.updateByBo(bo));
    }

    /**
     * 删除 DEMO。
     *
     * @param demoIds 主键串
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("workflow:demo:remove")
    @Log(title = "DEMO", businessType = BusinessType.DELETE)
    @DeleteMapping("/{demoIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] demoIds) {
        return toAjax(iWfDemoService.deleteWithValidByIds(Arrays.asList(demoIds), true));
    }
}
