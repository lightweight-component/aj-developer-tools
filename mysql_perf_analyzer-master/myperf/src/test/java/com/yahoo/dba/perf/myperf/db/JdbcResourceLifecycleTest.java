package com.yahoo.dba.perf.myperf.db;

import com.yahoo.dba.perf.myperf.common.DBUtils;
import com.yahoo.dba.perf.myperf.common.MyPerfContext;
import com.yahoo.dba.perf.myperf.common.QueryParameters;
import com.yahoo.dba.perf.myperf.common.Sql;
import com.yahoo.dba.perf.myperf.common.SqlManager;
import com.yahoo.dba.perf.myperf.metrics.MySQLMetricsDb;
import com.yahoo.dba.perf.myperf.process.MyProfiler;
import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;
import junit.extensions.TestSetup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JdbcResourceLifecycleTest extends TestCase {
    private static MyPerfContext context;

    public static Test suite() {
        return new TestSetup(new TestSuite(JdbcResourceLifecycleTest.class)) {
            private final Path keyFile = Paths.get(".des");
            private boolean keyExisted;

            @Override
            protected void setUp() {
                keyExisted = Files.exists(keyFile);
                context = new MyPerfContext();
            }

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

    public void testConnectionCheckClosesQueryResourcesButKeepsBorrowedConnection() {
        JdbcFixture jdbc = new JdbcFixture();
        assertTrue(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("result.close", "statement.close"), jdbc.events);
    }

    public void testConnectionCheckClosesResourcesWhenReadingFails() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.readFailure = new SQLException("read failed");
        assertFalse(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("result.close", "statement.close"), jdbc.events);
    }

    public void testConnectionCheckClosesStatementWhenQueryFails() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.queryFailure = new SQLException("query failed");
        assertFalse(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("statement.close"), jdbc.events);
    }

    public void testConnectionCheckClosesStatementWhenConfigurationFails() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.configurationFailure = new SQLException("timeout failed");
        assertFalse(DBUtils.checkConnection(jdbc.connection));
        assertEquals(Arrays.asList("statement.close"), jdbc.events);
    }

    public void testTableLookupClosesResultOnEarlyReturn() {
        JdbcFixture jdbc = new JdbcFixture();
        assertTrue(DBUtils.hasTable(jdbc.connection, "schema", "value"));
        assertEquals(Arrays.asList("result.close"), jdbc.events);
    }

    public void testPlainQueryKeepsBorrowedConnectionAndClearsCancellationReference() throws Exception {
        assertQueryResources(false, false, false);
    }

    public void testPreparedQueryBindsParametersAndClosesResources() throws Exception {
        assertQueryResources(true, false, false);
    }

    public void testSubstitutedQueryClosesResources() throws Exception {
        assertQueryResources(true, true, false);
    }

    public void testGenericQueryClosesResources() throws Exception {
        assertQueryResources(true, false, true);
    }

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
        } else {
            assertEquals("select 'value'", jdbc.executedSql);
        }
    }

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

    public void testOwnedConnectionClosesAfterSuccessfulUpdate() {
        JdbcFixture jdbc = new JdbcFixture();
        assertTrue(metricsDb(jdbc).enableMetrics("group", "host", true, "owner", false));
        assertEquals(Arrays.asList("statement.close", "connection.close"), jdbc.events);
    }

    public void testFailedUpdateRollsBackBeforeClosingOwnedConnection() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.queryFailure = new SQLException("update failed");
        assertFalse(metricsDb(jdbc).enableMetrics("group", "host", true, "owner", false));
        assertEquals(Arrays.asList("statement.close", "connection.rollback", "connection.close"), jdbc.events);
    }

    public void testFailedStatementCreationStillRollsBackAndClosesConnection() {
        JdbcFixture jdbc = new JdbcFixture();
        jdbc.creationFailure = new SQLException("prepare failed");
        assertFalse(metricsDb(jdbc).enableMetrics("group", "host", true, "owner", false));
        assertEquals(Arrays.asList("connection.rollback", "connection.close"), jdbc.events);
    }

    public void testExplainPlanClosesFirstResultBeforeRunningCostQuery() throws Exception {
        JdbcFixture jdbc = new JdbcFixture();
        assertEquals(2, profiler(jdbc).runExplainPlan().getRows().size());
        assertEquals(Arrays.asList("result.close", "result.close", "statement.close"), jdbc.events);
    }

    public void testStatsClosesEveryResultBeforeRunningNextQuery() throws Exception {
        JdbcFixture jdbc = new JdbcFixture();
        assertNotNull(profiler(jdbc).runStats());
        assertEquals(Arrays.asList("result.close", "result.close", "result.close", "statement.close"), jdbc.events);
    }

    private MyProfiler profiler(JdbcFixture jdbc) throws Exception {
        MyProfiler profiler = new MyProfiler();
        profiler.setSqlText("select 'value'");
        Field connection = MyProfiler.class.getDeclaredField("connection");
        connection.setAccessible(true);
        connection.set(profiler, jdbc.connection);
        return profiler;
    }

    private QueryExecutor executor(SqlManager manager) {
        QueryExecutor executor = new QueryExecutor();
        executor.setSqlManager(manager);
        executor.setFrameworkContext(context);
        return executor;
    }

    private DBConnectionWrapper wrapper(final JdbcFixture jdbc) {
        return new DBConnectionWrapper() {
            @Override
            public Connection getConnection() {
                return jdbc.connection;
            }
        };
    }

    private MySQLMetricsDb metricsDb(final JdbcFixture jdbc) {
        return new MySQLMetricsDb() {
            @Override
            protected Connection createConnection(boolean autocommit) {
                return jdbc.connection;
            }
        };
    }

    private static class JdbcFixture {
        final List<String> events = new ArrayList<>();
        SQLException creationFailure;
        SQLException queryFailure;
        SQLException readFailure;
        SQLException configurationFailure;
        SQLException resultCloseFailure;
        SQLException statementCloseFailure;
        String preparedSql;
        String executedSql;
        String boundValue;
        boolean connectionClosed;
        boolean resultOpen;
        int row;

        final ResultSetMetaData columns = proxy(ResultSetMetaData.class, (p, m, a) -> {
            if ("getColumnCount".equals(m.getName()))
                return 1;
            if ("getColumnName".equals(m.getName()))
                return "value";
            if ("getColumnType".equals(m.getName()))
                return Types.VARCHAR;
            return defaultValue(m.getReturnType());
        });
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
        final PreparedStatement statement = proxy(PreparedStatement.class, (p, m, a) -> {
            if ("close".equals(m.getName())) {
                events.add("statement.close");
                if (statementCloseFailure != null)
                    throw statementCloseFailure;
            } else if ("setQueryTimeout".equals(m.getName()) && configurationFailure != null) {
                throw configurationFailure;
            } else if ("setString".equals(m.getName())) boundValue = (String) a[1];
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
        final DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (p, m, a) ->
                "getTables".equals(m.getName()) ? result : defaultValue(m.getReturnType()));
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

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

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
