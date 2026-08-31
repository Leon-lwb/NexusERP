package com.nexus.system.controller.system;

import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.vo.SysSocialVo;
import com.nexus.system.service.ISysSocialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社会化关系（第三方账号绑定）Controller
 * <p>提供当前登录用户已绑定的第三方社会化账号（如微信、Github 等）列表查询接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author thiszhc
 * @date 2023-06-16
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/social")
public class SysSocialController extends BaseController {

    /** 社会化账号业务服务，提供按用户查询第三方绑定关系等能力。 */
    private final ISysSocialService socialUserService;

    /**
     * 查询当前登录用户绑定的社会化账号列表。
     *
     * @return 当前用户的社会化账号列表
     */
    @GetMapping("/list")
    public R<List<SysSocialVo>> list() {
        return R.ok(socialUserService.queryListByUserId(LoginHelper.getUserId()));
    }

}
