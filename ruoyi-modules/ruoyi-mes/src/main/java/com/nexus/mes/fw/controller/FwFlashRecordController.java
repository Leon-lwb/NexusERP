package com.nexus.mes.fw.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.fw.domain.bo.FwFlashRecordBo;
import com.nexus.mes.fw.domain.vo.FwFlashRecordVo;
import com.nexus.mes.fw.service.IFwFlashRecordService;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES固件刷写记录 Controller
 * <p>M2 刷写作业: 追溯查询 + 补录 + 作业确认(confirm) + SN 远程搜索。</p>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/fw/flash")
public class FwFlashRecordController extends BaseController {

    private final IFwFlashRecordService fwFlashRecordService;

    /**
     * 分页查询刷写记录
     */
    @SaCheckPermission("mes:fw:flash:list")
    @GetMapping("/list")
    public TableDataInfo<FwFlashRecordVo> list(FwFlashRecordBo bo, PageQuery pageQuery) {
        return fwFlashRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取刷写记录详情
     */
    @SaCheckPermission("mes:fw:flash:query")
    @GetMapping("/{flashRecordId}")
    public R<FwFlashRecordVo> getInfo(@PathVariable Long flashRecordId) {
        return R.ok(fwFlashRecordService.selectById(flashRecordId));
    }

    /**
     * 新增刷写记录(管理员补录, 宽松校验)
     */
    @SaCheckPermission("mes:fw:flash:add")
    @Log(title = "刷写记录补录", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FwFlashRecordBo bo) {
        return toAjax(fwFlashRecordService.insertByBo(bo));
    }

    /**
     * 修改刷写记录(纠错: 结果/失败原因/设备/时间等)
     */
    @SaCheckPermission("mes:fw:flash:edit")
    @Log(title = "刷写记录修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FwFlashRecordBo bo) {
        return toAjax(fwFlashRecordService.updateByBo(bo));
    }

    /**
     * 批量删除刷写记录(仅 MANUAL 补录记录可删, AUTO 作业记录禁止删除)
     */
    @SaCheckPermission("mes:fw:flash:remove")
    @Log(title = "刷写记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(fwFlashRecordService.deleteByIds(ids));
    }

    /**
     * 刷写作业确认: 严格校验 SN 非报废、固件已发布, 落一条记录(FAIL 重刷保留历史)
     */
    @SaCheckPermission("mes:fw:flash:confirm")
    @Log(title = "刷写作业确认", businessType = BusinessType.INSERT)
    @PostMapping("/confirm")
    public R<Void> confirm(@Validated @RequestBody FwFlashRecordBo bo) {
        return toAjax(fwFlashRecordService.confirmFlash(bo));
    }

    /**
     * SN 远程搜索(录入/确认弹窗下拉): 按 SN 编码模糊查询 pro_sn
     */
    @SaCheckPermission("mes:fw:flash:list")
    @GetMapping("/sn-options")
    public R<List<ProSnVo>> snOptions(@RequestParam(required = false) String snCode) {
        return R.ok(fwFlashRecordService.listSnOptions(snCode));
    }
}