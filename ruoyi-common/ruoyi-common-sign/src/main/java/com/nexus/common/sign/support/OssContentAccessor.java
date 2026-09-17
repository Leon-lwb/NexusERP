package com.nexus.common.sign.support;

/**
 * 对象存储内容访问 SPI：按 {@code ossId} 读写对象存储中的字节。
 *
 * <p><b>为什么需要这个接口（请勿"简化"掉）</b></p>
 *
 * <p>本模块（{@code ruoyi-common-sign}）需要读印章原图、并把生成的预览图写回对象存储。
 * 但"ossId → 文件记录 / 字节"的能力只存在于 {@code ruoyi-modules/ruoyi-system}：
 * {@code ISysOssService}、{@code SysOssVo}、以及负责按服务商取 {@code OssClient} 的
 * {@code OssFactory#instance(String)} 全都在系统模块里。而系统模块
 * <b>已经反向依赖本模块</b>（{@code ruoyi-system/pom.xml} 依赖 {@code ruoyi-common-sign}，
 * 用于提供 {@code PasswordSignCredentialChecker}）。若本模块直接依赖 {@code ruoyi-system}，
 * 就构成 Maven 循环依赖，整仓编译直接失败。</p>
 *
 * <p>同理，{@code ruoyi-common-oss} 也无法替代：它只提供 {@code OssFactory}/{@code OssClient}
 * （按 configKey 直连桶），<b>没有</b> ossId → 对象键（{@code sys_oss} 行）的映射，
 * 而对象键前缀/租户/服务商只有数据库记录里才有。</p>
 *
 * <p>因此采用与 {@link SignCredentialChecker} <b>完全相同</b>的形态：接口留在本模块，
 * 实现放在具备数据能力的 {@code ruoyi-system}（{@code com.nexus.system.sign.SysOssContentAccessor}），
 * 由 Spring 装配。这是本模块既定的跨模块解耦手段，不是权宜之计。</p>
 *
 * <p><b>装配语义</b></p>
 * <ul>
 *   <li>调用方必须容忍实现缺失：请用 {@code ObjectProvider<OssContentAccessor>}
 *       或 {@code @Autowired(required = false)} 注入，缺实现时给出明确日志/异常，
 *       <b>不要</b>直接字段注入导致 NPE（参考 {@code SignAuthVerifier} 对空
 *       {@code List<SignCredentialChecker>} 的宽容处理）。</li>
 *   <li>实现方无需标注 {@code @Primary}/{@code @Order}：同一时刻只应有一个实现。</li>
 * </ul>
 *
 * @author nexus
 */
public interface OssContentAccessor {

    /**
     * 读取 OSS 对象字节。
     *
     * <p><b>对象不存在一律返回 {@code null}，不抛异常</b>：调用方把「取不到」当作正常分支
     * （例如印章原图被删、ossId 是历史脏数据），不应因一次读取失败中断业务。</p>
     *
     * @param ossId 文件在 {@code sys_oss} 中的唯一标识；为 {@code null} 时直接返回 {@code null}
     * @return 对象字节；ossId 未知 / 记录不存在 / 远端对象缺失 / 读取异常时返回 {@code null}
     */
    byte[] read(Long ossId);

    /**
     * 写入字节到对象存储，并落一条 {@code sys_oss} 记录。
     *
     * @param content     待写入的字节内容，不得为空
     * @param fileName    期望的文件名（用于推导对象后缀；实现方只取其基名，忽略目录部分）
     * @param contentType 期望的 MIME 类型；仅在后缀缺失时用于推导后缀
     *                    （{@code ISysOssService.upload(File)} 按后缀反查 MIME）
     * @return 新写入文件的 ossId
     * @throws RuntimeException 写入失败（磁盘/网络/存储服务异常）。调用方如需容错请自行捕获
     */
    Long store(byte[] content, String fileName, String contentType);
}
