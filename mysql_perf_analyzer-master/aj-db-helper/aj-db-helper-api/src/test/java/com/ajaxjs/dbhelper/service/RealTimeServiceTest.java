package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.model.GlobalStatusSnapshot;
import com.ajaxjs.sqlman.JdbcConnection;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealTimeServiceTest {
    @Test
    void selectsSqlForOldAndModernVersionsAndNormalizesResults() throws Exception {
        verifyVersion(5, 6, "SELECT VARIABLE_NAME, VARIABLE_VALUE FROM information_schema.GLOBAL_STATUS");
        verifyVersion(5, 7, "SHOW GLOBAL STATUS");
        verifyVersion(8, 0, "SHOW GLOBAL STATUS");
    }

    private void verifyVersion(int major, int minor, String sql) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseMajorVersion()).thenReturn(major);
        when(metadata.getDatabaseMinorVersion()).thenReturn(minor);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(sql)).thenReturn(result);
        when(result.next()).thenReturn(true, true, false);
        when(result.getString(1)).thenReturn("Threads_connected", "Uptime");
        when(result.getString(2)).thenReturn("9007199254740993123", "100");
        JdbcConnection.setConnection(connection);

        try {
            GlobalStatusSnapshot snapshot = new RealTimeService().globalStatus("tHrEaDs");
            assertEquals(1, snapshot.getVariables().size());
            assertEquals("THREADS_CONNECTED", snapshot.getVariables().get(0).get("VARIABLE_NAME"));
            assertEquals("9007199254740993123", snapshot.getVariables().get(0).get("VARIABLE_VALUE"));
            assertEquals(Long.valueOf(100), snapshot.getUptime());
            assertTrue(snapshot.getSampledAt() > 0);
            verify(result).close();
            verify(statement).close();
            verify(connection, never()).close();
        } finally {
            JdbcConnection.setConnection(null);
        }
    }
}
