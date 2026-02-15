package com.ajaxjs.dataservice.crud;

import com.ajaxjs.dataservice.core.DataAccessObject;
import com.ajaxjs.framework.model.PageVO;
import com.ajaxjs.spring.DiContextUtil;
import com.ajaxjs.sqlman.v1.PageResult;
import com.ajaxjs.sqlman.util.Utils;
import com.ajaxjs.util.CommonConstant;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class FastCrudService implements FastCrudController {
    public final Map<String, FastCrud<Map<String, Object>, Long>> namespaces = new HashMap<>();

    public boolean isInit;

    public void set(String namespace, FastCrud<Map<String, Object>, Long> item) {
        if (item.getDao() == null)
            item.setDao(DiContextUtil.getBean(DataAccessObject.class));// TODO by @Autowired?

        namespaces.put(namespace, item);
    }

    private FastCrud<Map<String, Object>, Long> getCRUD(String namespace) {
        if (!namespaces.containsKey(namespace))
            throw new IllegalStateException("命名空间 " + namespace + " 没有配置 BaseCRUD");

        return namespaces.get(namespace);
    }

    @Override
    public Map<String, Object> info(String namespace, Long id) {
        return getCRUD(namespace).infoMap(id);
    }

    @Override
    public List<Map<String, Object>> list(String namespace) {
        String where = getWhereClause(Objects.requireNonNull(DiContextUtil.getRequest()));

        return getCRUD(namespace).listMap(where);
    }

    @Override
    public PageResult<Map<String, Object>> page(String namespace) {
        String where = getWhereClause(Objects.requireNonNull(DiContextUtil.getRequest()));
        PageResult<Map<String, Object>> result = getCRUD(namespace).page(where);

        return getCRUD(namespace).page(where);
    }

    @Override
    public Long create(String namespace, Map<String, Object> params) {
        return getCRUD(namespace).create(params);
    }

    @Override
    public Long create(Map<String, Object> params, String namespace) {
        return getCRUD(namespace).create(params);
    }

    @Override
    public Boolean update(String namespace, Map<String, Object> params) {
        return getCRUD(namespace).update(params);
    }

    @Override
    public Boolean update(Map<String, Object> params, String namespace) {
        return update(namespace, params);
    }

    @Override
    public Boolean delete(String namespace, Long id) {
        return getCRUD(namespace).delete(id);
    }

    /**
     * 基于 URL 的 QueryString，设计一个条件查询的参数规范，可以转化为 SQL 的 Where 里面的查询
     * usage:
     * <pre>
     *  ?q_name=张三&q_age=18&q_sex=1&ql_address=上海&lo=and // 默认是 OR 关系
     * </pre>
     *
     * @param request 请求对象
     * @return SQL Where 语句
     */
    public static String getWhereClause(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        List<String> arr = new ArrayList<>();

        for (String parameterName : parameters.keySet()) {
            boolean isQuery = parameterName.startsWith("q_");
            boolean isQueryLike = parameterName.startsWith("ql_");

            if (!isQuery && !isQueryLike)// 跳过不符合条件的参数
                continue;

            StringBuilder pair = new StringBuilder();
            String[] parameterValues = parameters.get(parameterName);
            String fieldName = parameterName.substring(isQueryLike ? 3 : 2);  // 构建 SQL 查询
            Utils.escapeSqlInjection(fieldName);

//            whereClause.append(" OR ");
            pair.append(fieldName);

            // 处理单值参数
            if (parameterValues.length == 1) {
                String value = Utils.escapeSqlInjection(parameterValues[0]).trim();

                if (isQuery) {
                    pair.append(" = ");
                    pair.append("'").append(value).append("'");
                }

                if (isQueryLike) {
                    pair.append(" LIKE ");
                    pair.append("'%").append(value).append("%'");
                }
            } else {
                // 处理数组参数
                pair.append(" IN (");

                if (parameterValues.length > 0) {
                    for (String parameterValue : parameterValues) {
                        pair.append("'");
                        pair.append(Utils.escapeSqlInjection(parameterValue).trim());
                        pair.append("',");
                    }

                    pair.deleteCharAt(pair.length() - 1);
                }

                pair.append(")");
            }

            arr.add(pair.toString());
        }

        if (arr.size() == 0)
            return CommonConstant.EMPTY_STRING;
        else {
            StringBuilder whereClause = new StringBuilder(); // 创建一个用于存储 SQL 查询的 StringBuilder
            whereClause.append(" AND (");
            String logicalOperators = "and".equals(request.getParameter("lo")) ? " AND " : " OR ";
            whereClause.append(String.join(logicalOperators, arr));
            whereClause.append(")");

            return whereClause.toString();// 返回 SQL 查询
        }
    }
}
