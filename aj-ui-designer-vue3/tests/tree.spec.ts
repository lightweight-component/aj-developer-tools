import { describe, expect, it } from "vitest";
import { normalizeNodes, removeNode } from "../src/core/tree";

describe("设计数据树", (): void => {
  it("兼容旧版输入类型并生成节点标识", (): void => {
    const nodes = normalizeNodes([{ type: "input_text", children: [] }]);

    expect(nodes[0].type).toBe("Input");
    expect(nodes[0].id).toBeTruthy();
  });

  it("能够在嵌套容器中移除节点", (): void => {
    const nodes = normalizeNodes([{ id: "parent", type: "Form", children: [{ id: "child", type: "Button", children: [] }] }]);
    const removed = removeNode(nodes, "child");

    expect(removed?.type).toBe("Button");
    expect(nodes[0].children).toHaveLength(0);
  });
});
