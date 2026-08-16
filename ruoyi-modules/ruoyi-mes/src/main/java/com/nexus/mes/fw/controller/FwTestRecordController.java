package com.nexus.mes.fw.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.fw.domain.bo.FwTestRecordBo;
import com.nexus.mes.fw.domain.vo.FwTestRecordVo;
import com.nexus.mes.fw.service.IFwTestRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MES测试记录 Controller (SN级全检)
 * <p>开单(beginTest)自动匹配测试项落待测行, 填报(report)批量提交并自动判定 PASS/FAIL。</p>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/fw/test-record")
public class FwTestRecordController extends BaseController {

    private final IFwTestRecordService fwTestRecordService;

    /**
     * 分页查询测试记录
     */
    @SaCheckPermission("mes:fw:testrecord:list")
    @GetMapping("/list")
    public TableDataInfo<FwTestRecordVo> list(FwTestRecordBo bo, PageQuery pageQuery) {
        return fwTestRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取测试记录详情
     */
    @SaCheckPermission("mes:fw:testrecord:query")
    @GetMapping("/{testRecordId}")
    public R<FwTestRecordVo> getInfo(@PathVariable Long testRecordId) {
        return R.ok(fwTestRecordService.selectById(testRecordId));
    }

    /**
     * 按测试单号查询全部行(详情/追溯)
     */
    @SaCheckPermission("mes:fw:testrecord:query")
    @GetMapping("/test-no/{testNo}")
    public R<List<FwTestRecordVo>> byTestNo(@PathVariable String testNo) {
        return R.ok(fwTestRecordService.selectByTestNo(testNo));
    }

    /**
     * 开单: SN 匹配测试项落待测行, 自动生成测试单号
     */
    @SaCheckPermission("mes:fw:testrecord:add")
    @Log(title = "测试开单", businessType = BusinessType.INSERT)
    @PostMapping("/begin")
    public R<List<FwTestRecordVo>> begin(@Validated @RequestBody FwTestRecordBo bo) {
        return R.ok(fwTestRecordService.beginTest(bo));
    }

    /**
     * 填报: 批量提交实测值并自动判定
     */
    @SaCheckPermission("mes:fw:testrecord:add")
    @Log(title = "测试填报", businessType = BusinessType.UPDATE)
    @PostMapping("/report")
    public R<List<FwTestRecordVo>> report(@Validated @RequestBody List<FwTestRecordBo> rows) {
        return R.ok(fwTestRecordService.reportTestRows(rows));
    }

    /**
     * 批量删除测试记录行(仅未判定草稿行可删)
     */
    @SaCheckPermission("mes:fw:testrecord:remove")
    @Log(title = "测试记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(fwTestRecordService.deleteByIds(ids));
    }

    /**
     * 测试统计概览
     */
    @SaCheckPermission("mes:fw:testrecord:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(FwTestRecordBo bo) {
        return R.ok(fwTestRecordService.summary(bo));
    }
}