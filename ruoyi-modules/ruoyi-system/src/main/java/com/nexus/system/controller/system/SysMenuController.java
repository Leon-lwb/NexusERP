package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.lang.tree.Tree;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.constant.TenantConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.SysMenu;
import com.nexus.system.domain.bo.SysMenuBo;
import com.nexus.system.domain.vo.RouterVo;
import com.nexus.system.domain.vo.SysMenuVo;
import com.nexus.system.service.ISysMenuService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单管理Controller
 * <p>提供菜单路由构建、菜单树查询、角色/租户套餐菜单树加载、菜单增删改及级联删除等接口。
 * 属于系统管理（System）模块，菜单管理限定超管与租户管理员角色。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/menu")
public class SysMenuController extends BaseController {

    /** 菜单业务服务，提供菜单树、路由构建、唯一性校验与增删改能力。 */
    private final ISysMenuService menuService;

    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping("/getRouters")
    public R<List<RouterVo>> getRouters() {
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(LoginHelper.getUserId());
        return R.ok(menuService.buildMenus(menus));
    }

    /**
     * 查询菜单列表（树形结构）。
     *
     * @param menu 菜单查询条件（菜单名称、状态等）
     * @return 菜单列表
     */
    @SaCheckRole(value = {
        TenantConstants.SUPER_ADMIN_ROLE_KEY,
        TenantConstants.TENANT_ADMIN_ROLE_KEY
    }, mode = SaMode.OR)
    @SaCheckPermission("system:menu:list")
    @GetMapping("/list")
    public R<List<SysMenuVo>> list(SysMenuBo menu) {
        List<SysMenuVo> menus = menuService.selectMenuList(menu, LoginHelper.getUserId());
        return R.ok(menus);
    }

    /**
     * 根据菜单编号获取详细信息
     *
     * @param menuId 菜单ID
     */
    @SaCheckRole(value = {
        TenantConstants.SUPER_ADMIN_ROLE_KEY,
        TenantConstants.TENANT_ADMIN_ROLE_KEY
    }, mode = SaMode.OR)
    @SaCheckPermission("system:menu:query")
    @GetMapping(value = "/{menuId}")
    public R<SysMenuVo> getInfo(@PathVariable Long menuId) {
        return R.ok(menuService.selectMenuById(menuId));
    }

    /**
     * 获取菜单下拉树列表（用于新增/修改菜单时选择上级菜单）。
     *
     * @param menu 菜单查询条件
     * @return 菜单下拉树
     */
    @SaCheckPermission("system:menu:query")
    @GetMapping("/treeselect")
    public R<List<Tree<Long>>> treeselect(SysMenuBo menu) {
        List<SysMenuVo> menus = menuService.selectMenuList(menu, LoginHelper.getUserId());
        return R.ok(menuService.buildMenuTreeSelect(menus));
    }

    /**
     * 加载对应角色菜单列表树
     *
     * @param roleId 角色ID
     */
    @SaCheckPermission("system:menu:query")
    @GetMapping(value = "/roleMenuTreeselect/{roleId}")
    public R<MenuTreeSelectVo> roleMenuTreeselect(@PathVariable("roleId") Long roleId) {
        List<SysMenuVo> menus = menuService.selectMenuList(LoginHelper.getUserId());
        MenuTreeSelectVo selectVo = new MenuTreeSelectVo(
            menuService.selectMenuListByRoleId(roleId),
            menuService.buildMenuTreeSelect(menus));
        return R.ok(selectVo);
    }

    /**
     * 加载对应租户套餐菜单列表树
     *
     * @param packageId 租户套餐ID
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:query")
    @GetMapping(value = "/tenantPackageMenuTreeselect/{packageId}")
    public R<MenuTreeSelectVo> tenantPackageMenuTreeselect(@PathVariable("packageId") Long packageId) {
        List<SysMenuVo> menus = menuService.selectMenuList(LoginHelper.getUserId());
        List<Tree<Long>> list = menuService.buildMenuTreeSelect(menus);
        // 删除租户管理菜单
        list.removeIf(menu -> menu.getId() == 6L);
        List<Long> ids = new ArrayList<>();
        if (packageId > 0L) {
            ids = menuService.selectMenuListByPackageId(packageId);
        }
        MenuTreeSelectVo selectVo = new MenuTreeSelectVo(ids, list);
        return R.ok(selectVo);
    }

    /**
     * 新增菜单，校验菜单名称、外链地址与路由配置唯一性。
     *
     * @param menu 菜单新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:add")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysMenuBo menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            return R.fail("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        } else if (SystemConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath())) {
            return R.fail("新增菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        } else if (!menuService.checkRouteConfigUnique(menu)) {
            return R.fail("新增菜单'" + menu.getMenuName() + "'失败，路由名称或地址已存在");
        }
        return toAjax(menuService.insertMenu(menu));
    }

    /**
     * 修改菜单，校验菜单名称、外链地址、上级菜单与路由配置唯一性。
     *
     * @param menu 菜单修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:edit")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysMenuBo menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        } else if (SystemConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath())) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        } else if (menu.getMenuId().equals(menu.getParentId())) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        } else if (!menuService.checkRouteConfigUnique(menu)) {
            return R.fail("修改菜单'" + menu.getMenuName() + "'失败，路由名称或地址已存在");
        }
        return toAjax(menuService.updateMenu(menu));
    }

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:remove")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{menuId}")
    public R<Void> remove(@PathVariable("menuId") Long menuId) {
        if (menuService.hasChildByMenuId(menuId)) {
            return R.warn("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId)) {
            return R.warn("菜单已分配,不允许删除");
        }
        return toAjax(menuService.deleteMenuById(menuId));
    }

    /**
     * 角色菜单列表树信息
     *
     * @param checkedKeys 选中菜单列表
     * @param menus       菜单下拉树结构列表
     */
    public record MenuTreeSelectVo(List<Long> checkedKeys, List<Tree<Long>> menus) {
    }

    /**
     * 批量级联删除菜单
     *
     * @param menuIds 菜单ID串
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:remove")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/cascade/{menuIds}")
    public R<Void> remove(@PathVariable("menuIds") Long[] menuIds) {
        List<Long> menuIdList = List.of(menuIds);
        if (menuService.hasChildByMenuId(menuIdList)) {
            return R.warn("存在子菜单,不允许删除");
        }
        menuService.deleteMenuById(menuIdList);
        return R.ok();
    }

}
