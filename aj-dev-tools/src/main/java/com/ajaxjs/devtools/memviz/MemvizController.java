package com.ajaxjs.devtools.memviz;

import com.ajaxjs.devtools.memviz.model.GraphModel;
import com.ajaxjs.devtools.memviz.model.vo.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.management.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

/**
 * 内存可视化 HTTP 入口，提供堆快照生成和 HPROF 图模型查询。
 * <p>统一路径为 {@code /memviz}；通过 HotSpotDiagnostic MBean 转储当前 JVM，
 * 通过解析服务读取服务端本地文件。Lombok 生成构造器以注入解析服务。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/memviz")
public class MemvizController {
    /**
     * 将本地 HPROF 文件解析为图与类统计的服务，由构造器注入。
     */
    private final HprofParseService parseService;

    /**
     * HotSpotDiagnostic 平台 MBean 名称，用于调用当前 JVM 的堆转储操作。
     */
    private static final String HOTSPOT_BEAN = "com.sun.management:type=HotSpotDiagnostic";

    /**
     * HotSpotDiagnostic MBean 提供的堆转储操作名称。
     */
    private static final String DUMP_METHOD = "dumpHeap";

    /**
     * 快照文件名中的本地时间格式，精确到秒；同秒生成可能出现同名文件。
     */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 通过 HotSpotDiagnostic MBean 将当前 JVM 堆转储到本地文件。
     * <p>目录不存在时尝试创建；转储前检查至少 512 MiB 可用空间，
     * 该检查不限制最终文件大小，也不预留磁盘空间。转储可能暂停应用，
     * 仅包含存活对象时可能触发垃圾回收。文件名时间精确到秒。</p>
     *
     * @param live 是否仅转储存活对象
     * @param dir  快照输出目录
     * @return 生成的 HPROF 文件对象
     * @throws IllegalStateException        目录无法创建或剩余空间不足
     * @throws java.io.UncheckedIOException 磁盘空间查询发生 I/O 错误
     * @throws RuntimeException             HotSpot MBean 不可用或堆转储调用失败
     */
    public File dump(boolean live, File dir) {
        if (!dir.exists() && !dir.mkdirs())
            throw new IllegalStateException("Cannot create dump dir: " + dir);

        String name = "heap_" + LocalDateTime.now().format(FMT) + (live ? "_live" : "") + ".hprof";
        File out = new File(dir, name);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        try {
            ObjectName objName = new ObjectName(HOTSPOT_BEAN);
            // 检查转储目录当前可用空间，不限制最终文件大小
            assertDiskHasSpace(dir.toPath(), 512L * 1024 * 1024); // 至少 512MB 空间
            server.invoke(objName, DUMP_METHOD, new Object[]{out.getAbsolutePath(), live}, new String[]{"java.lang.String", "boolean"});
        } catch (ReflectionException e) {
            throw new RuntimeException("dump.ReflectionException", e);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("dump.MalformedObjectNameException", e);
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException("dump.InstanceNotFoundException", e);
        } catch (MBeanException e) {
            throw new RuntimeException("dump.MBeanException", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return out;
    }

    /**
     * 处理 {@code POST /memviz/snapshot}，同步创建堆快照并返回服务端绝对路径。
     * <p>默认包含非存活对象，输出目录默认是 /tmp/memviz；不直接下载文件。</p>
     *
     * @param live 是否仅转储存活对象，默认 false
     * @param dir  本地快照输出目录
     * @return 包含文件绝对路径的响应对象
     */
    @PostMapping("/snapshot")
    public SnapshotResponse snapshot(@RequestParam(defaultValue = "false") boolean live, @RequestParam(defaultValue = "/tmp/memviz") String dir) {
        File f = dump(live, new File(dir));
        SnapshotResponse response = new SnapshotResponse();
        response.setFile(f.getAbsolutePath());

        return response;
    }

    /**
     * 处理 {@code GET /memviz/graph}，读取指定文件并构建类聚合图。
     * <p>include 按逗号拆分类名前缀；启用筛选后仍保留 String、byte 数组、
     * String 数组及 ArrayList 等名称匹配的基础类型。空筛选不限制类名。</p>
     *
     * @param file                服务端 HPROF 文件路径
     * @param include             可选的逗号分隔类名前缀
     * @param collapseCollections 是否简化集合节点展示，默认 true
     * @return 解析结果，包含节点、抽样引用边和统计
     */
    @GetMapping(value = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public GraphModel graph(@RequestParam String file,
                            @RequestParam(required = false) String include, // 例如: com.myapp.,java.util.HashMap
                            @RequestParam(defaultValue = "true") boolean collapseCollections) {
        Predicate<String> filter = null;

        if (StringUtils.hasText(include)) {
            String[] prefixes = include.split(",");
            filter = fqcn -> {
                // 总是包含重要的基础类，以便显示大对象
                if (fqcn.equals("java.lang.String") || fqcn.equals("byte[]") ||
                        fqcn.startsWith("java.lang.String[") || fqcn.startsWith("java.util.ArrayList"))
                    return true;

                for (String p : prefixes) // 检查用户指定的前缀
                    if (fqcn.startsWith(p.trim()))
                        return true;

                return false;
            };
        }

        return parseService.parseToGraph(new File(file), filter, collapseCollections);
    }

    /**
     * 检查目录所在文件系统的当前可用空间是否达到最低要求。
     * <p>仅执行瞬时检查，不创建目录、不预留空间，也不限制转储文件大小。</p>
     *
     * @param dir          已存在的目标目录路径
     * @param minFreeBytes 最低可用空间，单位为字节
     * @throws java.io.IOException   无法读取目标文件系统的空间信息
     * @throws IllegalStateException 可用空间低于最低要求
     */
    public static void assertDiskHasSpace(Path dir, long minFreeBytes) throws IOException {
        FileStore store = Files.getFileStore(dir);

        if (store.getUsableSpace() < minFreeBytes)
            throw new IllegalStateException("Low disk space for heap dump: need " + minFreeBytes + " bytes free");
    }
}