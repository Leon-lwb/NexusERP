package com.nexus.erp.cv.service;

import cn.hutool.core.io.IoUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.domain.FaceDtos.RegisterResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 底库批量注册服务(测试期)
 * <p>用于测试阶段:将已绑定头像的测试账号批量注册到 vision 底库(subject=T{tenantId}-EMP-工号)。
 * 上线集中采集时,可在管理端逐人注册(见 CvFaceController),本服务提供批量能力。</p>
 * <p><b>数据源</b>:类级 {@code @DS("erp")} 是必需的——{@code sch_worker} 在 ry-erp 库,
 * 漏标会落到默认 master(ry-vue)库并抛 {@code Table 'ry-vue.sch_worker' doesn't exist},
 * 批量注册端点必然 500。{@code sys_user}/{@code sys_oss} 在 master 库,调用点显式
 * {@link DynamicDataSourceContextHolder#push}("master") 切回主库。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("erp")
public class CvFaceBatchRegisterService {

    private final VisionClient visionClient;
    private final ISysOssService sysOssService;
    private final SysUserMapper sysUserMapper;
    private final SchWorkerMapper workerMapper;

    /**
     * 批量注册:遍历已绑定头像的 SysUser,若其对应 SchWorker 存在则用头像照片注册底库
     *
     * @param limit 单批最大处理数(防御)
     * @return 注册结果摘要
     */
    public List<RegisterResult> registerFromAvatars(int limit) {
        List<RegisterResult> results = new ArrayList<>();
        // 查有头像的用户: sys_user 在 master 库(ry-vue),类级 @DS("erp") 会误落 erp 库,显式切主数据源
        List<SysUser> users;
        DynamicDataSourceContextHolder.push("master");
        try {
            users = sysUserMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysUser>lambdaQuery()
                    .isNotNull(SysUser::getAvatar)
                    .last("LIMIT " + Math.max(1, Math.min(limit, 500)))
            );
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        // 底库 subject 带租户维度: 不同租户同工号不得互相覆盖/串库
        String tenantId = TenantHelper.getTenantId();
        for (SysUser user : users) {
            // 按 userId 找对应工人,取工号
            SchWorker worker = workerMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SchWorker>lambdaQuery()
                    .eq(SchWorker::getUserId, user.getUserId())
                    .last("LIMIT 1")
            );
            if (worker == null || worker.getEmployeeId() == null) {
                log.warn("[cv] 批量注册跳过: userId={} 无对应工人或未填工号", user.getUserId());
                continue;
            }
            byte[] avatarBytes = loadAvatar(user.getAvatar());
            if (avatarBytes == null) {
                log.warn("[cv] 批量注册跳过: userId={} 头像读取失败", user.getUserId());
                continue;
            }
            String subject = CvSubjectHelper.build(tenantId, worker.getEmployeeId());
            try {
                RegisterResult result = visionClient.registerFace(avatarBytes, subject,
                    user.getUserName() != null ? user.getUserName() : worker.getUserName(),
                    "批量注册(测试期头像)");
                results.add(result);
                log.info("[cv] 批量注册成功, subject={}", subject);
            } catch (VisionException e) {
                log.warn("[cv] 批量注册失败, subject={}: {}", subject, e.getMessage());
            }
        }
        return results;
    }

    /**
     * 从 MinIO 读取头像字节(ossId → SysOssVo → 私有桶内容)
     *
     * @param ossId 头像文件 ossId(SysUser.avatar)
     * @return 头像图片字节;读取失败或记录不存在返回 null
     */
    private byte[] loadAvatar(Long ossId) {
        try {
            // sys_oss 在 master 库(ry-vue),类级 @DS("erp") 下需显式切主数据源
            DynamicDataSourceContextHolder.push("master");
            try {
                return readAvatar(ossId);
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        } catch (Exception e) {
            log.warn("[cv] 读取头像失败, ossId={}: {}", ossId, e.getMessage());
            return null;
        }
    }

    private byte[] readAvatar(Long ossId) throws Exception {
        SysOssVo ossVo = sysOssService.getById(ossId);
        if (ossVo == null || ossVo.getFileName() == null) {
            return null;
        }
        try (InputStream in = OssFactory.instance(ossVo.getService())
            .getObjectContent(ossVo.getFileName())) {
            return IoUtil.readBytes(in);
        }
    }
}
