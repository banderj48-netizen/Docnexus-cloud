package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.file.dto.DocumentContentConversion;
import com.xyf.docnexus.file.service.DocumentContentConverter;
import com.xyf.docnexus.file.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认文档内容转换器。
 *
 * <p>该实现只展示从真实文件流抽取到的内容；对暂未支持的格式直接返回明确错误，不伪造页面内容。</p>
 */
@Slf4j
@Service
public class DefaultDocumentContentConverter implements DocumentContentConverter {

    private static final Set<String> EDITABLE_EXTENSIONS = Set.of("txt", "docx", "pptx");

    /**
     * 从真实文件流中抽取可展示内容。
     */
    @Override
    public DocumentContentConversion convert(String fileExt, InputStream inputStream) {
        String extension = normalize(fileExt);
        String plainText = switch (extension) {
            case "txt" -> readText(inputStream);
            case "docx" -> readDocx(inputStream);
            case "doc" -> readDoc(inputStream);
            case "pptx" -> readPptx(inputStream);
            case "ppt", "wps", "wpt", "dps", "dpt", "wpd" -> throw new IllegalArgumentException("该格式需要接入 LibreOffice 转换器后才能真实打开");
            default -> throw new IllegalArgumentException("当前文件格式暂不支持在线打开");
        };
        String normalizedText = plainText == null ? "" : plainText.trim();
        String contentHtml = toSafeHtml(normalizedText);
        String contentHash = HashUtils.sha256(contentHtml);
        return new DocumentContentConversion("HTML", contentHtml, normalizedText, contentHash, editable(extension));
    }

    /**
     * 判断文件扩展名是否支持在线编辑保存。
     */
    @Override
    public boolean editable(String fileExt) {
        return EDITABLE_EXTENSIONS.contains(normalize(fileExt));
    }

    /**
     * 读取 TXT 文本内容。
     */
    private String readText(InputStream inputStream) {
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("读取 TXT 内容失败", exception);
        }
    }

    /**
     * 读取 DOCX 段落文本。
     */
    private String readDocx(InputStream inputStream) {
        try (InputStream stream = inputStream; XWPFDocument document = new XWPFDocument(stream)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n"));
        } catch (Exception exception) {
            throw new IllegalStateException("读取 DOCX 内容失败", exception);
        }
    }

    /**
     * 读取 DOC 老格式正文。
     */
    private String readDoc(InputStream inputStream) {
        try (InputStream stream = inputStream; WordExtractor extractor = new WordExtractor(stream)) {
            return extractor.getText();
        } catch (Exception exception) {
            throw new IllegalStateException("读取 DOC 内容失败", exception);
        }
    }

    /**
     * 读取 PPTX 文本框内容。
     */
    private String readPptx(InputStream inputStream) {
        try (InputStream stream = inputStream; XMLSlideShow slideShow = new XMLSlideShow(stream)) {
            StringBuilder builder = new StringBuilder();
            slideShow.getSlides().forEach(slide -> {
                builder.append("第 ").append(slide.getSlideNumber()).append(" 页").append('\n');
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            builder.append(text.trim()).append('\n');
                        }
                    }
                }
                builder.append('\n');
            });
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("读取 PPTX 内容失败", exception);
        }
    }

    /**
     * 将纯文本转成安全 HTML。
     */
    private String toSafeHtml(String text) {
        if (text == null || text.isBlank()) {
            return "<p>暂无可展示文本内容</p>";
        }
        return text.lines()
                .map(line -> line.isBlank() ? "<p><br></p>" : "<p>" + escape(line) + "</p>")
                .collect(Collectors.joining(""));
    }

    /**
     * 转义 HTML 特殊字符，防止文档内容注入脚本。
     */
    private String escape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 规范化文件扩展名。
     */
    private String normalize(String fileExt) {
        return fileExt == null ? "" : fileExt.toLowerCase(Locale.ROOT);
    }
}
