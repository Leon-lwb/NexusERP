package com.nexus.ai.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.oss.entity.UploadResult;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 知识库文档处理服务
 * <p>整合文档上传→OSS持久化→解析→分段→向量化→存储的完整流程</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeProcessService {

    /** 文档解析服务，负责将上传文件解析为纯文本。 */
    private final DocumentParserService documentParserService;
    /** 文本分段服务，负责按知识库配置切分片段。 */
    private final ChunkingService chunkingService;
    /** 向量存储服务，负责片段向量化与入库。 */
    private final VectorStoreService vectorStoreService;
    /** 知识文档表 Mapper，负责文档记录读写与状态更新。 */
    private final AiKnowledgeDocumentMapper documentMapper;
    /** 知识片段表 Mapper，负责片段落库。 */
    private final AiKnowledgeChunkMapper chunkMapper;
    /** 知识库业务服务，用于读取知识库分段/向量化配置。 */
    private final IAiKnowledgeService aiKnowledgeService;
    /** 向量索引安全重建服务，重建/失败期间拦截文档导入与重试，避免向量被 DROP 清掉或维度混杂。 */
    private final KnowledgeIndexRebuildService knowledgeIndexRebuildService;

    /** AI 专用线程池，用于异步提交文档处理任务。 */
    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /**
     * 上传并处理文档
     * <p>1. 上传文件到 OSS 持久化存储</p>
     * <p>2. 创建文档记录</p>
     * <p>3. 异步处理：从 OSS 下载→解析→分段→向量化→存储</p>
     *
     * @param knowledgeId 知识库ID
     * @param file        上传的文件
     * @return 文档ID
     */
    public Long uploadAndProcess(Long knowledgeId, MultipartFile file) {
        final String tenantId = captureTenantId();
        requireKnowledge(knowledgeId, tenantId);
        // 索引状态门：重建中/失败的知识库禁止导入（导入的向量会被重建 DROP 清掉，vectorId 悬空）
        knowledgeIndexRebuildService.assertIndexReadyForDocumentOps(knowledgeId);

        // 1. 校验文件类型
        String fileName = file.getOriginalFilename();
        if (!documentParserService.isSupported(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }

        // 2. 上传文件到 OSS
        String ossUrl;
        try {
            String suffix = fileName.substring(fileName.lastIndexOf("."));
            UploadResult uploadResult = OssFactory.instance().uploadSuffix(file.getBytes(), suffix, file.getContentType());
            ossUrl = uploadResult.getUrl();
            log.info("文档已上传到 OSS: fileName={}, url={}", fileName, ossUrl);
        } catch (Exception e) {
            log.warn("知识文档上传失败: errorType={}", e.getClass().getSimpleName());
            throw new ServiceException("文档上传失败，请稍后重试或联系管理员。");
        }

        // 3. 创建文档记录
        AiKnowledgeDocument document = new AiKnowledgeDocument();
        document.setTenantId(tenantId);
        document.setKnowledgeId(knowledgeId);
        document.setFileName(fileName);
        document.setFilePath(ossUrl);
        document.setFileSize(file.getSize());
        document.setChunkCount(0);
        document.setStatus("0"); // 待解析
        documentMapper.insert(document);

        Long documentId = document.getId();

        /** 异步处理文档，成功与失败回写均保持请求租户。 */
        submitDocumentProcessing(documentId, knowledgeId, tenantId, false);

        return documentId;
    }

    /**
     * 处理文档（从 OSS 下载→解析→分段→向量化→存储）
     *
     * @param documentId   文档ID
     * @param knowledgeId  知识库ID
     * @param tenantId     提交时捕获的精确租户身份
     * @param retry        是否重试；仅重试在核对来源与索引状态后清理旧片段和向量
     */
    private void processDocument(Long documentId, Long knowledgeId, String tenantId, boolean retry) {
        boolean documentClaimed = false;
        try {
            AiKnowledgeDocument document = documentMapper.selectById(documentId);
            if (document == null || !tenantId.equals(document.getTenantId())
                || !knowledgeId.equals(document.getKnowledgeId())) {
                throw new ServiceException("文档不存在或租户、知识库关联已失效，拒绝处理");
            }
            /** 原子认领：首次上传只允许待解析，重试允许待解析或失败；未认领不得清理或覆盖状态。 */
            int claimed = documentMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getId, documentId)
                .eq(AiKnowledgeDocument::getTenantId, tenantId)
                .eq(AiKnowledgeDocument::getKnowledgeId, knowledgeId)
                .in(AiKnowledgeDocument::getStatus, retry ? List.of("0", "3") : List.of("0"))
                .set(AiKnowledgeDocument::getStatus, "1"));
            if (claimed != 1) {
                return;
            }
            documentClaimed = true;
            AiKnowledgeVo knowledgeVo = requireKnowledge(knowledgeId, tenantId);
            knowledgeIndexRebuildService.assertIndexReadyForDocumentOps(knowledgeId);
            if (retry) {
                /** 核对来源与索引后先清理向量；向量清理失败时保留 SQL 旧片段，提交拒绝不触发清理。 */
                vectorStoreService.deleteByDocumentId(documentId, knowledgeId);
                chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
                    .eq(AiKnowledgeChunk::getDocumentId, documentId));
            }
            updateDocumentStatus(documentId, "1", null);

            // 1. 从 OSS 下载文件并解析（通过 MinIO Client 直接读取，避免私有桶 403）
            String content;
            try (InputStream inputStream = OssFactory.instance().getObjectContent(document.getFilePath())) {
                content = documentParserService.parse(inputStream, document.getFileName());
            }

            if (content == null || content.trim().isEmpty()) {
                updateDocumentStatus(documentId, "3", "文档内容为空");
                return;
            }

            // 2. 获取知识库配置用于分段和向量化
            Integer textBlockSize = knowledgeVo.getTextBlockSize();
            Integer overlapChar = knowledgeVo.getOverlapChar();
            String separator = knowledgeVo.getSeparator();
            String embeddingModel = knowledgeVo.getEmbeddingModel();

            // 3. 分段（使用知识库配置参数）
            List<ChunkingService.ChunkResult> chunks = chunkingService.split(content, textBlockSize, overlapChar, separator);
            if (chunks.isEmpty()) {
                updateDocumentStatus(documentId, "3", "分段结果为空");
                return;
            }

            // 4. 保存片段到数据库
            List<AiKnowledgeChunk> chunkEntities = new ArrayList<>();
            for (ChunkingService.ChunkResult chunk : chunks) {
                AiKnowledgeChunk chunkEntity = new AiKnowledgeChunk();
                chunkEntity.setTenantId(tenantId);
                chunkEntity.setDocumentId(documentId);
                chunkEntity.setKnowledgeId(knowledgeId);
                chunkEntity.setContent(chunk.getContent());
                chunkEntity.setChunkIndex(chunk.getIndex());
                chunkMapper.insert(chunkEntity);
                chunkEntities.add(chunkEntity);
            }

            // 5. 向量化并存储（使用知识库指定的 Embedding 模型）
            List<String> vectorIds = vectorStoreService.storeChunks(knowledgeId, chunkEntities, embeddingModel);

            // 6. 更新文档状态
            AiKnowledgeDocument updateDoc = new AiKnowledgeDocument();
            updateDoc.setId(documentId);
            updateDoc.setChunkCount(chunkEntities.size());
            updateDoc.setStatus("2"); // 已完成
            documentMapper.updateById(updateDoc);

            log.info("文档处理完成: documentId={}, chunks={}, vectors={}", documentId, chunkEntities.size(), vectorIds.size());
        } catch (Exception e) {
            log.error("文档处理异常: documentId={}, errorType={}", documentId, e.getClass().getSimpleName());
            if (documentClaimed) {
                /** 该字段会通过文档查询接口展示；厂商、OSS 或解析器原始异常可能含地址和凭据。 */
                updateDocumentStatus(documentId, "3", "文档处理失败，请检查资料格式、知识库配置或联系管理员。");
            }
        }
    }

    /**
     * 更新文档状态
     *
     * @param documentId   文档ID
     * @param status       状态（0待解析 1解析中 2已完成 3失败）
     * @param errorMessage 错误信息
     */
    private void updateDocumentStatus(Long documentId, String status, String errorMessage) {
        AiKnowledgeDocument updateDoc = new AiKnowledgeDocument();
        updateDoc.setId(documentId);
        updateDoc.setStatus(status);
        updateDoc.setErrorMessage(errorMessage != null && errorMessage.length() > 500
            ? errorMessage.substring(0, 500) : errorMessage);
        documentMapper.updateById(updateDoc);
    }

    /**
     * 重新处理文档（从 OSS 重新下载解析）
     * <p>入口只校验并提交任务；工作线程重核归属与索引就绪后再清理旧来源，提交拒绝不触发清理。</p>
     *
     * @param documentId 文档ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryProcess(Long documentId) {
        final String tenantId = captureTenantId();
        AiKnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null || !tenantId.equals(document.getTenantId()) || document.getKnowledgeId() == null) {
            throw new ServiceException("文档不存在或租户、知识库关联无效，拒绝重试");
        }
        requireKnowledge(document.getKnowledgeId(), tenantId);
        // 索引状态门：重建中/失败的知识库禁止重试（下方会删旧片段+向量并重导入，与重建互相破坏）
        knowledgeIndexRebuildService.assertIndexReadyForDocumentOps(document.getKnowledgeId());
        if (!"3".equals(document.getStatus()) && !"0".equals(document.getStatus())) {
            throw new IllegalStateException("只能重新处理待解析或失败的文档，当前状态: " + document.getStatus());
        }

        /** 异步重新处理，捕获知识库 ID，不能随文档实体后续变化而改写来源。 */
        submitDocumentProcessing(documentId, document.getKnowledgeId(), tenantId, true);

        log.info("重新处理文档: documentId={}", documentId);
    }

    /**
     * 提交处理任务，同时传播 AI 与数据库租户上下文，异步结束清理，内联执行恢复请求上下文。
     *
     * @param documentId  文档ID
     * @param knowledgeId 提交时确认的知识库ID
     * @param tenantId    提交时捕获的租户文本身份
     * @param retry       是否在工作线程校验通过后清理旧来源
     */
    private void submitDocumentProcessing(Long documentId, Long knowledgeId, String tenantId, boolean retry) {
        final Long userId = LoginHelper.getUserId();
        final Thread submittingThread = Thread.currentThread();
        Runnable processing = () -> {
            boolean inline = Thread.currentThread() == submittingThread;
            String previousTenant = AiRequestContext.getTenantId();
            Long previousUser = AiRequestContext.getUserId();
            if (!inline) {
                AiRequestContext.clear();
            }
            AiRequestContext.set(tenantId, userId);
            try {
                TenantHelper.dynamic(tenantId, () -> processDocument(documentId, knowledgeId, tenantId, retry));
            } finally {
                if (inline) {
                    AiRequestContext.set(previousTenant, previousUser);
                } else {
                    AiRequestContext.clear();
                }
            }
        };
        try {
            aiExecutor.execute(processing);
        } catch (RejectedExecutionException rejected) {
            /** 拒绝回写也必须重新核对归属，不能借失败处理修改排队期间已变更的文档。 */
            try {
                TenantHelper.dynamic(tenantId, () -> {
                    AiKnowledgeDocument current = documentMapper.selectById(documentId);
                    if (current != null && tenantId.equals(current.getTenantId())
                        && knowledgeId.equals(current.getKnowledgeId())) {
                        /** 拒绝回写同样采用 CAS，不能把其他任务已认领或已完成的文档重新开放。 */
                        AiKnowledgeDocument failure = new AiKnowledgeDocument();
                        failure.setStatus("3");
                        failure.setErrorMessage("文档处理任务未提交，请稍后重试。");
                        documentMapper.update(failure, new LambdaUpdateWrapper<AiKnowledgeDocument>()
                            .eq(AiKnowledgeDocument::getId, documentId)
                            .eq(AiKnowledgeDocument::getTenantId, tenantId)
                            .eq(AiKnowledgeDocument::getKnowledgeId, knowledgeId)
                            .in(AiKnowledgeDocument::getStatus, retry ? List.of("0", "3") : List.of("0")));
                    }
                });
            } catch (RuntimeException unavailable) {
                log.warn("知识文档提交拒绝后状态回写不可用: errorType={}", unavailable.getClass().getSimpleName());
            }
            throw new ServiceException("文档处理任务未提交，请稍后重试。");
        }
    }

    /**
     * 捕获明确的租户文本身份，缺失或非法时拒绝执行，不使用默认租户或数值转换。
     *
     * @return 保留前导零的租户身份
     */
    private String captureTenantId() {
        final String tenantId;
        try {
            tenantId = TenantHelper.getTenantId();
        } catch (Exception e) {
            throw new ServiceException("无法获取知识文档租户上下文，拒绝执行");
        }
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9_-]{1,20}")) {
            throw new ServiceException("知识文档租户上下文缺失或无效，拒绝执行");
        }
        return tenantId;
    }

    /**
     * 确认知识库仍可见且属于当前精确租户，禁止使用默认配置处理来源未知的文档。
     *
     * @param knowledgeId 知识库ID
     * @param tenantId    当前租户身份
     * @return 已核对归属的知识库配置
     */
    private AiKnowledgeVo requireKnowledge(Long knowledgeId, String tenantId) {
        AiKnowledgeVo knowledge = knowledgeId == null ? null : aiKnowledgeService.selectById(knowledgeId);
        if (knowledge == null || !tenantId.equals(knowledge.getTenantId())) {
            throw new ServiceException("知识库不存在或租户归属无效，拒绝处理文档");
        }
        return knowledge;
    }
}
