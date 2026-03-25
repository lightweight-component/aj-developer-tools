package com.ajaxjs.devtools.memviz.model;

import com.ajaxjs.util.RandomTools;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试深度大小计算的User类
 */
@Data
@AllArgsConstructor
public class User {
    /**
     * 用户名
     */
    private String name;
    
    /**
     * 年龄
     */
    private int age;

    public static List<String> largeStrings = new ArrayList<>();

    /**
     * 存储测试User对象的静态容器
     */
    public static final List<User> users = new ArrayList<>();

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