package com.toolkit.converter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * PDF → HTML 转换器
 * 提取 PDF 文本内容，智能分析结构（标题、段落），生成规范 HTML
 */
public class PdfToHtmlConverter {

    private static final Logger LOGGER = Logger.getLogger(PdfToHtmlConverter.class.getName());

    // 匹配常见编号模式：一、 二、 1. 2. (1) (2) 第一条 等
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百]+[章条节款]|[一二三四五六七八九十]+[、.]|\\d+[、.])\\s*.*");

    private static final Pattern SUB_ITEM_PATTERN = Pattern.compile(
            "^(\\d+[)）]|[（(]\\d+[)）]|[a-zA-Z][)）.])\\s*.*");

    public static String convert(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            String title = file.getName().replaceAll("\\.[^.]+$", "");

            // 按页提取文本
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            StringBuilder body = new StringBuilder();

            int totalPages = doc.getNumberOfPages();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);

                if (page > 1) {
                    body.append("    <div class=\"page-break\"></div>\n");
                }

                List<String> lines = splitAndMergeLines(pageText);
                boolean firstLine = (page == 1);

                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    // 第一页第一行非空文本作为标题
                    if (firstLine) {
                        title = trimmed;
                        body.append("    <h1>").append(HtmlTemplate.escapeHtml(trimmed)).append("</h1>\n");
                        firstLine = false;
                        continue;
                    }

                    // 判断是否是大标题/章节标题
                    if (HEADING_PATTERN.matcher(trimmed).matches()) {
                        body.append("    <h2>").append(HtmlTemplate.escapeHtml(trimmed)).append("</h2>\n");
                    }
                    // 子项条目
                    else if (SUB_ITEM_PATTERN.matcher(trimmed).matches()) {
                        body.append("    <p class=\"no-indent indent-list\">")
                                .append(HtmlTemplate.escapeHtml(trimmed)).append("</p>\n");
                    }
                    // 普通段落
                    else {
                        body.append("    <p>").append(HtmlTemplate.escapeHtml(trimmed)).append("</p>\n");
                    }
                }
            }

            return HtmlTemplate.wrap(title, body.toString());
        }
    }

    /**
     * 将 PDF 提取的文本按逻辑段落分组
     * PDF 提取的文本通常每行都有换行符，需要合并属于同一段落的行
     */
    private static List<String> splitAndMergeLines(String pageText) {
        String[] rawLines = pageText.split("\\n");
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String raw : rawLines) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                // 空行 = 段落分隔
                if (!current.isEmpty()) {
                    merged.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }

            // 如果是新的编号/标题开头，先结束上一段
            if ((HEADING_PATTERN.matcher(trimmed).matches()
                    || SUB_ITEM_PATTERN.matcher(trimmed).matches())
                    && !current.isEmpty()) {
                merged.add(current.toString().trim());
                current.setLength(0);
            }

            if (!current.isEmpty()) current.append(" ");
            current.append(trimmed);
        }

        if (!current.isEmpty()) {
            merged.add(current.toString().trim());
        }

        return merged;
    }
}
