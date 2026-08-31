package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.ai.domain.bo.AiKnowledgeDocumentBo;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeDocumentVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeDocumentService;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI知识库文档Service实现
 * <p>包含级联删除（文档→片段）功能</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@RequiredArgsConstructor
@Service
public class AiKnowledgeDocumentServiceImpl implements IAiKnowledgeDocumentService {

    private final AiKnowledgeDocumentMapper baseMapper;
    private final AiKnowledgeChunkMapper chunkMapper;

    @Override
    public TableDataInfo<AiKnowledgeDocumentVo> selectPageList(AiKnowledgeDocumentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiKnowledgeDocument> lqw = buildQueryWrapper(bo);
        Page<AiKnowledgeDocumentVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<AiKnowledgeDocumentVo> selectByKnowledgeId(Long knowledgeId) {
        return baseMapper.selectVoList(Wrappers.<AiKnowledgeDocument>lambdaQuery()
            .eq(AiKnowledgeDocument::getKnowledgeId, knowledgeId));
    }

    @Override
    public AiKnowledgeDocumentVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(AiKnowledgeDocumentBo bo) {
        AiKnowledgeDocument entity = MapstructUtils.convert(bo, AiKnowledgeDocument.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiKnowledgeDocumentBo bo) {
        AiKnowledgeDocument entity = MapstructUtils.convert(bo, AiKnowledgeDocument.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        // 级联删除片段
        chunkMapper.delete(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .in(AiKnowledgeChunk::getDocumentId, idList));
        // 删除文档
        return baseMapper.deleteBatchIds(idList);
    }

    private LambdaQueryWrapper<AiKnowledgeDocument> buildQueryWrapper(AiKnowledgeDocumentBo bo) {
        return Wrappers.<AiKnowledgeDocument>lambdaQuery()
            .eq(bo.getKnowledgeId() != null, AiKnowledgeDocument::getKnowledgeId, bo.getKnowledgeId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), AiKnowledgeDocument::getStatus, bo.getStatus());
    }
}
