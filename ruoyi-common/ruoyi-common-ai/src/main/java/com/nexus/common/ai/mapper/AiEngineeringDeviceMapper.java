package com.nexus.common.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.ai.domain.entity.AiEngineeringDevice;

/** 工程设备持久化契约；业务写操作由带 CAS 的专属仓储完成。 */
public interface AiEngineeringDeviceMapper extends BaseMapper<AiEngineeringDevice> {}
