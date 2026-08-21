package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.pro.domain.ProAndon;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProAndonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 工位终端安灯服务 (M2 契约 C7/C8)
 * <p>
 * 包装既有 {@link IProAndonService#callAndon} 逻辑(含 SSE 实时推送);
 * 工位机四键枚举 MATERIAL/REPAIR/PERSON/QUALITY 与后端 QUALITY/MATERIAL/EQUIPMENT/OTHER 做双向映射。
 * 跨库访问 pro_andon/pro_flow_card/pro_work_order(ry-mes 库), 类级 @DS("mes") 切库
 * (ruoyi-mes 的 Mapper 本身无 @DS, 由其 Service 层切换, 本类同理)。
 * </p>
 *
 * @author Nexus
 */
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationAndonService {

    private final IProAndonService proAndonService;
    private final ProAndonMapper andonMapper;
    private final ProWorkOrderMapper workOrderMapper;
    private final ProFlowCardMapper flowCardMapper;

    /**
     * 安灯呼叫(C7): 工位机 callType 映射后走既有 callAndon, 复用 SSE 推送。
     *
     * @param callType     工位机呼叫类型 MATERIAL/REPAIR/PERSON/QUALITY
     * @param callReason   呼叫原因(可空)
     * @param stationCode  工位编码
     * @param operatorCode 呼叫人工号(可空)
     * @param woNo         工单编号(可空, 反查 mesWoId 冗余)
     * @param cardNo       流转卡编号(可空, 反查 cardId 冗余)
     * @return {andonNo}
     */
    public Map<String, Object> call(String callType, String callReason, String stationCode,
                                    String operatorCode, String woNo, String cardNo) {
        String backendType = toBackendCallType(callType);
        // 工位码归一化(剥离第 6 段设备编码), 与台账/报工记录的工位标识保持一致
        String station = StationPassService.normalizeStationCode(stationCode);
        Long mesWoId = null;
        if (StringUtils.isNotBlank(woNo)) {
            ProWorkOrder wo = workOrderMapper.selectOne(Wrappers.<ProWorkOrder>lambdaQuery()
                .eq(ProWorkOrder::getWoNo, woNo.trim()).last("LIMIT 1"));
            if (wo == null) {
                throw new ServiceException("工单编号不存在: " + woNo);
            }
            mesWoId = wo.getMesWoId();
        }
        Long cardId = null;
        if (StringUtils.isNotBlank(cardNo)) {
            ProFlowCard card = flowCardMapper.selectOne(Wrappers.<ProFlowCard>lambdaQuery()
                .eq(ProFlowCard::getCardNo, cardNo.trim()).last("LIMIT 1"));
            if (card == null) {
                throw new ServiceException("流转卡编号不存在: " + cardNo);
            }
            cardId = card.getCardId();
        }
        ProAndonVo vo = proAndonService.callAndon(mesWoId, cardId, null, backendType,
            callReason, station, operatorCode);
        Map<String, Object> data = new HashMap<>(2);
        data.put("andonNo", vo != null ? vo.getAndonNo() : "");
        return data;
    }

    /**
     * 工位最新安灯状态查询(C8): 按 station_code 查最新一条(状态任意)。
     *
     * @param stationCode 工位编码
     * @return {andonNo, callType, andonStatus, callTime, responderName}; 无记录返回 null
     */
    public Map<String, Object> latest(String stationCode) {
        if (StringUtils.isBlank(stationCode)) {
            return null;
        }
        ProAndon andon = andonMapper.selectOne(Wrappers.<ProAndon>lambdaQuery()
            .eq(ProAndon::getStationCode, StationPassService.normalizeStationCode(stationCode))
            .orderByDesc(ProAndon::getCallTime)
            .last("LIMIT 1"));
        if (andon == null) {
            return null;
        }
        Map<String, Object> data = new HashMap<>(8);
        data.put("andonNo", andon.getAndonNo());
        data.put("callType", toClientCallType(andon.getCallType()));
        data.put("andonStatus", andon.getAndonStatus());
        data.put("callTime", andon.getCallTime());
        data.put("responderName", andon.getResponderName());
        return data;
    }

    /**
     * 工位机四键 → 后端呼叫类型: REPAIR→EQUIPMENT(叫修=设备故障), PERSON→OTHER(叫人), 其余同名直传。
     */
    private String toBackendCallType(String clientType) {
        if (StringUtils.isBlank(clientType)) {
            throw new ServiceException("呼叫类型不能为空");
        }
        return switch (clientType.trim()) {
            case "MATERIAL" -> IProAndonService.TYPE_MATERIAL;
            case "REPAIR" -> IProAndonService.TYPE_EQUIPMENT;
            case "PERSON" -> IProAndonService.TYPE_OTHER;
            case "QUALITY" -> IProAndonService.TYPE_QUALITY;
            default -> throw new ServiceException("非法呼叫类型: " + clientType);
        };
    }

    /** 后端呼叫类型 → 工位机四键(逆映射, 供 C8 回显)。 */
    private String toClientCallType(String backendType) {
        if (backendType == null) {
            return null;
        }
        return switch (backendType) {
            case "EQUIPMENT" -> "REPAIR";
            case "OTHER" -> "PERSON";
            default -> backendType;
        };
    }
}
