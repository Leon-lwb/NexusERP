package com.nexus.mes.eq.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.eq.domain.EqInspectionRecord;

/**
 * MES设备点检记录 Mapper接口
 * <p>设备通道(ruoyi-station)注入使用, 无独立 VO(点检明细走 JSON 透传)。</p>
 *
 * @author Nexus
 */
public interface EqInspectionRecordMapper extends BaseMapperPlus<EqInspectionRecord, EqInspectionRecord> {

}
