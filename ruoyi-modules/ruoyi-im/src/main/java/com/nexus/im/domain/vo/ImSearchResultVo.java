package com.nexus.im.domain.vo;

import com.nexus.im.domain.ImMessage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * P4-8: 消息搜索结果 VO。
 * <p>包含匹配的消息列表 + 分页信息，用于全端消息搜索功能。
 *
 * @author nexus
 */
@Data
public class ImSearchResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 匹配的消息列表（含发送者信息） */
    private List<ImMessage> messages;

    /** 当前页码 */
    private Integer pageNum;

    /** 每页大小 */
    private Integer pageSize;

    /** 总匹配数（简化版：仅返回当前页条数，不额外查 count） */
    private Integer total;

    public ImSearchResultVo() {
    }

    public ImSearchResultVo(List<ImMessage> messages, Integer pageNum, Integer pageSize) {
        this.messages = messages;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = messages != null ? messages.size() : 0;
    }
}
