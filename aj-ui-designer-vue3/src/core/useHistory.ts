import { computed, ref } from "vue";

export function useHistory<T>(initial: T, limit: number = 50) {
  const past = ref<string[]>([]);
  const future = ref<string[]>([]);
  const current = ref<T>(structuredClone(initial));

  function snapshot(): void {
    past.value.push(JSON.stringify(current.value));
    if (past.value.length > limit)
      past.value.shift();

    future.value = [];
  }

  function replace(value: T, saveHistory: boolean = true): void {
    if (saveHistory)
      snapshot();

    current.value = structuredClone(value);
  }

  function undo(): void {
    const value: string | undefined = past.value.pop();
    if (!value)
      return;

    future.value.push(JSON.stringify(current.value));
    current.value = JSON.parse(value) as T;
  }

  function redo(): void {
    const value: string | undefined = future.value.pop();
    if (!value)
      return;

    past.value.push(JSON.stringify(current.value));
    current.value = JSON.parse(value) as T;
  }

  return { current, replace, snapshot, undo, redo, canUndo: computed((): boolean => past.value.length > 0), canRedo: computed((): boolean => future.value.length > 0) };
}
