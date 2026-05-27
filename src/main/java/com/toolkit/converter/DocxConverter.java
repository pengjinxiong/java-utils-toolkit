package com.toolkit.converter;

import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * DOCX → HTML 转换器
 * 解析 docx 的段落、表格、样式，生成规范 HTML
 */
public class DocxConverter {

    private static final Logger LOGGER = Logger.getLogger(DocxConverter.class.getName());

    public static String convert(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            String title = extractTitle(file, doc);
            StringBuilder body = new StringBuilder();

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    body.append(convertParagraph(para));
                } else if (element instanceof XWPFTable table) {
                    body.append(convertTable(table));
                }
            }

            return HtmlTemplate.wrap(title, body.toString());
        }
    }

    private static String extractTitle(File file, XWPFDocument doc) {
        // 尝试从第一个标题段落提取
        for (XWPFParagraph para : doc.getParagraphs()) {
            String style = para.getStyle();
            if (style != null && (style.contains("Heading") || style.contains("heading")
                    || style.equals("1") || style.equals("Title"))) {
                String text = para.getText().trim();
                if (!text.isEmpty()) return text;
            }
        }
        // 退回到文件名
        return file.getName().replaceAll("\\.[^.]+$", "");
    }

    private static String convertParagraph(XWPFParagraph para) {
        String text = para.getText().trim();
        if (text.isEmpty()) return "";

        String style = para.getStyle();
        ParagraphAlignment alignment = para.getAlignment();

        // 判断标题级别
        int headingLevel = getHeadingLevel(style, para);
        if (headingLevel > 0 && headingLevel <= 6) {
            return "    <h" + headingLevel + ">" + buildRunsHtml(para) + "</h" + headingLevel + ">\n";
        }

        // 普通段落
        StringBuilder sb = new StringBuilder();
        String cssClasses = getCssClasses(para, alignment);
        String inlineStyle = getInlineStyle(para, alignment);

        sb.append("    <p");
        if (!cssClasses.isEmpty()) sb.append(" class=\"").append(cssClasses).append("\"");
        if (!inlineStyle.isEmpty()) sb.append(" style=\"").append(inlineStyle).append("\"");
        sb.append(">");
        sb.append(buildRunsHtml(para));
        sb.append("</p>\n");

        return sb.toString();
    }

    /**
     * 解析段落中的 runs，保留加粗/斜体等格式
     */
    private static String buildRunsHtml(XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            String runText = run.text();
            if (runText == null || runText.isEmpty()) continue;

            String escaped = HtmlTemplate.escapeHtml(runText);
            boolean bold = run.isBold();
            boolean italic = run.isItalic();
            boolean underline = run.getUnderline() != UnderlinePatterns.NONE;

            if (bold || italic || underline) {
                StringBuilder styles = new StringBuilder();
                if (bold) styles.append("font-weight:bold;");
                if (italic) styles.append("font-style:italic;");
                if (underline) styles.append("text-decoration:underline;");
                sb.append("<span style=\"").append(styles).append("\">").append(escaped).append("</span>");
            } else {
                sb.append(escaped);
            }
        }
        return sb.toString();
    }

    private static int getHeadingLevel(String style, XWPFParagraph para) {
        if (style == null) {
            // 通过字体大小和加粗推测标题
            for (XWPFRun run : para.getRuns()) {
                int fontSize = run.getFontSizeAsDouble() != null ? run.getFontSizeAsDouble().intValue() : 0;
                if (run.isBold() && fontSize >= 18) return 1;
                if (run.isBold() && fontSize >= 15) return 2;
                if (run.isBold() && fontSize >= 13) return 3;
            }
            return 0;
        }

        // Word 内置样式
        return switch (style) {
            case "Title", "title" -> 1;
            case "1", "Heading1", "heading 1", "heading1" -> 1;
            case "2", "Heading2", "heading 2", "heading2" -> 2;
            case "3", "Heading3", "heading 3", "heading3" -> 3;
            case "4", "Heading4", "heading 4", "heading4" -> 4;
            case "5", "Heading5", "heading 5", "heading5" -> 5;
            case "6", "Heading6", "heading 6", "heading6" -> 6;
            default -> {
                if (style.toLowerCase().contains("heading")) {
                    // 尝试从样式名中提取数字
                    for (char c : style.toCharArray()) {
                        if (Character.isDigit(c)) yield Character.getNumericValue(c);
                    }
                }
                yield 0;
            }
        };
    }

    private static String getCssClasses(XWPFParagraph para, ParagraphAlignment alignment) {
        StringBuilder classes = new StringBuilder();
        // 检查是否全部加粗
        boolean allBold = !para.getRuns().isEmpty() && para.getRuns().stream().allMatch(XWPFRun::isBold);
        if (allBold) classes.append("bold ");

        // 检查缩进
        int indentLeft = para.getIndentationLeft();
        if (indentLeft > 500) classes.append("indent-list ");
        else if (indentLeft <= 0 && alignment != ParagraphAlignment.CENTER) classes.append("no-indent ");

        if (alignment == ParagraphAlignment.CENTER) classes.append("center ");
        if (alignment == ParagraphAlignment.RIGHT) classes.append("right ");

        return classes.toString().trim();
    }

    private static String getInlineStyle(XWPFParagraph para, ParagraphAlignment alignment) {
        StringBuilder style = new StringBuilder();
        int indentLeft = para.getIndentationLeft();
        // 超过常规缩进的，加 margin-left
        if (indentLeft > 800) {
            int marginPt = indentLeft / 20; // twips → pt (approx)
            style.append("margin-left:").append(marginPt).append("pt;");
        }
        return style.toString();
    }

    private static String convertTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <table>\n");

        List<XWPFTableRow> rows = table.getRows();
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            sb.append("        <tr>\n");
            String cellTag = (i == 0) ? "th" : "td";

            for (XWPFTableCell cell : row.getTableCells()) {
                sb.append("            <").append(cellTag).append(">");
                StringBuilder cellContent = new StringBuilder();
                for (XWPFParagraph p : cell.getParagraphs()) {
                    String text = p.getText().trim();
                    if (!text.isEmpty()) {
                        if (!cellContent.isEmpty()) cellContent.append("<br/>");
                        cellContent.append(buildRunsHtml(p));
                    }
                }
                sb.append(cellContent);
                sb.append("</").append(cellTag).append(">\n");
            }
            sb.append("        </tr>\n");
        }
        sb.append("    </table>\n");
        return sb.toString();
    }
}
