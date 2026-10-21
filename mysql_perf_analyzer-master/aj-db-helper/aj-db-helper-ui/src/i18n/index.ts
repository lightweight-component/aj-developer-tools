import { createI18n } from "vue-i18n";
import zhCN from "./locales/zh-CN.json";
import zhTW from "./locales/zh-TW.json";
import enUS from "./locales/en-US.json";
import viewZhCN from "view-ui-plus/dist/locale/zh-CN.js";
import viewZhTW from "view-ui-plus/dist/locale/zh-TW.js";
import viewEnUS from "view-ui-plus/dist/locale/en-US.js";

export const supportedLocales = ["zh-CN", "zh-TW", "en-US"] as const;
export type AppLocale = typeof supportedLocales[number];
const storageKey = "db-helper.locale";

/** 默认简体中文；存储不可用或值无效时仍可正常启动。 */
export function initialLocale(): AppLocale {
  try {
    const saved = localStorage.getItem(storageKey);
    return supportedLocales.includes(saved as AppLocale) ? saved as AppLocale : "zh-CN";
  } catch {
    return "zh-CN";
  }
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale(),
  fallbackLocale: "zh-CN",
  missingWarn: false,
  fallbackWarn: false,
  messages: {
    "zh-CN": { ...zhCN, ...viewZhCN },
    "zh-TW": { ...zhTW, ...viewZhTW },
    "en-US": { ...enUS, ...viewEnUS },
  },
});

/** 脚本中使用 t('key', '中文原文')，模板使用同签名的 $t。 */
export const t = i18n.global.t;

export function setLocale(locale: AppLocale): void {
  if (!supportedLocales.includes(locale))
    return;

  i18n.global.locale.value = locale;

  try {
    localStorage.setItem(storageKey, locale);
  } catch {
    // 隐私模式或存储被禁用时，只在当前页面记住选择。
  }
}

export function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString(i18n.global.locale.value, { hour12: false });
}
