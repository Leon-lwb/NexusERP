package com.nexus.system.domain.vo;

import com.nexus.common.core.utils.StringUtils;
import lombok.Data;

/**
 * 路由显示信息
 *
 * @author nexus
 */

@Data
public class MetaVo {

    /**
     * 设置该路由在侧边栏和面包屑中展示的名字
     */
    private String title;

    /**
     * 设置该路由的图标，对应路径src/assets/icons/svg
     */
    private String icon;

    /**
     * 设置为true，则不会被 <keep-alive>缓存
     */
    private Boolean noCache;

    /**
     * 内链地址（http(s)://开头）
     */
    private String link;

    /**
     * 激活菜单
     */
    private String activeMenu;

    /**
     * 构造路由元信息（仅标题与图标）。
     *
     * @param title 侧边栏与面包屑展示的标题
     * @param icon 图标（对应 src/assets/icons/svg 路径）
     */
    public MetaVo(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    /**
     * 构造路由元信息（标题、图标、是否缓存）。
     *
     * @param title 侧边栏与面包屑展示的标题
     * @param icon 图标（对应 src/assets/icons/svg 路径）
     * @param noCache 是否不缓存（true 则不被 keep-alive 缓存）
     */
    public MetaVo(String title, String icon, Boolean noCache) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
    }

    /**
     * 构造路由元信息（标题、图标、内链地址）。
     *
     * @param title 侧边栏与面包屑展示的标题
     * @param icon 图标（对应 src/assets/icons/svg 路径）
     * @param link 内链地址（仅 http(s):// 开头时生效）
     */
    public MetaVo(String title, String icon, String link) {
        this.title = title;
        this.icon = icon;
        this.link = link;
    }

    /**
     * 构造路由元信息（标题、图标、是否缓存、内链地址），内链地址仅 http(s) 开头时设置。
     *
     * @param title 侧边栏与面包屑展示的标题
     * @param icon 图标（对应 src/assets/icons/svg 路径）
     * @param noCache 是否不缓存（true 则不被 keep-alive 缓存）
     * @param link 内链地址（仅 http(s):// 开头时生效）
     */
    public MetaVo(String title, String icon, Boolean noCache, String link) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
        if (StringUtils.ishttp(link)) {
            this.link = link;
        }
    }

    /**
     * 构造完整路由元信息，内链地址与激活菜单均按规则过滤后设置。
     *
     * @param title 侧边栏与面包屑展示的标题
     * @param icon 图标（对应 src/assets/icons/svg 路径）
     * @param noCache 是否不缓存（true 则不被 keep-alive 缓存）
     * @param link 内链地址（仅 http(s):// 开头时生效）
     * @param activeMenu 激活菜单（仅以 / 开头时生效）
     */
    public MetaVo(String title, String icon, Boolean noCache, String link, String activeMenu) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
        if (StringUtils.ishttp(link)) {
            this.link = link;
        }
        if (StringUtils.startWithAnyIgnoreCase(activeMenu, "/")) {
            this.activeMenu = activeMenu;
        }
    }

}
