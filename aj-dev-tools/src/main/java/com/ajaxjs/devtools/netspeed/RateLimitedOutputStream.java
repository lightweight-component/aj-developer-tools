package com.ajaxjs.devtools.netspeed;

import com.ajaxjs.devtools.netspeed.core.BandwidthLimitManager;
import com.ajaxjs.devtools.netspeed.core.TokenBucket;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 通过令牌桶限制同步 Servlet 响应写入速度的输出流。
 * <p>每次写入先消耗令牌，数组写入按配置大小分块；可使用独立桶或共享桶。
 * 统计字节只在底层写入成功返回后累计，令牌消耗与成功写入量可能因 I/O 失败而不同。
 * <p>不支持 Servlet 非阻塞写入，不保证多个线程并发操作同一流的安全性。
 * 动态调整只改变桶速率，不更新构造参数或分块大小。
 */
@Slf4j
public class RateLimitedOutputStream extends ServletOutputStream {
    /**
     * 实际写入的底层流。
     */
    private final OutputStream outputStream;

    /**
     * 独立或共享令牌桶，负责申请字节配额。
     */
    @Getter
    private final TokenBucket tokenBucket;

    /**
     * 分块配置，输出流内为限制后的实际大小，包装器内非正值表示自动选择。
     */
    @Getter
    private final int chunkSize;

    /**
     * 构造时配置的字节带宽，不随动态修改桶速率更新，setBandwidth 后该值不变。
     * 初始配置带宽，字节/秒
     */
    @Getter
    private final long bandwidthBytesPerSecond;

    /**
     * 本流成功写入的累计字节数，不含其他共享流。
     */
    @Getter
    private long totalBytesWritten = 0;

    /**
     * 流创建时 nanoTime，用于生命周期速率统计。
     */
    private final long startTime = System.nanoTime();

    /**
     * 是否已标记关闭，不代表底层刷新或关闭一定成功。
     */
    private volatile boolean closed = false;

    /**
     * 是否已经记录首次数组写入日志。
     */
    private boolean logged = false;

    /**
     * 创建限速输出流。
     * <p>未提供共享桶时新建容量和速率均为一秒带宽的桶；提供共享桶时原样复用。
     * 未指定分块大小时按带宽分档选择，实际分块限制在 512 至 65536 字节。
     * 底层流和共享桶应非空，带宽应为正数，当前不统一校验。
     *
     * @param outputStream            非空底层输出流，由本流关闭时负责关闭
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     */
    public RateLimitedOutputStream(OutputStream outputStream, long bandwidthBytesPerSecond) {
        this(outputStream, bandwidthBytesPerSecond, calculateOptimalChunkSize(bandwidthBytesPerSecond));
    }

    /**
     * 创建限速输出流。
     * <p>未提供共享桶时新建容量和速率均为一秒带宽的桶；提供共享桶时原样复用。
     * 未指定分块大小时按带宽分档选择，实际分块限制在 512 至 65536 字节。
     * 底层流和共享桶应非空，带宽应为正数，当前不统一校验。
     *
     * @param outputStream            非空底层输出流，由本流关闭时负责关闭
     * @param tokenBucket             要复用的令牌桶，输出流构造要求非空，包装器允许 null
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     */
    public RateLimitedOutputStream(OutputStream outputStream, TokenBucket tokenBucket, long bandwidthBytesPerSecond) {
        this(outputStream, tokenBucket, bandwidthBytesPerSecond, calculateOptimalChunkSize(bandwidthBytesPerSecond));
    }

    /**
     * 创建限速输出流。
     * <p>未提供共享桶时新建容量和速率均为一秒带宽的桶；提供共享桶时原样复用。
     * 未指定分块大小时按带宽分档选择，实际分块限制在 512 至 65536 字节。
     * 底层流和共享桶应非空，带宽应为正数，当前不统一校验。
     *
     * @param outputStream            非空底层输出流，由本流关闭时负责关闭
     * @param tokenBucket             要复用的令牌桶，输出流构造要求非空，包装器允许 null
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     * @param chunkSize               请求分块字节数，构造器及包装器的具体处理见方法说明
     */
    public RateLimitedOutputStream(OutputStream outputStream, TokenBucket tokenBucket, long bandwidthBytesPerSecond, int chunkSize) {
        this.outputStream = outputStream;
        this.bandwidthBytesPerSecond = bandwidthBytesPerSecond;
        this.chunkSize = Math.max(512, Math.min(chunkSize, 65536));
        this.tokenBucket = tokenBucket;

        log.info("RateLimitedOutputStream created with shared bucket: bandwidth={}/s, chunkSize={}", BandwidthLimitManager.formatBytes(bandwidthBytesPerSecond), chunkSize);
    }

