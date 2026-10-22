package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.service.rag.KnowledgeImportService;
import com.nexus.ai.service.rag.KnowledgeIndexRebuildService;
import com.nexus.ai.service.rag.KnowledgeProcessService;
import com.nexus.ai.service.rag.RetrieverService;
import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.bo.AiKnowledgeChunkBo;
import com.nexus.common.ai.domain.bo.AiKnowledgeDocumentBo;
import com.nexus.common.ai.domain.vo.AiKnowledgeChunkVo;
import com.nexus.common.ai.domain.vo.AiKnowledgeDocumentVo;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.service.IAiKnowledgeChunkService;
import com.nexus.common.ai.service.IAiKnowledgeDocumentService;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * AI 知识库管理 Controller
 * <p>提供知识库/文档/片段的 CRUD、文档上传与重试、检索测试等接口</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController extends BaseController {

    /** 知识库业务服务，提供知识库 CRUD 与分页查询。 */
    private final IAiKnowledgeService aiKnowledgeService;
    /** 知识库文档业务服务，提供文档 CRUD 与按知识库查询。 */
    private final IAiKnowledgeDocumentService aiKnowledgeDocumentService;
    /** 知识库片段业务服务，提供片段 CRUD 与按文档/知识库查询。 */
    private final IAiKnowledgeChunkService aiKnowledgeChunkService;
    /** 知识文档处理服务，负责上传后解析、分段、向量化。 */
    private final KnowledgeProcessService knowledgeProcessService;
    /** 知识批量导入服务，支持从 URL 列表批量导入文档。 */
    private final KnowledgeImportService knowledgeImportService;
    /** 知识检索服务，提供向量/混合检索与重排能力。 */
    private final RetrieverService retrieverService;
    /** 向量索引安全重建服务，编辑走确认门，另提供独立的重建触发入口。 */
    private final KnowledgeIndexRebuildService knowledgeIndexRebuildService;

    // ==================== 知识库管理 ====================

    /**
     * 分页查询知识库列表
     * <p>权限标识：{@code ai:knowledge:list}</p>
     */
    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/list")
    public TableDataInfo<AiKnowledgeVo> list(AiKnowledgeBo bo, PageQuery pageQuery) {
        return aiKnowledgeService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出知识库数据到 Excel
     * <p>权限标识：{@code ai:knowledge:export}</p>
     */
    @SaCheckPermission("ai:knowledge:export")
    @Log(title = "AI知识库管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(AiKnowledgeBo bo, HttpServletResponse response) {
        List<AiKnowledgeVo> list = aiKnowledgeService.selectList(bo);
        ExcelUtil.exportExcel(list, "AI知识库数据", AiKnowledgeVo.class, response);
    }

    /**
     * 根据主键获取知识库详细信息
     * <p>权限标识：{@code ai:knowledge:query}</p>
     */
    @SaCheckPermission("ai:knowledge:query")
    @GetMapping("/{id}")
    public R<AiKnowledgeVo> getInfo(@PathVariable Long id) {
        return R.ok(aiKnowledgeService.selectById(id));
    }

    /**
     * 新增知识库
     * <p>权限标识：{@code ai:knowledge:add}</p>
     */
    @SaCheckPermission("ai:knowledge:add")
    @Log(title = "AI知识库管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiKnowledgeBo bo) {
        return toAjax(aiKnowledgeService.insertByBo(bo));
    }

    /**
     * 修改知识库（经向量索引安全门）
     * <p>关键配置（Embedding 模型/维度/存储类型）变更且已有片段时，未携带
     * rebuildIndex=true 确认标记的请求返回错误码 REBUILD_INDEX_REQUIRED，由前端弹确认框</p>
     * <p>权限标识：{@code ai:knowledge:edit}</p>
     */
    @SaCheckPermission("ai:knowledge:edit")
    @Log(title = "AI知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiKnowledgeBo bo) {
        return toAjax(knowledgeIndexRebuildService.updateConfiguration(bo));
    }

    /**
     * 触发知识库向量索引重建（异步执行）
     * <p>活跃重建进行中（REBUILDING 且有存活任务）时拒绝，需等待完成；
     * 检测到崩溃遗留的中断重建（REBUILDING 但无存活任务）时强制重启恢复（整库重嵌入幂等）</p>
     * <p>权限标识：{@code ai:knowledge:rebuild}</p>
     */
    @SaCheckPermission("ai:knowledge:rebuild")
    @Log(title = "AI知识库索引重建", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/rebuild-index")
    public R<Void> rebuildIndex(@PathVariable Long id) {
        knowledgeIndexRebuildService.requestRebuild(id);
        return R.ok();
    }

    /**
     * 批量删除知识库（级联删除文档和片段）
     * <p>索引重建中（REBUILDING）拒绝：重建任务按片段快照重嵌入，删除会被复活成孤儿向量</p>
     * <p>权限标识：{@code ai:knowledge:remove}</p>
     */
    @SaCheckPermission("ai:knowledge:remove")
    @Log(title = "AI知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        for (Long id : ids) {
            knowledgeIndexRebuildService.assertNoActiveRebuild(id);
        }
        return toAjax(aiKnowledgeService.deleteByIds(ids));
    }

    // ==================== 文档管理 ====================

    /**
     * 分页查询知识库文档列表
     * <p>权限标识：{@code ai:knowledge:list}</p>
     */
    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/document/list")
    public TableDataInfo<AiKnowledgeDocumentVo> documentList(AiKnowledgeDocumentBo bo, PageQuery pageQuery) {
        return aiKnowledgeDocumentService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据知识库ID查询文档列表
     * <p>权限标识：{@code ai:knowledge:list}</p>
     */
    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/document/listByKnowledge/{knowledgeId}")
    public R<List<AiKnowledgeDocumentVo>> documentListByKnowledge(@PathVariable Long knowledgeId) {
        return R.ok(aiKnowledgeDocumentService.selectByKnowledgeId(knowledgeId));
    }

    /**
     * 根据主键获取文档详细信息
     * <p>权限标识：{@code ai:knowledge:query}</p>
     */
    @SaCheckPermission("ai:knowledge:query")
    @GetMapping("/document/{id}")
    public R<AiKnowledgeDocumentVo> getDocumentInfo(@PathVariable Long id) {
        return R.ok(aiKnowledgeDocumentService.selectById(id));
    }

    /**
     * 批量删除文档（级联删除片段）
     * <p>索引重建中（REBUILDING）拒绝：重建任务按片段快照重嵌入，删除会被复活成孤儿向量</p>
     * <p>权限标识：{@code ai:knowledge:remove}</p>
     */
    @SaCheckPermission("ai:knowledge:remove")
    @Log(title = "AI知识库文档管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/document/{ids}")
    public R<Void> removeDocument(@PathVariable Long[] ids) {
        for (Long id : ids) {
            var document = aiKnowledgeDocumentService.selectById(id);
            if (document != null) {
                knowledgeIndexRebuildService.assertNoActiveRebuild(document.getKnowledgeId());
            }
        }
        return toAjax(aiKnowledgeDocumentService.deleteByIds(ids));
    }

    /**
     * 上传文档到知识库（异步解析→分段→向量化）
     * <p>权限标识：{@code ai:knowledge:add}</p>
     */
    @SaCheckPermission("ai:knowledge:add")
    @Log(title = "AI知识库文档上传", businessType = BusinessType.INSERT)
    @PostMapping("/document/upload/{knowledgeId}")
    public R<Long> uploadDocument(@PathVariable Long knowledgeId, @RequestParam("file") MultipartFile file) {
        Long documentId = knowledgeProcessService.uploadAndProcess(knowledgeId, file);
        return R.ok(documentId);
    }

    /**
     * 重新处理文档（从 OSS 重新下载解析）
     * <p>权限标识：{@code ai:knowledge:edit}</p>
     */
    @SaCheckPermission("ai:knowledge:edit")
    @Log(title = "AI知识库文档重试", businessType = BusinessType.UPDATE)
    @PostMapping("/document/retry/{documentId}")
    public R<Void> retryDocument(@PathVariable Long documentId) {
        knowledgeProcessService.retryProcess(documentId);
        return R.ok();
    }

    // ==================== 批量导入 ====================

    /**
     * 批量从 URL 导入文档到知识库
     * <p>支持从指定 URL 列表批量下载文档并导入，每个 URL 对应一个文档</p>
     * <p>权限标识：{@code ai:knowledge:add}</p>
     *
     * @param knowledgeId 知识库ID
     * @param urls        文档 URL 列表
     * @return 导入结果（成功数/失败数/失败详情）
     */
    @SaCheckPermission("ai:knowledge:add")
    @Log(title = "AI知识库文档导入", businessType = BusinessType.IMPORT)
    @PostMapping("/document/importUrls/{knowledgeId}")
    public R<Map<String, Object>> importUrls(
        @PathVariable Long knowledgeId,
        @RequestBody List<String> urls) {
        // 索引状态门：重建中/失败的知识库禁止导入（向量会被重建 DROP 清掉，vectorId 悬空）
        knowledgeIndexRebuildService.assertIndexReadyForDocumentOps(knowledgeId);
        Map<String, Object> result = knowledgeImportService.importFromUrls(knowledgeId, urls);
        return R.ok(result);
    }

    // ==================== 检索测试 ====================

    /**
     * 知识库检索测试
     *
     * <p>权限标识：{@code ai:knowledge:query}
     *
     * <p>支持参数覆盖：topK、threshold、enableRerank、rerankModel、enableHybrid、hybridAlpha
     *
     * @param params 请求参数，包含 knowledgeId（知识库ID）、query（查询文本）及可选覆盖参数
     * @return 检索到的片段内容列表
     */
    @SaCheckPermission("ai:knowledge:query")
    @PostMapping("/retrieval")
    public R<List<String>> retrieval(@RequestBody Map<String, Object> params) {
        Long knowledgeId = Long.valueOf(params.get("knowledgeId").toString());
        String query = params.get("query").toString();
        return R.ok(retrieverService.retrieve(knowledgeId, query, params));
    }

    // ==================== 片段管理 ====================

    /**
     * 分页查询知识库片段列表
     * <p>权限标识：{@code ai:knowledge:list}</p>
     */
    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/chunk/list")
    public TableDataInfo<AiKnowledgeChunkVo> chunkList(AiKnowledgeChunkBo bo, PageQuery pageQuery) {
        return aiKnowledgeChunkService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据文档ID查询片段列表
     * <p>权限标识：{@code ai:knowledge:list}</p>
     */
    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/chunk/listByDocument/{documentId}")
    public R<List<AiKnowledgeChunkVo>> chunkListByDocument(@PathVariable Long documentId) {
        return R.ok(aiKnowledgeChunkService.selectByDocumentId(documentId));
    }

    /**
     * 根据知识库ID查询片段列表
     * <p>权限标识：{@code ai:knowledge:list}</p>
     */
    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/chunk/listByKnowledge/{knowledgeId}")
    public R<List<AiKnowledgeChunkVo>> chunkListByKnowledge(@PathVariable Long knowledgeId) {
        return R.ok(aiKnowledgeChunkService.selectByKnowledgeId(knowledgeId));
    }

}
