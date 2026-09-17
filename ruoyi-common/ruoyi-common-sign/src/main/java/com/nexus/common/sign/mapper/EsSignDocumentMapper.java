package com.nexus.common.sign.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.domain.vo.EsSignDocumentVo;

/**
 * 待签正文 Mapper。
 *
 * <p>P2a-3 起 VO 泛型改为 {@link EsSignDocumentVo}（与同模块的
 * {@code EsSealMapper}/{@code EsSignatureMapper} 一致）：此前没有 VO，
 * 只能把实体自身当 VO 占位，导致 {@code selectVoList} 等「查出来直接是 VO」的方法
 * 实际返回实体。改 VO 泛型不影响任何既有调用 —— 现有代码走的全是
 * {@code selectById}/{@code selectOne}/{@code insert}/{@code updateById} 等实体方法。</p>
 *
 * @author nexus
 */
public interface EsSignDocumentMapper extends BaseMapperPlus<EsSignDocument, EsSignDocumentVo> {
}
