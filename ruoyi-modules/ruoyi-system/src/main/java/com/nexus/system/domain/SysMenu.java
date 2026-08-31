package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单权限表 sys_menu
 *
 * @author wanghuan
 */

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /**
     * 菜单ID
     */
    @TableId(value = "menu_id")
    private Long menuId;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 显示顺序
     */
    private Integer orderNum;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 路由参数
     */
    private String queryParam;

    /**
     * 是否为外链（0是 1否）
     */
    private String isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    private String isCache;

    /**
     * 类型（M目录 C菜单 F按钮）
     */
    private String menuType;

    /**
     * 显示状态（0显示 1隐藏）
     */
    private String visible;

    /**
     * 菜单状态（0正常 1停用）
     */
    private String status;

    /**
     * 权限字符串
     */
    private String perms;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 备注
     */
    private String remark;

    /**
     * 父菜单名称
     */
    @TableField(exist = false)
    private String parentName;

    /**
     * 子菜单
     */
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();

    /**
     * 获取前端路由名称（首字母大写的路由地址，非外链一级目录为空串）。
     *
     * @return 路由名称
     */
    public String getRouteName() {
        String routerName = StringUtils.capitalize(path);
        // 非外链并且是一级目录（类型为目录）
        if (isMenuFrame()) {
            routerName = StringUtils.EMPTY;
        }
        return routerName;
    }

    /**
     * 获取前端路由地址，依据父子关系、外链与内链规则换算实际路径。
     *
     * @return 路由地址
     */
    public String getRouterPath() {
        String routerPath = this.path;
        // 内链打开外网方式
        if (!Constants.TOP_PARENT_ID.equals(getParentId()) && isInnerLink()) {
            routerPath = innerLinkReplaceEach(routerPath);
        }
        // 非外链并且是一级目录（类型为目录）
        if (Constants.TOP_PARENT_ID.equals(getParentId()) && SystemConstants.TYPE_DIR.equals(getMenuType())
            && SystemConstants.NO_FRAME.equals(getIsFrame())) {
            routerPath = "/" + this.path;
        }
        // 非外链并且是一级目录（类型为菜单）
        else if (isMenuFrame()) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 获取前端组件信息，按菜单类型与内外链规则选择对应组件。
     *
     * @return 组件路径或布局组件标识
     */
    public String getComponentInfo() {
        String component = SystemConstants.LAYOUT;
        if (StringUtils.isNotEmpty(this.component) && !isMenuFrame()) {
            component = this.component;
        } else if (StringUtils.isEmpty(this.component) && !Constants.TOP_PARENT_ID.equals(getParentId()) && isInnerLink()) {
            component = SystemConstants.INNER_LINK;
        } else if (StringUtils.isEmpty(this.component) && isParentView()) {
            component = SystemConstants.PARENT_VIEW;
        }
        return component;
    }

    /**
     * 判断是否为菜单框架（顶级菜单且为菜单类型、非外链）。
     *
     * @return 是菜单框架返回 true，否则返回 false
     */
    public boolean isMenuFrame() {
        return Constants.TOP_PARENT_ID.equals(getParentId()) && SystemConstants.TYPE_MENU.equals(menuType) && isFrame.equals(SystemConstants.NO_FRAME);
    }

    /**
     * 判断是否为内链组件（非外链且路由地址为 http(s) 链接）。
     *
     * @return 是内链返回 true，否则返回 false
     */
    public boolean isInnerLink() {
        return isFrame.equals(SystemConstants.NO_FRAME) && StringUtils.ishttp(path);
    }

    /**
     * 判断是否为父级视图组件（非顶级目录且为目录类型）。
     *
     * @return 是父级视图返回 true，否则返回 false
     */
    public boolean isParentView() {
        return !Constants.TOP_PARENT_ID.equals(getParentId()) && SystemConstants.TYPE_DIR.equals(menuType);
    }

    /**
     * 对内链地址中的协议头与特殊字符进行替换，便于路由拼接。
     *
     * @param path 原始内链地址
     * @return 替换后的地址
     */
    public static String innerLinkReplaceEach(String path) {
        return StringUtils.replaceEach(path, new String[]{Constants.HTTP, Constants.HTTPS, Constants.WWW, ".", ":"},
            new String[]{"", "", "", "/", "/"});
    }
}
