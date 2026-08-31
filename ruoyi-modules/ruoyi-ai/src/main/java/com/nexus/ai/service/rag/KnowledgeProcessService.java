package com.nexus.ai.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.oss.entity.UploadResult;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.oss.core.OssClient;
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
    private final java.util.concurrent.Executor aiExecutor;

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
            throw new RuntimeException("文件上传到 OSS 失败: " + e.getMessage(), e);
        }

        // 3. 创建文档记录
        AiKnowledgeDocument document = new AiKnowledgeDocument();
        document.setKnowledgeId(knowledgeId);
        document.setFileName(fileName);
        document.setFilePath(ossUrl);
        document.setFileSize(file.getSize());
        document.setChunkCount(0);
        document.setStatus("0"); // 待解析
        documentMapper.insert(document);

        Long documentId = document.getId();

        // 4. 异步处理文档
        // 请求线程捕获租户上下文，异步线程内 TenantHelper.dynamic 恢复，避免 chunk/document insert 时 tenant_id 落 0
        final String tenantIdStr = TenantHelper.getTenantId();
        aiExecutor.execute(() -> {
            try {
                TenantHelper.dynamic(tenantIdStr, () -> {
                    processDocument(documentId, knowledgeId);
                });
            } catch (Exception e) {
                log.error("文档处理失败: documentId={}, error={}", documentId, e.getMessage(), e);
                updateDocumentStatus(documentId, "3", e.getMessage());
            }
        });

        return documentId;
    }

    /**
     * 处理文档（从 OSS 下载→解析→分段→向量化→存储）
     *
     * @param documentId   文档ID
     * @param knowledgeId  知识库ID
     */
    private void processDocument(Long documentId, Long knowledgeId) {
        // 更新状态为解析中
        updateDocumentStatus(documentId, "1", null);

        try {
            AiKnowledgeDocument document = documentMapper.selectById(documentId);
            if (document == null) {
                throw new IllegalStateException("文档不存在: " + documentId);
            }

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
            var knowledgeVo = aiKnowledgeService.selectById(knowledgeId);
            Integer textBlockSize = knowledgeVo != null ? knowledgeVo.getTextBlockSize() : null;
            Integer overlapChar = knowledgeVo != null ? knowledgeVo.getOverlapChar() : null;
            String separator = knowledgeVo != null ? knowledgeVo.getSeparator() : null;
            String embeddingModel = knowledgeVo != null ? knowledgeVo.getEmbeddingModel() : null;

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
            log.error("文档处理异常: documentId={}", documentId, e);
            updateDocumentStatus(documentId, "3", e.getMessage());
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
     * <p>删除旧片段和向量使用事务保证一致性</p>
     *
     * @param documentId 文档ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryProcess(Long documentId) {
        AiKnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        // 索引状态门：重建中/失败的知识库禁止重试（下方会删旧片段+向量并重导入，与重建互相破坏）
        knowledgeIndexRebuildService.assertIndexReadyForDocumentOps(document.getKnowledgeId());
        if (!"3".equals(document.getStatus()) && !"0".equals(document.getStatus())) {
            throw new IllegalStateException("只能重新处理待解析或失败的文档，当前状态: " + document.getStatus());
        }

        // 删除旧的片段和向量
        chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
            .eq(AiKnowledgeChunk::getDocumentId, documentId));
        vectorStoreService.deleteByDocumentId(documentId, document.getKnowledgeId());

        // 异步重新处理（请求线程捕获租户上下文，异步线程内 TenantHelper.dynamic 恢复）
        final String tenantIdStr = TenantHelper.getTenantId();
        aiExecutor.execute(() -> {
            try {
                TenantHelper.dynamic(tenantIdStr, () -> {
                    processDocument(documentId, document.getKnowledgeId());
                });
            } catch (Exception e) {
                log.error("文档重试处理失败: documentId={}, error={}", documentId, e.getMessage(), e);
                updateDocumentStatus(documentId, "3", e.getMessage());
            }
        });

        log.info("重新处理文档: documentId={}", documentId);
    }
}
