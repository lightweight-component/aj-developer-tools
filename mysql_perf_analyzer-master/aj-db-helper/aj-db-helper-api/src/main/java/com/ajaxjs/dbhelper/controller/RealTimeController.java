package com.ajaxjs.dbhelper.controller;

import com.ajaxjs.dbhelper.model.GlobalStatusSnapshot;
import com.ajaxjs.spring.annotation.BizAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/realtime")
public interface RealTimeController {
    /**
     * 查看 MySQL 活动连接、会话与正在执行的 SQL。
     *
     * @param isAll false = active list
     * @return 进程列表
     */
    @GetMapping("/process_list")
    @BizAction("进程列表")
    List<Map<String, Object>> processlist(@RequestParam(required = false) boolean isAll);

    /**
     * 采样 MySQL 全局状态；关键词按指标名称进行不区分大小写的包含匹配。
     *
     * @param keyword 指标关键词，省略时返回全部指标。
     * @return 带采样时间的状态快照。
     */
    @GetMapping("/global_status")
    @BizAction("全局状态")
    GlobalStatusSnapshot globalStatus(@RequestParam(required = false) String keyword) throws SQLException;

    /**
     * 获取全部 MySQL 全局变量，供前端本地筛选。
     * @return 统一为 VARIABLE_NAME、VARIABLE_VALUE 两列的变量列表。
     */
    @GetMapping("/global_variables")
    @BizAction("全局变量")
    List<Map<String, String>> globalVariables() throws SQLException;
}
