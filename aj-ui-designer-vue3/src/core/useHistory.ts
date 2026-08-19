import { computed, ref } from "vue";

/**
 * 为可序列化的设计数据提供有限步数的撤销/重做。
 * 快照采用 JSON 字符串，确保历史记录不会和当前响应式对象共享引用。
 */
export function useHistory<T>(initial: T, limit: number = 50) {
  const past = ref<string[]>([]);
  const future = ref<string[]>([]);
  const current = ref<T>(structuredClone(initial));

  /** 在会改变数据前调用，保存当前状态并废弃原有重做分支。 */
  function snapshot(): void {
    past.value.push(JSON.stringify(current.value));
    if (past.value.length > limit)
      past.value.shift();

    future.value = [];
  }

  /** 用完整数据替换当前状态，适用于 JSON 导入和重置等原子操作。 */
  function replace(value: T, saveHistory: boolean = true): void {
    if (saveHistory)
      snapshot();

    current.value = structuredClone(value);
  }

  /** 回到最近一次快照，并把离开的状态压入重做栈。 */
  function undo(): void {
    const value: string | undefined = past.value.pop();
    if (!value)
      return;

    future.value.push(JSON.stringify(current.value));
    current.value = JSON.parse(value) as T;
  }

  /** 恢复最近一次被撤销的状态。 */
  function redo(): void {
    const value: string | undefined = future.value.pop();
    if (!value)
      return;

    past.value.push(JSON.stringify(current.value));
    current.value = JSON.parse(value) as T;
  }

  return { current, replace, snapshot, undo, redo, canUndo: computed((): boolean => past.value.length > 0), canRedo: computed((): boolean => future.value.length > 0) };
}
