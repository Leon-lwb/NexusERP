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
import com.nexus.im.domain.vo.ImGroupMemberVo;
import com.nexus.im.service.IImChatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * 聊天管理Controller（PC 端）
 * <p>提供私聊/群聊消息发送、历史记录查询、会话列表、群组管理、已读标记、消息撤回等 REST 接口，
 * 属于 IM（即时通讯）模块，所有接口均要求登录态（{@code @SaCheckLogin}）。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/im/chat")
public class ImChatController extends BaseController {

    /** 聊天业务服务，聚合私聊/群聊发送、历史、群组、已读等能力。 */
    private final IImChatService imChatService;

    /**
     * 发送私聊消息。
     *
     * @param bo 私聊发送参数（接收方、内容、消息类型、clientMsgId 等）
     * @return 发送成功后的消息 ID
     */
    @SaCheckLogin
    @PostMapping("/private/send")
    public R<Long> sendPrivate(@Validated @RequestBody ImPrivateSendBo bo) {
        return imChatService.sendPrivate(bo);
    }

    /**
     * 发送群聊消息。
     *
     * @param bo 群聊发送参数（群组、内容、消息类型、@ 提醒、clientMsgId 等）
     * @return 发送成功后的消息 ID
     */
    @SaCheckLogin
    @PostMapping("/group/send")
    public R<Long> sendGroup(@Validated @RequestBody ImGroupSendBo bo) {
        return imChatService.sendGroup(bo);
    }

    /**
     * 分页查询与指定用户的私聊历史消息。
     *
     * @param peerUserId 对端用户 ID
     * @param pageQuery  分页查询参数
     * @return 私聊消息分页结果
     */
    @SaCheckLogin
    @GetMapping("/private/history")
    public TableDataInfo<ImMessage> privateHistory(@NotNull @RequestParam Long peerUserId, PageQuery pageQuery) {
        return imChatService.privateHistory(peerUserId, pageQuery);
    }

    /**
     * 分页查询指定群组的群聊历史消息。
     *
     * @param groupId  群组 ID
     * @param pageQuery 分页查询参数
     * @return 群聊消息分页结果
     */
    @SaCheckLogin
    @GetMapping("/group/history")
    public TableDataInfo<ImMessage> groupHistory(@NotNull @RequestParam Long groupId, PageQuery pageQuery) {
        return imChatService.groupHistory(groupId, pageQuery);
    }

    /**
     * 私聊历史（带发送者名称/头像），供前端聊天页直接渲染
     */
    @SaCheckLogin
    @GetMapping("/private/history/withSender")
    public TableDataInfo<Map<String, Object>> privateHistoryWithSender(@NotNull @RequestParam Long peerUserId, PageQuery pageQuery) {
        return imChatService.privateHistoryWithSender(peerUserId, pageQuery);
    }

    /**
     * 群聊历史（带发送者名称/头像），供前端聊天页直接渲染
     */
    @SaCheckLogin
    @GetMapping("/group/history/withSender")
    public TableDataInfo<Map<String, Object>> groupHistoryWithSender(@NotNull @RequestParam Long groupId, PageQuery pageQuery) {
        return imChatService.groupHistoryWithSender(groupId, pageQuery);
    }

    /**
     * 查询当前登录用户的会话列表（按最近消息时间排序，含未读数）。
     *
     * @return 会话视图列表
     */
    @SaCheckLogin
    @GetMapping("/conversations")
    public R<List<ImConversationVo>> conversations() {
        return R.ok(imChatService.listConversations());
    }

    /**
     * 创建群聊并把创建人设为群主，同时加入初始成员。
     *
     * @param bo 建群参数（群名、头像、初始成员）
     * @return 新建群组 ID
     */
    @SaCheckLogin
    @PostMapping("/group")
    public R<Long> createGroup(@Validated @RequestBody ImGroupCreateBo bo) {
        return imChatService.createGroup(bo);
    }

    /**
     * 向指定群组添加成员（群主操作）。
     *
     * @param groupId 群组 ID
     * @param bo      待添加成员参数
     * @return 操作结果
     */
    @SaCheckLogin
    @PostMapping("/group/{groupId}/members")
    public R<Void> addMembers(@PathVariable Long groupId, @Validated @RequestBody ImGroupMemberAddBo bo) {
        return imChatService.addGroupMembers(groupId, bo);
    }

    /**
     * 标记与指定用户的私聊消息为已读（幂等）。
     *
     * @param peerUserId 对端用户 ID
     * @return 操作结果
     */
    @SaCheckLogin
    @PutMapping("/private/read")
    public R<Void> markPrivateRead(@NotNull @RequestParam Long peerUserId) {
        // 标记已读为幂等操作：无未读消息时更新 0 行也算成功，不应返回"操作失败"
        imChatService.markPrivateRead(peerUserId);
        return R.ok();
    }

    /**
     * 标记指定群组的群聊消息为已读（幂等）。
     *
     * @param groupId 群组 ID
     * @return 操作结果
     */
    @SaCheckLogin
    @PutMapping("/group/read")
    public R<Void> markGroupRead(@NotNull @RequestParam Long groupId) {
        imChatService.markGroupRead(groupId);
        return R.ok();
    }

    /**
     * 群成员列表（含用户名称/头像/是否群主）
     */
    @SaCheckLogin
    @GetMapping("/group/{groupId}/members")
    public R<List<ImGroupMemberVo>> listGroupMembers(@PathVariable Long groupId) {
        return R.ok(imChatService.listGroupMembers(groupId));
    }

    /**
     * 将成员移出群组（群主操作，群主自身不可被移除）。
     *
     * @param groupId 群组 ID
     * @param userId  待移除成员用户 ID
     * @return 操作结果
     */
    @SaCheckLogin
    @DeleteMapping("/group/{groupId}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        return imChatService.removeMember(groupId, userId);
    }

    /**
     * 撤回自己的消息（2 分钟内）
     */
    @SaCheckLogin
    @PutMapping("/message/{messageId}/revoke")
    public R<Void> revokeMessage(@PathVariable Long messageId) {
        return imChatService.revokeMessage(messageId);
    }
}
