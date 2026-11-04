package com.ajaxjs.devtools.webssh.controller;


import com.ajaxjs.devtools.webssh.model.ServerConfig;
import com.ajaxjs.devtools.webssh.service.ServerRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
public class ServerController {
    @Autowired
    private ServerRepository serverRepository;

    /**
     * 获取服务器列表
     */
    @GetMapping
    public List<ServerConfig> getServers() {
        List<ServerConfig> servers = serverRepository.findAllServers();
        servers.forEach(server -> server.setPassword(null));  // 不返回密码信息到前端

        return servers;
    }

    /**
     * 获取单个服务器配置
     */
    @GetMapping("/{id}")
    public ServerConfig getServer(@PathVariable Long id) {
        return serverRepository.findServerById(id);
    }

    /**
     * 添加服务器
     */
    @PostMapping
    public Map<String, Object> addServer(HttpServletResponse httpResponse, @RequestBody ServerConfig server) {
        try {
            // 验证必要参数
            if (server.getHost() == null || server.getHost().trim().isEmpty())
                return badRequest(httpResponse, Map.of("success", false, "message", "服务器地址不能为空"));
            if (server.getUsername() == null || server.getUsername().trim().isEmpty())
                return badRequest(httpResponse, Map.of("success", false, "message", "用户名不能为空"));
            if (server.getPassword() == null || server.getPassword().trim().isEmpty())
                return badRequest(httpResponse, Map.of("success", false, "message", "密码不能为空"));

            // 设置默认值
            if (server.getPort() == null)
                server.setPort(22);
            if (server.getName() == null || server.getName().trim().isEmpty())
                server.setName(server.getUsername() + "@" + server.getHost());

            // 密码加密存储（生产环境建议）
            // server.setPassword(encryptPassword(server.getPassword()));
            Long serverId = serverRepository.saveServer(server);
            return Map.of("success", true, "id", serverId);
        } catch (Exception e) {
            return badRequest(httpResponse, Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 删除服务器
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteServer(@PathVariable Long id) {
        serverRepository.deleteServer(id);
        return Map.of("success", true);
    }

    /**
     * 测试服务器连接
     */
    @PostMapping("/test")
    public Map<String, Object> testConnection(HttpServletResponse httpResponse, @RequestBody ServerConfig server) throws JSchException {
        // 验证必要参数
        if (server.getHost() == null || server.getHost().trim().isEmpty())
            return badRequest(httpResponse, Map.of("success", false, "message", "服务器地址不能为空"));

        if (server.getUsername() == null || server.getUsername().trim().isEmpty())
            return badRequest(httpResponse, Map.of("success", false, "message", "用户名不能为空"));

        if (server.getPassword() == null || server.getPassword().trim().isEmpty())
            return badRequest(httpResponse, Map.of("success", false, "message", "密码不能为空"));

        // 设置默认端口
        int port = server.getPort() != null ? server.getPort() : 22;

        // 简单的连接测试
        JSch jsch = new JSch();
        Session session = jsch.getSession(server.getUsername(), server.getHost(), port);
        session.setPassword(server.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000); // 5秒超时
        session.disconnect();

        return Map.of("success", true, "message", "连接测试成功");

    }

    /**
     * 设置参数或操作失败的 HTTP 状态，并直接返回原有错误数据。
     *
     * @param response 当前 HTTP 响应
     * @param data     错误数据对象
     * @return 原始数据对象
     */
    private Map<String, Object> badRequest(HttpServletResponse response, Map<String, Object> data) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return data;
    }
}
