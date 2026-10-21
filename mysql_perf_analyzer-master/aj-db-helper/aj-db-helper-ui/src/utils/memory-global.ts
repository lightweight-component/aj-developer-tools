export const memoryFields = [
  'COUNT_ALLOC', 'COUNT_FREE', 'SUM_NUMBER_OF_BYTES_ALLOC', 'SUM_NUMBER_OF_BYTES_FREE',
  'LOW_COUNT_USED', 'CURRENT_COUNT_USED', 'HIGH_COUNT_USED',
  'LOW_NUMBER_OF_BYTES_USED', 'CURRENT_NUMBER_OF_BYTES_USED', 'HIGH_NUMBER_OF_BYTES_USED',
] as const;
export type MemoryField = typeof memoryFields[number];
export type MemoryRow = { EVENT_NAME: string; THREAD_ID?: string } & Record<MemoryField, string | null>;
export interface MemorySnapshot { sampledAt: number; rows: MemoryRow[]; }
export const normalizeMemoryKey = (key: string) => key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toUpperCase();

/** 与服务端 LIKE 保持一致，空值或 % 均表示全部线程。 */
export function normalizeThreadFilter(value: string): string {
  const filter = value.trim();
  if (!/^[0-9%_]*$/.test(filter))
    throw new Error('INVALID_THREAD_FILTER');
  return /^%*$/.test(filter) ? '' : filter;
}

const memoryKey = (row: MemoryRow) => JSON.stringify([row.THREAD_ID ?? '', row.EVENT_NAME]);

/** 所有字段为整数计数或字节数；使用 BigInt 保留大整数，速率截取至小数点后六位。 */
export function compareMemory(current: MemorySnapshot, baseline: MemorySnapshot): Record<string, string | null>[] {
  const previous = new Map(baseline.rows.map(row => [memoryKey(row), row]));
  const elapsed = current.sampledAt - baseline.sampledAt;
  return current.rows.flatMap(row => {
    const old = previous.get(memoryKey(row));
    const output: Record<string, string | null> = { ...row };
    let changed = false;
    for (const field of memoryFields) {
      const now = row[field];
      const before = old ? old[field] : '0';
      const delta = now !== null && before !== null ? BigInt(now) - BigInt(before) : null;
      output[field + '_OLD'] = before;
      output[field + '_DELTA'] = delta?.toString() ?? null;
      output[field + '_PER_SEC'] = delta !== null && elapsed > 0 ? formatRate(delta * 1000000000n / BigInt(elapsed)) : null;
      changed ||= delta !== null ? delta !== 0n : now !== before;
    }
    return changed ? [output] : [];
  });
}

function formatRate(micro: bigint): string {
  const sign = micro < 0n ? '-' : '';
  const digits = (micro < 0n ? -micro : micro).toString().padStart(7, '0');
  return sign + (digits.slice(0, -6) + '.' + digits.slice(-6)).replace(/\.?0+$/, '');
}
