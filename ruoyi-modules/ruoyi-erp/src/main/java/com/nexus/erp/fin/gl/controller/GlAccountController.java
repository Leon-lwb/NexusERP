package com.nexus.erp.fin.gl.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.gl.domain.bo.GlAccountBo;
import com.nexus.erp.fin.gl.domain.vo.GlAccountVo;
import com.nexus.erp.fin.gl.service.IGlAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会计科目 Controller
 *
 * @author nexus
 * @date 2026-07-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/gl/account")
public class GlAccountController extends BaseController {

    private final IGlAccountService glAccountService;

    /** 科目列表(树形,前端 handleTree 构建) */
    @SaCheckPermission("fin:gl:account:list")
    @GetMapping("/list")
    public R<List<GlAccountVo>> list(GlAccountBo bo) {
        return R.ok(glAccountService.queryList(bo));
    }

    /** 查询会计科目详情 */
    @SaCheckPermission("fin:gl:account:query")
    @GetMapping("/{accountId}")
    public R<GlAccountVo> getInfo(@PathVariable Long accountId) {
        return R.ok(glAccountService.queryById(accountId));
    }

    /** 新增会计科目 */
    @SaCheckPermission("fin:gl:account:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody GlAccountBo bo) {
        return toAjax(glAccountService.insertByBo(bo));
    }

    /** 修改会计科目 */
    @SaCheckPermission("fin:gl:account:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody GlAccountBo bo) {
        return toAjax(glAccountService.updateByBo(bo));
    }

    /** 删除会计科目 */
    @SaCheckPermission("fin:gl:account:remove")
    @DeleteMapping("/{accountIds}")
    public R<Void> remove(@PathVariable Long[] accountIds) {
        return toAjax(glAccountService.deleteByIds(accountIds));
    }

    /** 末级科目(凭证录入下拉) */
    @GetMapping("/leaf")
    public R<List<GlAccountVo>> leaf(@RequestParam(required = false) String accountType) {
        return R.ok(glAccountService.selectLeafAccounts(accountType));
    }
}
