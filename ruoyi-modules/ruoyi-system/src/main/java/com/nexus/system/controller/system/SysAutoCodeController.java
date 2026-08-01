package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动编码Controller
 * <p>提供编码生成（落库）和编码预览（不落库）两个接口。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@RestController
@RequestMapping("/system/autocode")
public class SysAutoCodeController {

    /** 自动编码工具，负责按规则生成或预览业务单据编码。 */
    @Autowired
    private AutoCodeUtil autoCodeUtil;

    /**
     * 生成编码（落库，消耗流水号）。
     * <p>
     * 由后端业务 Service 在保存单据时调用，或由前端提交表单前调用。
     * 每次调用都会持久化更新 sys_auto_code_result 表。
     * </p>
     *
     * @param ruleCode       编码规则编码
     * @param inputCharacter 传入字符（可选）
     * @return 生成的完整编码字符串
     */
    @SaCheckPermission("system:autocode:get")
    @GetMapping(value = {"/get/{ruleCode}/{inputCharacter}", "/get/{ruleCode}"})
    public String getAutoCode(@PathVariable String ruleCode, @PathVariable(required = false) String inputCharacter) {
        return autoCodeUtil.genSerialCode(ruleCode, inputCharacter);
    }

    /**
     * 预览编码（不落库，不消耗流水号）。
     * <p>
     * 仅计算并返回下一个编码，<b>不会</b>写入 sys_auto_code_result 表。
     * 适用于前端 el-switch 开关切换时展示编码预览，
     * 用户取消操作不会导致流水号跳号。
     * </p>
     *
     * @param ruleCode       编码规则编码
     * @param inputCharacter 传入字符（可选）
     * @return 预览的编码字符串
     */
    @SaCheckPermission("system:autocode:preview")
    @GetMapping(value = {"/preview/{ruleCode}/{inputCharacter}", "/preview/{ruleCode}"})
    public String previewAutoCode(@PathVariable String ruleCode, @PathVariable(required = false) String inputCharacter) {
        return autoCodeUtil.previewSerialCode(ruleCode, inputCharacter);
    }
}
