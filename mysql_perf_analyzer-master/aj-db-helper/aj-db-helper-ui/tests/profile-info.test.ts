import { afterEach, describe, expect, it, vi } from "vitest";
import { visibleProfileColumns } from "../src/utils/profile-info";
import { analyzeQuery } from "../src/api/profiling";

afterEach(() => vi.unstubAllGlobals());

describe("阶段分析", () => {
  it("保留阶段和耗时，过滤各种零值，保留极小非零值及未知列", () => {
    const rows = [
      { STATUS: "starting", DURATION: "0.000000", CPU_USER: "0.000000", SOURCE_FILE: null, NEW_METRIC: "0.0000000000000000001", SWAPS: "0e3" },
      { STATUS: "starting", DURATION: "0", CPU_USER: ".0", SOURCE_FILE: "", NEW_METRIC: "0", SWAPS: "-0.00" },
    ];
    expect(visibleProfileColumns(rows)).toEqual(["STATUS", "DURATION", "NEW_METRIC"]);
    expect(rows).toHaveLength(2);
  });

  it("一次请求取得两类结果，并保留不支持时的说明", async () => {
    const data = { sessionStats: [{ VARIABLE_NAME: "COM_SELECT", VARIABLE_VALUE: "1" }], profileInfo: [], profileAvailable: false, profileMessage: "当前数据库不支持 SHOW PROFILE" };
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 1, data }), { headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetcher);
    expect(await analyzeQuery("select 1")).toEqual(data);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0]?.[0]).toContain("/profiling/analyze");
    expect(fetcher.mock.calls[0]?.[1].body.get("sql")).toBe("select 1");
  });
});
