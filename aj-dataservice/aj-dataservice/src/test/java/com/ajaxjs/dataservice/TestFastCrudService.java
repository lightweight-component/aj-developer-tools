package com.ajaxjs.dataservice;


import com.ajaxjs.dataservice.crud.CrudService;
import com.ajaxjs.dataservice.crud.FastCrud;
import com.ajaxjs.sqlman.model.tablemodel.TableModel;
import com.ajaxjs.sqlman.v1.PageResult;
import com.ajaxjs.util.ObjectHelper;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class TestFastCrudService extends BaseTest {
    @Autowired
    CrudService crud;

    @Test
    public void testInfo() {
        // 定义表的 CRUD
        FastCrud<Map<String, Object>, Long> fastCRUD = new FastCrud<>();
        fastCRUD.setDao(crud);
        fastCRUD.setTableName("Employees");
        Map<String, Object> info = fastCRUD.info(1L);
        System.out.println(info);
        assertNotNull(info);

        FastCrud<Employee, Long> fastCRUD2 = new FastCrud<>();
        fastCRUD2.setDao(crud);
        fastCRUD2.setTableName("Employees");
        fastCRUD2.setClz(Employee.class);
        Employee info1 = fastCRUD2.info(1L);
        System.out.println(info1);
        assertNotNull(info1);
    }

    @Test
    public void testList() {
        FastCrud<Map<String, Object>, Long> fastCRUD = new FastCrud<>();
        fastCRUD.setDao(crud);
        fastCRUD.setTableName("Employees");

        // 列表排序按照 hire_date 字段排序，默认是 create_date，现改之
        TableModel tableModel = new TableModel();
        tableModel.setCreateDateField("hire_date");

        fastCRUD.setListOrderByDate(true);
        fastCRUD.setTableModel(tableModel);

        List<Map<String, Object>> list = fastCRUD.listMap(null);
        System.out.println(list);
        assertNotNull(list);

        FastCrud<Employee, Long> fastCRUD2 = new FastCrud<>();
        fastCRUD2.setDao(crud);
        fastCRUD2.setClz(Employee.class);
        fastCRUD2.setTableName("Employees");
        fastCRUD2.setListOrderByDate(true);
        fastCRUD2.setTableModel(tableModel);

        List<Employee> list2 = fastCRUD2.list();
        System.out.println(list2);
        assertNotNull(list2);
    }
    @Test
    public void testPage() {
        FastCrud<Map<String, Object>, Long> fastCRUD = new FastCrud<>();
        fastCRUD.setDao(crud);
        fastCRUD.setTableName("Employees");

        // 列表排序按照 hire_date 字段排序，默认是 create_date，现改之
        TableModel tableFieldName = new TableModel();
        tableFieldName.setCreateDateField("hire_date");

        fastCRUD.setListOrderByDate(true);
        fastCRUD.setTableModel(tableFieldName);

        PageResult<Map<String, Object>> list = fastCRUD.pageMap(null);
        System.out.println(list.size());
        assertNotNull(list);

        FastCrud<Employee, Long> fastCRUD2 = new FastCrud<>();
        fastCRUD2.setDao(crud);
        fastCRUD2.setClz(Employee.class);
        fastCRUD2.setTableName("Employees");
        fastCRUD2.setListOrderByDate(true);
        fastCRUD2.setTableModel(tableFieldName);

        PageResult<Employee> list2 = fastCRUD2.page(null);
        System.out.println(list2);
        assertNotNull(list2);
    }

    @Test
    public void testCreate() {
        FastCrud<Employee, Long> fastCRUD = new FastCrud<>();
        fastCRUD.setDao(crud);
        fastCRUD.setTableName("Employees");
        Employee e = new Employee();
        e.setId(9L);
        e.setName("Tom");

        Long newlyId = fastCRUD.createBean(e);
        System.out.println(newlyId);
        assertTrue(newlyId > 0);

        FastCrud<Employee, Long> fastCRUD2 = new FastCrud<>();
        fastCRUD2.setDao(crud);
        fastCRUD2.setTableName("Employees");

        Map<String,Object> map = ObjectHelper.mapOf("id", 10L, "name", "Ben");
        Long newlyId2 = fastCRUD2.create(map);

        System.out.println(newlyId2);
        assertTrue(newlyId2 > 0);
    }

    @Data
    public static class Employee {
        private Long id;

        private String name;

        private Date birthday;

        private Date hireDate;

        private String department;
    }
}