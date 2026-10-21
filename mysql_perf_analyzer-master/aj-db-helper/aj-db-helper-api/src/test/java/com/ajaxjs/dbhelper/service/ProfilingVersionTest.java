package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.util.MysqlVersion;
import com.ajaxjs.sqlman.JdbcConnection;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfilingVersionTest {
    @Test
    void checksExactPatchBoundaryAndReleaseSuffix() throws Exception {
        check(5, 5, "5.5.62", false);
        check(5, 6, "5.6.4", false);
        check(5, 6, "5.6.5-log", true);
        check(5, 6, "5.6.10-enterprise", true);
        check(5, 7, "5.7.44", true);
        check(8, 0, "8.0.43", true);
    }

    private void check(int major, int minor, String version, boolean expected) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseMajorVersion()).thenReturn(major);
        when(metadata.getDatabaseMinorVersion()).thenReturn(minor);
        when(metadata.getDatabaseProductVersion()).thenReturn(version);
        assertEquals(expected, MysqlVersion.isAtLeast(connection, 5, 6, 5), version);
    }

    @Test
    void unsupportedVersionIsRejectedBeforeExecutingSql() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseMajorVersion()).thenReturn(5);
        when(metadata.getDatabaseMinorVersion()).thenReturn(6);
        when(metadata.getDatabaseProductVersion()).thenReturn("5.6.4-log");
        JdbcConnection.setConnection(connection);

        try {
            UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                    () -> new ProfilingService().explainPlanJson("select 1"));
            assertTrue(error.getMessage().contains("5.6.5"));
            verify(connection, never()).createStatement();
            verify(connection, never()).prepareStatement(anyString());
            verify(connection, never()).close();
        } finally {
            JdbcConnection.setConnection(null);
        }
    }

    @Test
    void metadataFailureDoesNotExecuteQuery() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenThrow(new SQLException("metadata unavailable"));
        JdbcConnection.setConnection(connection);

        try {
            assertThrows(IllegalStateException.class, () -> new ProfilingService().explainPlanJson("select 1"));
            verify(connection, never()).createStatement();
        } finally {
            JdbcConnection.setConnection(null);
        }
    }
}
