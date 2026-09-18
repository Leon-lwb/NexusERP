package com.nexus.common.sign.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.vo.SignatureVo;

/**
 * 签名记录 Mapper。
 * <p>append-only：仅使用 insert / select，禁止调用 updateById / deleteById。</p>
 *
 * @author nexus
 */
public interface EsSignatureMapper extends BaseMapperPlus<EsSignature, SignatureVo> {
}
