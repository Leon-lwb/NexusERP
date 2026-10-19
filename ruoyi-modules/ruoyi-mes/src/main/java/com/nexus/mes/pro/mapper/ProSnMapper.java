package com.nexus.mes.pro.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/**
 * MES SN序列号Mapper接口
 *
 * @author Nexus
 */
public interface ProSnMapper extends BaseMapperPlus<ProSn, ProSnVo> {
    /** Original immutable delivery for this actual local identity; later revisions do not replace it on replay. */
    @Select("""
        SELECT m.manifest_id,m.revision,m.content_hash AS manifest_hash,m.status,m.good_qty,
               l.result AS line_result,o.event_id AS source_event_id,o.status AS source_status,
               o.payload AS source_payload,o.content_hash AS source_hash,o.result_payload AS source_result
        FROM pro_completion_manifest m
        JOIN pro_completion_manifest_line l
          ON CAST(l.tenant_id AS BINARY(80))=CAST(m.tenant_id AS BINARY(80))
             AND l.manifest_id=m.manifest_id AND l.revision=m.revision
        LEFT JOIN mes_trace_outbox o
          ON CAST(o.tenant_id AS BINARY(80))=CAST(m.tenant_id AS BINARY(80))
             AND o.source_module='MES' AND o.aggregate_type='COMPLETION_MANIFEST'
             AND o.aggregate_id=m.manifest_id AND o.aggregate_version=1
             AND o.event_type='COMPLETION_MANIFEST_FROZEN'
        WHERE m.tenant_id=#{tenantId} AND m.mes_wo_id=#{woId}
          AND l.local_sn_id=#{localSnId} AND l.identity_id=#{identityId}
        ORDER BY m.revision,m.manifest_id LIMIT 2
        """)
    List<Map<String,Object>> completionSubmissions(@Param("tenantId") String tenantId,@Param("woId") Long woId,
        @Param("localSnId") Long localSnId,@Param("identityId") Long identityId);
}
