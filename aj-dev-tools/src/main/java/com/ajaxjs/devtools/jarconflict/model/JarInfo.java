package com.ajaxjs.devtools.jarconflict.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一个被扫描的 JAR 或开发环境 classes 目录的元数据及类清单。
 * <p>名称和版本由扫描器推断，不能等同于完整 Maven 坐标。开发目录也使用此模型，
 * 所以报告的总 JAR 数可能包含目录。类清单只包含扫描器保留的条目。
 */
@Data
@NoArgsConstructor
public class JarInfo {
    /**
     * 推断的库名，通常是去掉版本和扩展名后的文件名。
     */
    private String name;

    /**
     * 扫描来源路径，开发目录也使用此字段。
     */
    private String path;

    /**
     * 推断的版本，或 dev、maven、unknown 等标记。
     */
    private String version;

    /**
     * 格式化后的大小文本，不是可直接计算的字节数。
     */
    private String size;

    /**
     * 扫描保留的全限定类名清单，不包含名称带美元符号的类。
     */
    private List<String> classes;

    /**
     * 创建依赖元数据，类清单由调用方另行填充。
     *
     * @param name    库名
     * @param path    来源路径
     * @param version 版本或版本标记
     * @param size    格式化的大小文本
     */
    public JarInfo(String name, String path, String version, String size) {
        this.name = name;
        this.path = path;
        this.version = version;
        this.size = size;
    }
}
