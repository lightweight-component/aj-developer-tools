package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.*;
import com.yahoo.dba.perf.myperf.metrics.MySQLMetricsDb;
import com.yahoo.dba.perf.myperf.process.MyProfiler;
import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 表示jdbc resource lifecycle test相关的类型。
 */
public class JdbcResourceLifecycleTest extends TestCase {
    /**
     * 保存context信息。
     */
    private static MyPerfContext context;

    /**
     * 执行suite操作。
     *
     * @return 处理结果。
     */
    public static Test suite() {
        /**
         * 表示相关的类型。
         */
        return new TestSetup(new TestSuite(JdbcResourceLifecycleTest.class)) {
            /**
             * 保存key file信息。
             */
            private final Path keyFile = Paths.get(".des");
            /**
             * 标记key existed状态。
             */
            private boolean keyExisted;

            /**
             * 设置up。
             */
            @Override
            protected void setUp() {
                keyExisted = Files.exists(keyFile);
                context = new MyPerfContext();
            }

            /**
             * 执行tear down操作。
             */
            @Override
            protected void tearDown() throws Exception {
                // MetaDB creates a key; remove only a key created by this test suite.
                if (!keyExisted && Files.exists(keyFile)) {
                    keyFile.toFile().setWritable(true);
                    Files.delete(keyFile);
                }
            }
        };
    }

    /**
     * 测试connection check closes query resources but keeps borrowed connection。
     */
    public void testConnectionCheckClosesQueryResourcesButKeepsBorrowedConnection() {
        JdbcFixture jdbc = new JdbcFixture();
        assertTrue(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("result.close", "statement.close"), jdbc.events);
    }

