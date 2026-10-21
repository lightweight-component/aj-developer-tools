import { t } from "../i18n";
export type ProfileRow = Record<string, string | null>;

export const profileColumnTitles: Record<string, string> = {
  get STATUS() { return t('ui.executionStage', "执行阶段"); }, get DURATION() { return t('ui.durationSeconds', "耗时（秒）"); }, get CPU_USER() { return t('ui.userCPUSeconds', "用户态 CPU（秒）"); }, get CPU_SYSTEM() { return t('ui.systemCPUSeconds', "内核态 CPU（秒）"); },
  get CONTEXT_VOLUNTARY() { return t('ui.voluntaryContextSwitches', "主动上下文切换"); }, get CONTEXT_INVOLUNTARY() { return t('ui.involuntaryContextSwitches', "非主动上下文切换"); },
  get BLOCK_OPS_IN() { return t('ui.blockInputOperations', "块输入次数"); }, get BLOCK_OPS_OUT() { return t('ui.blockOutputOperations', "块输出次数"); }, get MESSAGES_SENT() { return t('ui.messagesSent', "发送消息数"); }, get MESSAGES_RECEIVED() { return t('ui.messagesReceived', "接收消息数"); },
  get PAGE_FAULTS_MAJOR() { return t('ui.majorPageFaults', "主要缺页次数"); }, get PAGE_FAULTS_MINOR() { return t('ui.minorPageFaults', "次要缺页次数"); }, get SWAPS() { return t('ui.swaps', "交换次数"); },
  get SOURCE_FUNCTION() { return t('ui.sourceFunction', "源码函数"); }, get SOURCE_FILE() { return t('ui.sourceFile', "源码文件"); }, get SOURCE_LINE() { return t('ui.sourceLine', "源码行号"); },
};

/** 始终保留阶段和耗时；其余字段隐藏整列空值或数值零，保持原始行顺序及精度。 */
export function visibleProfileColumns(rows: ProfileRow[]): string[] {
  const keys = new Set(["STATUS", "DURATION", ...rows.flatMap((row) => Object.keys(row))]);
  return [...keys].filter((key) => key === "STATUS" || key === "DURATION" || rows.some((row) => {
    const value = row[key]?.trim();
    return !!value && !/^[+-]?(?:0+(?:\.0*)?|\.0+)(?:e[+-]?\d+)?$/i.test(value);
  }));
}
