package com.nexus.mes.eq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MES备件→ERP 过账同步台账 Mapper（MES-EQ-04）
 *
 * @author Nexus
 */
@Mapper
public interface EqSpareErpSyncMapper extends BaseMapper<EqSpareErpSync> {

    /**
     * 同步指定备件的全部 ERP 过账台账编码快照，包含逻辑删除历史行。
     */
    @Update("UPDATE eq_spare_erp_sync SET spare_code = #{spareCode} "
        + "WHERE tenant_id = #{tenantId} AND spare_id = #{spareId}")
    int updateSpareCodeBySpareId(@Param("tenantId") String tenantId,
                                 @Param("spareId") Long spareId,
                                 @Param("spareCode") String spareCode);
}
