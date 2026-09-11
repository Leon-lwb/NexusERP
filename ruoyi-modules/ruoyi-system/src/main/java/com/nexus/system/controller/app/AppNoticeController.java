package com.nexus.system.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.system.domain.SysNotice;
import com.nexus.system.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * App 系统公告控制器
 * <p>补齐《接口状态与后端缺口清单》§5.2 缺失的 {@code GET /app/notice/latest}：
 * 消息页顶部公告条只需要「最新若干条正常态公告 + 纯文本摘要」，
 * 不应要求登录用户具备管理端的 {@code system:notice:list} 权限。
 * <p>只读接口，公告按租户自动隔离（{@link SysNotice} 继承租户实体）。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/notice")
public class AppNoticeController {

    /** 默认返回条数（消息页顶部条） */
    private static final int DEFAULT_LIMIT = 3;
    /** 单次最大条数，防止异常大页拖垮 DB */
    private static final int MAX_LIMIT = 20;
    /** 摘要最大长度（Hutool 截断后会追加省略号） */
    private static final int SUMMARY_MAX_LENGTH = 80;
    /** 公告状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /** 公告 Mapper（直接查询，避免复用管理端带权限的分页入参） */
    private final SysNoticeMapper noticeMapper;

    /**
     * 最新公告列表（消息页顶部公告条）
     *
     * @param limit 返回条数，默认 3，上限 20
     * @return 公告精简列表：noticeId / title / summary / noticeType / publishTime
     */
    @SaCheckLogin
    @GetMapping("/latest")
    public R<List<Map<String, Object>>> latest(@RequestParam(required = false) Integer limit) {
        int size = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        Page<SysNotice> page = noticeMapper.selectPage(new Page<>(1, size),
            new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, STATUS_NORMAL)
                .orderByDesc(SysNotice::getCreateTime));

        List<Map<String, Object>> list = new ArrayList<>();
        for (SysNotice notice : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("noticeId", notice.getNoticeId());
            item.put("title", notice.getNoticeTitle());
            item.put("summary", summarize(notice.getNoticeContent()));
            item.put("noticeType", notice.getNoticeType());
            item.put("publishTime", notice.getCreateTime());
            list.add(item);
        }
        return R.ok(list);
    }

    /**
     * 富文本公告内容 → 纯文本摘要：去标签、折叠空白、超长截断。
     *
     * @param content 公告正文（可能为 HTML）
     * @return 摘要，无内容时返回空串
     */
    private String summarize(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String plain = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return StrUtil.maxLength(plain, SUMMARY_MAX_LENGTH);
    }
}
