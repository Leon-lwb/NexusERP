package com.nexus.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 缓存监控Controller
 * <p>提供 Redis 缓存监控信息查看接口，统计 Redis 命令执行情况与数据库规模。
 * 属于系统管理（System）模块的监控（monitor）分组。</p>
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/monitor/cache")
public class CacheController {

    /** Redis 连接工厂，用于获取底层 Redis 连接以查询缓存监控信息。 */
    private final RedissonConnectionFactory connectionFactory;

    /**
     * 获取缓存监控信息，包括 Redis 运行信息、数据库 Key 总数与各命令调用统计。
     *
     * @return 缓存监控信息视图对象，含 Redis 信息、库大小与命令统计饼图数据
     * @throws Exception 获取 Redis 连接或执行 info 命令异常时抛出
     */
    @SaCheckPermission("monitor:cache:list")
    @GetMapping()
    public R<CacheListInfoVo> getInfo() throws Exception {
        RedisConnection connection = connectionFactory.getConnection();
        try {
            Properties commandStats = connection.commands().info("commandstats");
            List<Map<String, String>> pieList = new ArrayList<>();
        if (commandStats != null) {
            commandStats.stringPropertyNames().forEach(key -> {
                Map<String, String> data = new HashMap<>(2);
                String property = commandStats.getProperty(key);
                data.put("name", StringUtils.removeStart(key, "cmdstat_"));
                data.put("value", StringUtils.substringBetween(property, "calls=", ",usec"));
                pieList.add(data);
            });
        }
        return R.ok(new CacheListInfoVo(
            connection.commands().info(),
            connection.commands().dbSize(), pieList));
        } finally {
            // 归还连接给连接池
            RedisConnectionUtils.releaseConnection(connection, connectionFactory);
        }
    }

    /**
     * 缓存监控列表信息
     *
     * @param info         信息
     * @param dbSize       数据库
     * @param commandStats 命令统计
     */
    public record CacheListInfoVo(Properties info, Long dbSize, List<Map<String, String>> commandStats) {}

}
