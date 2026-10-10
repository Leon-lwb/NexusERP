package com.nexus.mes.eq.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.vo.EqSpareStockLogVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MES备件库存流水 Mapper接口
 *
 * @author Nexus
 */
public interface EqSpareStockLogMapper extends BaseMapperPlus<EqSpareStockLog, EqSpareStockLogVo> {

    /**
     * 同步指定备件的全部库存流水编码快照，包含逻辑删除历史行。
     */
    @Update("UPDATE eq_spare_stock_log SET spare_code = #{spareCode} "
        + "WHERE tenant_id = #{tenantId} AND spare_id = #{spareId}")
    int updateSpareCodeBySpareId(@Param("tenantId") String tenantId,
                                 @Param("spareId") Long spareId,
                                 @Param("spareCode") String spareCode);
}
