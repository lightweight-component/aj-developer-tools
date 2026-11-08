package com.ajaxjs.devtools.netspeed.annotation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 以 1024 为换算基数的带宽单位及展示辅助枚举。
 * <p>单位在注解中表示每秒字节倍率，formatBytes 也用于普通字节量展示；输出不自动附加每秒后缀。
 */
@RequiredArgsConstructor
public enum BandwidthUnit {
    /**
     * 字节，倍率为一。
     */
    B(1),
    /**
     * 每单位 1024 字节。
     */
    KB(1024),
    /**
     * 每单位 1024 的平方字节。
     */
    MB(1024 * 1024),
    /**
     * 每单位 1024 的立方字节。
     */
    GB(1024 * 1024 * 1024);

    /**
     * 当前枚举单位对应的字节倍率，KB、MB、GB 以 1024 为基数。
     */
    @Getter
    private final long bytesPerSecond;

    /**
     * 按当前枚举倍率换算为字节/秒，使用 long 乘法，不检查负数或溢出。
     *
     * @param value 当前枚举单位下的带宽数值
     * @return 换算后的字节速率
     */
    public long toBytesPerSecond(long value) {
        return value * bytesPerSecond;
    }

    /**
     * 按 B、KB、MB、GB 展示字节量，非 B 单位保留两位小数。
     * <p>采用默认 Locale 的 String.format，负数会进入 B 分支。
     *
     * @param bytes 待展示的字节量
     * @return 带单位的展示文本，不包含每秒后缀
     */
    public static String formatBytes(long bytes) {
        if (bytes < KB.getBytesPerSecond())
            return bytes + " B";
        else if (bytes < MB.getBytesPerSecond())
            return String.format("%.2f KB", bytes / (double) KB.getBytesPerSecond());
        else if (bytes < GB.getBytesPerSecond())
            return String.format("%.2f MB", bytes / (double) MB.getBytesPerSecond());
        else
            return String.format("%.2f GB", bytes / (double) GB.getBytesPerSecond());
    }
}
