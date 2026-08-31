package com.nexus.ai.service.rag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.ai.service.model.AiModelConnectionTestService;
import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 知识库向量索引安全重建服务
 * <p>治理 Embedding 模型 / 向量维度 / 存储类型变更引发的向量索引失效：</p>
 * <ul>
 *   <li><b>确认门</b>：已有片段的知识库发生关键配置变更且未显式确认（rebuildIndex != true）时，
 *       抛出 {@code ServiceException("REBUILD_INDEX_REQUIRED")}（精确 token，前端弹重建确认框）</li>
 *   <li><b>生命周期</b>：REBUILDING/进度/FAILED 脱敏错误/READY+完成时间，状态在异步任务提交前落库</li>
 *   <li><b>并发防重</b>：REBUILDING 状态经条件 UPDATE（WHERE status &lt;&gt; 'REBUILDING'）原子抢占，
 *       本 JVM 内活跃任务经 {@link #runningRebuilds} 原子登记判活——双重提交只有一个赢家；
 *       重建端点对<b>活跃</b>任务拒绝、对<b>崩溃遗留的孤儿</b> REBUILDING 强制重启（操作幂等）；
 *       完成/失败/进度均为条件写入（WHERE status='REBUILDING'），被取代的任务无法改写状态</li>
 *   <li><b>异步执行</b>：aiIndexRebuildExecutor 专用线程池（AbortPolicy，满载即拒转 FAILED，
 *       绝不退回提交线程）+ 租户上下文透传（同 {@link KnowledgeProcessService}：请求线程捕获
 *       {@link TenantHelper#getTenantId()}，异步线程内 {@link TenantHelper#dynamic} 恢复）</li>
 * </ul>
 * <p>写入分工：本服务独占 vector_index_status / vector_index_error / vector_index_updated_time，
 * 重建过程中的 vector_index_progress 分批进度由 {@link VectorStoreService#rebuildKnowledge} 回写
 * （批量边界在向量库内部）；导入路径（storeChunks）不触碰索引状态。</p>
 *
 * @author nexus-school
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexRebuildService {

    /** 向量索引状态：就绪（字典 ai_knowledge_index_status） */
    public static final String STATUS_READY = "READY";
    /** 向量索引状态：重建中 */
    public static final String STATUS_REBUILDING = "REBUILDING";
    /** 向量索引状态：失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 关键配置变更未确认的错误码（精确 token，前端 Task 10 依赖匹配，勿改文案） */
    public static final String REBUILD_REQUIRED_CODE = "REBUILD_INDEX_REQUIRED";

    /** 知识库 Mapper，读写知识库配置与向量索引状态 */
    private final AiKnowledgeMapper knowledgeMapper;
    /** 片段 Mapper，判断是否存在片段与重载待重建片段 */
    private final AiKnowledgeChunkMapper chunkMapper;
    /** 模型 Mapper，解析 Embedding 模型 API Key（失败信息脱敏用） */
    private final AiModelMapper modelMapper;
    /** 知识库业务服务，常规更新委托其执行（内含 Embedding 模型校验与客户端索引字段剥离，Task 3） */
    private final IAiKnowledgeService aiKnowledgeService;
    /** 向量存储服务，执行真正的集合重建与批量重嵌入，并统一维度解析 */
    private final VectorStoreService vectorStoreService;
    /** 向量索引重建专用线程池（AbortPolicy：满载拒绝由下方回写 FAILED，不退回提交线程） */
    @Qualifier("aiIndexRebuildExecutor")
    private final Executor aiIndexRebuildExecutor;

    /**
     * 本 JVM 内正在运行的重建任务（knowledgeId 集合）
     * <p>用于区分"活跃重建"与"崩溃遗留的孤儿 REBUILDING"：登记在提交线程原子完成
     * （{@code add} 返回 false 即有活跃任务），任务 finally 中移除；JVM 崩溃后集合随进程消失，
     * 遗留的 REBUILDING 即可被重建端点安全强制重启。注意：单实例部署假设（与
     * VectorStoreService 的 milvusStores 缓存一致），多节点需换集中式租约。</p>
     */
    private final Set<Long> runningRebuilds = ConcurrentHashMap.newKeySet();

    /**
     * 更新知识库配置（带向量索引安全门）
     * <p>关键配置（embeddingModel / 解析后维度 / vectorStore）变更且知识库已有片段时：</p>
     * <ul>
     *   <li>未确认 → {@code ServiceException("REBUILD_INDEX_REQUIRED")}</li>
     *   <li>已确认（rebuildIndex=true）→ 持久化新配置 → 条件抢占 REBUILDING → 提交异步重建；
     *       抢占失败（已有重建任务持有锁）→ {@code ServiceException("向量索引正在重建中")}
     *       （确认编辑路径不支持强制重启，需等待或走重建端点）</li>
     * </ul>
     * <p>无片段或无关键变更 → 委托 {@link IAiKnowledgeService#updateByBo}，索引状态不受影响。</p>
     *
     * @param bo 知识库业务对象（含 rebuildIndex 请求标记）
     * @return 影响行数
     */
    public int updateConfiguration(AiKnowledgeBo bo) {
        AiKnowledge persisted = requireKnowledge(bo.getId());
        boolean hasChunks = chunkMapper.selectCount(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getKnowledgeId, bo.getId())) > 0;
        if (!hasChunks || !criticalConfigChanged(persisted, bo)) {
            return aiKnowledgeService.updateByBo(bo);
        }
        if (!Boolean.TRUE.equals(bo.getRebuildIndex())) {
            throw new ServiceException(REBUILD_REQUIRED_CODE);
        }
        // 1. 持久化新配置（updateByBo 内含 Embedding 模型校验与 vector_index_* 客户端字段剥离）；
        //    校验失败在抢占前抛出，避免claimed了锁却无任务提交、知识库卡在 REBUILDING
        int rows = aiKnowledgeService.updateByBo(bo);
        // 2. 条件抢占 REBUILDING（先于异步提交落库；0 行 = 并发竞争失败，保持严格拒绝）
        if (!claimRebuildLock(bo.getId())) {
            throw new ServiceException("向量索引正在重建中");
        }
        if (!submitAsyncRebuild(bo.getId())) {
            // 极小窗口：抢占成功但提交瞬间被并发端点任务抢先登记——唯一任务已在跑，按重建中拒绝
            throw new ServiceException("向量索引正在重建中");
        }
        return rows;
    }

    /**
     * 请求重建知识库向量索引（rebuild-index 端点入口）
     * <p>活跃/孤儿判别（{@link #runningRebuilds} 提交线程原子登记为最终权威）：</p>
     * <ul>
     *   <li>本 JVM 有<b>活跃</b>任务 → 拒绝，等待完成（在跑任务窗口可达分钟级，
     *       二次调用强抢会造成双跑：T2 的 DROP 清掉 T1 半成品向量、状态互相覆盖）</li>
     *   <li>无活跃任务但条件抢占失败（DB 处于 REBUILDING）→ <b>崩溃遗留的孤儿</b> →
     *       强制重启（整库 DROP + 重嵌入幂等），恢复中断的重建</li>
     *   <li>提交瞬间与并发请求竞争登记失败 → 拒绝（唯一任务已在跑）</li>
     * </ul>
     *
     * @param knowledgeId 知识库ID
     */
    public void requestRebuild(Long knowledgeId) {
        requireKnowledge(knowledgeId);
        if (runningRebuilds.contains(knowledgeId)) {
            throw new ServiceException("向量索引正在重建中");
        }
        if (!claimRebuildLock(knowledgeId)) {
            if (runningRebuilds.contains(knowledgeId)) {
                // claim 失败 = DB 已 REBUILDING，且仍有活跃任务持有 → 拒绝
                throw new ServiceException("向量索引正在重建中");
            }
            log.warn("[KnowledgeIndexRebuild] 检测到中断的重建任务，强制重启: knowledgeId={}", knowledgeId);
            forceReclaimRebuildLock(knowledgeId);
        }
        if (!submitAsyncRebuild(knowledgeId)) {
            // 与并发请求在提交瞬间竞争登记失败：唯一任务已在跑，按重建中拒绝
            throw new ServiceException("向量索引正在重建中");
        }
    }

    /**
     * 文档变更门（导入/重试等会产生新向量的操作）：索引非 READY（REBUILDING/FAILED）时拒绝
     * <p>重建期间导入的向量会在 DROP 时被清掉、片段 vectorId 悬空；FAILED 索引维度存疑，
     * 继续导入会混入错维度向量。</p>
     *
     * @param knowledgeId 知识库ID
     */
    public void assertIndexReadyForDocumentOps(Long knowledgeId) {
        String status = requireKnowledge(knowledgeId).getVectorIndexStatus();
        if (status != null && !STATUS_READY.equals(status)) {
            throw new ServiceException("知识库索引正在重建或重建失败，请完成重建后再处理文档");
        }
    }

    /**
     * 文档/知识库删除门：仅在重建进行中（REBUILDING）拒绝
     * <p>重建任务按开启时的片段快照重嵌入，删除会被快照复活（孤儿向量）；FAILED 状态允许删除
     * （清理坏配置知识库的出口，删除只移除数据、不产生错维度向量）。</p>
     *
     * @param knowledgeId 知识库ID
     */
    public void assertNoActiveRebuild(Long knowledgeId) {
        AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge != null && STATUS_REBUILDING.equals(knowledge.getVectorIndexStatus())) {
            throw new ServiceException("向量索引正在重建中，请完成重建后再删除文档");
        }
    }

    /**
     * 执行重建（异步工作任务，禁止向外抛任何异常/错误）
     * <p>重载知识库与片段 → 按当前片段文本重嵌入（不重新下载/解析源文档）→ READY/100/完成时间；
     * 失败（含 Error，如 NoClassDefFoundError）则捕获记录脱敏错误（API Key/Bearer 剥离 + 截断），
     * 状态置 FAILED——绝不遗留 REBUILDING 死锁状态。</p>
     *
     * @param knowledgeId 知识库ID
     */
    public void rebuild(Long knowledgeId) {
        String embeddingModelName = null;
        try {
            AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
            if (knowledge == null) {
                log.warn("[KnowledgeIndexRebuild] 知识库不存在，跳过重建: knowledgeId={}", knowledgeId);
                return;
            }
            embeddingModelName = knowledge.getEmbeddingModel();
            List<AiKnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<AiKnowledgeChunk>lambdaQuery()
                .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId)
                .orderByAsc(AiKnowledgeChunk::getDocumentId)
                .orderByAsc(AiKnowledgeChunk::getChunkIndex));
            vectorStoreService.rebuildKnowledge(knowledgeId, embeddingModelName,
                vectorStoreService.resolveEmbeddingDimension(embeddingModelName), chunks);
            markReady(knowledgeId);
            log.info("[KnowledgeIndexRebuild] 重建完成: knowledgeId={}, chunks={}", knowledgeId,
                chunks == null ? 0 : chunks.size());
        } catch (Throwable e) {
            // 捕获 Throwable（含 LinkageError 等）：异步任务任何逃逸都会让知识库永久卡在 REBUILDING
            log.error("[KnowledgeIndexRebuild] 重建失败: knowledgeId={}", knowledgeId, e);
            markFailed(knowledgeId, sanitizedError(embeddingModelName, e));
        }
    }

    /**
     * 判断持久化配置与请求配置的关键值（Embedding 模型 / 解析后维度 / 存储类型）是否发生变化
     * <p>请求侧字段为 null 表示不修改该列（MyBatis-Plus updateById 跳过 null 列），
     * 按持久化值参与比较，视为未变更；维度解析统一委托
     * {@link VectorStoreService#resolveEmbeddingDimension}（单一事实来源）。</p>
     */
    private boolean criticalConfigChanged(AiKnowledge persisted, AiKnowledgeBo requested) {
        String requestedModel = StringUtils.isBlank(requested.getEmbeddingModel())
            ? persisted.getEmbeddingModel()
            : requested.getEmbeddingModel();
        if (!StringUtils.isBlank(requestedModel)
            && !requestedModel.equals(persisted.getEmbeddingModel())) {
            return true;
        }
        if (StringUtils.isNotBlank(requested.getVectorStore())
            && !requested.getVectorStore().equals(persisted.getVectorStore())) {
            return true;
        }
        return vectorStoreService.resolveEmbeddingDimension(persisted.getEmbeddingModel())
            != vectorStoreService.resolveEmbeddingDimension(requestedModel);
    }

    private AiKnowledge requireKnowledge(Long knowledgeId) {
        if (knowledgeId == null) {
            throw new ServiceException("知识库ID不能为空");
        }
        AiKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new ServiceException("知识库不存在: " + knowledgeId);
        }
        return knowledge;
    }

    /**
     * 条件抢占重建锁：UPDATE ... SET status='REBUILDING', progress=0, error 清空
     * WHERE id=? AND vector_index_status &lt;&gt; 'REBUILDING'
     * <p>原子关闭双重入队竞争：两个并发确认/请求只有一个能影响行数，另一个收到 0 行即失败。</p>
     *
     * @return 是否抢占成功
     */
    private boolean claimRebuildLock(Long knowledgeId) {
        AiKnowledge patch = new AiKnowledge();
        patch.setVectorIndexStatus(STATUS_REBUILDING);
        patch.setVectorIndexProgress(0);
        patch.setVectorIndexError("");
        return knowledgeMapper.update(patch, Wrappers.<AiKnowledge>lambdaUpdate()
            .eq(AiKnowledge::getId, knowledgeId)
            .ne(AiKnowledge::getVectorIndexStatus, STATUS_REBUILDING)) > 0;
    }

    /**
     * 无条件重置为 REBUILDING/0（仅重建端点的强制重启恢复路径使用）
     */
    private void forceReclaimRebuildLock(Long knowledgeId) {
        AiKnowledge patch = new AiKnowledge();
        patch.setId(knowledgeId);
        patch.setVectorIndexStatus(STATUS_REBUILDING);
        patch.setVectorIndexProgress(0);
        patch.setVectorIndexError("");
        knowledgeMapper.updateById(patch);
    }

    /**
     * 条件写入完成状态：UPDATE ... SET READY/100/清空错误/完成时间 WHERE id=? AND status='REBUILDING'
     * <p>0 行 = 状态已不处于 REBUILDING（本任务已被取代/状态被外部改写）——仅告警，
     * 绝不覆盖他人状态（防止迟到的陈旧任务把半成品集合翻转成 READY）。</p>
     */
    private void markReady(Long knowledgeId) {
        AiKnowledge done = new AiKnowledge();
        done.setVectorIndexStatus(STATUS_READY);
        done.setVectorIndexProgress(100);
        done.setVectorIndexError("");
        done.setVectorIndexUpdatedTime(new Date());
        int rows = knowledgeMapper.update(done, Wrappers.<AiKnowledge>lambdaUpdate()
            .eq(AiKnowledge::getId, knowledgeId)
            .eq(AiKnowledge::getVectorIndexStatus, STATUS_REBUILDING));
        if (rows == 0) {
            log.warn("[KnowledgeIndexRebuild] 完成写入被跳过（任务已被取代，状态非 REBUILDING）: knowledgeId={}", knowledgeId);
        }
    }

    /**
     * 条件写入失败状态（WHERE status='REBUILDING'；进度保留最后一次批次值；
     * 不写完成时间——该列语义为"最近完成时间"，FAILED 行不应展示）
     * <p>0 行 = 已被取代，仅告警不改写。</p>
     */
    private void markFailed(Long knowledgeId, String sanitizedError) {
        try {
            AiKnowledge update = new AiKnowledge();
            update.setVectorIndexStatus(STATUS_FAILED);
            update.setVectorIndexError(sanitizedError);
            int rows = knowledgeMapper.update(update, Wrappers.<AiKnowledge>lambdaUpdate()
                .eq(AiKnowledge::getId, knowledgeId)
                .eq(AiKnowledge::getVectorIndexStatus, STATUS_REBUILDING));
            if (rows == 0) {
                log.warn("[KnowledgeIndexRebuild] 失败写入被跳过（任务已被取代，状态非 REBUILDING）: knowledgeId={}", knowledgeId);
            }
        } catch (Exception e) {
            log.error("[KnowledgeIndexRebuild] 写入 FAILED 状态失败: knowledgeId={}", knowledgeId, e);
        }
    }

    /**
     * 错误脱敏：复用 {@link AiModelConnectionTestService#sanitize}（API Key 替换 + Bearer 令牌剥离 +
     * 300 字符截断，列宽 VARCHAR(500) 内），并尽可能带入模型 API Key 做精确替换
     */
    private String sanitizedError(String embeddingModelName, Throwable error) {
        String apiKey = null;
        if (StringUtils.isNotBlank(embeddingModelName)) {
            AiModel model = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getModelName, embeddingModelName)
                .last("LIMIT 1"));
            if (model != null) {
                apiKey = model.getApiKey();
            }
        }
        return AiModelConnectionTestService.sanitize(error.getMessage(), apiKey);
    }

    /**
     * 提交异步重建（aiIndexRebuildExecutor + 租户上下文透传）
     * <p>提交线程先向 {@link #runningRebuilds} <b>原子登记</b>（失败即有活跃任务，返回 false，
     * 这是双重提交的最终权威闸门——claim 与登记之间的微观间隙由本原子操作收敛为单赢家）；
     * 任务 finally 中移除登记。异步 runnable 永不抛出（rebuild 内部全量捕获 Throwable）；
     * 专用线程池满载被 AbortPolicy 拒绝时回写 FAILED（管理员可重试），不阻塞提交线程。</p>
     *
     * @return 是否登记并提交成功（false = 已有活跃任务，调用方应按重建中拒绝）
     */
    private boolean submitAsyncRebuild(Long knowledgeId) {
        if (!runningRebuilds.add(knowledgeId)) {
            return false;
        }
        final String tenantId = captureTenantId();
        try {
            aiIndexRebuildExecutor.execute(() -> {
                try {
                    if (tenantId == null || tenantId.isEmpty()) {
                        rebuild(knowledgeId);
                    } else {
                        TenantHelper.dynamic(tenantId, () -> rebuild(knowledgeId));
                    }
                } finally {
                    runningRebuilds.remove(knowledgeId);
                }
            });
        } catch (RejectedExecutionException e) {
            runningRebuilds.remove(knowledgeId);
            log.error("[KnowledgeIndexRebuild] 重建任务提交被拒绝: knowledgeId={}", knowledgeId, e);
            markFailed(knowledgeId, "向量索引重建任务提交失败，请稍后重试");
        }
        return true;
    }

    /**
     * 捕获当前租户上下文（无租户/非 Web 环境——如单元测试——返回 null，按无租户执行）
     */
    private String captureTenantId() {
        try {
            return TenantHelper.getTenantId();
        } catch (Exception e) {
            return null;
        }
    }
}
