package com.nexus.system.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.http.HtmlUtil;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.core.utils.StreamUtils;
import com.nexus.common.core.utils.ValidatorUtils;
import com.nexus.common.excel.core.ExcelListener;
import com.nexus.common.excel.core.ExcelResult;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.system.domain.bo.SysUserBo;
import com.nexus.system.domain.vo.SysUserImportVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysConfigService;
import com.nexus.system.service.ISysUserService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 系统用户自定义导入监听器
 * <p>监听 Excel 用户导入过程，逐行解析并新增或更新用户（依据是否支持覆盖），
 * 汇总成功/失败信息，导入失败时抛出含错误明细的异常。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Slf4j
public class SysUserImportListener extends AnalysisEventListener<SysUserImportVo> implements ExcelListener<SysUserImportVo> {

    /** 用户业务服务，提供按用户名查询与用户增改能力。 */
    private final ISysUserService userService;

    /** 系统参数配置的初始密码（BCrypt 加密后），用于新增用户默认密码。 */
    private final String password;

    /** 是否支持覆盖更新已存在用户。 */
    private final Boolean isUpdateSupport;

    /** 当前操作人用户 ID，记录为创建/更新人。 */
    private final Long operUserId;

    /** 导入成功条数。 */
    private int successNum = 0;
    /** 导入失败条数。 */
    private int failureNum = 0;
    /** 导入成功信息汇总。 */
    private final StringBuilder successMsg = new StringBuilder();
    /** 导入失败信息汇总。 */
    private final StringBuilder failureMsg = new StringBuilder();

    /**
     * 构造导入监听器，从配置读取初始密码并解析当前操作人。
     *
     * @param isUpdateSupport 是否支持覆盖更新已存在用户
     */
    public SysUserImportListener(Boolean isUpdateSupport) {
        String initPassword = SpringUtils.getBean(ISysConfigService.class).selectConfigByKey("sys.user.initPassword");
        this.userService = SpringUtils.getBean(ISysUserService.class);
        this.password = BCrypt.hashpw(initPassword);
        this.isUpdateSupport = isUpdateSupport;
        this.operUserId = LoginHelper.getUserId();
    }

    /**
     * 解析并逐行处理导入的用户数据：不存在则新增，支持覆盖则更新，否则计入失败。
     *
     * @param userVo  当前行解析出的用户导入视图
     * @param context Excel 解析上下文
     */
    @Override
    public void invoke(SysUserImportVo userVo, AnalysisContext context) {
        SysUserVo sysUser = this.userService.selectUserByUserName(userVo.getUserName());
        try {
            // 验证是否存在这个用户
            if (ObjectUtil.isNull(sysUser)) {
                SysUserBo user = BeanUtil.toBean(userVo, SysUserBo.class);
                ValidatorUtils.validate(user);
                user.setPassword(password);
                user.setCreateBy(operUserId);
                userService.insertUser(user);
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、账号 ").append(user.getUserName()).append(" 导入成功");
            } else if (isUpdateSupport) {
                Long userId = sysUser.getUserId();
                SysUserBo user = BeanUtil.toBean(userVo, SysUserBo.class);
                user.setUserId(userId);
                ValidatorUtils.validate(user);
                userService.checkUserAllowed(user.getUserId());
                userService.checkUserDataScope(user.getUserId());
                user.setUpdateBy(operUserId);
                userService.updateUser(user);
                successNum++;
                successMsg.append("<br/>").append(successNum).append("、账号 ").append(user.getUserName()).append(" 更新成功");
            } else {
                failureNum++;
                failureMsg.append("<br/>").append(failureNum).append("、账号 ").append(sysUser.getUserName()).append(" 已存在");
            }
        } catch (Exception e) {
            failureNum++;
            String msg = "<br/>" + failureNum + "、账号 " + HtmlUtil.cleanHtmlTag(userVo.getUserName()) + " 导入失败：";
            String message = e.getMessage();
            if (e instanceof ConstraintViolationException cvException) {
                message = StreamUtils.join(cvException.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
            }
            failureMsg.append(msg).append(message);
            log.error(msg, e);
        }
    }

    /**
     * 所有数据解析完成后的回调，本实现无需额外处理。
     *
     * @param context Excel 解析上下文
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {

    }

    /**
     * 返回导入结果：无失败时汇总成功信息，有失败时抛出含错误明细的异常。
     *
     * @return Excel 导入结果视图（含分析信息）
     */
    @Override
    public ExcelResult<SysUserImportVo> getExcelResult() {
        return new ExcelResult<>() {

            @Override
            public String getAnalysis() {
                if (failureNum > 0) {
                    failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
                    throw new ServiceException(failureMsg.toString());
                } else {
                    successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
                }
                return successMsg.toString();
            }

            @Override
            public List<SysUserImportVo> getList() {
                return null;
            }

            @Override
            public List<String> getErrorList() {
                return null;
            }
        };
    }
}
