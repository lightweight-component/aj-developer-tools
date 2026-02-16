package com.ajaxjs.mysqlmonitor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseTest {
    @BeforeEach
    void initAll() {
//        DataBaseConnection.initDb();
    }

    @AfterEach
    void closeDb() {
//        JdbcConnection.closeDb();
    }
}