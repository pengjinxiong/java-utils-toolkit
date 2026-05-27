package com.toolkit.converter;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.usermodel.*;
import java.io.FileInputStream;

public class DiagDoc {
    public static void main(String[] args) throws Exception {
        java.io.File dir = new java.io.File("/Users/mac/java/java-utils-toolkit/src/main/resources");
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".doc"));
        String path = files != null && files.length > 0 ? files[0].getAbsolutePath() : "";
        try (FileInputStream fis = new FileInputStream(path);
             HWPFDocument doc = new HWPFDocument(fis)) {
            Range range = doc.getRange();
            int n = range.numParagraphs();
            for (int i = 0; i < Math.min(n, 30); i++) {
                Paragraph p = range.getParagraph(i);
                String text = p.text().trim().replaceAll("[\\x07\\x0c\\x0d\\x0a]+$", "");
                if (text.length() > 60) text = text.substring(0, 60);
                if (text.isEmpty()) continue;
                CharacterRun r0 = p.getCharacterRun(0);
                StyleDescription sd = doc.getStyleSheet().getStyleDescription(p.getStyleIndex());
                String styleName = sd != null ? sd.getName() : "null";
                System.out.printf("Para[%2d] style=%2d(%s) bold=%b fs=%d text=%s%n",
                    i, p.getStyleIndex(), styleName, r0.isBold(), r0.getFontSize()/2, text);
            }
        }
    }
}
