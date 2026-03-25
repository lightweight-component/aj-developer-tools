package com.ajaxjs.devtools.webssh;

import com.ajaxjs.devtools.webssh.model.ServerConfig;
import com.ajaxjs.sqlman.Action;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ServerRepository {
    private final String INSERT_SERVER = """
            INSERT INTO servers (name, host, port, username, password, created_at, updated_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final String SELECT_ALL_SERVERS = """
            SELECT id, name, host, port, username, password, created_at, updated_at 
            FROM servers ORDER BY created_at DESC
            """;

    private final String SELECT_SERVER_BY_ID = """
            SELECT id, name, host, port, username, password, created_at, updated_at 
            FROM servers WHERE id = ?
            """;

    private final String UPDATE_SERVER = """
            UPDATE servers SET name=?, host=?, port=?, username=?, password=?, updated_at=? 
            WHERE id=?
            """;

    private final String DELETE_SERVER = "DELETE FROM servers WHERE id = ?";

    public Long saveServer(ServerConfig server) {
        return new Action(server).create().execute(true, Long.class).getNewlyId();
    }

    public List<ServerConfig> findAllServers() {
        return new Action(SELECT_ALL_SERVERS).query().list(ServerConfig.class);
    }

    public Optional<ServerConfig> findServerById(Long id) {
        ServerConfig serverConfig = new Action(SELECT_SERVER_BY_ID).query(id).one(ServerConfig.class);

        return serverConfig == null ? Optional.empty() : Optional.of(serverConfig);
    }

    public void updateServer(ServerConfig server) {
        new Action(server).update().execute();
    }

    public void deleteServer(Long id) {
        new Action(DELETE_SERVER).update(id).execute();
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