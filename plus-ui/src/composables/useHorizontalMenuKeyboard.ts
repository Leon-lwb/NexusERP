import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue';
import type { MenuInstance } from 'element-plus';

/** 为动态折叠的横向菜单补齐键盘入口，不依赖组件库的私有实例。 */
export const useHorizontalMenuKeyboard = () => {
  const menuRef = ref<MenuInstance>();
  const popupClass = `horizontal-navigation-${useId().replace(/[^\w-]/g, '-')}`;
  let observer: MutationObserver | undefined;

  const enabled = (item: HTMLElement) => !item.classList.contains('is-disabled') && item.getAttribute('aria-disabled') !== 'true';
  const topItems = () => {
    const root = menuRef.value?.$el as HTMLElement | undefined;
    return root
      ? Array.from(root.querySelectorAll<HTMLElement>('[role="menuitem"]')).filter(
          (item) => item.closest('[role="menubar"]') === root && !item.parentElement?.closest('[role="menuitem"]')
        )
      : [];
  };
  /** 每次仅查询具体弹层的直属菜单项，排除嵌套列表与重复包装。 */
  const popupItems = (popup: HTMLElement | null) =>
    popup
      ? Array.from(popup.querySelectorAll<HTMLElement>('[role="menuitem"]')).filter(
          (item) => enabled(item) && item.closest('.el-menu--popup') === popup
        )
      : [];
  const itemIndex = (item: HTMLElement) => (item.classList.contains('el-sub-menu__hide-arrow') ? 'sub-menu-more' : item.dataset.menuIndex);
  const popupForOwner = (owner: HTMLElement) => {
    const id = owner.querySelector('[aria-describedby]')?.getAttribute('aria-describedby');
    return id ? (document.getElementById(id)?.querySelector<HTMLElement>('.el-menu--popup') ?? null) : null;
  };

  /** 通过 Tooltip 的 ARIA 关联定位当前层的触发项，兼容鼠标和键盘打开。 */
  const ownerForPopup = (popup: HTMLElement | null) => {
    const popper = popup?.closest<HTMLElement>('.el-popper');
    if (!popper?.id) return null;
    const title = Array.from(document.querySelectorAll<HTMLElement>('.el-sub-menu__title[aria-describedby]')).find((element) =>
      element.getAttribute('aria-describedby')?.split(/\s+/).includes(popper.id)
    );
    return title?.closest<HTMLElement>('[role="menuitem"]') ?? null;
  };

  /** 折叠与路由变化会重建菜单节点，因此持续维护焦点和省略入口名称。 */
  const syncFocus = () => {
    topItems().forEach((item) => {
      const tabIndex = enabled(item) ? 0 : -1;
      if (item.tabIndex !== tabIndex) item.tabIndex = tabIndex;
      if (item.classList.contains('el-sub-menu')) {
        item.setAttribute('aria-haspopup', 'menu');
        if (!item.textContent?.trim()) item.setAttribute('aria-label', '更多导航菜单');
      }
    });
  };

  const closePopup = (target: HTMLElement) => {
    const owner = ownerForPopup(target.closest<HTMLElement>('.el-menu--popup'));
    const index = owner ? itemIndex(owner) : undefined;
    if (!owner || !index) return false;
    menuRef.value?.close(index);
    owner.focus();
    return true;
  };

  /** 在捕获阶段处理键盘，避免初始菜单监听器与重建后的监听器重复触发。 */
  const handleKeydown = async (event: KeyboardEvent) => {
    if (!(event.target instanceof HTMLElement)) return;
    const target = event.target.closest<HTMLElement>('[role="menuitem"]');
    if (!target) return;
    const inPopup = Boolean(target.closest(`.${popupClass}`));
    const items = inPopup ? popupItems(target.closest<HTMLElement>('.el-menu--popup')) : topItems().filter(enabled);
    const index = items.indexOf(target);
    if (index < 0) return;
    let nextIndex: number | undefined;
    if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = items.length - 1;
    else if (event.key === (inPopup ? 'ArrowDown' : 'ArrowRight')) nextIndex = (index + 1) % items.length;
    else if (event.key === (inPopup ? 'ArrowUp' : 'ArrowLeft')) nextIndex = (index - 1 + items.length) % items.length;
    if (nextIndex !== undefined) {
      event.preventDefault();
      event.stopPropagation();
      items[nextIndex].focus();
      return;
    }
    if (inPopup && ['Escape', 'ArrowLeft'].includes(event.key)) {
      if (closePopup(target)) {
        event.preventDefault();
        event.stopPropagation();
      }
      return;
    }
    if (!['Enter', ' ', 'ArrowDown', 'ArrowUp', 'ArrowRight'].includes(event.key)) return;
    if (inPopup && !['Enter', ' ', 'ArrowRight'].includes(event.key)) return;
    if (inPopup && event.key === 'ArrowRight' && !target.classList.contains('el-sub-menu')) return;
    event.preventDefault();
    event.stopPropagation();
    if (!target.classList.contains('el-sub-menu')) {
      if (event.key === 'Enter' || event.key === ' ') target.click();
      return;
    }
    const menuIndex = itemIndex(target);
    if (!menuIndex) return;
    menuRef.value?.open(menuIndex);
    await nextTick();
    const children = popupItems(popupForOwner(target));
    (event.key === 'ArrowUp' ? children.at(-1) : children[0])?.focus();
  };

  onMounted(() => {
    const root = menuRef.value?.$el as HTMLElement | undefined;
    if (!root) return;
    syncFocus();
    observer = new MutationObserver(syncFocus);
    observer.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['tabindex', 'class'] });
    document.addEventListener('keydown', handleKeydown, true);
  });
  onBeforeUnmount(() => {
    observer?.disconnect();
    document.removeEventListener('keydown', handleKeydown, true);
  });
  return { menuRef, popupClass };
};
