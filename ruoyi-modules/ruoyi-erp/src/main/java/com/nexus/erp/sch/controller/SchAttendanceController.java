package com.nexus.erp.sch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.system.service.ISysOssService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.sch.domain.bo.SchAttendanceBo;
import com.nexus.erp.sch.domain.vo.SchAttendanceVo;
import com.nexus.erp.sch.service.ISchAttendanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考勤记录Controller
 *
 * @author nexus
 * @date 2026-01-06
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/attendance")
public class SchAttendanceController extends BaseController {

    private final ISchAttendanceService schAttendanceService;
    private final ISysOssService sysOssService;

    /**
     * 分页查询考勤记录列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 考勤记录分页列表
     */
    @SaCheckPermission("sch:attendance:list")
    @GetMapping("/list")
    public TableDataInfo<SchAttendanceVo> list(SchAttendanceBo bo, PageQuery pageQuery) {
        return schAttendanceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出考勤记录数据到 Excel
     * <p>权限标识：{@code sch:attendance:export}</p>
     * <p>日志记录：title="考勤记录"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("sch:attendance:export")
    @Log(title = "考勤记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchAttendanceBo bo, HttpServletResponse response) {
        List<SchAttendanceVo> list = schAttendanceService.selectList(bo);
        ExcelUtil.exportExcel(list, "考勤记录数据", SchAttendanceVo.class, response);
    }

    /**
     * 获取考勤记录详细信息
     * <p>权限标识：{@code sch:attendance:query}</p>
     *
     * @param attendanceId 考勤记录ID
     * @return 考勤记录详细信息
     */
    @SaCheckPermission("sch:attendance:query")
    @GetMapping("/{attendanceId}")
    public R<SchAttendanceVo> getInfo(@PathVariable Long attendanceId) {
        return R.ok(schAttendanceService.selectById(attendanceId));
    }

    /**
     * 新增考勤记录
     * <p>权限标识：{@code sch:attendance:add}</p>
     * <p>日志记录：title="考勤记录"，businessType=INSERT</p>
     *
     * @param bo 考勤记录业务对象
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:add")
    @Log(title = "考勤记录", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchAttendanceBo bo) {
        return toAjax(schAttendanceService.insertByBo(bo));
    }

    /**
     * 修改考勤记录
     * <p>权限标识：{@code sch:attendance:edit}</p>
     * <p>日志记录：title="考勤记录"，businessType=UPDATE</p>
     *
     * @param bo 考勤记录业务对象
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:edit")
    @Log(title = "考勤记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchAttendanceBo bo) {
        return toAjax(schAttendanceService.updateByBo(bo));
    }

    /**
     * 删除考勤记录
     * <p>权限标识：{@code sch:attendance:remove}</p>
     * <p>日志记录：title="考勤记录"，businessType=DELETE</p>
     *
     * @param attendanceIds 考勤记录ID数组
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:remove")
    @Log(title = "考勤记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{attendanceIds}")
    public R<Void> remove(@PathVariable Long[] attendanceIds) {
        return toAjax(schAttendanceService.deleteByIds(attendanceIds));
    }

    /**
     * FAIL 复查:标记误判(人工改 PASS)
     * <p>考勤管理员复查 FAIL 记录,确认是误判(照片相似但阈值误判)时改 PASS。
     * 操作写入 remark 前缀 {@code [FACE-REVIEW]} 供审计。</p>
     *
     * @param attendanceId 考勤记录ID
     * @param remark       复查备注(可选)
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:review")
    @Log(title = "考勤复查", businessType = BusinessType.UPDATE)
    @PutMapping("/{attendanceId}/face/mark-pass")
    public R<Void> markFacePass(@PathVariable Long attendanceId, @RequestParam(required = false) String remark) {
        return toAjax(schAttendanceService.markFaceVerified(attendanceId, "PASS", remark));
    }

    /**
     * FAIL 复查:标记代打卡(违规,状态置 FAIL 且不再自动补跑)
     * <p>考勤管理员确认是代打卡/照片冒用时调用,记录审计备注。</p>
     *
     * @param attendanceId 考勤记录ID
     * @param remark       复查备注(可选)
     * @return 操作结果
     */
    @SaCheckPermission("sch:attendance:review")
    @Log(title = "考勤复查", businessType = BusinessType.UPDATE)
    @PutMapping("/{attendanceId}/face/mark-proxy")
    public R<Void> markFaceProxy(@PathVariable Long attendanceId, @RequestParam(required = false) String remark) {
        return toAjax(schAttendanceService.markFaceVerified(attendanceId, "FAIL", remark));
    }

    /**
     * 打卡人脸照片查看(私有桶 MinIO,经后端代理流式下载)
     * <p>私有桶 presigned URL 短时效且含敏感信息,复查页统一经本端点代理浏览。</p>
     *
     * @param ossId 存证照片 ossId
     * @param response HTTP 响应
     */
    @SaCheckPermission("sch:attendance:review")
    @GetMapping("/face-photo/{ossId}")
    public void facePhoto(@PathVariable Long ossId, HttpServletResponse response) {
        try {
            sysOssService.download(ossId, response);
        } catch (Exception e) {
            throw new ServiceException("照片读取失败: " + e.getMessage());
        }
    }
}
