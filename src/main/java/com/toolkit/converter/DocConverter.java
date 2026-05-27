package com.toolkit.converter;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * DOC（旧版 Word 97-2003）→ HTML 转换器
 */
public class DocConverter {

    private static final Logger LOGGER = Logger.getLogger(DocConverter.class.getName());

    public static String convert(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument doc = new HWPFDocument(fis)) {

            String title = file.getName().replaceAll("\\.[^.]+$", "");
            StringBuilder body = new StringBuilder();

            Range range = doc.getRange();
            TableIterator tableIter = new TableIterator(range);

            int numParagraphs = range.numParagraphs();
            int paraIndex = 0;

            while (paraIndex < numParagraphs) {
                Paragraph para = range.getParagraph(paraIndex);

                // 检查是否是表格的一部分
                if (tableIter.hasNext()) {
                    Table table = tableIter.next();
                    body.append(convertTable(table));
                    // 跳过表格占用的段落
                    int tableEnd = table.getEndOffset();
                    while (paraIndex < numParagraphs && range.getParagraph(paraIndex).getStartOffset() < tableEnd) {
                        paraIndex++;
                    }
                    continue;
                }

                body.append(convertParagraph(para));
                paraIndex++;
            }

            return HtmlTemplate.wrap(title, body.toString());
        }
    }

    private static String convertParagraph(Paragraph para) {
        String text = para.text().trim();
        // 去掉段落末尾的特殊字符
        text = text.replaceAll("[\\x07\\x0c\\x0d\\x0a]+$", "").trim();
        if (text.isEmpty()) return "";

        int styleIndex = para.getStyleIndex();
        boolean isBold = para.getCharacterRun(0).isBold();
        int fontSize = para.getCharacterRun(0).getFontSize() / 2; // half-points → pt
        int justification = para.getJustification();

        // 推测标题（仅居中的加粗大字视为标题）
        if (justification == 1 && isBold && fontSize >= 18) {
            return buildHeading("h1", justification, text);
        }
        if (justification == 1 && isBold && fontSize >= 14) {
            return buildHeading("h2", justification, text);
        }
        if (justification == 1 && isBold && fontSize >= 13) {
            return buildHeading("h3", justification, text);
        }

        // 构建带格式的段落
        StringBuilder sb = new StringBuilder();
        String cssClass = isBold ? "bold no-indent" : "";
        if (justification == 1) cssClass = (cssClass + " center").trim();
        if (justification == 2) cssClass = (cssClass + " right").trim();

        // 段落级缩进
        int firstLineIndent = para.getFirstLineIndent();
        int indentFromLeft = para.getIndentFromLeft();
        StringBuilder paraStyle = new StringBuilder();
        if (firstLineIndent != 0) {
            paraStyle.append("text-indent:").append(firstLineIndent / 20.0).append("pt;");
        }
        if (indentFromLeft != 0) {
            paraStyle.append("margin-left:").append(indentFromLeft / 20.0).append("pt;");
        }

        sb.append("    <p");
        if (!cssClass.isEmpty()) sb.append(" class=\"").append(cssClass).append("\"");
        if (!paraStyle.isEmpty()) {
            sb.append(" style=\"").append(paraStyle).append("\"");
        }
        sb.append(">");

        // 逐 run 解析格式
        int numRuns = para.numCharacterRuns();
        for (int i = 0; i < numRuns; i++) {
            CharacterRun run = para.getCharacterRun(i);
            String runText = run.text();
            if (runText == null) continue;
            runText = runText.replaceAll("[\\x07\\x0c\\x0d\\x0a]+$", "");
            if (runText.isEmpty()) continue;

            boolean underline = run.getUnderlineCode() != 0;
            // 合并连续的 underline 空白 run（下划线填空区域）
            if (underline && runText.trim().isEmpty()) {
                int totalLen = runText.length();
                int j = i + 1;
                for (; j < numRuns; j++) {
                    CharacterRun nextRun = para.getCharacterRun(j);
                    String nextText = nextRun.text();
                    if (nextText == null) break;
                    nextText = nextText.replaceAll("[\\x07\\x0c\\x0d\\x0a]+$", "");
                    if (nextText.isEmpty()) continue;
                    if (nextRun.getUnderlineCode() != 0 && nextText.trim().isEmpty()) {
                        totalLen += nextText.length();
                    } else {
                        break;
                    }
                }
                String blanks = "_".repeat(Math.max(totalLen, 2));
                sb.append(HtmlTemplate.escapeHtml(blanks));
                i = j - 1;
                continue;
            }

            String escaped = HtmlTemplate.escapeHtml(runText);
            boolean bold = run.isBold();
            boolean italic = run.isItalic();
            int runFontSize = run.getFontSize() / 2;

            if (bold || italic || runFontSize != 12) {
                StringBuilder styles = new StringBuilder();
                if (bold) styles.append("font-weight:bold;");
                if (italic) styles.append("font-style:italic;");
                if (runFontSize != 12) styles.append("font-size:").append(runFontSize).append("pt;");
                sb.append("<span style=\"").append(styles).append("\">").append(escaped).append("</span>");
            } else {
                sb.append(escaped);
            }
        }

        sb.append("</p>\n");
        return sb.toString();
    }

    private static String buildHeading(String tag, int justification, String text) {
        String cls = "";
        if (justification == 1) cls = " class=\"center\"";
        if (justification == 2) cls = " class=\"right\"";
        return "    <" + tag + cls + ">" + HtmlTemplate.escapeHtml(text) + "</" + tag + ">\n";
    }

    private static String convertTable(Table table) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <table>\n");

        for (int r = 0; r < table.numRows(); r++) {
            TableRow row = table.getRow(r);
            sb.append("        <tr>\n");
            String cellTag = (r == 0) ? "th" : "td";

            for (int c = 0; c < row.numCells(); c++) {
                TableCell cell = row.getCell(c);
                sb.append("            <").append(cellTag).append(">");

                StringBuilder cellContent = new StringBuilder();
                for (int p = 0; p < cell.numParagraphs(); p++) {
                    String text = cell.getParagraph(p).text().trim()
                            .replaceAll("[\\x07\\x0c\\x0d\\x0a]+$", "").trim();
                    if (!text.isEmpty()) {
                        if (!cellContent.isEmpty()) cellContent.append("<br/>");
                        cellContent.append(HtmlTemplate.escapeHtml(text));
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
