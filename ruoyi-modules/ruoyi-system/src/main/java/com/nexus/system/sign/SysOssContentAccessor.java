package com.nexus.system.sign;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * {@link OssContentAccessor} 的 {@code ruoyi-system} 实现：把 ossId 映射到
 * {@code sys_oss} 记录 + {@code OssFactory}/{@code OssClient} 完成真正的读写。
 *
 * <p><b>为何实现放在系统模块</b>：{@code sys_oss} 表与 {@code ISysOssService} 都只属于系统模块，
 * 而系统模块已反向依赖 {@code ruoyi-common-sign}，反向依赖会形成 Maven 循环。接口留在 common 侧，
 * 实现留在这里，由 Spring 装配 —— 与 {@code PasswordSignCredentialChecker} 同一形态。</p>
 *
 * <p><b>数据源（{@code @DS("master")}，勿删）</b>：{@code sys_oss} 位于 ry-vue 库（主数据源 master）。
 * 不固定数据源时，从 MES/ERP 上下文（{@code @DS("mes")}/{@code @DS("erp")}）调用本类会打到业务库并报
 * 「表不存在」。理由与注解层级选择详见
 * {@code com.nexus.common.sign.service.impl.EsSealServiceImpl} 的类注释。</p>
 *
 * <p><b>读取路径的真实 API</b>（与 {@code SysOssServiceImpl.download}、
 * {@code CvFaceBatchRegisterService.loadAvatar} 一致）：</p>
 * <pre>
 *   ISysOssService.getById(ossId) -> SysOssVo(fileName, service)
 *   OssFactory.instance(vo.getService()).getObjectContent(vo.getFileName()) -> InputStream
 * </pre>
 * <p><b>写入路径的真实 API</b>：{@code ISysOssService.upload(File)} —— 它内部用
 * {@code OssFactory.instance()} 上传并按后缀推导 MIME，然后<b>插入 sys_oss 记录</b>并返回带
 * {@code ossId} 的 {@code SysOssVo}。正因如此本实现必须落临时文件（{@code upload(File)} 按文件名取后缀），
 * 且必须在使用后清理。</p>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DS("master")
public class SysOssContentAccessor implements OssContentAccessor {

    /** 未指定后缀且 contentType 无法推导时的兜底后缀。 */
    private static final String DEFAULT_SUFFIX = ".bin";

    /** {@code Files.createTempFile} 要求前缀至少 3 个字符。 */
    private static final int MIN_TEMP_PREFIX_LENGTH = 3;

    private final ISysOssService sysOssService;

    /**
     * 读取 OSS 对象字节。
     *
     * <p>契约要求「取不到就是 null」，因此这里把查询失败、配置缺失、远端 404、IO 异常全部收敛成
     * {@code null} + WARN：印章原图丢失只应导致「没有预览图」，不应让印章相关接口整体 500。</p>
     *
     * @param ossId 文件在 {@code sys_oss} 中的唯一标识
     * @return 对象字节；不存在或读取失败返回 {@code null}
     */
    @Override
    public byte[] read(Long ossId) {
        if (ossId == null) {
            return null;
        }
        try {
            SysOssVo ossVo = sysOssService.getById(ossId);
            if (ossVo == null) {
                log.warn("[sign] 读取 OSS 对象失败：sys_oss 无记录, ossId={}", ossId);
                return null;
            }
            if (StringUtils.isBlank(ossVo.getFileName())) {
                log.warn("[sign] 读取 OSS 对象失败：sys_oss 记录缺少 fileName, ossId={}", ossId);
                return null;
            }
            try (InputStream in = OssFactory.instance(ossVo.getService())
                .getObjectContent(ossVo.getFileName())) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            // 远端对象被删/桶未配置/网络异常都在此处收敛为 null
            log.warn("[sign] 读取 OSS 对象失败, ossId={}: {}", ossId, e.getMessage());
            return null;
        }
    }

    /**
     * 写入字节到对象存储，并落一条 {@code sys_oss} 记录。
     *
     * @param content     待写入的字节内容
     * @param fileName    期望文件名（仅取基名推导后缀）
     * @param contentType 期望 MIME（后缀缺失时用于推导后缀）
     * @return 新写入文件的 ossId
     * @throws ServiceException 入参非法、临时文件读写失败或对象存储未返回 ossId
     */
    @Override
    public Long store(byte[] content, String fileName, String contentType) {
        if (content == null || content.length == 0) {
            throw new ServiceException("写入对象存储的内容为空");
        }
        String suffix = resolveSuffix(fileName, contentType);
        String prefix = resolvePrefix(fileName);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(prefix, suffix);
            Files.write(tempFile, content);
            SysOssVo ossVo = sysOssService.upload(tempFile.toFile());
            if (ossVo == null || ossVo.getOssId() == null) {
                throw new ServiceException("对象存储未返回 ossId");
            }
            return ossVo.getOssId();
        } catch (IOException e) {
            throw new ServiceException("写入对象存储失败：{}", e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("[sign] 临时文件清理失败, path={}: {}", tempFile, e.getMessage());
                }
            }
        }
    }

    /**
     * 推导对象后缀（决定 {@code sys_oss.file_suffix} 与 MIME）。
     *
     * @param fileName    期望文件名
     * @param contentType 期望 MIME
     * @return 形如 {@code .png} 的后缀
     */
    private static String resolveSuffix(String fileName, String contentType) {
        String baseName = baseName(fileName);
        int dot = baseName == null ? -1 : baseName.lastIndexOf('.');
        if (dot >= 0 && dot < baseName.length() - 1) {
            return baseName.substring(dot).toLowerCase(Locale.ROOT);
        }
        if (StringUtils.isNotBlank(contentType)) {
            String mime = contentType.trim().toLowerCase(Locale.ROOT);
            int slash = mime.indexOf('/');
            if (slash > 0 && slash < mime.length() - 1) {
                String sub = mime.substring(slash + 1);
                // image/png -> .png，image/svg+xml -> .svg
                int plus = sub.indexOf('+');
                sub = plus > 0 ? sub.substring(0, plus) : sub;
                sub = "jpeg".equals(sub) ? "jpg" : sub;
                return "." + sub;
            }
        }
        return DEFAULT_SUFFIX;
    }

    /**
     * 推导临时文件名前缀（去掉目录、非安全字符，并补齐最小长度）。
     *
     * @param fileName 期望文件名
     * @return 临时文件名前缀
     */
    private static String resolvePrefix(String fileName) {
        String baseName = baseName(fileName);
        if (StringUtils.isBlank(baseName)) {
            baseName = "oss";
        }
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        String safe = baseName.replaceAll("[^0-9A-Za-z_-]", "_");
        if (safe.length() < MIN_TEMP_PREFIX_LENGTH) {
            safe = (safe + "___").substring(0, MIN_TEMP_PREFIX_LENGTH);
        }
        // createTempFile 会追加随机数，控制前缀长度避免文件名过长
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
    }

    /**
     * 取文件名的基名（剥离目录部分）。
     *
     * @param fileName 原始文件名，可为 null
     * @return 基名，入参为空时返回 null
     */
    private static String baseName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }
}
