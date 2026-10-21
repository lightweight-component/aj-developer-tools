package com.ajaxjs.dbhelper.util;

import lombok.experimental.UtilityClass;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 JDBC 元数据比较 MySQL 版本，支持发行版后缀及补丁版本。
 */
@UtilityClass
public class MysqlVersion {
    private static final Pattern VERSION = Pattern.compile("^\\s*\\d+\\.\\d+\\.(\\d+)");

    /**
     * 比较主、次版本。
     */
    public static boolean isAtLeast(Connection connection, int major, int minor) throws SQLException {
        return isAtLeast(connection, major, minor, 0);
    }

    /**
     * JDBC 没有补丁版本 API，仅在主、次版本相同时解析完整版本中的补丁号。
     */
    public static boolean isAtLeast(Connection connection, int major, int minor, int patch) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        int actualMajor = metadata.getDatabaseMajorVersion();
        int actualMinor = metadata.getDatabaseMinorVersion();

        if (actualMajor != major)
            return actualMajor > major;

        if (actualMinor != minor)
            return actualMinor > minor;

        if (patch == 0)
            return true;

        String version = metadata.getDatabaseProductVersion();
        Matcher matcher = VERSION.matcher(version == null ? "" : version);

        if (!matcher.find())
            throw new IllegalStateException("无法识别 MySQL 补丁版本：" + version);

        return Integer.parseInt(matcher.group(1)) >= patch;
    }
}
