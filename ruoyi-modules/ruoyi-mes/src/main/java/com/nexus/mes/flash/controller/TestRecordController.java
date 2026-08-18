package com.nexus.mes.flash.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.flash.domain.bo.TestRecordBo;
import com.nexus.mes.flash.domain.vo.TestRecordVo;
import com.nexus.mes.flash.service.ITestRecordService;
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
@RequestMapping("/mes/flash/test-record")
public class TestRecordController extends BaseController {

    private final ITestRecordService mesTestRecordService;

    /**
     * 分页查询测试记录
     */
    @SaCheckPermission("mes:flash:testrecord:list")
    @GetMapping("/list")
    public TableDataInfo<TestRecordVo> list(TestRecordBo bo, PageQuery pageQuery) {
        return mesTestRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取测试记录详情
     */
    @SaCheckPermission("mes:flash:testrecord:query")
    @GetMapping("/{testRecordId}")
    public R<TestRecordVo> getInfo(@PathVariable Long testRecordId) {
        return R.ok(mesTestRecordService.selectById(testRecordId));
    }

    /**
     * 按测试单号查询全部行(详情/追溯)
     */
    @SaCheckPermission("mes:flash:testrecord:query")
    @GetMapping("/test-no/{testNo}")
    public R<List<TestRecordVo>> byTestNo(@PathVariable String testNo) {
        return R.ok(mesTestRecordService.selectByTestNo(testNo));
    }

    /**
     * 开单: SN 匹配测试项落待测行, 自动生成测试单号
     */
    @SaCheckPermission("mes:flash:testrecord:add")
    @Log(title = "测试开单", businessType = BusinessType.INSERT)
    @PostMapping("/begin")
    public R<List<TestRecordVo>> begin(@Validated @RequestBody TestRecordBo bo) {
        return R.ok(mesTestRecordService.beginTest(bo));
    }

    /**
     * 填报: 批量提交实测值并自动判定
     */
    @SaCheckPermission("mes:flash:testrecord:add")
    @Log(title = "测试填报", businessType = BusinessType.UPDATE)
    @PostMapping("/report")
    public R<List<TestRecordVo>> report(@Validated @RequestBody List<TestRecordBo> rows) {
        return R.ok(mesTestRecordService.reportTestRows(rows));
    }

    /**
     * 批量删除测试记录行(仅未判定草稿行可删)
     */
    @SaCheckPermission("mes:flash:testrecord:remove")
    @Log(title = "测试记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(mesTestRecordService.deleteByIds(ids));
    }

    /**
     * 测试统计概览
     */
    @SaCheckPermission("mes:flash:testrecord:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(TestRecordBo bo) {
        return R.ok(mesTestRecordService.summary(bo));
    }
}