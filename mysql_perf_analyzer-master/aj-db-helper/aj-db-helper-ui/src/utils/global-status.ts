import type { GlobalStatusSnapshot } from "../api/realtime";

/** 使用定点整数计算，避免大于 2^53 的 MySQL 计数器丢失精度。 */
function decimal(value: string | null) {
  if (value === null || !/^[+-]?\d+(\.\d+)?$/.test(value))
    return null;

  const scale = value.split(".")[1]?.length ?? 0;
  return { value: BigInt(value.replace(".", "")), scale };
}

function format(value: bigint, scale: number): string {
  const sign = value < 0n ? "-" : "";
  const digits = (value < 0n ? -value : value).toString().padStart(scale + 1, "0");

  if (!scale)
    return sign + digits;

  return (sign + digits.slice(0, -scale) + "." + digits.slice(-scale)).replace(/\.?0+$/, "");
}

export function compareGlobalStatus(current: GlobalStatusSnapshot, baseline: GlobalStatusSnapshot) {
  const previous = new Map(baseline.variables.map((row) => [row.VARIABLE_NAME, row.VARIABLE_VALUE]));
  const elapsed = current.sampledAt - baseline.sampledAt;

  return current.variables.map((row) => {
    const old = previous.get(row.VARIABLE_NAME) ?? null;
    const nowNumber = decimal(row.VARIABLE_VALUE);
    const oldNumber = decimal(old);
    let delta: string | null = null;
    let rate: string | null = null;

    if (nowNumber && oldNumber && elapsed > 0) {
      const scale = Math.max(nowNumber.scale, oldNumber.scale);
      const difference = nowNumber.value * 10n ** BigInt(scale - nowNumber.scale)
        - oldNumber.value * 10n ** BigInt(scale - oldNumber.scale);
      delta = format(difference, scale);
      rate = format(difference * 1000000000n / (10n ** BigInt(scale) * BigInt(elapsed)), 6);
    }

    return {
      ...row,
      VARIABLE_VALUE_OLD: old,
      VARIABLE_VALUE_DELTA: delta,
      VARIABLE_VALUE_PER_SEC: rate,
      changed: previous.has(row.VARIABLE_NAME) && row.VARIABLE_VALUE !== old,
    };
  });
}
