package com.nexus.erp.wm.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.domain.vo.pda.PdaBasedataSyncVo;
import com.nexus.erp.wm.service.IPdaBasedataService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 主数据增量同步接口 (物料/库位/仓库)
 * <p>
 * 客户端携带 lastSyncTime 游标(上次返回的 serverTime), 首次传 0。
 * 权限: wm:basedata:sync (N3: 此前仅登录态, 与 /app/pda 其他作业端点权限强制不一致)。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/basedata")
public class AppPdaBasedataController {

    private final IPdaBasedataService pdaBasedataService;

    @SaCheckPermission("wm:basedata:sync")
    @GetMapping("/sync")
    public R<PdaBasedataSyncVo> sync(@RequestParam(defaultValue = "0") Long lastSyncTime) {
        return R.ok(pdaBasedataService.sync(lastSyncTime));
    }
}
