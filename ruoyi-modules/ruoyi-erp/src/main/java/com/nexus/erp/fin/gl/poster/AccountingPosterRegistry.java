package com.nexus.erp.fin.gl.poster;

import cn.hutool.core.collection.CollUtil;
import com.nexus.common.core.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AccountingPoster 注册表:Spring 注入所有 Poster 实现,按 sourceType 路由。
 *
 * @author nexus
 * @date 2026-07-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingPosterRegistry {

    private final List<AccountingPoster> posters;
    private Map<String, AccountingPoster> map = new HashMap<>();

    @PostConstruct
    void init() {
        if (CollUtil.isNotEmpty(posters)) {
            for (AccountingPoster p : posters) {
                map.put(p.sourceType(), p);
            }
        }
        log.info("AccountingPoster 注册 {} 个: {}", map.size(), map.keySet());
    }

    /**
     * 按来源类型获取 Poster。
     *
     * @param sourceType 来源类型(对应 GlConstants.SOURCE_*)
     * @return AccountingPoster 实现
     * @throws ServiceException 若未找到对应类型的 Poster
     */
    public AccountingPoster get(String sourceType) {
        AccountingPoster poster = map.get(sourceType);
        if (poster == null) {
            throw new ServiceException("无【{}】类型的记账 Poster,请检查实现", sourceType);
        }
        return poster;
    }
}
