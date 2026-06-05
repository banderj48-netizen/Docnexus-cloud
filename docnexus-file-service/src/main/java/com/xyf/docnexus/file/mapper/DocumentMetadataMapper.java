package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.DocumentMetadata;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档详细元数据 Mapper。
 */
@Mapper
public interface DocumentMetadataMapper {

    /**
     * 查询单个文件的详细元数据。
     */
    @Select("""
            SELECT *
            FROM document_metadata
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
            LIMIT 1
            """)
    DocumentMetadata selectByFile(@Param("userId") Long userId, @Param("fileId") String fileId);

    /**
     * 新增或更新用户/AI 抽取出的详细元数据。
     */
    @Insert("""
            INSERT INTO document_metadata (
                file_id, user_id, title, authors_json, institution,
                journal, conference_name, publisher, publish_year, doi,
                isbn, abstract_text, reference_count, assignment_subject,
                report_type, requirement_type, form_purpose, extraction_source,
                confidence, evidence_json, created_at, updated_at
            ) VALUES (
                #{fileId}, #{userId}, #{title}, #{authorsJson}, #{institution},
                #{journal}, #{conferenceName}, #{publisher}, #{publishYear}, #{doi},
                #{isbn}, #{abstractText}, #{referenceCount}, #{assignmentSubject},
                #{reportType}, #{requirementType}, #{formPurpose}, #{extractionSource},
                #{confidence}, #{evidenceJson}, NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                authors_json = VALUES(authors_json),
                institution = VALUES(institution),
                journal = VALUES(journal),
                conference_name = VALUES(conference_name),
                publisher = VALUES(publisher),
                publish_year = VALUES(publish_year),
                doi = VALUES(doi),
                isbn = VALUES(isbn),
                abstract_text = VALUES(abstract_text),
                reference_count = VALUES(reference_count),
                assignment_subject = VALUES(assignment_subject),
                report_type = VALUES(report_type),
                requirement_type = VALUES(requirement_type),
                form_purpose = VALUES(form_purpose),
                extraction_source = VALUES(extraction_source),
                confidence = VALUES(confidence),
                evidence_json = VALUES(evidence_json),
                updated_at = NOW()
            """)
    int upsert(DocumentMetadata metadata);
}
