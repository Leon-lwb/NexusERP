package com.nexus.mes.pro.service;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.ProAndonBo;
import com.nexus.mes.pro.domain.vo.ProAndonVo;

import java.util.Map;

/**
 * MES 安灯业务层服务接口
 * <p>
 * 状态机: CALLED -> RESPONDED -> RESOLVED / CANCELLED
 * 呼叫类型: QUALITY(质量异常) / MATERIAL(物料短缺) / EQUIPMENT(设备故障) / OTHER(其他)
 *
 * @author Nexus
 */
public interface IProAndonService {

    /** 状态: 已呼叫 */
    String STATUS_CALLED = "CALLED";
    /** 状态: 已响应 */
    String STATUS_RESPONDED = "RESPONDED";
    /** 状态: 已解决 */
    String STATUS_RESOLVED = "RESOLVED";
    /** 状态: 已取消 */
    String STATUS_CANCELLED = "CANCELLED";

    /** 呼叫类型: 质量异常 */
    String TYPE_QUALITY = "QUALITY";
    /** 呼叫类型: 物料短缺 */
    String TYPE_MATERIAL = "MATERIAL";
    /** 呼叫类型: 设备故障 */
    String TYPE_EQUIPMENT = "EQUIPMENT";
    /** 呼叫类型: 其他 */
    String TYPE_OTHER = "OTHER";

    /** 来源: 手动 */
    String SOURCE_MANUAL = "MANUAL";
    /** 来源: IoT */
    String SOURCE_IOT = "IoT";

    /**
     * 分页查询安灯列表
     */
    TableDataInfo<ProAndonVo> selectPageList(ProAndonBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取安灯详情
     */
    ProAndonVo selectById(Long andonId);

    /**
     * 看板聚合统计(按状态/类型分组)
     */
    Map<String, Object> dashboardStats();

    /**
     * 手动呼叫安灯
     *
     * @param mesWoId      关联MES工单ID(可空)
     * @param cardId       关联流转卡ID(可空)
     * @param stepRecordId 关联工序记录ID(可空)
     * @param callType     呼叫类型
     * @param callReason   呼叫原因
     * @return 安灯视图对象
     */
    ProAndonVo callAndon(Long mesWoId, Long cardId, Long stepRecordId,
                         String callType, String callReason);

    /**
     * 可信内部 IoT 告警入口，不供 控制器请求选择来源。
     * 首次插入即为 SOURCE_IOT，在告警租户内处理，不借用登录态呼叫人。
     * 设备身份保留在 callReason；不增加设备/告警关联列。
     *
     * @param alert 来自 IoT 的真实告警，须有有效租户、设备ID/编码；MES设备ID可空
     * @return 安灯视图对象
     * @throws IllegalArgumentException 租户或设备身份无效
     */
    ProAndonVo callIotAndon(IotAlert alert);

    /**
     * 呼叫安灯(完整参数版, 工位终端设备通道使用)
     *
     * @param mesWoId      关联MES工单ID(可空)
     * @param cardId       关联流转卡ID(可空)
     * @param stepRecordId 关联工序记录ID(可空)
     * @param callType     呼叫类型
     * @param callReason   呼叫原因
     * @param stationCode  工位编码(可空, 冗余存储供工位维度查询)
     * @param operatorCode 呼叫人工号(可空, 免登录通道无 LoginHelper 上下文时作为呼叫人留痕)
     * @return 安灯视图对象
     */
    ProAndonVo callAndon(Long mesWoId, Long cardId, Long stepRecordId,
                         String callType, String callReason, String stationCode, String operatorCode);

    /**
     * 响应安灯(CALLED -> RESPONDED)
     */
    void respondAndon(Long andonId);

    /**
     * 解决安灯(RESPONDED -> RESOLVED), 计算 duration_minutes
     */
    void resolveAndon(Long andonId, String remark);

    /**
     * 取消安灯(CALLED -> CANCELLED)
     */
    void cancelAndon(Long andonId);
}
