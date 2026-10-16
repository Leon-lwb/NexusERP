import { engineeringFeatureEnabled } from './model';

/** 前端入口需显式开启，后端仍独立检查工程模块配置与权限。 */
export const ENGINEERING_ENABLED = engineeringFeatureEnabled(import.meta.env?.VITE_APP_ENGINEERING_ENABLED);
export const ENGINEERING_DISABLED_NOTICE = '工程连接器功能尚未启用，请管理员核对前后端开关与连接器诊断后启用';
