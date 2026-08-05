package com.nexus.system.controller.app;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.system.domain.bo.AppUserAgreementBo;
import com.nexus.system.domain.vo.AppUserAgreementVo;
import com.nexus.system.service.IAppUserAgreementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * App用户协议同意控制器
 * <p>
 * 记录用户对隐私政策/用户服务协议等的同意情况(移动端关于页"政策协议"入口)。
 *
 * @author nexus
 * @date 2026-08-05
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/agreement")
public class AppAgreementController {

    private final IAppUserAgreementService appUserAgreementService;

    /**
     * 记录用户对某协议的同意
     * <p>幂等: 同用户同协议同版本重复提交直接成功;版本不同则视为重新同意(更新版本并刷新同意时间)</p>
     *
     * @param bo 协议标识与版本
     * @return 操作结果
     */
    @PostMapping("/agree")
    public R<Void> agree(@RequestBody AppUserAgreementBo bo) {
        if (ObjectUtil.isNull(bo) || StrUtil.isBlank(bo.getAgreementKey())) {
            return R.fail("协议标识不能为空");
        }
        if (StrUtil.isBlank(bo.getAgreementVersion())) {
            return R.fail("协议版本不能为空");
        }
        Long userId = LoginHelper.getUserId();
        if (ObjectUtil.isNull(userId)) {
            return R.fail("用户未登录");
        }
        appUserAgreementService.agree(userId, bo.getAgreementKey(), bo.getAgreementVersion());
        return R.ok();
    }

    /**
     * 查询当前用户已同意的协议列表
     *
     * @return 已同意协议列表
     */
    @GetMapping("/agreed")
    public R<List<AppUserAgreementVo>> agreed() {
        Long userId = LoginHelper.getUserId();
        if (ObjectUtil.isNull(userId)) {
            return R.fail("用户未登录");
        }
        return R.ok(appUserAgreementService.listAgreed(userId));
    }

}
