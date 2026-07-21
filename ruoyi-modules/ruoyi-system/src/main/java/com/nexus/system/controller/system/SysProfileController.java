package com.nexus.system.controller.system;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.utils.file.MimeTypeUtils;
import com.nexus.common.encrypt.annotation.ApiEncrypt;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.helper.DataPermissionHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysUserBo;
import com.nexus.system.domain.bo.SysUserPasswordBo;
import com.nexus.system.domain.bo.SysUserProfileBo;
import com.nexus.system.domain.vo.ProfileUserVo;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.service.ISysUserService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * 个人信息管理Controller
 * <p>提供当前登录用户的个人信息查看、资料修改、密码重置与头像上传等接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController {

    /** 用户业务服务，提供用户信息、角色/岗位组、资料与密码、头像更新等能力。 */
    private final ISysUserService userService;
    /** OSS 对象存储业务服务，用于上传用户头像文件。 */
    private final ISysOssService ossService;

    /**
     * 获取当前登录用户的个人信息，包含用户资料、角色组与岗位组。
     *
     * @return 当前用户个人信息视图
     */
    @GetMapping
    public R<ProfileVo> profile() {
        SysUserVo user = userService.selectUserById(LoginHelper.getUserId());
        String roleGroup = userService.selectUserRoleGroup(user.getUserId());
        String postGroup = userService.selectUserPostGroup(user.getUserId());
        // 单独做一个vo专门给个人中心用 避免数据被脱敏
        ProfileUserVo profileUser = BeanUtil.toBean(user, ProfileUserVo.class);
        ProfileVo profileVo = new ProfileVo(profileUser, roleGroup, postGroup);
        return R.ok(profileVo);
    }

    /**
     * 修改当前登录用户的个人资料（昵称、手机号、邮箱等），校验唯一性。
     *
     * @param profile 个人资料修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @RepeatSubmit
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> updateProfile(@Validated @RequestBody SysUserProfileBo profile) {
        SysUserBo user = BeanUtil.toBean(profile, SysUserBo.class);
        user.setUserId(LoginHelper.getUserId());
        String username = LoginHelper.getUsername();
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user)) {
            return R.fail("修改用户'" + username + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return R.fail("修改用户'" + username + "'失败，邮箱账号已存在");
        }
        int rows = DataPermissionHelper.ignore(() -> userService.updateUserProfile(user));
        if (rows > 0) {
            return R.ok();
        }
        return R.fail("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置当前登录用户的登录密码，校验旧密码正确性及新旧密码不一致。
     *
     * @param bo 含旧密码与新密码的参数（接口加密传输）
     * @return 操作结果，成功时返回成功状态
     */
    @RepeatSubmit
    @ApiEncrypt
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public R<Void> updatePwd(@Validated @RequestBody SysUserPasswordBo bo) {
        SysUserVo user = userService.selectUserById(LoginHelper.getUserId());
        String password = user.getPassword();
        if (!BCrypt.checkpw(bo.getOldPassword(), password)) {
            return R.fail("修改密码失败，旧密码错误");
        }
        if (BCrypt.checkpw(bo.getNewPassword(), password)) {
            return R.fail("新密码不能与旧密码相同");
        }
        int rows = DataPermissionHelper.ignore(() -> userService.resetUserPwd(user.getUserId(), BCrypt.hashpw(bo.getNewPassword())));
        if (rows > 0) {
            return R.ok();
        }
        return R.fail("修改密码异常，请联系管理员");
    }

    /**
     * 上传并更新当前登录用户的头像（仅支持图片格式）。
     *
     * @param avatarfile 用户头像文件
     * @return 上传成功时返回头像地址，失败则返回错误信息
     */
    @RepeatSubmit
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AvatarVo> avatar(@RequestPart("avatarfile") MultipartFile avatarfile) {
        if (ObjectUtil.isNotNull(avatarfile) && !avatarfile.isEmpty()) {
            String extension = FileUtil.extName(avatarfile.getOriginalFilename());
            if (!StringUtils.equalsAnyIgnoreCase(extension, MimeTypeUtils.IMAGE_EXTENSION)) {
                return R.fail("文件格式不正确，请上传" + Arrays.toString(MimeTypeUtils.IMAGE_EXTENSION) + "格式");
            }
            SysOssVo oss = ossService.upload(avatarfile);
            String avatar = oss.getUrl();
            boolean updateSuccess = DataPermissionHelper.ignore(() -> userService.updateUserAvatar(LoginHelper.getUserId(), oss.getOssId()));
            if (updateSuccess) {
                return R.ok(new AvatarVo(avatar));
            }
        }
        return R.fail("上传图片异常，请联系管理员");
    }

    /**
     * 用户头像信息
     *
     * @param imgUrl 头像地址
     */
    public record AvatarVo(String imgUrl) {}

    /**
     * 用户个人信息
     *
     * @param user      用户信息
     * @param roleGroup 用户所属角色组
     * @param postGroup 用户所属岗位组
     */
    public record ProfileVo(ProfileUserVo user, String roleGroup, String postGroup) {}

}
