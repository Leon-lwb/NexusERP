interface Settings {
  title: string
  sideTheme: string
  showSettings: boolean
  topNav: boolean
  tagsView: boolean
  tagsIcon: boolean
  fixedHeader: boolean
  sidebarLogo: boolean
  dynamicTitle: boolean
  footerVisible: boolean
  footerContent: string
}

export default {
  title: import.meta.env.VITE_APP_TITLE,
  sideTheme: 'theme-dark',
  showSettings: true,
  topNav: false,
  tagsView: true,
  tagsIcon: false,
  fixedHeader: false,
  sidebarLogo: true,
  dynamicTitle: false,
  footerVisible: false,
  footerContent: 'Copyright © 2025 NEXUSERP. All Rights Reserved.'
} as Settings
