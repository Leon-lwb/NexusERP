package com.nexus.ai.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ERP 表结构缓存初始化器
 * <p>
 * 在应用启动完成后（ApplicationReadyEvent）触发 ErpTableSchemaManager 的缓存加载。
 * 使用 ApplicationReadyEvent 而非 @PostConstruct，确保所有数据源和 Bean 都已完全初始化。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpTableSchemaInitializer {

    /** ERP 表结构缓存管理器，触发其缓存加载 */
    private final ErpTableSchemaManager erpTableSchemaManager;

    /**
     * 应用启动完成后初始化表结构缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[SchemaInitializer] 应用启动完成，开始初始化 ERP 表结构缓存...");
        try {
            erpTableSchemaManager.initializeSchema();
            int tableCount = erpTableSchemaManager.cachedTableCount();
            log.info("[SchemaInitializer] ERP 表结构缓存初始化完成，共加载 {} 张表", tableCount);
        } catch (Exception e) {
            log.error("[SchemaInitializer] ERP 表结构缓存初始化失败", e);
        }
    }
}
