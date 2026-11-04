package com.ajaxjs.devtools.webssh.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ServerConfig {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}