    /**
     * 创建限速输出流。
     * <p>未提供共享桶时新建容量和速率均为一秒带宽的桶；提供共享桶时原样复用。
     * 未指定分块大小时按带宽分档选择，实际分块限制在 512 至 65536 字节。
     * 底层流和共享桶应非空，带宽应为正数，当前不统一校验。
     *
     * @param outputStream            非空底层输出流，由本流关闭时负责关闭
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     * @param chunkSize               请求分块字节数，构造器及包装器的具体处理见方法说明
     */
    public RateLimitedOutputStream(OutputStream outputStream, long bandwidthBytesPerSecond, int chunkSize) {
        this.outputStream = outputStream;
        this.bandwidthBytesPerSecond = bandwidthBytesPerSecond;
        this.chunkSize = Math.max(512, Math.min(chunkSize, 65536));
        this.tokenBucket = new TokenBucket(bandwidthBytesPerSecond, bandwidthBytesPerSecond);// 桶容量 = 1秒流量，允许短时突发

        log.info("RateLimitedOutputStream created: bandwidth={}/s, chunkSize={}, capacity={}/s",
                BandwidthLimitManager.formatBytes(bandwidthBytesPerSecond), chunkSize, BandwidthLimitManager.formatBytes(bandwidthBytesPerSecond));
    }

    /**
     * 按带宽分档选择 1024、4096、8192 或 16384 字节。
     * <p>分界分别为 200 KiB/s、1 MiB/s、5 MiB/s，采用离散阈值，并非除以五十的公式。
     *
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     * @return 自动选择的分块字节数
     */
    private static int calculateOptimalChunkSize(long bandwidthBytesPerSecond) {
        if (bandwidthBytesPerSecond < 200 * 1024)
            return 1024; // 低于 200KB/s，使用 1-4KB
        else if (bandwidthBytesPerSecond < 1024 * 1024)
            return 4096; // 200KB/s - 1MB/s，使用 4-8KB
        else if (bandwidthBytesPerSecond < 5 * 1024 * 1024)
            return 8192;  // 1MB/s - 5MB/s，使用 8-16KB
        else
            return 16384; // 高于 5MB/s，使用 16-32KB
    }

    /**
     * 检查关闭状态后获取令牌，再向底层流写入数据。
     * <p>单字节调用消耗一个令牌；数组调用委托区间重载，区间重载按 chunkSize 分块，
     * 每块底层写入成功后累计字节。零长度区间在关闭检查后直接返回。
     * 本类未统一预先校验数组区间，非法参数的异常行为也受底层输出流影响。
     *
     * @param b 待写入字节的整数值
     * @throws IOException 流已关闭或底层 I/O 操作失败时抛出
     */
    @Override
    public void write(int b) throws IOException {
        checkClosed();
        tokenBucket.acquire(1);
        outputStream.write(b);
        totalBytesWritten++;
    }

