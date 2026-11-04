package com.ajaxjs.devtools.webssh.controller;

import com.ajaxjs.devtools.webssh.model.FileInfo;
import com.ajaxjs.devtools.webssh.service.FileTransferService;
import com.ajaxjs.devtools.webssh.service.ServerRepository;
import com.ajaxjs.util.ObjectHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileTransferController {
    private final FileTransferService fileTransferService;

    private final ServerRepository serverRepository;

    /**
     * 上传文件到服务器
     */
    @PostMapping("/upload/{serverId}")
    public boolean uploadFile(@PathVariable Long serverId, @RequestParam("file") MultipartFile file, @RequestParam String remotePath) {
        if (file.isEmpty())
            throw new IllegalArgumentException("文件不能为空");

        fileTransferService.uploadFile(serverRepository.findServerById(serverId), file, remotePath);

        return true;
    }

    /**
     * 批量上传文件
     */
    @PostMapping("/upload-batch/{serverId}")
    public boolean uploadFiles(@PathVariable Long serverId, @RequestParam("files") MultipartFile[] files, @RequestParam String remotePath) {
        if (ObjectHelper.isEmpty(files))
            throw new IllegalArgumentException("请选择要上传的文件");

        fileTransferService.uploadFiles(serverRepository.findServerById(serverId), files, remotePath);

        return true;
    }

    /**
     * 从服务器下载文件
     */
    @GetMapping("/download/{serverId}")
    public byte[] downloadFile(HttpServletResponse httpResponse,
                               @PathVariable Long serverId,
                               @RequestParam String remoteFilePath) throws Exception {
        byte[] fileContent = fileTransferService.downloadFile(serverRepository.findServerById(serverId), remoteFilePath);
        // 从路径中提取文件名
        String filename = remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1);

        httpResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        httpResponse.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        return fileContent;

    }

    /**
     * 列出远程目录内容
     */
    @GetMapping("/list/{serverId}")
    public Map<String, Object> listDirectory(@PathVariable Long serverId, @RequestParam String remotePath) throws Exception {
        List<FileInfo> files = fileTransferService.listDirectory(serverRepository.findServerById(serverId), remotePath);

        return Map.of(
                "success", true,
                "files", files,
                "path", remotePath
        );
    }

    /**
     * 创建远程目录
     */
    @PostMapping("/mkdir/{serverId}")
    public Map<String, Object> createDirectory(HttpServletResponse httpResponse,
                                               @PathVariable Long serverId,
                                               @RequestBody Map<String, String> request) throws Exception {
        String remotePath = request.get("remotePath");

        if (remotePath == null || remotePath.trim().isEmpty())
            return badRequest(httpResponse, Map.of("success", false, "message", "目录路径不能为空"));

        fileTransferService.createRemoteDirectory(serverRepository.findServerById(serverId), remotePath);

        return Map.of(
                "success", true,
                "message", "目录创建成功",
                "path", remotePath
        );


    }

    /**
     * 删除远程文件或目录
     */
    @DeleteMapping("/delete/{serverId}")
    public Map<String, Object> deleteFile(HttpServletResponse httpResponse,
                                          @PathVariable Long serverId,
                                          @RequestParam String remotePath,
                                          @RequestParam(value = "isDirectory", defaultValue = "false") boolean isDirectory) throws Exception {
        fileTransferService.deleteRemoteFile(serverRepository.findServerById(serverId), remotePath, isDirectory);

        return Map.of(
                "success", true,
                "message", (isDirectory ? "目录" : "文件") + "删除成功",
                "path", remotePath
        );
    }

    /**
     * 重命名远程文件
     */
    @PostMapping("/rename/{serverId}")
    public Map<String, Object> renameFile(HttpServletResponse httpResponse,
                                          @PathVariable Long serverId,
                                          @RequestBody Map<String, String> request) {
        String oldPath = request.get("oldPath");
        String newPath = request.get("newPath");

        if (oldPath == null || newPath == null || oldPath.trim().isEmpty() || newPath.trim().isEmpty())
            return badRequest(httpResponse, Map.of("success", false, "message", "路径不能为空"));

        fileTransferService.renameRemoteFile(serverRepository.findServerById(serverId), oldPath, newPath);

        return Map.of(
                "success", true,
                "message", "重命名成功",
                "oldPath", oldPath,
                "newPath", newPath
        );
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
