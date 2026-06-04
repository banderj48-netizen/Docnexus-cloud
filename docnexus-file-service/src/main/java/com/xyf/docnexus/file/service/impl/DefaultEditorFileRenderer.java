package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.file.service.EditorFileRenderer;
import com.xyf.docnexus.file.util.DocumentHtmlSanitizer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 默认在线编辑文件生成器。
 *
 * <p>TXT 直接保存纯文本，DOCX 保存为新的 DOCX，PPTX 按文本页重建基础幻灯片。</p>
 */
@Service
public class DefaultEditorFileRenderer implements EditorFileRenderer {

    /**
     * 把前端安全 HTML 重新生成当前文件格式的二进制内容。
     */
    @Override
    public byte[] render(String fileExt, String contentHtml) {
        String extension = normalize(fileExt);
        String plainText = toPlainText(contentHtml);
        return switch (extension) {
            case "txt" -> plainText.getBytes(StandardCharsets.UTF_8);
            case "docx" -> renderDocx(plainText);
            case "pptx" -> renderPptx(plainText);
            case "doc", "ppt" -> wrapHtml(contentHtml);
            default -> throw new IllegalArgumentException("当前文件格式暂不支持在线保存");
        };
    }

    /**
     * 获取生成文件的内容类型。
     */
    @Override
    public String contentType(String fileExt) {
        return switch (normalize(fileExt)) {
            case "txt" -> "text/plain;charset=UTF-8";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "doc" -> "application/msword";
            default -> "application/octet-stream";
        };
    }

    /**
     * 生成 DOCX 文档。
     */
    private byte[] renderDocx(String plainText) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String line : plainText.split("\\R", -1)) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("生成 DOCX 文件失败", exception);
        }
    }

    /**
     * 生成基础 PPTX 文档。
     */
    private byte[] renderPptx(String plainText) {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String[] blocks = plainText.split("(?m)^第\\s*\\d+\\s*页\\s*$");
            boolean created = false;
            for (String block : blocks) {
                String text = block.trim();
                if (text.isBlank()) {
                    continue;
                }
                XSLFSlide slide = slideShow.createSlide();
                XSLFTextBox textBox = slide.createTextBox();
                textBox.setAnchor(new Rectangle(60, 60, 600, 360));
                textBox.setText(text);
                created = true;
            }
            if (!created) {
                XSLFSlide slide = slideShow.createSlide();
                XSLFTextBox textBox = slide.createTextBox();
                textBox.setAnchor(new Rectangle(60, 60, 600, 360));
                textBox.setText(plainText);
            }
            slideShow.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("生成 PPTX 文件失败", exception);
        }
    }

    /**
     * 生成可被 Office 打开的 HTML 载荷。
     */
    private byte[] wrapHtml(String contentHtml) {
        String html = """
                <html>
                <head><meta charset="UTF-8"></head>
                <body>%s</body>
                </html>
                """.formatted(sanitize(contentHtml));
        return html.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将 HTML 粗略转为纯文本。
     */
    private String toPlainText(String html) {
        return sanitize(html)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    /**
     * 保存前再次清理危险标签和外链资源。
     */
    private String sanitize(String html) {
        return DocumentHtmlSanitizer.sanitize(html);
    }

    /**
     * 规范化扩展名。
     */
    private String normalize(String fileExt) {
        return fileExt == null ? "" : fileExt.toLowerCase(Locale.ROOT);
    }
}
