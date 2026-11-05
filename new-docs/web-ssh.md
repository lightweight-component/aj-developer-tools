# SSH 客户端：在浏览器中执行远程命令
https://juejin.cn/post/7536118823206125618

在日常运维工作中，我们经常需要通过SSH连接多台服务器进行操作。传统的SSH客户端虽然功能完善，但在某些场景下存在一些限制。

本文将介绍如何使用Spring Boot开发一个Web SSH客户端，让用户可以通过浏览器直接连接和操作远程服务器。

这种方案在企业内部运维管理、临时访问、移动办公等场景中具有一定的实用价值。

## Web SSH客户端的应用场景

相比传统SSH客户端，Web SSH在以下场景中具有实际价值：

#### 传统SSH客户端的局限性

**客户端依赖**：需要在每台设备上安装SSH客户端软件

**统一管理困难**：难以统一管理服务器连接配置和用户权限

**操作审计不便**：缺乏统一的操作日志记录和管理

**移动设备支持有限**：在手机、平板上操作体验较差

**防火墙限制**：某些网络环境下SSH端口可能被阻止

#### Web SSH的实际优势

**无需安装客户端**：通过浏览器即可使用，降低部署成本

**统一权限管理**：可以集中管理用户的服务器访问权限

**操作记录可追溯**：所有SSH操作都可以记录和审计

**移动设备友好**：在移动设备上也能提供相对较好的使用体验

**绕过端口限制**：通过HTTP/HTTPS端口提供服务

这些特点使得Web SSH在企业内部运维平台、云服务管理后台、教学环境等场景中有实际的应用价值。

## 技术方案设计

本文将基于以下技术栈实现Web SSH客户端：

#### 后端技术选型

**Spring Boot 3.x**：提供Web框架和自动配置能力

**JSch库**：Java实现的SSH2客户端，用于建立SSH连接

**WebSocket**：实现浏览器与服务器间的双向实时通信

**SqlMan**：轻量级数据库操作，存储服务器配置和操作记录

#### 前端技术选型

**HTML + JavaScript**：构建Web界面，无需复杂框架

**Xterm.js**：在浏览器中模拟终端界面

**WebSocket API**：与后端建立实时通信连接

#### 系统架构

```
浏览器终端 ←→ WebSocket ←→ Spring Boot应用 ←→ SSH连接 ←→ 目标服务器
     ↓                         ↓
  用户界面                   数据存储
  命令输入                   操作记录
  结果显示                   配置管理

```

核心流程：用户在浏览器中输入SSH连接信息，Spring Boot后端使用JSch库建立SSH连接，通过WebSocket将终端数据实时传输到前端Xterm.js组件进行显示。


#### 9\. 数据库初始化

创建必要的数据库表结构：

```
-- 服务器配置表
CREATE TABLE IF NOT EXISTS servers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '服务器名称',
    host VARCHAR(255) NOT NULL COMMENT '服务器地址',
    port INT DEFAULT 22 COMMENT 'SSH端口',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    password VARCHAR(500) NOT NULL COMMENT '密码（建议加密存储）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 删除现有测试数据（避免重复插入）
DELETE FROM servers;

-- 插入测试服务器数据
INSERT INTO servers (name, host, port, username, password) VALUES
('本地测试服务器', 'localhost', 22, 'root', 'password'),
('开发服务器', '192.168.1.100', 22, 'dev', 'devpass'),
('测试服务器', '192.168.1.101', 22, 'test', 'testpass'),
('生产服务器', '192.168.1.200', 22, 'prod', 'prodpass');
```

应用配置文件：

```
# 生产环境配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app_config?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: root
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

server:
  port: 8080
  servlet:
    context-path: /
  compression:
    enabled: true
    mime-types: text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json
  tomcat:
    max-connections: 200
    threads:
      max: 100
      min-spare: 10

logging:
  level:
    root: INFO
    com.example.webssh: DEBUG
  file:
    name: logs/webssh.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# 自定义配置
webssh:
  ssh:
    connection-timeout: 30000
    session-timeout: 1800000
    max-connections-per-user: 10
  file:
    upload-max-size: 100MB
    temp-dir: /tmp/webssh-uploads
  collaboration:
    enabled: true
    max-participants: 10
    session-timeout: 3600000
```

## 性能优化与最佳实践

#### 1\. 缓存优化

```
@Service
@EnableCaching
public class CachedServerService {
    
    @Cacheable(value = "servers", key = "#username")
    public List<Server> getUserServers(String username) {
        return serverRepository.findByCreatedBy(username);
    }
    
    @CacheEvict(value = "servers", key = "#username")
    public void clearUserServersCache(String username) {
        // 清理缓存
    }
}
```

#### 2\. 安全增强

```
@Component
public class SecurityEnhancements {
    
    /**
     * 密码加密存储
     */
    public String encryptPassword(String password) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encryptedPassword = cipher.doFinal(password.getBytes());
            return Base64.getEncoder().encodeToString(encryptedPassword);
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }
    
    /**
     * 操作审计
     */
    @EventListener
    public void handleSSHCommand(SSHCommandEvent event) {
        auditService.logSSHOperation(
            event.getUsername(),
            event.getServerHost(), 
            event.getCommand(),
            event.getTimestamp()
        );
    }
}
```

## 总结

通过JSch库处理SSH连接，WebSocket实现实时通信，我们构建了一个功能完整的Web SSH解决方案。

在实际生产环境中使用时，还需要考虑以下几个方面：

#### 安全注意事项

-   密码应该加密存储，不要明文保存
-   添加用户认证机制，避免无权限访问
-   考虑使用SSH密钥认证替代密码认证
-   限制可连接的服务器范围和用户权限

#### 性能优化

-   SSH连接池管理，避免频繁建立连接
-   WebSocket连接数量控制
-   大量输出时的数据传输优化