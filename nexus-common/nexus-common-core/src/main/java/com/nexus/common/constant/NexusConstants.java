package com.nexus.common.constant;

import java.util.Arrays;
import java.util.List;

/**
 * @author wanghuan
 * @date 2025/11/18
 */
public class NexusConstants {

    //生产计划状态
    public static final String STATUS_DRAFT = "DRAFT";

    //生产计划版本 为当前版本
    public static final Long VERSION_INIT = 1L;
    //生产计划版本 不为当前版本
    public static final Long VERSION_NOT_CURRENT = 0L;

    //编码是否唯一
    public static final String UNIQUE = "0";
    public static final String NOT_UNIQUE = "1";

    /**
     * 单据的状态类型
     */
    public static final String ORDER_STATUS_PREPARE="PREPARE";
    public static final String ORDER_STATUS_CONFIRMED="CONFIRMED";
    public static final String ORDER_STATUS_APPROVING="APPROVING";
    public static final String ORDER_STATUS_APPROVED="APPROVED";
    public static final String ORDER_STATUS_FINISHED="FINISHED";
    public static final String ORDER_STATUS_CANCELED="CANCELED";

    /**
     * 账户部门在生产中心部门
     */
    public static final Integer PRODUCTION_CENTER_DEPT_ID = 111;

    /**
     * 生产班组类别
     */
    public static final String[] TEAM_CATEGORY_PRODUCTION = {"SMT Production Team","Assembly Team","FATP Team","Plastic Molding Team","Metal Stamping Team"};
    /**
     * 辅助班组类别
     */
    public static final String[] TEAM_CATEGORY_AUXILIARY = {"Equipment Maintenance Team","IQC Team","Warehouse Material Team"};
    
    /**
     * 生产班组类别列表（用于快速查找）
     */
    public static final List<String> TEAM_CATEGORY_PRODUCTION_LIST = Arrays.asList(TEAM_CATEGORY_PRODUCTION);
    /**
     * 辅助班组类别列表（用于快速查找）
     */
    public static final List<String> TEAM_CATEGORY_AUXILIARY_LIST = Arrays.asList(TEAM_CATEGORY_AUXILIARY);
}
