package com.nexus.ai.service.config;

import com.nexus.common.ai.domain.bo.AiConfigBo;
import com.nexus.common.ai.domain.vo.AiConfigVo;

/**
 * AI 运行参数配置服务接口
 * <p>提供 nexus.ai 运行参数的读取与修改：配置落 ai_config 表（分组 JSON），
 * 修改后回填 AiProperties 单例即时生效，并广播多节点同步</p>
 *
 * @author NexusERP
 */
public interface IAiConfigService {

    /**
     * 读取当前生效的 AI 运行参数（yml 兜底默认 + ai_config 表覆盖的合并结果）
     * <p>apiKey 明文不回显，仅返回是否已配置标识</p>
     */
    AiConfigVo getConfig();

    /**
     * 保存 AI 运行参数并即时生效
     * <p>逐组 upsert 到 ai_config 表 → 回填 AiProperties → 按变更联动缓存失效
     * （向量库连接变更逐出 Milvus 客户端缓存、表前缀变更刷新 ERP 表结构缓存）→ Redis 广播多节点重载</p>
     *
     * @param bo 配置业务对象（webSearch.apiKey 为空表示保留原值）
     */
    void updateConfig(AiConfigBo bo);
}
