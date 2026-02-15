package org.foo.controller;

import com.ajaxjs.dataservice.crud.FastCrud;
import com.ajaxjs.dataservice.crud.FastCrudService;
import com.ajaxjs.sqlman.model.tablemodel.TableModel;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通用万能型 API 接口
 */
@RestController
@RequestMapping("/simple_api")
public class SimpleApiController extends FastCrudService implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 定义表的 CRUD
        FastCrud<Map<String, Object>, Long> permission = new FastCrud<>();
        permission.setTableName("sys_bookmark");

        TableModel tableModel = new TableModel();
        tableModel.setHasIsDeleted(true);
        tableModel.setDelField("stat");
        permission.setTableModel(tableModel);

        set("bookmark", permission);

        FastCrud<Map<String, Object>, Long> role = new FastCrud<>();
        role.setTableName("per_role");
        role.setTableModel(tableModel);

        set("role", role);
    }
}
