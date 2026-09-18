package com.nexus.common.sign.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.domain.vo.EsSignDocumentVo;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.service.IESSignDocumentService;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.pdf.PdfContentStamper;
import com.nexus.common.sign.util.Sm3Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 待签文档正文服务实现（P2a-3）。
 *
 * <p><b>数据源（类级 {@code @DS("master")}，勿删）：</b>{@code es_sign_document} 只存在于
 * ry-vue 库（主数据源 master）。{@code @DS} 是线程级上下文栈，本模块若不自行固定就会继承调用方
 * 的数据源，在 MES/ERP 上下文下打到 ry-mes / ry-erp 库报「表不存在」。理由与三种
 * 装配形态的完整论证见 {@link ESignatureServiceImpl} 的类注释；
 * 本类与 {@code ESignatureServiceImpl} / {@code EsSealServiceImpl} / {@code EsSealGrantServiceImpl}
 * 同属被 {@code SignDataSourceMasterTest} 守卫的一组。</p>
 *
 * <p><b>不重造轮子：</b>SM3 走 {@link Sm3Utils}（本模块唯一密码学出口，与哈希链同源），
 * PDF 魔数判定走 {@link PdfContentStamper#isPdf}（与 {@code signDocument} 读源正文时
 * 用的是同一个方法），对象存储读写走 {@link OssContentAccessor}。
 * 本类只补一件 P2a-2 缺的东西：把字节**登记**进来。</p>
 *
 * <p><b>换版不变量（并发安全）：</b>任一 {@code (租户, bizType, bizId)} 下最多一行
 * {@code is_current='1'}。由「按业务键的 Redis 锁」+「锁延迟到事务提交之后才释放」共同保证
 * —— 只在 {@code finally} 里放锁是不够的，理由见 {@link #releaseLockAfterTransaction}。</p>
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("master")
public class EsSignDocumentServiceImpl implements IESSignDocumentService {

    /** 正文只接受 PDF：扩展名 */
    private static final String PDF_SUFFIX = ".pdf";
    /** 正文只接受 PDF：MIME */
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    /** 通用二进制 MIME：部分浏览器/客户端不识别扩展名时会这么上报，仍需以字节头为准 */
    private static final String OCTET_STREAM = "application/octet-stream";
    /** 单文件大小上限：与 {@code spring.servlet.multipart.max-file-size: 10MB} 取同一值 */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    /** {@code file_name} 列长度上限（varchar(255)） */
    private static final int MAX_FILE_NAME_LENGTH = 255;
    /** {@code is_current}：当前生效版本 */
    private static final String IS_CURRENT_YES = "1";
    /** {@code is_current}：历史版本 */
    private static final String IS_CURRENT_NO = "0";
    /** {@code sign_status}：未签 */
    private static final String SIGN_STATUS_UNSIGNED = "0";
    /** 同一业务键换版串行化锁的 key 前缀（按租户隔离） */
    private static final String DOC_LOCK_PREFIX = "es:sign:doc:";
    /** 锁持有时长(ms) */
    private static final long LOCK_EXPIRE = 30_000L;
    /** 获取锁超时(ms) */
    private static final long LOCK_ACQUIRE_TIMEOUT = 10_000L;

    /** 待签文档 Mapper */
    private final EsSignDocumentMapper baseMapper;

    /**
     * 对象存储读写 SPI。
     * <p>按 SPI 契约用 {@link ObjectProvider} 注入：实现方在 {@code ruoyi-system}，
     * 缺失时给出明确业务异常而不是 NPE（同 {@code ESignatureServiceImpl}）。</p>
     */
    private final ObjectProvider<OssContentAccessor> ossAccessorProvider;

    /** 换版串行化锁（Lock4j/Redisson，与哈希链锁同源实现） */
    private final LockTemplate lockTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadDocument(String bizType, Long bizId, MultipartFile file) {
        // ① 准入判定：全部排在「写对象存储 / 写库」之前，
        //    因此被拒绝的请求不会留下任何半套数据（孤儿对象也没有）
        if (StringUtils.isBlank(bizType)) {
            throw new ServiceException("业务类型不能为空");
        }
        if (bizId == null) {
            throw new ServiceException("业务单据ID不能为空");
        }
        // readBytes 兼作「空文件/null」的守卫，故排在最前（其余校验都需要 file 非空）
        byte[] content = readBytes(file);
        String fileName = resolveFileName(file);
        validateContentType(file.getContentType());
        // 以实际读到的字节数为准，不信任 MultipartFile#getSize()
        if (content.length > MAX_FILE_SIZE) {
            throw new ServiceException("PDF 文件不能超过 {}MB（当前 {} 字节）",
                MAX_FILE_SIZE / 1024 / 1024, content.length);
        }
        // 服务端准入的核心：认字节，不认后缀名、也不认客户端声明的 Content-Type
        // （两者都能被伪造，浏览器也会错报）。与 signDocument 读源正文时同一个判定方法。
        if (!PdfContentStamper.isPdf(content)) {
            throw new ServiceException("仅支持 PDF 文件（文件头不是 %PDF-），请重新导出后再上传");
        }
        int pageCount = countPages(content);
        String fileHash = Sm3Utils.hashHexBytes(content);

        // ② 换版必须串行：并发上传若各自读到「当前版本 = N」，会写出两行 doc_version=N+1，
        //    并各自把对方置成 is_current='0' —— 结果可能是零行 is_current='1'。
        //    锁的范围取 (租户, 业务类型, 业务单据)，不同单据之间互不阻塞。
        LockInfo lock = lockTemplate.lock(docLockKey(bizType, bizId), LOCK_EXPIRE, LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("文档上传通道繁忙，请稍后重试");
        }
        boolean releaseDeferred = false;
        try {
            EsSignDocument latest = selectLatest(bizType, bizId);
            int version = (latest == null || latest.getDocVersion() == null) ? 1 : latest.getDocVersion() + 1;

            // 先写对象存储拿到 ossId，再在同一事务里写库：反过来会出现「行指向一个不存在的文件」。
            // 对象存储的写入无法随事务回滚，极端情况下会留下一个无行引用的孤儿对象 ——
            // 可接受，绝不接受有一行指向不存在的正文（与 signDocument 同一取舍）。
            Long fileOssId = ossAccessor().store(content, fileName, PDF_CONTENT_TYPE);
            if (latest != null) {
                markPreviousVersionsNotCurrent(bizType, bizId);
            }

            EsSignDocument entity = new EsSignDocument();
            Long docId = IdWorker.getId();
            entity.setDocId(docId);
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setFileOssId(fileOssId);
            entity.setFileName(fileName);
            entity.setFileSize((long) content.length);
            entity.setFileHash(fileHash);
            entity.setPageCount(pageCount);
            entity.setDocVersion(version);
            entity.setIsCurrent(IS_CURRENT_YES);
            entity.setSignStatus(SIGN_STATUS_UNSIGNED);
            baseMapper.insert(entity);

            // 锁要活到事务提交之后才真正排他，否则第二个上传者会在第一个事务提交前拿到锁、
            // 读到同一个「当前版本 N」（MySQL 默认 REPEATABLE READ 下读不到未提交的行），
            // 于是写出第二行 doc_version=N+1 且同样标 is_current='1'。
            releaseDeferred = releaseLockAfterTransaction(lock);

            log.info("待签文档登记完成: docId={}, bizType={}, bizId={}, docVersion={}, fileOssId={}, size={}, pageCount={}",
                docId, bizType, bizId, version, fileOssId, content.length, pageCount);
            return docId;
        } finally {
            // 延迟释放已注册时，由事务回调负责释放；其余路径（校验失败/无事务上下文）在此立即释放
            if (!releaseDeferred) {
                lockTemplate.releaseLock(lock);
            }
        }
    }

    /**
     * 把换版锁的释放推迟到当前事务结束（提交或回滚）之后。
     *
     * <p><b>为什么不能直接在 finally 里放锁：</b>本方法开事务（{@code REQUIRED}），
     * 而 {@code finally} 在<b>提交之前</b>执行。锁一放，排队等待的第二个上传请求立刻进来，
     * 但它读到的仍是「第一个事务尚未提交」的库状态 —— 同样的 {@code doc_version} 被算出两次，
     * 两行都标 {@code is_current='1'}，正是本模块要守住的不变量被破坏。
     * 延迟到提交后释放，锁的持有区间才真正覆盖「读版本 → 写新版本 → 提交」这段临界区。</p>
     *
     * <p>无事务上下文时（直连调用、单元测试）同步机制不可用，返回 {@code false}
     * 由调用方在 {@code finally} 里立即释放 —— 与本仓库
     * {@code EqEquipmentServiceImpl} 的 afterCommit 写法同一形态。</p>
     *
     * @param lock 待释放的锁
     * @return true 表示已注册延迟释放（调用方不得再释放一次）
     */
    private boolean releaseLockAfterTransaction(LockInfo lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lockTemplate.releaseLock(lock);
            }
        });
        return true;
    }

    @Override
    public List<EsSignDocumentVo> listByBiz(String bizType, Long bizId) {
        if (StringUtils.isBlank(bizType)) {
            throw new ServiceException("业务类型不能为空");
        }
        return baseMapper.selectVoList(Wrappers.<EsSignDocument>lambdaQuery()
            .eq(EsSignDocument::getBizType, bizType)
            .eq(bizId != null, EsSignDocument::getBizId, bizId)
            .orderByDesc(EsSignDocument::getDocVersion));
    }

    @Override
    public EsSignDocumentVo getCurrent(String bizType, Long bizId) {
        if (StringUtils.isBlank(bizType)) {
            throw new ServiceException("业务类型不能为空");
        }
        if (bizId == null) {
            throw new ServiceException("业务单据ID不能为空");
        }
        // 「只有一行 is_current='1'」由 uploadDocument 在换版锁内维护；
        // 这里仍加 orderBy + LIMIT 1，是为了在历史脏数据（早期手工插入的两行 '1'）下
        // 取最新的那一版，而不是抛 TooManyResultsException 让整页打不开。
        return baseMapper.selectVoOne(Wrappers.<EsSignDocument>lambdaQuery()
            .eq(EsSignDocument::getBizType, bizType)
            .eq(EsSignDocument::getBizId, bizId)
            .eq(EsSignDocument::getIsCurrent, IS_CURRENT_YES)
            .orderByDesc(EsSignDocument::getDocVersion)
            .orderByDesc(EsSignDocument::getDocId)
            .last("LIMIT 1"));
    }

    @Override
    public EsSignDocumentVo getByDocId(Long docId) {
        if (docId == null) {
            throw new ServiceException("文档ID不能为空");
        }
        EsSignDocumentVo vo = baseMapper.selectVoById(docId);
        if (vo == null) {
            throw new ServiceException("待签文档不存在");
        }
        return vo;
    }

    /**
     * 取该业务键下版本号最大的行（换版锁内调用）。
     *
     * <p>按 {@code doc_version} 倒序取而不是按 {@code is_current='1'} 取：即便历史数据留下了
     * 「没有当前版本」或「多个当前版本」的脏状态，版本号也能继续单调递增，不会写出重复版本。</p>
     *
     * @param bizType 业务类型
     * @param bizId   业务单据ID
     * @return 最新一行；该业务键尚无文档时返回 null
     */
    private EsSignDocument selectLatest(String bizType, Long bizId) {
        return baseMapper.selectOne(Wrappers.<EsSignDocument>lambdaQuery()
            .eq(EsSignDocument::getBizType, bizType)
            .eq(EsSignDocument::getBizId, bizId)
            .orderByDesc(EsSignDocument::getDocVersion)
            .orderByDesc(EsSignDocument::getDocId)
            .last("LIMIT 1"));
    }

    /**
     * 把该业务键下所有 {@code is_current='1'} 的历史行置为 '0'（换版锁内调用）。
     *
     * <p>用条件更新而不是按主键更新：一次语句即保证「任一时刻最多一行 is_current='1'」
     * 这一不变量，同时顺带清理历史脏数据里可能存在的多行 '1'。</p>
     *
     * @param bizType 业务类型
     * @param bizId   业务单据ID
     */
    private void markPreviousVersionsNotCurrent(String bizType, Long bizId) {
        LambdaUpdateWrapper<EsSignDocument> luw = Wrappers.<EsSignDocument>lambdaUpdate()
            .eq(EsSignDocument::getBizType, bizType)
            .eq(EsSignDocument::getBizId, bizId)
            .eq(EsSignDocument::getIsCurrent, IS_CURRENT_YES)
            .set(EsSignDocument::getIsCurrent, IS_CURRENT_NO);
        baseMapper.update(null, luw);
    }

    /**
     * 换版锁 key：{@code es:sign:doc:{tenantId}:{bizType}:{bizId}}。
     *
     * <p>必须带租户：多租户下不同租户的同名业务键是完全无关的单据，不能互相阻塞。</p>
     *
     * @param bizType 业务类型
     * @param bizId   业务单据ID
     * @return 锁 key
     */
    private static String docLockKey(String bizType, Long bizId) {
        String tenantId = LoginHelper.getTenantId();
        return DOC_LOCK_PREFIX + (tenantId == null ? "" : tenantId) + ":" + bizType + ":" + bizId;
    }

    /**
     * 解析并校验文件名。
     *
     * @param file 上传文件
     * @return 去掉目录前缀后的文件名
     */
    private static String resolveFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (StringUtils.isBlank(original)) {
            throw new ServiceException("上传文件缺少文件名，无法登记正文");
        }
        // 部分客户端（如旧版 IE）会带完整路径，只保留基名
        String name = original.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(PDF_SUFFIX)) {
            throw new ServiceException("仅支持 PDF 文件，当前文件名【{}】", name);
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            throw new ServiceException("文件名过长（最多 {} 个字符）", MAX_FILE_NAME_LENGTH);
        }
        return name;
    }

    /**
     * 校验客户端声明的 Content-Type。
     *
     * <p>取值口径：<b>声明了具体类型就必须是 PDF</b>（{@code application/pdf}），
     * 未声明或声明为 {@code application/octet-stream} 一律放行 —— 不少浏览器/客户端
     * 对本地文件只给通用二进制类型，为其拒绝一次合法上传是真实的可用性缺陷。
     * Content-Type 只是「提前给出可读错误」的辅助，真正的准入是字节头魔数。</p>
     *
     * @param contentType 客户端声明的内容类型（可空）
     */
    private static void validateContentType(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if (PDF_CONTENT_TYPE.equals(normalized) || OCTET_STREAM.equals(normalized)) {
            return;
        }
        throw new ServiceException("文件类型必须是 PDF（当前声明为 {}）", contentType);
    }

    /**
     * 读取上传字节。
     *
     * @param file 上传文件
     * @return 字节内容
     */
    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("上传文件读取失败，请重试");
        }
    }

    /**
     * 用 PDFBox 解析页数。
     *
     * <p>解析同时充当「文件确实是一份能打开的 PDF」的准入：损坏/加密的 PDF 在这里被拒绝，
     * 绝不会以 {@code page_count=null} 的姿态进库 —— 页数是后续落位合法性（页码越界）
     * 的判据，缺了它，签章阶段就只能靠 {@code StampSpec} 抛异常才发现问题。</p>
     *
     * @param content PDF 字节
     * @return 页数
     */
    private static int countPages(byte[] content) {
        try (PDDocument doc = Loader.loadPDF(content)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            log.warn("PDF 解析失败，拒绝登记正文（size={}）", content.length, e);
            throw new ServiceException("PDF 文件解析失败（可能已损坏或被加密），无法登记正文");
        }
    }

    /**
     * 取对象存储 SPI 实现。
     *
     * <p>实现方在 {@code ruoyi-system}（避免 Maven 循环依赖，见 {@link OssContentAccessor}
     * 的说明），缺失时给出明确业务异常而不是 NPE。</p>
     *
     * @return SPI 实现
     */
    private OssContentAccessor ossAccessor() {
        OssContentAccessor accessor =
            ossAccessorProvider == null ? null : ossAccessorProvider.getIfAvailable();
        if (accessor == null) {
            throw new ServiceException("对象存储内容访问实现缺失，无法读写文档正文");
        }
        return accessor;
    }
}
