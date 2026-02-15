package com.ajaxjs.dataservice.crud;

import com.ajaxjs.dataservice.core.DataAccessObject;
import com.ajaxjs.spring.DiContextUtil;
import com.ajaxjs.sqlman.v1.DataAccessException;
import com.ajaxjs.sqlman.v1.Pager;
import com.ajaxjs.sqlman.SmallMyBatis;
import com.ajaxjs.sqlman.v1.Sql;
import com.ajaxjs.sqlman.annotation.Id;
import com.ajaxjs.sqlman.annotation.Table;
import com.ajaxjs.sqlman.v1.Entity;
import com.ajaxjs.sqlman.model.tablemodel.TableModel;
import com.ajaxjs.sqlman.v1.PageResult;
import com.ajaxjs.util.ObjectHelper;
import com.ajaxjs.util.reflect.Methods;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Component
public class CrudService implements DataAccessObject {
    private SmallMyBatis smallMyBatis;

    @Override
    public <T> T queryOne(Class<T> clz, String sql, Object... params) {
        return Sql.instance().input(sql, params).queryOne(clz);
    }

    @Override
    public <T> T info(Class<T> beanClz, String sql, Object... params) {
        return Sql.instance().input(sql, params).query(beanClz);
    }

    @Override
    public <T> T infoBySqlId(Class<T> beanClz, String sqlId, Map<String, Object> mapParams, Object... params) {
        String sql = smallMyBatis.handleSql(mapParams, sqlId);

        return Sql.instance().input(sql, params).query(beanClz);
    }

    @Override
    public Map<String, Object> infoMap(String sql, Object... params) {
        return Sql.instance().input(sql, params).query();
    }

    @Override
    public Map<String, Object> infoMapBySqlId(String sqlId, Map<String, Object> mapParams, Object... params) {
        String sql = smallMyBatis.handleSql(mapParams, sqlId);

        return Sql.instance().input(sql, params).query();
    }

    @Override
    public <T> List<T> list(Class<T> beanClz, String sql, Object... params) {
        return getList(Sql.instance().input(sql, params).queryList(beanClz));
    }

    @Override
    public <T> List<T> listById(Class<T> beanClz, String sqlId, Map<String, Object> mapParams, Object... params) {
        String sql = smallMyBatis.handleSql(mapParams, sqlId);

        return getList(Sql.instance().input(sql, params).queryList(beanClz));
    }

    @Override
    public List<Map<String, Object>> listMap(String sql, Object... params) {
        return getList(Sql.instance().input(sql, params).queryList());
    }

    @Override
    public List<Map<String, Object>> listMapBySqlId(String sqlId, Map<String, Object> mapParams, Object... params) {
        String sql = smallMyBatis.handleSql(mapParams, sqlId);
        InheritableThreadLocal k;

        return getList(Sql.instance().input(sql, params).queryList());
    }

    @Override
    public <T> PageResult<T> page(Class<T> beanClz, String sql, Map<String, Object> paramsMap) {
        sql = SmallMyBatis.handleSql(sql, paramsMap);

        Pager pager = new Pager();
        pager.getParams(DiContextUtil.getRequest());

        return Sql.instance().input(sql).page(beanClz, pager.getStart(), pager.getLimit());
    }

    @Override
    public <T> PageResult<T> pageBySqlId(Class<T> beanClz, String sqlId, Map<String, Object> mapParams) {
        String sql = smallMyBatis.handleSql(mapParams, sqlId);

        return Sql.instance().input(sql).page(beanClz);
    }

    @Override
    public Long create(String talebName, Object entity, String idField) {
        TableModel model = new TableModel();
        model.setTableName(talebName);

        if (StringUtils.hasText(idField))
            model.setIdField(idField); // 如果ID字段名不为空，则设置 ID 字段名

        return Entity.instance().setTableModel(model).input(entity).create(Long.class).getNewlyId();
    }

    @Override
    public Long createWithIdField(Object entity, String idField) {
        return create(getTableName(entity), entity, idField);
    }

    @Override
    public Long createWithIdField(Object entity) {
        return createWithIdField(entity, getIdField(entity));
    }

    @Override
    public Long create(Object entity) {
        return create(getTableName(entity), entity, null);
    }

    @Override
    public boolean update(String talebName, Object entity, String idField) {
        TableModel model = new TableModel();
        model.setTableName(talebName);

        if (StringUtils.hasText(idField))
            model.setIdField(idField);
        else
            throw new DataAccessException("未指定 id，这将会是批量全体更新！");

        return Entity.instance().setTableModel(model).input(entity).update().isOk();
    }

    @Override
    public boolean update(String talebName, Object entity) {
        return update(talebName, entity, null);
    }

    @Override
    public boolean update(Object entity) {
        return update(getTableName(entity), entity);
    }

    @Override
    public boolean updateWithIdField(Object entity) {
        return updateWithIdField(entity, getIdField(entity));
    }

    @Override
    public boolean updateWithIdField(Object entity, String idField) {
        return update(getTableName(entity), entity, idField);
    }

    @Override
    public boolean updateWithWhere(Object entity, String where) {
        String talebName = getTableName(entity);

        return Entity.instance().setTableName(talebName).input(entity).update(where).isOk();
    }

    @Override
    public boolean delete(Object entity, Serializable id) {
        return delete(getTableName(entity), id);
    }

    @Override
    public boolean delete(String tableName, Serializable id) {
        // TODO: no id field
        return Sql.instance().input("DELETE FROM " + tableName + " WHERE " + id + " = ?", id).delete().isOk();
    }

    @Override
    public boolean delete(Object entity) {
        Object id = Methods.executeMethod(entity, "getId");

        if (id != null)
            return delete(entity, (Serializable) id);
        else {
            System.err.println("没有 getId()");
            return false;
        }
    }

    /**
     * 获取实体类上的表名（通过注解）
     *
     * @param entity 实体类
     * @return 表名
     */
    public static String getTableName(Object entity) {
        Table tableNameA = entity.getClass().getAnnotation(Table.class);

        if (tableNameA == null)
            throw new RuntimeException("实体类未提供表名");

        return tableNameA.value();
    }

    /**
     * 获取实体类上的 Id 字段名称（通过注解）
     *
     * @param entity 实体类
     * @return 表名
     */
    public static String getIdField(Object entity) {
        Id annotation = entity.getClass().getAnnotation(Id.class);

        if (annotation == null)
            throw new DataAccessException("没设置 IdField 注解，不知哪个主键字段");

        return annotation.value();
    }

    /**
     * 即使 List 为空（null），也要返回一个空的 List
     *
     * @param <T>  范型，List 中元素的类别
     * @param list 给定的 List对象，可以为 null
     * @return 如果给定的 List 不为 null，则直接返回原 List 对象；如果为 null，则返回一个空的 List 对象
     */
    public static <T> List<T> getList(List<T> list) {
        if (ObjectHelper.isEmpty(list))
            list = Collections.emptyList();

        return list;
    }

}