package com.ajaxjs.devtools.webssh.controller;

import com.ajaxjs.devtools.webssh.model.ServerConfig;
import com.ajaxjs.devtools.webssh.service.ServerRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理服务器配置
 */
@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {
    private final ServerRepository serverRepository;

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
    public Long addServer(@RequestBody ServerConfig server) {
        // 验证必要参数
        if (server.getHost() == null || server.getHost().trim().isEmpty())
            throw new IllegalArgumentException("服务器地址不能为空");
        if (server.getUsername() == null || server.getUsername().trim().isEmpty())
            throw new IllegalArgumentException("用户名不能为空");
        if (server.getPassword() == null || server.getPassword().trim().isEmpty())
            throw new IllegalArgumentException("密码不能为空");

        if (server.getPort() == null) // 设置默认值
            server.setPort(22);

        if (server.getName() == null || server.getName().trim().isEmpty())
            server.setName(server.getUsername() + "@" + server.getHost());

        // 密码加密存储（生产环境建议）
        // server.setPassword(encryptPassword(server.getPassword()));
        return serverRepository.saveServer(server);
    }

    /**
     * 删除服务器
     */
    @DeleteMapping("/{id}")
    public boolean deleteServer(@PathVariable Long id) {
        serverRepository.deleteServer(id);

        return true;
    }

    /**
     * 测试服务器连接
     */
    @PostMapping("/test")
    public boolean testConnection(@RequestBody ServerConfig server) throws JSchException {
        // 验证必要参数
        if (server.getHost() == null || server.getHost().trim().isEmpty())
            throw new IllegalArgumentException("服务器地址不能为空");

        if (server.getUsername() == null || server.getUsername().trim().isEmpty())
            throw new IllegalArgumentException("用户名不能为空");

        if (server.getPassword() == null || server.getPassword().trim().isEmpty())
            throw new IllegalArgumentException("密码不能为空");

        int port = server.getPort() != null ? server.getPort() : 22; // 设置默认端口

        JSch jsch = new JSch();// 简单的连接测试
        Session session = jsch.getSession(server.getUsername(), server.getHost(), port);
        session.setPassword(server.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000); // 5秒超时
        session.disconnect();

        return true;
    }
}
