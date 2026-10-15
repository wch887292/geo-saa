// GEO-SaaS 前端国际化入口（#2 i18n 中英切换）
//
// 设计要点：
// 1. 语言持久化到 localStorage（key: geo-saa-lang），刷新后保持用户选择
// 2. 默认语言：浏览器 Accept-Language 含 zh 则中文，否则英文；可被 localStorage 覆盖
// 3. 切换语言时同步设置 axios 请求头 Accept-Language，使后端返回的错误消息与前端一致
// 4. 使用 legacy: false（Vue 3 Composition API 模式），支持 <template> 中 $t() 与 setup 中 useI18n()
import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'

export const LANG_KEY = 'geo-saa-lang'
export const LANG_ZH = 'zh-CN'
export const LANG_EN = 'en'

/** 检测默认语言：localStorage > 浏览器语言 > 中文 */
function detectDefaultLocale() {
  const saved = localStorage.getItem(LANG_KEY)
  if (saved === LANG_ZH || saved === LANG_EN) return saved
  const browserLang = navigator.language || navigator.userLanguage || ''
  return browserLang.toLowerCase().startsWith('zh') ? LANG_ZH : LANG_EN
}

const i18n = createI18n({
  legacy: false,
  locale: detectDefaultLocale(),
  fallbackLocale: LANG_ZH,
  messages: {
    [LANG_ZH]: zhCN,
    [LANG_EN]: en
  }
})

/**
 * 切换语言，持久化到 localStorage 并同步到 document.documentElement.lang
 * （便于 CSS :lang() 选择器与无障碍工具识别）。
 * 后端 Accept-Language 头由 axios 拦截器在每次请求时读取 i18n.global.locale 动态设置。
 */
export function setLanguage(lang) {
  if (lang !== LANG_ZH && lang !== LANG_EN) return
  i18n.global.locale.value = lang
  localStorage.setItem(LANG_KEY, lang)
  document.documentElement.lang = lang
}

export default i18n
