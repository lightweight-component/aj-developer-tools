import { afterEach, describe, expect, it, vi } from "vitest";
import { buildPlanTree, parseExplainJson } from "../src/utils/explain-plan";
import { getExplainPlan, getExplainPlanJson } from "../src/api/profiling";

afterEach(() => vi.unstubAllGlobals());

describe("执行计划解析", () => {
  const plan = { query_block: { select_id: 1, nested_loop: [{ table: { table_name: "robot", access_type: "range", possible_keys: ["PRIMARY"], attached_condition: "id < 10" } }] } };

  it("支持 JDBC EXPLAIN 字符串列和已解析的对象", () => {
    expect(parseExplainJson({ EXPLAIN: JSON.stringify(plan) })).toEqual(plan);
    expect(parseExplainJson({ explain: plan })).toEqual(plan);
    expect(parseExplainJson(plan)).toEqual(plan);
  });
  it("拒绝错误 JSON 和非对象计划", () => {
    expect(() => parseExplainJson({ EXPLAIN: "invalid" })).toThrow();
    expect(() => parseExplainJson({ EXPLAIN: "null" })).toThrow();
    expect(() => parseExplainJson({ EXPLAIN: "[]" })).toThrow();
  });
  it("树保留嵌套连接、数组及附加条件", () => {
    const tree = JSON.stringify(buildPlanTree(plan));
    expect(tree).toContain("robot");
    expect(tree).toContain("PRIMARY");
    expect(tree).toContain("id < 10");
    expect(tree).toContain("嵌套循环连接");
  });
});

describe("执行计划接口", () => {
  it("HTTP 错误仍展示服务端的不支持版本提示", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 0, message: "当前 MySQL 5.5 不支持 JSON 执行计划，需要 MySQL 5.6.5 或更高版本", data: null }), { status: 500, headers: { "Content-Type": "application/json" } })));
    await expect(getExplainPlanJson("select 1")).rejects.toThrow("需要 MySQL 5.6.5");
  });
  it("POST 表单 SQL，统一传统计划列名", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 1, data: [{ id: 1, table: "robot", selectType: "SIMPLE", possibleKeys: "PRIMARY", keyLen: "4", Extra: null }] }), { headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetch);
    expect(await getExplainPlan("select '&+' from robot")).toEqual([{ ID: 1, TABLE: "robot", SELECT_TYPE: "SIMPLE", POSSIBLE_KEYS: "PRIMARY", KEY_LEN: "4", EXTRA: null }]);
    const [url, request] = fetch.mock.calls[0]!;
    expect(url).toContain("/profiling/explain_plan");
    expect(request.method).toBe("POST");
    expect(request.body.get("sql")).toBe("select '&+' from robot");
  });
  it("业务失败时返回服务端错误，不显示伪造结果", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 0, message: "Only SELECT statement is supported", data: null }), { headers: { "Content-Type": "application/json" } })));
    await expect(getExplainPlanJson("invalid")).rejects.toThrow("Only SELECT");
  });
});
