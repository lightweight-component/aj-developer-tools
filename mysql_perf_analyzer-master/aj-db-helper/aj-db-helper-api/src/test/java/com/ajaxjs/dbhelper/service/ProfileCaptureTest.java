package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dbhelper.model.ProfilingResult;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileCaptureTest {
    @Test
    void unsupportedServerDoesNotChangeSessionOrRetryQuery() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet variables = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW SESSION VARIABLES LIKE 'profiling%'")).thenReturn(variables);
        ProfilingResult result = new ProfilingResult();

        try (ProfileCapture capture = new ProfileCapture(connection)) {
            capture.enable();
            assertEquals("select 1", capture.query("select 1"));
            capture.collect(result);
        }

        assertFalse(result.isProfileAvailable());
        assertTrue(result.getProfileMessage().contains("不支持"));
        verify(statement, never()).execute(anyString());
        verify(connection, never()).close();
    }

    @Test
    void restoresOriginalSettingsWhenQueryFails() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet variables = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW SESSION VARIABLES LIKE 'profiling%'")).thenReturn(variables);
        when(variables.next()).thenReturn(true, true, false);
        when(variables.getString(1)).thenReturn("profiling", "profiling_history_size");
        when(variables.getString(2)).thenReturn("ON", "0");

        assertThrows(SQLException.class, () -> {
            try (ProfileCapture capture = new ProfileCapture(connection)) {
                capture.enable();
                assertTrue(capture.query("select 1").startsWith("/*aj_profile_"));
                throw new SQLException("query failed");
            }
        });

        verify(statement, times(2)).execute("SET SESSION profiling = 1");
        verify(statement).execute("SET SESSION profiling_history_size = 0");
        verify(connection, never()).close();
    }

    @Test
    void missingTargetDoesNotPickLatestUnrelatedQuery() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet variables = mock(ResultSet.class);
        ResultSet profiles = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW SESSION VARIABLES LIKE 'profiling%'")).thenReturn(variables);
        when(variables.next()).thenReturn(true, true, false);
        when(variables.getString(1)).thenReturn("profiling", "profiling_history_size");
        when(variables.getString(2)).thenReturn("OFF", "15");
        when(statement.executeQuery("SHOW PROFILES")).thenReturn(profiles);
        when(profiles.next()).thenReturn(true, false);
        when(profiles.getString("Query")).thenReturn("SHOW SESSION STATUS");
        ProfilingResult result = new ProfilingResult();

        try (ProfileCapture capture = new ProfileCapture(connection)) {
            capture.enable();
            capture.collect(result);
        }

        assertFalse(result.isProfileAvailable());
        assertTrue(result.getProfileMessage().contains("未找到"));
        verify(statement, never()).executeQuery(startsWith("SHOW PROFILE ALL"));
        verify(statement).execute("SET SESSION profiling = 0");
        verify(statement).execute("SET SESSION profiling_history_size = 15");
    }
}
