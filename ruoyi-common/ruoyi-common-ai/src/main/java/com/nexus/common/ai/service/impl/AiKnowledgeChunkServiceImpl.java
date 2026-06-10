package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.ai.domain.bo.AiKnowledgeChunkBo;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.vo.AiKnowledgeChunkVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.service.IAiKnowledgeChunkService;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI知识库文档片段Service实现
 *
 * @author NexusERP
 * @since 1.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKnowledgeChunkServiceImpl implements IAiKnowledgeChunkService {

    private final AiKnowledgeChunkMapper baseMapper;

    @Override
    public TableDataInfo<AiKnowledgeChunkVo> selectPageList(AiKnowledgeChunkBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKnowledgeChunk> lqw = buildQueryWrapper(bo);
        Page<AiKnowledgeChunkVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<AiKnowledgeChunkVo> selectByDocumentId(Long documentId) {
        return baseMapper.selectVoList(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getDocumentId, documentId));
    }

    @Override
    public List<AiKnowledgeChunkVo> selectByKnowledgeId(Long knowledgeId) {
        return baseMapper.selectVoList(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId));
    }

    @Override
    public int insertByBo(AiKnowledgeChunkBo bo) {
        AiKnowledgeChunk entity = MapstructUtils.convert(bo, AiKnowledgeChunk.class);
        return baseMapper.insert(entity);
    }

    @Override
    public int deleteByDocumentId(Long documentId) {
        return baseMapper.delete(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getDocumentId, documentId));
    }

    private LambdaQueryWrapper<AiKnowledgeChunk> buildQueryWrapper(AiKnowledgeChunkBo bo) {
        return Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(bo.getDocumentId() != null, AiKnowledgeChunk::getDocumentId, bo.getDocumentId())
            .eq(bo.getKnowledgeId() != null, AiKnowledgeChunk::getKnowledgeId, bo.getKnowledgeId());
    }
}
