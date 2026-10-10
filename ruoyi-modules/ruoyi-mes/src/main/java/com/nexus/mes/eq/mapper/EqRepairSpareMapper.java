package com.nexus.mes.eq.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.eq.domain.EqRepairSpare;
import com.nexus.mes.eq.domain.vo.EqRepairSpareVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MES维修领用备件明细 Mapper接口
 *
 * @author Nexus
 */
public interface EqRepairSpareMapper extends BaseMapperPlus<EqRepairSpare, EqRepairSpareVo> {

    /**
     * 同步指定备件的全部维修领用编码快照，包含逻辑删除历史行。
     */
    @Update("UPDATE eq_repair_spare SET spare_code = #{spareCode} "
        + "WHERE tenant_id = #{tenantId} AND spare_id = #{spareId}")
    int updateSpareCodeBySpareId(@Param("tenantId") String tenantId,
                                 @Param("spareId") Long spareId,
                                 @Param("spareCode") String spareCode);
}
