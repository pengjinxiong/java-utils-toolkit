package com.toolkit.converter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTML 预览与编辑对话框
 * 左侧源码编辑，右侧实时预览，确认后保存到指定文件
 */
public class HtmlPreviewDialog extends JDialog {

    private final JTextArea sourceArea;
    private final JEditorPane previewPane;
    private boolean saved = false;
    private final File outputFile;
    private String htmlSnapshot; // 用于撤销替换

    private HtmlPreviewDialog(JFrame parent, String initialHtml, File outputFile) {
        super(parent, "HTML 预览与编辑 — " + outputFile.getName(), true);
        this.outputFile = outputFile;
        setSize(1100, 700);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ========== 顶部工具栏 ==========
        JPanel toolBar = new JPanel(new BorderLayout(10, 5));
        toolBar.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel hint = new JLabel(
                "提示：右侧预览效果仅供参考，实际 PDF 渲染以 Flying Saucer / OpenHTMLToPDF 为准");
        hint.setFont(new Font("PingFang SC", Font.PLAIN, 12));
        hint.setForeground(Color.DARK_GRAY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnReplace = new JButton("变量替换");
        JButton btnUndo = new JButton("撤销替换");
        JButton btnRefresh = new JButton("刷新预览");
        JButton btnSave = new JButton("确认保存");
        JButton btnCancel = new JButton("取消");

        btnSave.setBackground(new Color(50, 120, 200));
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);
        btnSave.setPreferredSize(new Dimension(100, 30));

        btnRefresh.setPreferredSize(new Dimension(100, 30));
        btnCancel.setPreferredSize(new Dimension(80, 30));
        btnUndo.setPreferredSize(new Dimension(100, 30));
        btnUndo.setEnabled(false);

        btnPanel.add(btnReplace);
        btnPanel.add(btnUndo);
        btnPanel.add(btnRefresh);
        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);

        toolBar.add(hint, BorderLayout.WEST);
        toolBar.add(btnPanel, BorderLayout.EAST);

        // ========== 左侧源码编辑器 ==========
        sourceArea = new JTextArea(initialHtml, 20, 50);
        sourceArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        sourceArea.setLineWrap(true);
        sourceArea.setWrapStyleWord(true);
        sourceArea.setTabSize(2);
        JScrollPane sourceScroll = new JScrollPane(sourceArea);
        sourceScroll.setBorder(BorderFactory.createTitledBorder("HTML 源码（可手动编辑）"));

        // ========== 右侧预览面板 ==========
        previewPane = new JEditorPane();
        previewPane.setContentType("text/html; charset=UTF-8");
        previewPane.setEditable(false);
        previewPane.setText(simplifyForPreview(initialHtml));
        previewPane.setCaretPosition(0);
        JScrollPane previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createTitledBorder("实时预览"));

        // ========== 分割面板 ==========
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceScroll, previewScroll);
        split.setResizeWeight(0.5);
        split.setDividerLocation(520);

        // ========== 布局 ==========
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // ========== 事件绑定 ==========
        btnReplace.addActionListener(e -> {
            htmlSnapshot = sourceArea.getText(); // 保存替换前的快照
            boolean applied = ParamReplaceDialog.showDialog(this, sourceArea);
            if (applied) {
                btnUndo.setEnabled(true);
                refreshPreview();
            }
        });

        btnUndo.addActionListener(e -> {
            if (htmlSnapshot != null) {
                sourceArea.setText(htmlSnapshot);
                sourceArea.setCaretPosition(0);
                btnUndo.setEnabled(false);
                refreshPreview();
            }
        });

        btnRefresh.addActionListener(e -> refreshPreview());

        btnSave.addActionListener(e -> {
            try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
                String content = sourceArea.getText();
                // 保存前清理未被替换的 param-placeholder 标红包装，还原原本颜色
                content = content.replaceAll("<span class=\"param-placeholder\">(.*?)</span>", "$1");
                writer.write(content);
                saved = true;
                dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "保存失败：\n" + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCancel.addActionListener(e -> dispose());

        // 快捷键：Ctrl/Cmd + S 保存
        KeyStroke saveKey = KeyStroke.getKeyStroke("ctrl S");
        sourceArea.getInputMap().put(saveKey, "save");
        sourceArea.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                btnSave.doClick();
            }
        });
    }

    private void refreshPreview() {
        String html = simplifyForPreview(sourceArea.getText());
        previewPane.setText(html);
        previewPane.setCaretPosition(0);
    }

    private static String simplifyForPreview(String html) {
        String result = html
            .replace("<!DOCTYPE html>", "")
            .replaceAll("<html[^>]*>", "<html>")
            .replaceAll("(?s)<head>.*?</head>",
                "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" +
                "<style>" +
                "body{font-family:SimSun;font-size:12pt;margin:10px;color:black;}" +
                "h1,h2{text-align:center;font-weight:bold;margin:10px 0;}" +
                "p{margin:5px 0;}" +
                ".param-placeholder{color:red;text-decoration:underline;}" +
                "table{border-collapse:collapse;width:100%;}" +
                "td,th{border:1px solid #000;padding:5px;}" +
                "</style></head>")
            .replace("<br/>", "<br>")
            .replaceAll("\\$\\{param\\d+ ! \"\"\\}", "__________")
            .trim();
        return result;
    }

    /**
     * 显示预览对话框，阻塞直到用户关闭
     *
     * @return true 表示用户点击了确认保存
     */
    public static boolean showPreview(JFrame parent, String html, File outputFile) {
        HtmlPreviewDialog dialog = new HtmlPreviewDialog(parent, html, outputFile);
        dialog.setVisible(true);
        return dialog.saved;
    }
}
