package com.nexus.im.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * 添加群成员入参对象（BO）
 * <p>用于向已有群组批量添加成员，仅包含待添加成员的用户 ID 列表。</p>
 */
@Data
public class ImGroupMemberAddBo {

    /** 待添加成员用户 ID 列表，不能为空。 */
    @NotEmpty(message = "成员不能为空")
    @JsonAlias("memberIds")
    private List<Long> memberUserIds;
}
