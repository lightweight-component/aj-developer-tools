package com.ajaxjs.devtools.memviz.model;

import com.ajaxjs.util.RandomTools;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于手动制造堆分析样本的用户对象。
 * <p>Lombok 生成属性访问器、相等性方法、字符串表示和全参数构造器。
 * 静态容器持有测试对象，便于在后续堆快照中观察其占用。</p>
 */
@Data
@AllArgsConstructor
public class User {
    /**
     * 测试用户姓名，测试生成器使用较长随机字符串以制造可观察的内存占用。
     */
    private String name;

    /**
     * 测试用户年龄。
     */
    private int age;

    /**
     * 测试大字符串的共享持有容器；重复生成会追加，容器本身不提供线程安全保证。
     */
    public static List<String> largeStrings = new ArrayList<>();

    /**
     * 测试用户的共享持有容器；final 仅限制容器引用重新赋值，内容仍可修改。
     */
    public static final List<User> users = new ArrayList<>();

    /**
     * 向静态容器追加大字符串和用户对象，供手动生成堆快照时观察。
     * <p>每次创建 100 个长度为 1 Mi 字符的随机字符串，以及 5 个姓名长度为
     * 200 Ki 字符的用户。字符数量不等于实际占用字节数；静态引用阻止这些对象
     * 被正常回收。重复调用持续追加，不清空容器，也不提供并发保护。</p>
     */
    public static void createTest() {
        // Create 100 1MB strings and store them properly
        for (int i = 0; i < 100; i++) {
            String largeString = RandomTools.generateRandomString(1024 * 1024); // 1MB each
            largeStrings.add(largeString); // Store so they won't be GC'd
        }

        // 创建测试User对象，验证深度大小计算
        for (int i = 0; i < 5; i++) {  // 创建几个有大字符串的User对象
            String largeName = RandomTools.generateRandomString(200 * 1024); // 200KB的name
            User user = new User(largeName, 25 + i);
            users.add(user); // 将User对象存储在某个地方，避免被GC
        }
    }
}