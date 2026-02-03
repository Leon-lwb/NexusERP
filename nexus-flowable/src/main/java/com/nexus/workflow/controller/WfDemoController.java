package com.nexus.workflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.constant.UserConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.annotation.RepeatSubmit;
import com.nexus.common.annotation.Log;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.core.controller.BaseController;
import com.nexus.system.query.QueryGenerator;
import com.nexus.workflow.domain.bo.WfDemoBo;
import com.nexus.workflow.domain.vo.WfDemoVo;
import com.nexus.workflow.service.IWfDemoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DEMO
 *
 * @author nbacheng
 * @date 2023-10-12
 */
@Validated
@RequiredArgsConstructor
@RestController
@Slf4j
@RequestMapping("/workflow/demo")
public class WfDemoController extends BaseController {

    private final IWfDemoService iWfDemoService;

    /**
     * 查询DEMO列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:demo:list')")
    @GetMapping("/list")
    public WFTableDataInfo<WfDemoVo> list(WfDemoVo vo, PageQuery pageQuery, HttpServletRequest req) {
    	//by nbacheng for java.sql.SQLSyntaxErrorException: ORA-00918: 未明确定义列
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
	    return WFTableDataInfo.build(result);
    }

    /**
     * 导出DEMO列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:demo:export')")
    @Log(title = "DEMO", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WfDemoBo bo, HttpServletResponse response) {
        List<WfDemoVo> list = iWfDemoService.queryList(bo);
        ExcelUtil<WfDemoVo> util = new ExcelUtil<>(WfDemoVo.class);
        util.exportExcel(response, list, "DEMO", "DEMO");
    }

    /**
     * 获取DEMO详细信息
     *
     * @param demoId 主键
     */
    @PreAuthorize("@ss.hasPermi('workflow:demo:query')")
    @GetMapping("/{demoId}")
    public R<WfDemoVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long demoId) {
        return R.ok(iWfDemoService.queryById(demoId));
    }

    /**
     * 新增DEMO
     */
    @PreAuthorize("@ss.hasPermi('workflow:demo:add')")
    @Log(title = "DEMO", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody WfDemoBo bo) {
        return iWfDemoService.insertByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 修改DEMO
     */
    @PreAuthorize("@ss.hasPermi('workflow:demo:edit')")
    @Log(title = "DEMO", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody WfDemoBo bo) {
        return iWfDemoService.updateByBo(bo) ? R.ok() : R.fail();
    }

    /**
     * 删除DEMO
     *
     * @param demoIds 主键串
     */
    @PreAuthorize("@ss.hasPermi('workflow:demo:remove')")
    @Log(title = "DEMO", businessType = BusinessType.DELETE)
    @DeleteMapping("/{demoIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] demoIds) {
        return iWfDemoService.deleteWithValidByIds(Arrays.asList(demoIds), true) ? R.ok() : R.fail();
    }
}
