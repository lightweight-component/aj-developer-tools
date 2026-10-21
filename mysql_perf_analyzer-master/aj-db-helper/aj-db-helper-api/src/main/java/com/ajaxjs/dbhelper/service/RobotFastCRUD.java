package com.ajaxjs.dbhelper.service;

import com.ajaxjs.dataservice.fastcrud.Namespaces;
import com.ajaxjs.dataservice.fastcrud.sqlgenerator.AutoQuery;
import com.ajaxjs.dataservice.fastcrud.sqlgenerator.AutoQueryBusiness;
import com.ajaxjs.framework.database.DataBaseConnection;
import com.ajaxjs.iam.client.SecurityManager;
import com.ajaxjs.sqlman.JdbcConnection;
import com.ajaxjs.sqlman.model.tablemodel.TableModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class RobotFastCRUD extends Namespaces {
    {
        AutoQueryBusiness autoQueryBusiness = new AutoQueryBusiness() {
            @Override
            public boolean isListOrderByDate() {
                return true;
            }

            @Override
            public boolean isTenantIsolation() {
                return false;
            }

            @Override
            public boolean isCurrentUserOnly() {
                return false;
            }

            @Override
            public boolean isFilterDeleted() {
                return false;
            }

            @Override
            public Serializable getCurrentUserId() {
                return SecurityManager.getUser().getId();
            }

            @Override
            public Serializable getTenantId() {
                return null;
            }
        };

        TableModel projectTableModel = new TableModel();// @dep
        projectTableModel.setTableName("ds_project");

        TableModel widgetConfigTableModel = new TableModel();
        widgetConfigTableModel.setTableName("ds_widget_config");

        TableModel commonApiTableModel = new TableModel();
        commonApiTableModel.setTableName("ds_common_api");

        TableModel firmware = new TableModel();
        firmware.setTableName("firmware_update");

        TableModel voice = new TableModel();
        voice.setTableName("voice");

        TableModel voicePrint = new TableModel();
        voicePrint.setTableName("group_member");

        TableModel kb = new TableModel();
        kb.setTableName("kb_document");

        put("voice", new AutoQuery(voice, autoQueryBusiness));
        put("voice_print", new AutoQuery(voicePrint, autoQueryBusiness));
        put("kb_document", new AutoQuery(kb, autoQueryBusiness));
        put("firmware_update", new AutoQuery(firmware, autoQueryBusiness));
        put("ds_project", new AutoQuery(projectTableModel, autoQueryBusiness));
        put("ds_widget_config", new AutoQuery(widgetConfigTableModel, autoQueryBusiness));
        put("ds_common_api", new AutoQuery(commonApiTableModel, autoQueryBusiness));
    }

    @EventListener
    public void loadConfig(ApplicationReadyEvent event) {
        DataBaseConnection.initDb();
        loadFromDB(() -> SecurityManager.getUser().getId(), () -> 9);
        JdbcConnection.closeConnection();
    }
}
