package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 单件过站的固定锁定读；只在实际 MES RR 写事务内使用，租户条件不依赖插件注入。
 * 质量查询锁完整相关范围，不用最新一行替代空范围及新增结果的竞争保护。
 */
@DS("mes")
@Mapper
public interface StationPassGuardMapper {
    /** 锁定当前租户的真实 SN，重复编码由服务拒绝。 */
    @Select("SELECT * FROM pro_sn WHERE tenant_id=#{tenantId} AND sn_code=#{sn} AND del_flag='0' ORDER BY sn_id FOR UPDATE")
    List<ProSn> lockSn(@Param("tenantId") String tenantId, @Param("sn") String sn);

    /** 锁定完整报工历史，包括已删除的幂等承载，不能把墓碑当新请求。 */
    @Select("SELECT * FROM mes_station_report WHERE tenant_id=#{tenantId} AND sn_code=#{sn} ORDER BY report_id FOR UPDATE")
    List<StoredReport> lockReports(@Param("tenantId") String tenantId, @Param("sn") String sn);

    /** 卡主键与租户共同限定归属。 */
    @Select("SELECT * FROM pro_flow_card WHERE tenant_id=#{tenantId} AND card_id=#{cardId} AND del_flag='0' FOR UPDATE")
    ProFlowCard lockCard(@Param("tenantId") String tenantId, @Param("cardId") Long cardId);

    /** 锁定卡上全部有效工序，校验和推进使用同一组来源。 */
    @Select("SELECT * FROM pro_flow_card_step WHERE tenant_id=#{tenantId} AND card_id=#{cardId} AND del_flag='0' ORDER BY sequence_num,step_record_id FOR UPDATE")
    List<ProFlowCardStep> lockSteps(@Param("tenantId") String tenantId, @Param("cardId") Long cardId);

    /** 登记绑定可能多设备同工位；全部读取才能拒绝矛盾绑定。 */
    @Select("SELECT * FROM mes_station_device WHERE tenant_id=#{tenantId} AND station_code=#{station} AND del_flag='0' ORDER BY device_id FOR UPDATE")
    List<MesStationDevice> lockDevices(@Param("tenantId") String tenantId, @Param("station") String station);

    /** 锁定全部测试行及其范围，保持与查询界面一致的最新结果排序。 */
    @Select("SELECT * FROM mes_test_record WHERE tenant_id=#{tenantId} AND sn_code=#{sn} ORDER BY test_time DESC,test_record_id DESC FOR UPDATE")
    List<TestRecord> lockTests(@Param("tenantId") String tenantId, @Param("sn") String sn);

    /** 包含全部状态与删除行，避免状态或删除修改绕过质量范围锁。 */
    @Select("SELECT * FROM ncr_order WHERE tenant_id=#{tenantId} AND card_id=#{cardId} ORDER BY ncr_id FOR UPDATE")
    List<NcrOrder> lockNcrs(@Param("tenantId") String tenantId, @Param("cardId") Long cardId);

    /** 报工实体没有删除属性；锁读单独保留墓碑字段以严格判定重放。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    class StoredReport extends MesStationReport {
        private String delFlag;
    }
}
