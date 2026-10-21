import { afterEach, describe, expect, it, vi } from "vitest";
import { computed } from "vue";
import { i18n, initialLocale, setLocale, t } from "../src/i18n";
import { allPanels } from "../src/router/panels";
import { getSessionStatDescription } from "../src/utils/session-stat-descriptions";
import { profileColumnTitles } from "../src/utils/profile-info";
import zhCN from "../src/i18n/locales/zh-CN.json";
import zhTW from "../src/i18n/locales/zh-TW.json";
import enUS from "../src/i18n/locales/en-US.json";

afterEach(() => {
  i18n.global.locale.value = "zh-CN";
  vi.unstubAllGlobals();
});

describe("多语言", () => {
  it("支持第二个参数作为中文回退文案和第三个参数插值", () => {
    expect(t("login.title", "用户登录")).toBe("用户登录");
    i18n.global.locale.value = "en-US";
    expect(t("ui.processes2", "共 {count} 个进程", { named: { count: 5 } })).toBe("5 processes");
  });

  it("菜单、指标说明和阶段表头响应语言切换", () => {
    const panel = computed(() => allPanels.find((item) => item.path === "/realtime/processes")!.title);
    const description = computed(() => getSessionStatDescription("BYTES_SENT"));
    const column = computed(() => profileColumnTitles.STATUS);
    expect(panel.value).toBe("进程列表");
    i18n.global.locale.value = "en-US";
    expect(panel.value).toBe("Processes");
    expect(description.value).toBe("Bytes sent to the client");
    expect(column.value).toBe("Execution stage");
    expect(t("i.table.noDataText")).toBe("No Data");
    i18n.global.locale.value = "zh-TW";
    expect(panel.value).toBe("進程列表");
    expect(description.value).toBe("發送給客戶端的字節數");
    expect(getSessionStatDescription("UNKNOWN_STATUS")).toBeUndefined();
  });

  it("保存语言选择并容忍无效值和禁用存储", () => {
    const setItem = vi.fn();
    const getItem = vi.fn().mockReturnValue("zh-TW");
    vi.stubGlobal("localStorage", { getItem, setItem });
    expect(initialLocale()).toBe("zh-TW");
    setLocale("en-US");
    expect(setItem).toHaveBeenCalledWith("db-helper.locale", "en-US");
    getItem.mockReturnValue("invalid");
    expect(initialLocale()).toBe("zh-CN");
    getItem.mockImplementation(() => { throw new Error("disabled"); });
    setItem.mockImplementation(() => { throw new Error("disabled"); });
    expect(initialLocale()).toBe("zh-CN");
    expect(() => setLocale("zh-TW")).not.toThrow();
  });

  it("三份语言包覆盖相同文案且均可编译", () => {
    expect(Object.keys(zhTW.ui).sort()).toEqual(Object.keys(zhCN.ui).sort());
    expect(Object.keys(enUS.ui).sort()).toEqual(Object.keys(zhCN.ui).sort());
    for (const locale of ["zh-CN", "zh-TW", "en-US"] as const) {
      i18n.global.locale.value = locale;
      for (const key of Object.keys(zhCN.ui))
        expect(() => t(`ui.${key}`, { count: 1, total: 2, time: "12:00", baseline: "12:00", current: "12:01", interval: "60", panel: "Test", table: "robot", access: "range", rows: "5" })).not.toThrow();
    }
  });
});
