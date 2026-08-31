package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.service.DictService;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysNoticeBo;
import com.nexus.system.domain.vo.SysNoticeVo;
import com.nexus.system.service.ISysNoticeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 通知公告Controller
 * <p>提供通知公告的分页查询、详情、增删改等接口；新增公告会通过 SSE 向在线用户推送提醒。
 * 属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/notice")
public class SysNoticeController extends BaseController {

    /** 通知公告业务服务，提供分页、增删改等能力。 */
    private final ISysNoticeService noticeService;
    /** 字典服务，用于解析公告类型的字典标签。 */
    private final DictService dictService;

    /**
     * 分页查询通知公告列表。
     *
     * @param notice   公告查询条件（标题、类型、状态等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 通知公告分页结果
     */
    @SaCheckPermission("system:notice:list")
    @GetMapping("/list")
    public TableDataInfo<SysNoticeVo> list(SysNoticeBo notice, PageQuery pageQuery) {
        return noticeService.selectPageNoticeList(notice, pageQuery);
    }

    /**
     * 根据通知公告编号查询详细信息。
     *
     * @param noticeId 公告 ID
     * @return 通知公告详情
     */
    @SaCheckPermission("system:notice:query")
    @GetMapping(value = "/{noticeId}")
    public R<SysNoticeVo> getInfo(@PathVariable Long noticeId) {
        return R.ok(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 新增通知公告，并通过 SSE 向全部在线用户推送公告提醒。
     *
     * @param notice 通知公告新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:notice:add")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysNoticeBo notice) {
        int rows = noticeService.insertNotice(notice);
        if (rows <= 0) {
            return R.fail();
        }
        String type = dictService.getDictLabel("sys_notice_type", notice.getNoticeType());
        SseMessageUtils.publishAll("[" + type + "] " + notice.getNoticeTitle());
        return R.ok();
    }

    /**
     * 修改通知公告。
     *
     * @param notice 通知公告修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:notice:edit")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysNoticeBo notice) {
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 批量删除通知公告。
     *
     * @param noticeIds 公告 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:notice:remove")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public R<Void> remove(@PathVariable Long[] noticeIds) {
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }
}
