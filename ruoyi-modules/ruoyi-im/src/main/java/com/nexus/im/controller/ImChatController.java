package com.nexus.im.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.bo.ImGroupCreateBo;
import com.nexus.im.domain.bo.ImGroupMemberAddBo;
import com.nexus.im.domain.bo.ImGroupSendBo;
import com.nexus.im.domain.bo.ImPrivateSendBo;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.service.IImChatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * 聊天管理
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/im/chat")
public class ImChatController extends BaseController {

    private final IImChatService imChatService;

    @SaCheckLogin
    @PostMapping("/private/send")
    public R<Long> sendPrivate(@Validated @RequestBody ImPrivateSendBo bo) {
        return imChatService.sendPrivate(bo);
    }

    @SaCheckLogin
    @PostMapping("/group/send")
    public R<Long> sendGroup(@Validated @RequestBody ImGroupSendBo bo) {
        return imChatService.sendGroup(bo);
    }

    @SaCheckLogin
    @GetMapping("/private/history")
    public TableDataInfo<ImMessage> privateHistory(@NotNull @RequestParam Long peerUserId, PageQuery pageQuery) {
        return imChatService.privateHistory(peerUserId, pageQuery);
    }

    @SaCheckLogin
    @GetMapping("/group/history")
    public TableDataInfo<ImMessage> groupHistory(@NotNull @RequestParam Long groupId, PageQuery pageQuery) {
        return imChatService.groupHistory(groupId, pageQuery);
    }

    @SaCheckLogin
    @GetMapping("/conversations")
    public R<List<ImConversationVo>> conversations() {
        return R.ok(imChatService.listConversations());
    }

    @SaCheckLogin
    @PostMapping("/group")
    public R<Long> createGroup(@Validated @RequestBody ImGroupCreateBo bo) {
        return imChatService.createGroup(bo);
    }

    @SaCheckLogin
    @PostMapping("/group/{groupId}/members")
    public R<Void> addMembers(@PathVariable Long groupId, @Validated @RequestBody ImGroupMemberAddBo bo) {
        return imChatService.addGroupMembers(groupId, bo);
    }

    @SaCheckLogin
    @PutMapping("/private/read")
    public R<Void> markPrivateRead(@NotNull @RequestParam Long peerUserId) {
        return toAjax(imChatService.markPrivateRead(peerUserId));
    }
}
