package com.nexus.mes.eq.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.eq.domain.EqSpareReturnLine;
import com.nexus.mes.eq.domain.vo.EqSpareReturnLineVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MES备件退库单明细 Mapper接口（MES-EQ-04 收口）
 *
 * @author Nexus
 */
public interface EqSpareReturnLineMapper extends BaseMapperPlus<EqSpareReturnLine, EqSpareReturnLineVo> {

    /**
     * 同步指定备件的全部退库明细编码快照，包含逻辑删除历史行。
     */
    @Update("UPDATE eq_spare_return_line SET spare_code = #{spareCode} "
        + "WHERE tenant_id = #{tenantId} AND spare_id = #{spareId}")
    int updateSpareCodeBySpareId(@Param("tenantId") String tenantId,
                                 @Param("spareId") Long spareId,
                                 @Param("spareCode") String spareCode);
}
