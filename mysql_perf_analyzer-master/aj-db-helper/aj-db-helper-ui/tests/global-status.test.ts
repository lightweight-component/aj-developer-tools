import { describe, expect, it } from "vitest";
import { compareGlobalStatus } from "../src/utils/global-status";
import type { GlobalStatusSnapshot } from "../src/api/realtime";

function snapshot(value: string | null, sampledAt: number): GlobalStatusSnapshot {
  return { sampledAt, uptime: 100, variables: [{ VARIABLE_NAME: "TEST", VARIABLE_VALUE: value }] };
}

describe("全局状态采样比较", () => {
  it("以基准累计计算增量及每秒速率", () => {
    const [row] = compareGlobalStatus(snapshot("120", 6000), snapshot("100", 1000));
    expect(row?.VARIABLE_VALUE_DELTA).toBe("20");
    expect(row?.VARIABLE_VALUE_PER_SEC).toBe("4");
    expect(row?.VARIABLE_VALUE_OLD).toBe("100");
  });
  it("保留超出 JavaScript 安全整数范围的计数器精度", () => {
    const [row] = compareGlobalStatus(snapshot("9007199254740993125", 2000), snapshot("9007199254740993123", 1000));
    expect(row?.VARIABLE_VALUE_DELTA).toBe("2");
  });
  it("精确计算小数与负增量", () => {
    const [row] = compareGlobalStatus(snapshot("1.10", 3000), snapshot("1.25", 1000));
    expect(row?.VARIABLE_VALUE_DELTA).toBe("-0.15");
    expect(row?.VARIABLE_VALUE_PER_SEC).toBe("-0.075");
  });
  it("首次采样和非数值指标不生成速率", () => {
    expect(compareGlobalStatus(snapshot("1", 1000), snapshot("1", 1000))[0]?.VARIABLE_VALUE_PER_SEC).toBeNull();
    expect(compareGlobalStatus(snapshot("ON", 2000), snapshot("OFF", 1000))[0]?.VARIABLE_VALUE_DELTA).toBeNull();
  });
  it("新出现的指标不使用零值伪造比较基准", () => {
    const baseline = { sampledAt: 1000, uptime: 100, variables: [] };
    expect(compareGlobalStatus(snapshot("123", 2000), baseline)[0]?.VARIABLE_VALUE_DELTA).toBeNull();
  });
});
