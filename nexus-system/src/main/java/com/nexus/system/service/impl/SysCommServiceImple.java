package com.nexus.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.constant.Constants;
import com.nexus.common.constant.WebsocketConst;
import com.nexus.common.core.domain.entity.dto.MessageDTO;
import com.nexus.common.core.domain.entity.vo.SysUserVo;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.constant.TaskConstants;
//import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.websocket.websocket.WebSocketServer;
import com.nexus.system.domain.SysNotice;
import com.nexus.system.domain.SysNoticeSend;
import com.nexus.system.mapper.*;
import com.nexus.system.service.CommonService;
import com.nexus.system.service.ISysDeptService;
import com.nexus.system.service.ISysUserService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 通用 公共服务
 *
 * @author nbacheng
 * @date 2023-09-21
 */

@RequiredArgsConstructor
@Service
public class SysCommServiceImple extends ServiceImpl<CommonMapper, Object> implements CommonService {

	@Value("${flowable.message-base-url}")
	private String msgBaseUrl;

    @Resource
	private SysUserMapper userMapper;
    @Resource
    SysNoticeMapper sysNoticeMapper;
	@Resource
    private WebSocketServer webSocket;
	@Resource
	private SysNoticeSendMapper sysNoticeSendMapper;
	@Resource
	ISysUserService sysUserService;
	@Resource
	SysUserServiceImpl sysUserServiceImpl;
	@Resource
	ISysDeptService sysDeptService;
	@Resource
	private CommonMapper commonMapper;
	@Resource
	private SysMenuMapper sysMenuMapper;
	
	@Override
	public void sendSysNotice(MessageDTO message) {
		this.sendSysNotice(message.getFromUser(),
				message.getToUser(),
				message.getTitle(),
				message.getContent(),
				message.getCategory());		
	}

	/**
	 * 发消息
	 * @param fromUser
	 * @param toUser
	 * @param title
	 * @param msgContent
	 * @param setMsgCategory
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void sendSysNotice(String fromUser, String toUser, String title, String msgContent, String setMsgCategory) {
		/*SysNotice notice = new SysNotice();
		notice.setNoticeTitle(title);
		notice.setNoticeContent(msgContent);
		notice.setNoticeType(setMsgCategory);
		notice.setSender(Long.valueOf(fromUser));
		notice.setPriority(Constants.PRIORITY_M);
		notice.setMsgType(Constants.MSG_TYPE_UESR);
		notice.setSendStatus(Constants.HAS_SEND);
		notice.setSendTime(new Date());
		notice.setMsgType(Constants.MSG_TYPE_UESR);
		notice.setStatus("0");
		sysNoticeMapper.insert(notice);
		// 2.插入用户通告阅读标记表记录
		String userName = toUser;
		String[] userNames = userName.split(",");
		Long noticeId = notice.getNoticeId();
		for(int i=0;i<userNames.length;i++) {
			if(ObjectUtil.isNotEmpty(userNames[i])) {
				SysUserVo sysUser = sysUserService.selectUserByUserName(userNames[i]);
				if(sysUser==null) {
					continue;
				}
				SysNoticeSend noticeSend = new SysNoticeSend();
				noticeSend.setNoticeId(noticeId);
				noticeSend.setUserId(sysUser.getUserId());
				noticeSend.setReadFlag(Constants.NO_READ_FLAG);
				sysNoticeSendMapper.insert(noticeSend);
				JSONObject obj = new JSONObject();
				obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_USER);
				obj.put(WebsocketConst.MSG_USER_ID, sysUser.getUserName());
				obj.put(WebsocketConst.MSG_ID, notice.getNoticeId());
				obj.put(WebsocketConst.MSG_TXT, notice.getNoticeTitle());
				webSocket.sendMessage(sysUser.getUserName(), obj.toJSONString());
				webSocket.pushMessage(sysUser.getUserName(), obj.toJSONString());
			}
		}*/
		return;
	}

	@Override
	public String getBaseUrl() {
		return msgBaseUrl;
	}

	@Override
	public LoginUser getLoginUser() {
//		LoginUser user = LoginHelper.getLoginUser();
//		return user;
		return null;
	}

	@Override
	public List<SysUserVo> getUserListByRoleId(String roleId) {
		String realId = roleId.replace(TaskConstants.ROLE_GROUP_PREFIX,"");
		List<Long>  listUserId = commonMapper.selectUserIdByRoleId(Long.valueOf(realId));
		List<SysUserVo> listUser = new ArrayList<SysUserVo>();
//		if(ObjectUtil.isNotEmpty(listUserId)) {
//			for(Long userid :listUserId) {
//				if(ObjectUtil.isNotEmpty(userid)) {
//				  SysUserVo sysUser = sysUserService.selectUserById(Long.valueOf(userid.toString()));
//				  if(ObjectUtil.isNotEmpty(sysUser)) {
//					  listUser.add(sysUser);
//				  }
//				}
//
//			}
//		}
		return listUser;
	}

	@Override
	public SysUserVo getSysUserByUserId(Long UserId) {
		
		//return sysUserService.selectUserById(UserId);
		return null;
	}

	@Override
	public SysUserVo getSysUserByUserName(String userName) {
		
		//return sysUserService.selectUserByUserName(userName);
		return null;
	}

	@Override
	public String getDepLeaderByUserName(String userName) {
		
		//return sysDeptService.getDepLeaderByUserName(userName);
		return null;
	}

	@Override
	public Long selectMaxId() {
		//return sysMenuMapper.selectMaxId();
		return null;
	}

	@Override
	public List<SysUserVo> getAllUser() {
		//List<SysUserVo> userList = sysUserServiceImpl.list();
		//return userList;
		return null;
	}
	
}
