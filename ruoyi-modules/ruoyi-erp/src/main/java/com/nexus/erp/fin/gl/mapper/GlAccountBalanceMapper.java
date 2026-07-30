package com.nexus.erp.fin.gl.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.fin.gl.domain.GlAccountBalance;
import com.nexus.erp.fin.gl.domain.vo.GlAccountBalanceVo;

/**
 * 科目余额 Mapper
 * <p>试算平衡表由 Service 层 Java 聚合(余额表 + 科目表),不走 XML(同模块无 XML 约定)。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface GlAccountBalanceMapper extends BaseMapperPlus<GlAccountBalance, GlAccountBalanceVo> {
}
