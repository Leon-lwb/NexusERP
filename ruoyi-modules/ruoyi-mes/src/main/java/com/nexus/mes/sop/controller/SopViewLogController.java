package com.nexus.mes.sop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.sop.domain.bo.SopViewLogBo;
import com.nexus.mes.sop.domain.vo.SopViewLogVo;
import com.nexus.mes.sop.service.ISopViewLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * SOP查阅记录 Controller
 * <p>查阅记录查询 + 登记查阅(查看器打开时调) + 删除。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/sop/view-log")
public class SopViewLogController extends BaseController {

    private final ISopViewLogService sopViewLogService;

    /**
     * 分页查询查阅记录列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission("mes:sop:viewlog:list")
    @GetMapping("/list")
    public TableDataInfo<SopViewLogVo> list(SopViewLogBo bo, PageQuery pageQuery) {
        return sopViewLogService.selectPageList(bo, pageQuery);
    }

    /**
     * 登记查阅(查看器打开时调): viewer/time/wo/process/IP/UA, signature_id=null(预留P0-6)
     *
     * @param sopId         SOP ID
     * @param versionNo     查阅的版本号(可空)
     * @param woId          关联工单ID(可空)
     * @param woNo          工单编号(可空)
     * @param processId     关联工序ID(可空)
     * @param processName   工序名称(可空)
     * @param workstationId 工位ID(可空)
     * @return 操作结果
     */
    @SaCheckPermission("mes:sop:viewlog:list")
    @Log(title = "SOP查阅登记", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestParam Long sopId,
                       @RequestParam(required = false) String versionNo,
                       @RequestParam(required = false) Long woId,
                       @RequestParam(required = false) String woNo,
                       @RequestParam(required = false) Long processId,
                       @RequestParam(required = false) String processName,
                       @RequestParam(required = false) Long workstationId) {
        return toAjax(sopViewLogService.recordView(sopId, versionNo, woId, woNo, processId, processName, workstationId));
    }

    /**
     * 批量删除查阅记录
     *
     * @param ids 查阅记录ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:sop:viewlog:list")
    @Log(title = "SOP查阅记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(sopViewLogService.deleteByIds(ids));
    }
}
