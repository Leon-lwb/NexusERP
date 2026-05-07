-- ---------------------------------------------------------------------------
-- Nexus-ERP 即时通讯 / 站内统一通知 增量脚本 (MySQL)
-- 阶段 1：建表。执行前请备份数据库。
-- 表：sys_user_notification（站内通知中心）、im_group、im_group_member、im_message
-- 说明：群聊 MVP 不包含「个人已读游标」表；群消息 is_read 字段保留但不作业务含义。
-- ---------------------------------------------------------------------------

create table if not exists sys_user_notification
(
    notification_id bigint        not null        comment '通知主键',
    tenant_id         varchar(20) default '000000' comment '租户编号',
    user_id           bigint        not null        comment '接收用户ID',
    category          varchar(32)   not null        comment '分类：workflow_todo/sys_notice/im_private/im_group 等',
    ref_type          varchar(64)   default null    comment '关联业务类型',
    ref_id            varchar(128)  default null    comment '关联业务主键',
    title             varchar(200)  not null        comment '标题',
    content           text                          comment '内容(可存摘要或HTML)',
    read_flag         char(1)       default '0'     comment '是否已读（0否 1是）',
    ext_json          text                          comment '扩展JSON',
    create_dept       bigint(20)                    comment '创建部门',
    create_by         bigint(20)                    comment '创建者',
    create_time       datetime                      comment '创建时间',
    update_by         bigint(20)                    comment '更新者',
    update_time       datetime                      comment '更新时间',
    remark            varchar(500)  default null      comment '备注',
    primary key (notification_id),
    key idx_sun_user_read (tenant_id, user_id, read_flag, create_time),
    key idx_sun_category (tenant_id, user_id, category)
) engine = innodb comment = '用户站内通知';

create table if not exists im_group
(
    group_id     bigint        not null        comment '群ID',
    tenant_id    varchar(20)   default '000000' comment '租户编号',
    group_name   varchar(100)  not null        comment '群名称',
    avatar       varchar(500)  default null    comment '群头像',
    owner_user_id bigint       not null        comment '群主用户ID',
    status       char(1)       default '0'     comment '状态（0正常 1解散）',
    create_dept  bigint(20)                    comment '创建部门',
    create_by    bigint(20)                    comment '创建者',
    create_time  datetime                      comment '创建时间',
    update_by    bigint(20)                    comment '更新者',
    update_time  datetime                      comment '更新时间',
    remark       varchar(500)  default null    comment '备注',
    primary key (group_id),
    key idx_im_group_tenant (tenant_id, status)
) engine = innodb comment = 'IM群组';

create table if not exists im_group_member
(
    id          bigint        not null        comment '主键',
    tenant_id   varchar(20)   default '000000' comment '租户编号',
    group_id    bigint        not null        comment '群ID',
    user_id     bigint        not null        comment '成员用户ID',
    join_time   datetime                      comment '加入时间',
    create_dept bigint(20)                    comment '创建部门',
    create_by   bigint(20)                    comment '创建者',
    create_time datetime                      comment '创建时间',
    update_by   bigint(20)                    comment '更新者',
    update_time datetime                      comment '更新时间',
    primary key (id),
    unique key uk_group_user (group_id, user_id),
    key idx_igm_user (tenant_id, user_id)
) engine = innodb comment = 'IM群成员';

create table if not exists im_message
(
    message_id   bigint        not null        comment '消息主键',
    tenant_id    varchar(20)   default '000000' comment '租户编号',
    from_user_id bigint        not null        comment '发送人',
    to_user_id   bigint        default null    comment '接收人(私聊)',
    group_id     bigint        default null    comment '群ID(群聊)',
    content      text          not null        comment '消息内容',
    msg_type     varchar(32)   default 'text'  comment '消息类型',
    is_read      char(1)       default '0'     comment '私聊已读(0否1是)；群聊MVP不使用',
    revoked      char(1)       default '0'     comment '是否撤回（0否 1是）',
    create_dept  bigint(20)                    comment '创建部门',
    create_by    bigint(20)                    comment '创建者',
    create_time  datetime                      comment '创建时间',
    update_by    bigint(20)                    comment '更新者',
    update_time  datetime                      comment '更新时间',
    primary key (message_id),
    key idx_im_msg_private (tenant_id, from_user_id, to_user_id, create_time),
    key idx_im_msg_to (tenant_id, to_user_id, create_time),
    key idx_im_msg_group (tenant_id, group_id, create_time)
) engine = innodb comment = 'IM消息';