    /**
     * 测试connection check closes resources when reading fails。
     */
    public void testConnectionCheckClosesResourcesWhenReadingFails() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.readFailure = new SQLException("read failed");
        assertFalse(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("result.close", "statement.close"), jdbc.events);
    }

    /**
     * 测试connection check closes statement when query fails。
     */
    public void testConnectionCheckClosesStatementWhenQueryFails() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.queryFailure = new SQLException("query failed");
        assertFalse(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("statement.close"), jdbc.events);
    }

    /**
     * 测试connection check closes statement when configuration fails。
     */
    public void testConnectionCheckClosesStatementWhenConfigurationFails() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.configurationFailure = new SQLException("timeout failed");
        assertFalse(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("statement.close"), jdbc.events);
    }

    /**
     * 测试table lookup closes result on early return。
     */
    public void testTableLookupClosesResultOnEarlyReturn() {
        JdbcFixture jdbc = new JdbcFixture();
        assertTrue(DBUtils.hasTable(jdbc.connection, "schema", "value"));
        assertEquals(Arrays.asList("result.close"), jdbc.events);
    }

    /**
     * 测试plain query keeps borrowed connection and clears cancellation reference。
     */
    public void testPlainQueryKeepsBorrowedConnectionAndClearsCancellationReference() throws Exception {
        assertQueryResources(false, false, false);
    }

    /**
     * 测试prepared query binds parameters and closes resources。
     */
    public void testPreparedQueryBindsParametersAndClosesResources() throws Exception {
        assertQueryResources(true, false, false);
    }

    /**
     * 测试substituted query closes resources。
     */
    public void testSubstitutedQueryClosesResources() throws Exception {
        assertQueryResources(true, true, false);
    }

    /**
     * 测试generic query closes resources。
     */
    public void testGenericQueryClosesResources() throws Exception {
        assertQueryResources(true, false, true);
    }

    /**
     * 执行assert query resources操作。
     *
     * @param parameterized parameterized参数。
     * @param replace       replace参数。
     * @param generic       generic参数。
     */
    private void assertQueryResources(boolean parameterized, boolean replace, boolean generic) throws Exception {
        JdbcFixture jdbc = new JdbcFixture();
        Sql sql = new Sql();
        sql.setHandle("test");
        sql.setSqlText(parameterized ? "select '&p_1'" : "select 'value'");
        sql.setParamCount(parameterized ? 1 : 0);

        if (parameterized)
            sql.addParameter("p_1", null);

        sql.setReplace(replace);
        SqlManager manager = new SqlManager();
        manager.getSqlMap().put("test", sql);
        QueryExecutor executor = executor(manager);
        DBConnectionWrapper wrapper = wrapper(jdbc);
        QueryParameters params = new QueryParameters();
        params.setSql("test");
        params.getSqlParams().put("p_1", "value");
        assertEquals(1, (generic ? executor.executeQueryGeneric(params, wrapper, 1)
                : executor.executeQuery(params, wrapper, 1)).getRows().size());
        assertNull(wrapper.getCurrentStatement());
        assertEquals(Arrays.asList("result.close", "statement.close"), jdbc.events);
        assertEquals(parameterized && !replace, jdbc.preparedSql != null);

        if (parameterized && !replace) {
            assertEquals("select '?'", jdbc.preparedSql);
            assertEquals("value", jdbc.boundValue);
        } else
            assertEquals("select 'value'", jdbc.executedSql);

    }

    /**
     * 测试query failure remains primary when both closes fail。
     */
    public void testQueryFailureRemainsPrimaryWhenBothClosesFail() throws Exception {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.readFailure = new SQLException("read failed");
        jdbc.resultCloseFailure = new SQLException("result close failed");
        jdbc.statementCloseFailure = new SQLException("statement close failed");
        DBConnectionWrapper wrapper = wrapper(jdbc);
        try {
            executor(new SqlManager()).executeQuery(wrapper, "select 'value'", null, 1);
            fail("Expected the original read failure");
        } catch (SQLException ex) {
            assertSame(jdbc.readFailure, ex);
            assertEquals(2, ex.getSuppressed().length);
            assertSame(jdbc.resultCloseFailure, ex.getSuppressed()[0]);
            assertSame(jdbc.statementCloseFailure, ex.getSuppressed()[1]);
        }
        assertNull(wrapper.getCurrentStatement());
        assertEquals(Arrays.asList("result.close", "statement.close"), jdbc.events);
    }

    /**
     * 测试owned connection closes after successful update。
     */
    public void testOwnedConnectionClosesAfterSuccessfulUpdate() {
        JdbcFixture jdbc = new JdbcFixture();
        assertTrue(metricsDb(jdbc).enableMetrics("group", "host", true, "owner", false));
        assertEquals(Arrays.asList("statement.close", "connection.close"), jdbc.events);
    }

    /**
     * 测试failed update rolls back before closing owned connection。
     */
    public void testFailedUpdateRollsBackBeforeClosingOwnedConnection() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.queryFailure = new SQLException("update failed");
        assertFalse(metricsDb(jdbc).enableMetrics("group", "host", true, "owner", false));
        assertEquals(Arrays.asList("statement.close", "connection.rollback", "connection.close"), jdbc.events);
    }

    /**
     * 测试failed statement creation still rolls back and closes connection。
     */
    public void testFailedStatementCreationStillRollsBackAndClosesConnection() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.creationFailure = new SQLException("prepare failed");
        assertFalse(metricsDb(jdbc).enableMetrics("group", "host", true, "owner", false));
        assertEquals(Arrays.asList("connection.rollback", "connection.close"), jdbc.events);
    }

    /**
     * 测试explain plan closes first result before running cost query。
     */
    public void testExplainPlanClosesFirstResultBeforeRunningCostQuery() throws Exception {
        JdbcFixture jdbc = new JdbcFixture();
        assertEquals(2, profiler(jdbc).runExplainPlan().getRows().size());
        assertEquals(Arrays.asList("result.close", "result.close", "statement.close"), jdbc.events);
    }

    /**
     * 测试stats closes every result before running next query。
     */
    public void testStatsClosesEveryResultBeforeRunningNextQuery() throws Exception {
        JdbcFixture jdbc = new JdbcFixture();
        assertNotNull(profiler(jdbc).runStats());
        assertEquals(Arrays.asList("result.close", "result.close", "result.close", "statement.close"), jdbc.events);
    }

    /**
     * 执行profiler操作。
     *
     * @param jdbc jdbc参数。
     * @return 处理结果。
     */
    private MyProfiler profiler(JdbcFixture jdbc) throws Exception {
        MyProfiler profiler = new MyProfiler();
        profiler.setSqlText("select 'value'");
        Field connection = MyProfiler.class.getDeclaredField("connection");
        connection.setAccessible(true);
        connection.set(profiler, jdbc.connection);
        return profiler;
    }

    /**
     * 执行executor操作。
     *
     * @param manager manager参数。
     * @return 处理结果。
     */
    private QueryExecutor executor(SqlManager manager) {
        QueryExecutor executor = new QueryExecutor();
        executor.setSqlManager(manager);
        executor.setFrameworkContext(context);
        return executor;
    }

    /**
     * 执行wrapper操作。
     *
     * @param jdbc jdbc参数。
     * @return 处理结果。
     */
    private DBConnectionWrapper wrapper(final JdbcFixture jdbc) {
        /**
         * 表示相关的类型。
         */
        return new DBConnectionWrapper() {
            /**
             * 获取connection。
             * @return 处理结果。
             */
            @Override
            public Connection getConnection() {
                return jdbc.connection;
            }
        };
    }

    /**
     * 执行metrics db操作。
     *
     * @param jdbc jdbc参数。
     * @return 处理结果。
     */
    private MySQLMetricsDb metricsDb(final JdbcFixture jdbc) {
        /**
         * 表示相关的类型。
         */
        return new MySQLMetricsDb() {
            /**
             * 创建connection。
             * @param autocommit autocommit参数。
             * @return 处理结果。
             */
            @Override
            protected Connection createConnection(boolean autocommit) {
                return jdbc.connection;
            }
        };
    }

    /**
     * 表示jdbc fixture相关的类型。
     */
    private static class JdbcFixture {
        /**
         * 保存events集合。
         */
        final List<String> events = new ArrayList<>();
        /**
         * 保存creation failure信息。
         */
        SQLException creationFailure;
        /**
         * 保存query failure信息。
         */
        SQLException queryFailure;
        /**
         * 保存read failure信息。
         */
        SQLException readFailure;
        /**
         * 保存configuration failure信息。
         */
        SQLException configurationFailure;
        /**
         * 保存result close failure信息。
         */
        SQLException resultCloseFailure;
        /**
         * 保存statement close failure信息。
         */
        SQLException statementCloseFailure;
        /**
         * 保存prepared sql信息。
         */
        String preparedSql;
        /**
         * 保存executed sql信息。
         */
        String executedSql;
        /**
         * 保存bound value信息。
         */
        String boundValue;
        /**
         * 标记connection closed状态。
         */
        boolean connectionClosed;
        /**
         * 标记result open状态。
         */
        boolean resultOpen;
        /**
         * 保存row信息。
         */
        int row;

        /**
         * 保存columns信息。
         */
        final ResultSetMetaData columns = proxy(ResultSetMetaData.class, (p, m, a) -> {

            if ("getColumnCount".equals(m.getName()))
                return 1;

            if ("getColumnName".equals(m.getName()))
                return "value";

            if ("getColumnType".equals(m.getName()))
                return Types.VARCHAR;

            return defaultValue(m.getReturnType());
        });
        /**
         * 保存result信息。
         */
        final ResultSet result = proxy(ResultSet.class, (p, m, a) -> {

            if ("close".equals(m.getName())) {
                resultOpen = false;
                events.add("result.close");

                if (resultCloseFailure != null)
                    throw resultCloseFailure;

            } else if ("next".equals(m.getName())) {

                if (readFailure != null)
                    throw readFailure;

                return row++ == 0;
            } else if ("getMetaData".equals(m.getName())) return columns;
            else if ("getString".equals(m.getName())) return "value";

            return defaultValue(m.getReturnType());
        });
        /**
         * 保存statement信息。
         */
        final PreparedStatement statement = proxy(PreparedStatement.class, (p, m, a) -> {

            if ("close".equals(m.getName())) {
                events.add("statement.close");

                if (statementCloseFailure != null)
                    throw statementCloseFailure;

            } else if ("setQueryTimeout".equals(m.getName()) && configurationFailure != null)
                throw configurationFailure;
            else if ("setString".equals(m.getName())) boundValue = (String) a[1];
            else if ("getUpdateCount".equals(m.getName())) return 1;
            else if ("executeQuery".equals(m.getName()) || "execute".equals(m.getName())) {

                if (queryFailure != null)
                    throw queryFailure;

                if (a != null && a.length > 0)
                    executedSql = (String) a[0];

                if ("executeQuery".equals(m.getName())) {
                    assertFalse("Previous result was still open", resultOpen);
                    resultOpen = true;
                    row = 0;
                    return result;
                }

                return false;
            }

            return defaultValue(m.getReturnType());
        });
        /**
         * 保存metadata信息。
         */
        final DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (p, m, a) ->
                "getTables".equals(m.getName()) ? result : defaultValue(m.getReturnType()));
        /**
         * 保存connection信息。
         */
        final Connection connection = proxy(Connection.class, (p, m, a) -> {

            if ("close".equals(m.getName())) {
                connectionClosed = true;
                events.add("connection.close");
            } else if ("rollback".equals(m.getName())) {
                assertFalse("Connection closed before rollback", connectionClosed);
                events.add("connection.rollback");
            } else if ("getMetaData".equals(m.getName())) return metadata;
            else if ("prepareStatement".equals(m.getName()) || "createStatement".equals(m.getName())) {

                if (creationFailure != null)
                    throw creationFailure;

                if ("prepareStatement".equals(m.getName()))
                    preparedSql = (String) a[0];

                return statement;
            }

            return defaultValue(m.getReturnType());
        });
    }

    /**
     * 执行proxy操作。
     *
     * @param type    type参数。
     * @param handler handler参数。
     * @return 处理结果。
     */
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    /**
     * 执行default value操作。
     *
     * @param type type参数。
     * @return 处理结果。
     */
    private static Object defaultValue(Class<?> type) {

        if (type == boolean.class)
            return false;

        if (type == int.class)
            return 0;

        if (type == long.class)
            return 0L;

        return null;
    }
}
