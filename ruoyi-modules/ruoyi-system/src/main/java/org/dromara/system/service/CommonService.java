package org.dromara.system.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.system.domain.vo.SysUserVo;


/**
 * 通用 公共服务
 *
 * @author nbacheng
 * @date 2023-09-21
 */
public interface CommonService extends IService<Object> {

	/**
     * 1发送系统消息
     * @param message 使用构造器赋值参数 如果不设置category(消息类型)则默认为2 发送系统消息
     */
    void sendSysNotice(String message);

    /**
	 * 发消息
	 * @param fromUser
	 * @param toUser
	 * @param title
	 * @param msgContent
	 * @param setMsgCategory
	 */
    void sendSysNotice(String fromUser, String toUser, String title, String msgContent, String setMsgCategory);

    /**
     * 获取流程发送消息基地址
     * @return
     */
    String getBaseUrl();

    /**
     * 获取当前登录用户
     *
     * @return 当前登录用户信息
     */
    public LoginUser getLoginUser();

    /**
     * 根据角色获取用户列表
     *
     * @return 用户列表
     */
    public List<SysUserVo> getUserListByRoleId(String roleId);

    /**
     * 根据用户id获取用户信息
     *
     * @return 用户信息
     */
    public SysUserVo getSysUserByUserId(Long UserId);

    /**
     * 根据用户名称获取用户信息
     *
     * @return 用户信息
     */
    public SysUserVo getSysUserByUserName(String userName);

    /**
     * 根据用户名称获取用户信息
     *
     * @return 用户信息
     */
    public String getDepLeaderByUserName(String userName);
    /**
     * 获取菜单的最大id
     *
     * @return 最大id
     */
    public Long selectMaxId();
    /**
     * 获取所有用户信息
     *
     * @return 用户列表信息
     */
    public List<SysUserVo> getAllUser();

}
