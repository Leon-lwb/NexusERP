package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI知识库Service实现
 * <p>包含级联删除（知识库→文档→片段）和文档数量统计功能</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKnowledgeServiceImpl implements IAiKnowledgeService {

    private final AiKnowledgeMapper baseMapper;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final AiModelMapper modelMapper;

    @Override
    public TableDataInfo<AiKnowledgeVo> selectPageList(AiKnowledgeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKnowledge> lqw = buildQueryWrapper(bo);
        Page<AiKnowledgeVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        TableDataInfo<AiKnowledgeVo> result = TableDataInfo.build(page);
        // 设置文档数量
        fillDocumentCount(result.getRows());
        return result;
    }

    @Override
    public List<AiKnowledgeVo> selectList(AiKnowledgeBo bo) {
        List<AiKnowledgeVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillDocumentCount(list);
        return list;
    }

    @Override
    public AiKnowledgeVo selectById(Long id) {
        AiKnowledgeVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            Long count = documentMapper.selectCount(Wrappers.<AiKnowledgeDocument>lambdaQuery()
                .eq(AiKnowledgeDocument::getKnowledgeId, id));
            vo.setDocumentCount(count.intValue());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(AiKnowledgeBo bo) {
        validateEmbeddingModel(bo.getEmbeddingModel());
        AiKnowledge entity = MapstructUtils.convert(bo, AiKnowledge.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiKnowledgeBo bo) {
        validateEmbeddingModel(bo.getEmbeddingModel());
        AiKnowledge entity = MapstructUtils.convert(bo, AiKnowledge.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 校验 Embedding 模型是否有效
     * <p>要求模型存在、类型为 EMBEDDING、状态为正常</p>
     *
     * @param modelName 模型名称
     * @throws IllegalArgumentException 模型无效时抛出
     */
    private void validateEmbeddingModel(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            throw new IllegalArgumentException("Embedding 模型不能为空，请先在模型管理中配置 EMBEDDING 类型模型");
        }
        AiModel model = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
            .eq(AiModel::getModelName, modelName)
            .eq(AiModel::getModelType, "EMBEDDING")
            .eq(AiModel::getStatus, "1")
            .last("LIMIT 1"));
        if (model == null) {
            throw new IllegalArgumentException("Embedding 模型 '" + modelName + "' 不存在或未启用，请先在模型管理中配置");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        // 级联删除片段
        chunkMapper.delete(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .in(AiKnowledgeChunk::getKnowledgeId, idList));
        // 级联删除文档
        documentMapper.delete(Wrappers.<AiKnowledgeDocument>lambdaQuery()
            .in(AiKnowledgeDocument::getKnowledgeId, idList));
        // 删除知识库
        return baseMapper.deleteBatchIds(idList);
    }

    private LambdaQueryWrapper<AiKnowledge> buildQueryWrapper(AiKnowledgeBo bo) {
        return Wrappers.<AiKnowledge>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getKnowledgeName()), AiKnowledge::getKnowledgeName, bo.getKnowledgeName())
            .eq(StringUtils.isNotBlank(bo.getStatus()), AiKnowledge::getStatus, bo.getStatus());
    }

    /**
     * 填充文档数量
     */
    private void fillDocumentCount(List<AiKnowledgeVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> knowledgeIds = list.stream().map(AiKnowledgeVo::getId).toList();
        List<AiKnowledgeDocument> documents = documentMapper.selectList(
            Wrappers.<AiKnowledgeDocument>lambdaQuery()
                .in(AiKnowledgeDocument::getKnowledgeId, knowledgeIds)
                .select(AiKnowledgeDocument::getKnowledgeId));
        Map<Long, Long> countMap = documents.stream()
            .collect(Collectors.groupingBy(AiKnowledgeDocument::getKnowledgeId, Collectors.counting()));
        list.forEach(vo -> vo.setDocumentCount(countMap.getOrDefault(vo.getId(), 0L).intValue()));
    }
}
