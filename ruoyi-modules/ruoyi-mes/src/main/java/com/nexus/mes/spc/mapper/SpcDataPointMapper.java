package com.nexus.mes.spc.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.spc.domain.SpcDataPoint;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * MES SPC数据点 Mapper接口
 *
 * @author Nexus
 */
public interface SpcDataPointMapper extends BaseMapperPlus<SpcDataPoint, SpcDataPointVo> {

    /**
     * 物理删除组号超出本次分析分组范围的旧数据点（含历史逻辑删除僵尸行）。
     * <p>analyze 按 sample_group 1..N 连续 upsert，因此"范围外"等价于
     * {@code sample_group > maxGroup}；实体带 {@code @TableLogic}，{@code deleteByIds} 只会打
     * del_flag='2' 而残留僵尸行，故此处显式物理删除。</p>
     *
     * @param chartId  控制图ID
     * @param maxGroup 本次分析的最大组号
     * @return 删除行数
     */
    @Delete("DELETE FROM spc_data_point WHERE chart_id = #{chartId} AND sample_group > #{maxGroup}")
    int physicalDeleteOutOfRange(@Param("chartId") Long chartId, @Param("maxGroup") int maxGroup);
}
