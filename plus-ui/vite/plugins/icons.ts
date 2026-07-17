import Icons from 'unplugin-icons/vite';

export default () => {
  return Icons({
    // 关闭自动安装(图标集已在 devDependencies 中显式声明,避免开发时触发网络安装)
    autoInstall: false,
    compiler: "vue3"
  });
};
