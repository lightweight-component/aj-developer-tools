package com.ajaxjs.devtools.webssh.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FileInfo {
    private final String name;
    private final boolean isDirectory;
    private final long size;
    private final long lastModified;
    private final String permissions;
}
