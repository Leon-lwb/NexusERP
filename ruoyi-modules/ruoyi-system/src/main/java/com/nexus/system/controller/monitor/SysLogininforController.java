package com.nexus.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.CacheConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysLogininforBo;
import com.nexus.system.domain.vo.SysLogininforVo;
import com.nexus.system.service.ISysLogininforService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统访问记录（登录日志）Controller
 * <p>提供登录日志的分页查询、Excel 导出、批量删除、清空及账户解锁等接口。
 * 属于系统管理（System）模块的监控（monitor）分组。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/monitor/logininfor")
public class SysLogininforController extends BaseController {

    /** 系统访问记录（登录日志）业务服务，提供分页、导出、删除、清理与解锁等能力。 */
    private final ISysLogininforService logininforService;

    /**
     * 分页查询系统访问记录（登录日志）列表。
     *
     * @param logininfor 登录日志查询条件（IP、登录状态、时间范围等）
     * @param pageQuery  分页查询参数，包含页码、每页大小及排序条件
     * @return 分页结果，包含登录日志列表及分页元信息
     */
    @SaCheckPermission("monitor:logininfor:list")
    @GetMapping("/list")
    public TableDataInfo<SysLogininforVo> list(SysLogininforBo logininfor, PageQuery pageQuery) {
        return logininforService.selectPageLogininforList(logininfor, pageQuery);
    }

    /**
     * 导出系统访问记录列表为 Excel 文件。
     *
     * @param logininfor 登录日志查询条件
     * @param response   HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "登录日志", businessType = BusinessType.EXPORT)
    @SaCheckPermission("monitor:logininfor:export")
    @PostMapping("/export")
    public void export(SysLogininforBo logininfor, HttpServletResponse response) {
        List<SysLogininforVo> list = logininforService.selectLogininforList(logininfor);
        ExcelUtil.exportExcel(list, "登录日志", SysLogininforVo.class, response);
    }

    /**
     * 批量删除登录日志
     * @param infoIds 日志ids
     */
    @SaCheckPermission("monitor:logininfor:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public R<Void> remove(@PathVariable Long[] infoIds) {
        return toAjax(logininforService.deleteLogininforByIds(infoIds));
    }

    /**
     * 清理系统访问记录
     */
    @SaCheckPermission("monitor:logininfor:remove")
    @Log(title = "登录日志", businessType = BusinessType.CLEAN)
    @Lock4j
    @DeleteMapping("/clean")
    public R<Void> clean() {
        logininforService.cleanLogininfor();
        return R.ok();
    }

    /**
     * 解锁被登录失败次数限制锁定的账户，清除其密码错误计数缓存。
     *
     * @param userName 待解锁的用户名
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("monitor:logininfor:unlock")
    @Log(title = "账户解锁", businessType = BusinessType.OTHER)
    @RepeatSubmit()
    @GetMapping("/unlock/{userName}")
    public R<Void> unlock(@PathVariable("userName") String userName) {
        String loginName = CacheConstants.PWD_ERR_CNT_KEY + userName;
        if (RedisUtils.hasKey(loginName)) {
            RedisUtils.deleteObject(loginName);
        }
        return R.ok();
    }

}