    /**
     * 检查关闭状态后获取令牌，再向底层流写入数据。
     * <p>单字节调用消耗一个令牌；数组调用委托区间重载，区间重载按 chunkSize 分块，
     * 每块底层写入成功后累计字节。零长度区间在关闭检查后直接返回。
     * 本类未统一预先校验数组区间，非法参数的异常行为也受底层输出流影响。
     *
     * @param b 待写入的非空字节数组
     * @throws IOException 流已关闭或底层 I/O 操作失败时抛出
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * 检查关闭状态后获取令牌，再向底层流写入数据。
     * <p>单字节调用消耗一个令牌；数组调用委托区间重载，区间重载按 chunkSize 分块，
     * 每块底层写入成功后累计字节。零长度区间在关闭检查后直接返回。
     * 本类未统一预先校验数组区间，非法参数的异常行为也受底层输出流影响。
     *
     * @param b   待写入的非空字节数组
     * @param off 数组起始偏移
     * @param len 请求写入的字节数
     * @throws IOException 流已关闭或底层 I/O 操作失败时抛出
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();

        if (len == 0)
            return;

        if (!logged) {
            log.info("RateLimitedOutputStream.write() called with len={} bytes", len);
            logged = true;
        }

        int remaining = len; // 分块写入，使流量更平滑
        int offset = off;

        while (remaining > 0) {
            int size = Math.min(chunkSize, remaining);
            tokenBucket.acquire(size);
            outputStream.write(b, offset, size);
            offset += size;
            remaining -= size;
            totalBytesWritten += size;
        }

        if (totalBytesWritten % (1024 * 1024) == 0) {
            double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
            double rate = elapsed > 0 ? (totalBytesWritten / elapsed) / 1024.0 : 0;
            log.info("Written {} bytes, actual rate: {} KB/s", totalBytesWritten, String.format("%.2f", rate));
        }
    }

    /**
     * 检查流未关闭后刷新底层输出流。
     * <p>刷新本身不申请令牌，不增加已写字节计数。
     *
     * @throws IOException 流已关闭或底层 I/O 操作失败时抛出
     */
    @Override
    public void flush() throws IOException {
        checkClosed();
        outputStream.flush();
    }

    /**
     * 首次关闭时先标记关闭并记录统计，再刷新并关闭底层流。
     * <p>再次调用不执行操作；若刷新失败，当前实现不会继续调用底层 close，且关闭标记已经设置。
     *
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @Override
    public void close() throws IOException {
        if (closed)
            return;

        closed = true;
        double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
        double rate = elapsed > 0 ? (totalBytesWritten / elapsed) / 1024.0 : 0;
        log.info("RateLimitedOutputStream closing: total bytes={}, elapsed={}s, rate={} KB/s", totalBytesWritten, String.format("%.2f", elapsed), String.format("%.2f", rate));
        outputStream.flush();
        outputStream.close();
    }

    /**
     * 检查本流关闭标记，已关闭时抛出 IOException。
     *
     * @throws IOException 流已关闭或底层 I/O 操作失败时抛出
     */
    private void checkClosed() throws IOException {
        if (closed)
            throw new IOException("Stream is closed");
    }

    /**
     * 实现 ServletOutputStream 状态查询，只根据关闭标记返回结果。
     * <p>不检查令牌或底层网络可写性，因此返回 true 后写入仍可能阻塞。
     *
     * @return 未关闭时为 true
     */
    @Override
    public boolean isReady() {
        return !closed;
    }

    /**
     * 拒绝注册非阻塞写监听器；本实现只支持同步阻塞式写入。
     *
     * @param writeListener 调用者提供的写监听器，本实现不接受注册
     * @throws UnsupportedOperationException 始终抛出，本实现不支持非阻塞写入
     */
    @Override
    public void setWriteListener(WriteListener writeListener) {
        throw new UnsupportedOperationException("Async write not supported");
    }

    /**
     * 仅修改关联桶的填充速率，共享桶的其他流也会受影响。
     * <p>不更新构造时带宽字段、桶容量或分块大小。
     *
     * @param newBandwidth 新的桶填充速率，单位为字节/秒
     */
    public void setBandwidth(long newBandwidth) {
        tokenBucket.setRefillRate(newBandwidth);
    }

    /**
     * 委托令牌桶补充并读取可用令牌。
     *
     * @return 当前桶可用字节数
     */
    public long getAvailableTokens() {
        return tokenBucket.getAvailableTokens();
    }

    /**
     * 以本流成功写入字节数除以创建以来的整秒数。
     * <p>不足一秒时为零；与共享桶的总消耗速率具有不同统计范围。
     *
     * @return 本流累计平均写入速率，字节/秒
     */
    public double getActualRate() {
        long elapsedNanos = System.nanoTime() - startTime;

        if (elapsedNanos <= 0)
            return 0;

        long elapsedSeconds = elapsedNanos / 1_000_000_000L;

        return elapsedSeconds > 0 ? (double) totalBytesWritten / elapsedSeconds : 0;
    }

    /**
     * 委托读取桶中剩余令牌比例，不主动补充。
     *
     * @return 关联桶的剩余令牌比例
     */
    public double getBucketUtilization() {
        return tokenBucket.getUtilization();
    }
}
