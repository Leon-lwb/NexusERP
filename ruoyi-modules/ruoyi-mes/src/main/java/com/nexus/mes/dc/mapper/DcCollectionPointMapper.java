package com.nexus.mes.dc.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.domain.vo.DcCollectionPointVo;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * MES数据采集点 Mapper接口
 *
 * @author Nexus
 */
public interface DcCollectionPointMapper extends BaseMapperPlus<DcCollectionPoint, DcCollectionPointVo> {

    /**
     * 推进采集点数据补偿游标（最后成功归档时间）。
     *
     * <p>IotDataArchiveTask 归档成功后调用，使下次任务从该时间点继续拉取，实现断线回补； 空结果也推进（已扫描无数据 ≠ 应重扫）。
     *
     * @param pointId 采集点ID
     * @param archiveTime 归档截止时间
     * @return 受影响行数
     */
    @Update(
            "UPDATE dc_collection_point SET last_archive_time = #{archiveTime} WHERE point_id = #{pointId}")
    int updateLastArchiveTime(
            @Param("pointId") Long pointId, @Param("archiveTime") Date archiveTime);
}
