package com.ajaxjs.devtools.webssh.service;

import com.ajaxjs.devtools.webssh.model.ServerConfig;
import com.ajaxjs.sqlman.Action;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ServerRepository {
    private final String INSERT_SERVER = "INSERT INTO servers (name, host, port, username, password, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final String UPDATE_SERVER = "UPDATE servers SET name=?, host=?, port=?, username=?, password=?, updated_at= ? WHERE id= ?";

    public Long saveServer(ServerConfig server) {
        return new Action(server).create().execute(true, Long.class).getNewlyId();
    }

    public List<ServerConfig> findAllServers() {
        return new Action("SELECT id, name, host, port, username, password, created_at, updated_at FROM servers ORDER BY created_at DESC").query().list(ServerConfig.class);
    }

    public ServerConfig findServerById(Long id) {
        ServerConfig server = new Action(" SELECT id, name, host, port, username, password, created_at, updated_at FROM servers WHERE id = ?").query(id).one(ServerConfig.class);

        if (server == null)
            throw new NullPointerException("服务器[#" + id + "]不存在");

        return server;
    }

    public void updateServer(ServerConfig server) {
        new Action(server).update().execute();
    }

    public void deleteServer(Long id) {
        new Action("DELETE FROM servers WHERE id = ?").update(id).execute();
    }

    private ServerConfig mapRowToServer(ResultSet rs, int rowNum) throws SQLException {
        ServerConfig server = new ServerConfig();
        server.setId(rs.getLong("id"));
        server.setName(rs.getString("name"));
        server.setHost(rs.getString("host"));
        server.setPort(rs.getInt("port"));
        server.setUsername(rs.getString("username"));
        server.setPassword(rs.getString("password"));
        server.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        server.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        return server;
    }
}