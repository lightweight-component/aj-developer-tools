package com.ajaxjs.devtools.webssh.model;

import lombok.Data;

@Data
public class FileInfo {
    private String name;
    private boolean isDirectory;
    private long size;
    private long lastModified;
    private String permissions;

    public FileInfo(String name, boolean isDirectory, long size, long lastModified, String permissions) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.permissions = permissions;
    }
}
