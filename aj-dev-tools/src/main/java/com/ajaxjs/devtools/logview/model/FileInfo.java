package com.ajaxjs.devtools.logview.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileInfo {
    String name;

    Long size;

    Long lastModified;

    Boolean readable;
}
