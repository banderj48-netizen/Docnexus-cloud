package com.xyf.docnexus.file.dto;

import lombok.Data;

import java.util.List;

/**
 * OnlyOffice 保存回调请求体。
 *
 * <p>OnlyOffice status=2 或 status=6 且携带 url 时，文件服务会下载编辑后的文件并覆盖当前版本。</p>
 */
@Data
public class OnlyOfficeCallbackRequest {
    private Integer status;
    private String key;
    private String url;
    private String token;
    private String filetype;
    private List<String> users;
    private Integer forcesavetype;
    private Integer error;
    private String userdata;
}
