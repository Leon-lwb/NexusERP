package com.nexus.mes.eq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES备件→ERP 过账同步台账 Mapper（MES-EQ-04）
 *
 * @author Nexus
 */
@Mapper
public interface EqSpareErpSyncMapper extends BaseMapper<EqSpareErpSync> {
}
