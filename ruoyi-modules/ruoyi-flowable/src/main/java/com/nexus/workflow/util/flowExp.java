package com.nexus.workflow.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.CommonService;

import jakarta.annotation.Resource;




/**
 * 流程表达式应用类
 * <p>提供流程任务指派表达式中使用的动态处理人解析方法（单个用户、用户列表、部门经理），属于 Flowable 工作流模块。</p>
 * @author wanghuan
 * @date 2023-05-16
 */

@Service
public class flowExp {
	@Resource
	private CommonService commonService;

	/**
	 * 获取当前登录用户作为动态单个任务处理人。
	 *
	 * @return 当前登录用户名
	 */
	public  String getDynamicAssignee() {//动态单个用户例子
		SysUserVo loginUser = commonService.getSysUserByUserName(TaskUtils.getUserName());
		return loginUser.getUserName();
	}

	/**
	 * 获取管理员角色下的所有用户作为动态多处理人列表。
	 *
	 * @return 用户名列表
	 */
	public  List<String> getDynamicList() {//动态多个用户例子
		List<String> userlist = new ArrayList<String>();
		List<SysUserVo> list = new ArrayList<SysUserVo>();
		list = commonService.getUserListByRoleId("1");//管理员角色
		for(SysUserVo sysuser : list) {
			userlist.add(sysuser.getUserName());
		}
		return userlist;
	}

	/**
	 * 根据用户名称解析其部门经理作为动态处理人。
	 *
	 * @param userName 待解析的用户名
	 * @return 部门经理用户名
	 */
	//根据用户获取部门经理
	public  String getDynamicManager(String userName) { //动态部门经理例子
		// 获取部门负责人列表
  		return commonService.getDepLeaderByUserName(userName);
	}


	/**
	* 反射调用方法
	* @param newObj 实例化的一个对象
	* @param methodName 对象的方法名
	* @param args 参数数组
	* @return 返回值
	* @throws Exception
	*/
	public  Object invokeMethod(Object newObj, String methodName, Object[] args)throws Exception {
		Class ownerClass = newObj.getClass();
		Class[] argsClass = new Class[args.length];
		for (int i = 0, j = args.length; i < j; i++) {
		  argsClass[i] = args[i].getClass();
		}
		Method method = ownerClass.getMethod(methodName, argsClass);
		return method.invoke(newObj, args);
	}

}
