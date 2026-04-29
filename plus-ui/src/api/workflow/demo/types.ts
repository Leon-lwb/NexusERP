export interface WfDemoVO extends WfMyBusiness{
  /**
   * DEMO-ID
   */
  demoId: string |number;

  /**
   * 用户账号
   */
  userName: string;

  /**
   * 用户昵称
   */
  nickName: string;

  /**
   * 用户邮箱
   */
  email: string;

  /**
   * 头像地址
   */
  avatar: string;

  /**
   * 帐号状态（0正常 1停用）
   */
  status: string;

  /**
   * 备注
   */
  remark: string;
}

export interface WfDemo extends BaseEntity {
  /**
   * DEMO-ID
   */
  demoId: string |number;

  /**
   * 用户账号
   */
  userName: string;

  /**
   * 用户昵称
   */
  nickName: string;

  /**
   * 用户邮箱
   */
  email: string;

  /**
   * 头像地址
   */
  avatar: string;

  /**
   * 帐号状态（0正常 1停用）
   */
  status: string;

  /**
   * 备注
   */
  remark: string;
}
export interface WfDemoQuery extends PageQuery {
  /**
   * DEMO-ID
   */
  demoId: string |number;

  /**
   * 用户账号
   */
  userName: string;

  /**
   * 用户昵称
   */
  nickName: string;

  /**
   * 用户邮箱
   */
  email: string;

  /**
   * 头像地址
   */
  avatar: string;

  /**
   * 帐号状态（0正常 1停用）
   */
  status: string;

  /**
   * 备注
   */
  remark: string;
}
