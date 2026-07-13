package com.nexus.mes.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统租户只读Mapper(跨库查询 master 的 sys_tenant)
 * <p>
 * 供 MES 定时任务(SnailJob 线程无 Sa-Token 上下文)遍历有效租户,
 * 配合 TenantHelper.dynamic 实现多租户数据扫描。
 * <p>
 * Bean 名刻意使用 mesSysTenantMapper, 避免与 ruoyi-system 模块
 * 已有的 SysTenantMapper(bean 名 sysTenantMapper)冲突。
 *
 * @author Nexus
 */
@Mapper
public interface MesSysTenantMapper {

    /**
     * 查询所有有效租户ID(status=0 正常, del_flag=0 未删除)
     */
    @Select("SELECT tenant_id FROM sys_tenant WHERE status = '0' AND del_flag = '0'")
    List<String> selectAllTenantIds();
}
