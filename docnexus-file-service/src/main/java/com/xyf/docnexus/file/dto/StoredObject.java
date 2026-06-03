package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象存储写入结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredObject {
    private String bucketName;
    private String objectKey;
}
