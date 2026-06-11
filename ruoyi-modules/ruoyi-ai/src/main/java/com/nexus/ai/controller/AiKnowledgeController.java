package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.bo.AiKnowledgeDocumentBo;
import com.nexus.common.ai.domain.bo.AiKnowledgeChunkBo;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.domain.vo.AiKnowledgeDocumentVo;
import com.nexus.common.ai.domain.vo.AiKnowledgeChunkVo;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.ai.service.IAiKnowledgeDocumentService;
import com.nexus.common.ai.service.IAiKnowledgeChunkService;
import com.nexus.ai.service.rag.KnowledgeProcessService;
import com.nexus.ai.service.rag.RetrieverService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    private final IAiKnowledgeService aiKnowledgeService;
    private final IAiKnowledgeDocumentService aiKnowledgeDocumentService;
    private final IAiKnowledgeChunkService aiKnowledgeChunkService;
    private final KnowledgeProcessService knowledgeProcessService;
    private final RetrieverService retrieverService;

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
     * 修改知识库
     * <p>权限标识：{@code ai:knowledge:edit}</p>
     */
    @SaCheckPermission("ai:knowledge:edit")
    @Log(title = "AI知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiKnowledgeBo bo) {
        return toAjax(aiKnowledgeService.updateByBo(bo));
    }

    /**
     * 批量删除知识库（级联删除文档和片段）
     * <p>权限标识：{@code ai:knowledge:remove}</p>
     */
    @SaCheckPermission("ai:knowledge:remove")
    @Log(title = "AI知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
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
     * <p>权限标识：{@code ai:knowledge:remove}</p>
     */
    @SaCheckPermission("ai:knowledge:remove")
    @Log(title = "AI知识库文档管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/document/{ids}")
    public R<Void> removeDocument(@PathVariable Long[] ids) {
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

    // ==================== 检索测试 ====================

    /**
     * 知识库检索测试
     * <p>权限标识：{@code ai:knowledge:query}</p>
     * <p>支持参数覆盖：topK、threshold、enableRerank、rerankModel、enableHybrid、hybridAlpha</p>
     *
     * @param params 请求参数，包含 knowledgeId（知识库ID）、query（查询文本）及可选覆盖参数
     * @return 检索到的片段内容列表
     */
    @SaCheckPermission("ai:knowledge:query")
    @PostMapping("/retrieval")
    public R<List<String>> retrieval(@RequestBody java.util.Map<String, Object> params) {
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
