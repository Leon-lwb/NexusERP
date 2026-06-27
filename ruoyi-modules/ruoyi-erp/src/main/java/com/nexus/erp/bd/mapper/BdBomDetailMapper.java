package com.nexus.erp.bd.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * BOM明细Mapper接口
 *
 * @author nexus
 */
public interface BdBomDetailMapper extends BaseMapperPlus<BdBomDetail, BdBomDetailVo> {

    /**
     * 按 bomId 集合批量查询明细 (消除 N+1 的核心方法)
     */
    @Select("<script>" +
            "SELECT * FROM bd_bom_detail WHERE bom_id IN " +
            "<foreach item='id' collection='bomIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND status = 1 AND del_flag = '0' ORDER BY sort_order" +
            "</script>")
    List<BdBomDetail> selectBatchByBomIds(@Param("bomIds") Collection<Long> bomIds);
}
