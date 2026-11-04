package com.example.netspeed.web;

import com.example.netspeed.core.RateLimitedOutputStream;
import com.example.netspeed.core.TokenBucket;
import com.example.netspeed.manager.BandwidthLimitManager;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * 将 HTTP 响应输出延迟包装为同步限速流。
 * <p>可复用共享桶或首次访问时创建独立桶，缓存字节流和字符输出器。
 * 仅通过本包装器获取的输出对象执行限速；构造包装器不会自动替换原始响应。
 * <p>包装器未额外维护字符和字节输出的互斥约束；关闭时直接关闭限速流，不显式刷新字符缓冲。
 */
@Slf4j
public class BandwidthLimitResponseWrapper extends HttpServletResponseWrapper {
    /**
     * 构造时配置的字节带宽，不随动态修改桶速率更新。
     */
    private final long bandwidthBytesPerSecond;
    /**
     * 分块配置，输出流内为限制后的实际大小，包装器内非正值表示自动选择。
     */
    private final int chunkSize;
    /**
     * 可选共享桶，为 null 时首次创建输出流会新建独立桶。
     */
    private final TokenBucket sharedTokenBucket;
    /**
     * 延迟创建并缓存的限速输出流。
     */
    private RateLimitedOutputStream limitedOutputStream;
    /**
     * 延迟创建的字符输出器，使用首次获取时的响应字符编码。
     */
    private PrintWriter writer;
    /**
     * 是否已记录首次 getOutputStream 调用日志。
     */
    private boolean outputStreamUsed = false;
    /**
     * 保留的未使用标记，当前代码没有据此复制响应头。
     */
    private final boolean headersCopied = false;

    /**
     * 保存原始响应及限速参数，输出流延迟到首次获取时创建。
     * <p>共享桶为空时创建独立桶；分块大小非正时走自动选择，正值由限速流限制上下界。
     *
     * @param response 原始或调用者传入的 HTTP 响应
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     */
    public BandwidthLimitResponseWrapper(HttpServletResponse response, long bandwidthBytesPerSecond) {
        this(response, null, bandwidthBytesPerSecond, -1);
    }

    /**
     * 保存原始响应及限速参数，输出流延迟到首次获取时创建。
     * <p>共享桶为空时创建独立桶；分块大小非正时走自动选择，正值由限速流限制上下界。
     *
     * @param response 原始或调用者传入的 HTTP 响应
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     * @param chunkSize 请求分块字节数，构造器及包装器的具体处理见方法说明
     */
    public BandwidthLimitResponseWrapper(HttpServletResponse response, long bandwidthBytesPerSecond, int chunkSize) {
        this(response, null, bandwidthBytesPerSecond, chunkSize);
    }

    /**
     * 保存原始响应及限速参数，输出流延迟到首次获取时创建。
     * <p>共享桶为空时创建独立桶；分块大小非正时走自动选择，正值由限速流限制上下界。
     *
     * @param response 原始或调用者传入的 HTTP 响应
     * @param tokenBucket 要复用的令牌桶，输出流构造要求非空，包装器允许 null
     * @param bandwidthBytesPerSecond 配置带宽，单位为字节/秒，应为正数
     * @param chunkSize 请求分块字节数，构造器及包装器的具体处理见方法说明
     */
    public BandwidthLimitResponseWrapper(HttpServletResponse response, TokenBucket tokenBucket,
                                         long bandwidthBytesPerSecond,
                                         int chunkSize) {
        super(response);
        this.sharedTokenBucket = tokenBucket;
        this.bandwidthBytesPerSecond = bandwidthBytesPerSecond;
        this.chunkSize = chunkSize;
    }

    /**
     * 首次调用时创建并缓存 RateLimitedOutputStream，之后返回同一实例。
     * <p>共享桶非空时复用该桶，否则新建独立桶。此方法不直接写入响应正文。
     *
     * @return 缓存的同步限速输出流
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (!outputStreamUsed) {
            log.info("BandwidthLimitResponseWrapper.getOutputStream() called, bandwidth={}/s, sharedBucket={}",
                    BandwidthLimitManager.formatBytes(bandwidthBytesPerSecond), sharedTokenBucket != null);
            outputStreamUsed = true;
        }

        if (limitedOutputStream == null) {
            if (sharedTokenBucket != null) {
                if (chunkSize > 0)// 使用共享的 TokenBucket
                    limitedOutputStream = new RateLimitedOutputStream(super.getOutputStream(), sharedTokenBucket, bandwidthBytesPerSecond, chunkSize);
                else
                    limitedOutputStream = new RateLimitedOutputStream(super.getOutputStream(), sharedTokenBucket, bandwidthBytesPerSecond);
            } else {
                if (chunkSize > 0) // 创建新的 TokenBucket（兼容旧代码）
                    limitedOutputStream = new RateLimitedOutputStream(super.getOutputStream(), bandwidthBytesPerSecond, chunkSize);
                else
                    limitedOutputStream = new RateLimitedOutputStream(super.getOutputStream(), bandwidthBytesPerSecond);
            }
        }

        return limitedOutputStream;
    }

    /**
     * 按响应当前字符编码创建并缓存 PrintWriter，写入通过限速输出流。
     * <p>编码在首次创建时确定；autoFlush 为 true，但 PrintWriter 仍可能缓冲字符。
     * 本类未额外实现 getWriter 与 getOutputStream 的互斥限制。
     *
     * @return 缓存的字符输出器
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null)
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), getCharacterEncoding()), true);

        return writer;
    }

    /**
     * 优先刷新已创建的字符输出器，否则刷新限速流，最后刷新原响应缓冲区。
     * <p>没有创建输出对象时仅调用父类，刷新不额外申请令牌。
     *
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    @Override
    public void flushBuffer() throws IOException {
        if (writer != null)
            writer.flush();
        else if (limitedOutputStream != null)
            limitedOutputStream.flush();

        super.flushBuffer();
    }

    /**
     * 读取已创建的限速流，不触发初始化。
     *
     * @return 尚未获取过输出对象时为 null，否则为缓存流
     */
    public RateLimitedOutputStream getRateLimitedOutputStream() {
        return limitedOutputStream;
    }

    /**
     * 已创建限速流时记录写入量并关闭底层限速流；否则不执行操作。
     * <p>不显式刷新缓存的 PrintWriter，调用方应先刷新字符缓冲。
     *
     * @throws IOException 底层输出或刷新操作失败时抛出
     */
    public void close() throws IOException {
        if (limitedOutputStream != null) {
            log.info("BandwidthLimitResponseWrapper closing, total bytes: {}",
                    limitedOutputStream.getTotalBytesWritten());
            limitedOutputStream.close();
        }
    }
}
