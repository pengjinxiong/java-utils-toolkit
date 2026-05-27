package com.toolkit.converter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档转 HTML 工具
 * 将 doc/docx/pdf 文件转换为可被 Java（Flying Saucer/OpenHTMLToPDF）渲染成 PDF 的 HTML 文件
 */
public class DocToHtmlTool {

    private static final Logger LOGGER = Logger.getLogger(DocToHtmlTool.class.getName());

    private JFrame frame;
    private DefaultListModel<File> fileListModel;
    private JTextField outputDirField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton btnConvert;
    private JButton btnPreview;
    private final AtomicBoolean converting = new AtomicBoolean(false);
    private JTextArea replaceRulesArea;
    private JCheckBox chkAutoUnderline;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new DocToHtmlTool().createAndShowGUI(null));
    }

    /** 从 Launcher 启动 */
    public static void launch(Runnable onBack) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new DocToHtmlTool().createAndShowGUI(onBack));
    }

    private void createAndShowGUI(Runnable onBack) {
        frame = new JFrame("文档转 HTML 工具 v1.0");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(850, 550);
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setLocationRelativeTo(null);
        if (onBack != null) {
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) { onBack.run(); }
            });
        }
        initComponents(onBack);
        frame.setVisible(true);
    }

    private void initComponents(Runnable onBack) {
        fileListModel = new DefaultListModel<>();
        JList<File> fileList = new JList<>(fileListModel);
        fileList.setCellRenderer(new FileListRenderer());
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setFixedCellHeight(28);

        // 拖拽支持
        new DropTarget(fileList, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> dropped = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : dropped) {
                        addFileIfValid(f);
                    }
                    dtde.dropComplete(true);
                    refreshConvertButton();
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);

        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "待转换文件列表（支持 doc/docx/pdf，可拖拽添加）",
                TitledBorder.LEFT, TitledBorder.TOP));

        // 顶部按钒
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton btnAdd = new JButton("📄 添加文件");
        JButton btnRemove = new JButton("❌ 移除选中");
        JButton btnClear = new JButton("🗑️ 清空");
        topLeft.add(btnAdd);
        topLeft.add(btnRemove);
        topLeft.add(btnClear);
        topPanel.add(topLeft, BorderLayout.WEST);
        if (onBack != null) {
            JButton btnBack = new JButton("← 返回主界面");
            btnBack.addActionListener(e -> frame.dispose());
            JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
            topRight.add(btnBack);
            topPanel.add(topRight, BorderLayout.EAST);
        }

        btnAdd.addActionListener(e -> addFilesByDialog());
        btnRemove.addActionListener(e -> {
            int[] sel = fileList.getSelectedIndices();
            for (int i = sel.length - 1; i >= 0; i--) fileListModel.remove(sel[i]);
            refreshConvertButton();
        });
        btnClear.addActionListener(e -> { fileListModel.clear(); refreshConvertButton(); });

        // 底部面板
        JPanel bottomPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        bottomPanel.add(new JLabel("输出目录："), gbc);

        outputDirField = new JTextField(30);
        outputDirField.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1.0;
        bottomPanel.add(outputDirField, gbc);

        JButton btnOutDir = new JButton("选择目录");
        gbc.gridx = 2; gbc.weightx = 0;
        bottomPanel.add(btnOutDir, gbc);
        btnOutDir.addActionListener(e -> selectOutputDir());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("等待开始");
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        bottomPanel.add(progressBar, gbc);

        statusLabel = new JLabel("请添加文件并选择输出目录", SwingConstants.LEFT);
        statusLabel.setForeground(Color.GRAY);
        gbc.gridy = 2;
        bottomPanel.add(statusLabel, gbc);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));

        btnPreview = new JButton("预览编辑");
        btnPreview.setEnabled(false);
        btnPreview.setFont(btnPreview.getFont().deriveFont(Font.BOLD, 14f));
        btnPreview.setFocusPainted(false);
        btnPreview.setPreferredSize(new Dimension(130, 35));
        btnPreview.addActionListener(e -> startPreview());

        btnConvert = new JButton("🚀 开始转换");
        btnConvert.setEnabled(false);
        btnConvert.setFont(btnConvert.getFont().deriveFont(Font.BOLD, 14f));
        btnConvert.setBackground(new Color(50, 120, 200));
        btnConvert.setForeground(Color.WHITE);
        btnConvert.setFocusPainted(false);
        btnConvert.setPreferredSize(new Dimension(160, 35));
        btnConvert.addActionListener(e -> startConvert());

        actionPanel.add(btnPreview);
        actionPanel.add(btnConvert);
        gbc.gridy = 3;
        bottomPanel.add(actionPanel, gbc);

        // 文件列表面板（占上中区域）
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // 模板变量替换面板
        JPanel replacePanel = createReplacePanel();
        centerPanel.add(replacePanel, BorderLayout.SOUTH);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        frame.add(mainPanel);
    }

    // ==================== 模板变量替换面板 ====================

    private JPanel createReplacePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "模板变量替换（可选）",
                TitledBorder.LEFT, TitledBorder.TOP));

        replaceRulesArea = new JTextArea(5, 40);
        replaceRulesArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        replaceRulesArea.setLineWrap(true);
        replaceRulesArea.setWrapStyleWord(true);
        replaceRulesArea.setText(
                "# 每行一条规则，格式：原文本=${变量名}\n" +
                "# 示例：\n" +
                "# 甲方（客户方）：张三=${realName ! \"\"}\n" +
                "# 丙方（服务方）：李四=${platformName ! \"\"}\n");
        JScrollPane scroll = new JScrollPane(replaceRulesArea);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        chkAutoUnderline = new JCheckBox("自动将 _____ 下划线替换为 ${paramN ! \"\"}");
        chkAutoUnderline.setSelected(true);
        JButton btnHelp = new JButton("帮助");
        btnHelp.addActionListener(e -> showReplaceHelp());
        btnPanel.add(chkAutoUnderline);
        btnPanel.add(btnHelp);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void showReplaceHelp() {
        JOptionPane.showMessageDialog(frame,
                "模板变量替换规则说明：\n\n" +
                "1. 手动规则：每行一条，格式为 原文本=${变量名}\n" +
                "   例：甲方：张三=${realName ! \"\"}\n\n" +
                "2. 自动下划线：勾选后，所有连续的 _____ 下划线\n" +
                "   会自动替换为 ${param1 ! \"\"}、${param2 ! \"\"}...\n\n" +
                "3. 手动规则优先级高于自动下划线。",
                "替换规则帮助", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addFileIfValid(File f) {
        if (!f.isFile()) return;
        String name = f.getName().toLowerCase();
        if (!(name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".pdf"))) return;
        // 去重
        for (int i = 0; i < fileListModel.size(); i++) {
            if (fileListModel.get(i).getAbsolutePath().equals(f.getAbsolutePath())) return;
        }
        fileListModel.addElement(f);
    }

    private void addFilesByDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("文档文件 (doc, docx, pdf)", "doc", "docx", "pdf"));
        chooser.setDialogTitle("选择要转换的文档");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) addFileIfValid(f);
            // 自动填充输出目录
            if (outputDirField.getText().isEmpty() && chooser.getSelectedFiles().length > 0) {
                File parent = chooser.getSelectedFiles()[0].getParentFile();
                if (parent != null) outputDirField.setText(parent.getAbsolutePath());
            }
            refreshConvertButton();
        }
    }

    private void selectOutputDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择 HTML 输出目录");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            refreshConvertButton();
        }
    }

    private void refreshConvertButton() {
        boolean canAction = fileListModel.size() > 0
                && !outputDirField.getText().isEmpty()
                && !converting.get();
        btnConvert.setEnabled(canAction);
        if (btnPreview != null) {
            btnPreview.setEnabled(canAction);
        }
    }

    private void startConvert() {
        if (converting.get()) return;
        converting.set(true);
        btnConvert.setEnabled(false);

        String outDir = outputDirField.getText();
        int total = fileListModel.size();
        File[] files = new File[total];
        for (int i = 0; i < total; i++) files[i] = fileListModel.get(i);

        progressBar.setValue(0);
        progressBar.setString("开始转换...");

        new Thread(() -> {
            int success = 0, fail = 0;
            for (int i = 0; i < total; i++) {
                File file = files[i];
                String baseName = file.getName().replaceAll("\\.[^.]+$", "");
                File outFile = new File(outDir, baseName + ".html");

                final int idx = i;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("转换 (" + (idx + 1) + "/" + total + ")：" + file.getName());
                    statusLabel.setForeground(new Color(0, 80, 200));
                });

                try {
                    String html = convertToHtml(file);
                    try (FileWriter writer = new FileWriter(outFile, StandardCharsets.UTF_8)) {
                        writer.write(html);
                    }
                    success++;
                } catch (Exception ex) {
                    fail++;
                    LOGGER.log(Level.SEVERE, "转换失败: " + file.getName(), ex);
                }

                final int prog = (int) ((i + 1) * 100.0 / total);
                SwingUtilities.invokeLater(() -> progressBar.setValue(prog));
            }

            final int fs = success, ff = fail;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(100);
                converting.set(false);
                refreshConvertButton();

                if (ff == 0) {
                    progressBar.setString("全部完成");
                    statusLabel.setForeground(new Color(0, 140, 0));
                    statusLabel.setText("完成！成功转换 " + fs + " 个文件");
                    JOptionPane.showMessageDialog(frame,
                            "转换完成！\n成功：" + fs + " 个\n输出目录：" + outDir,
                            "完成", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    progressBar.setString("完成（含失败）");
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("完成：成功 " + fs + " 个，失败 " + ff + " 个");
                    JOptionPane.showMessageDialog(frame,
                            "转换完成\n成功：" + fs + " 个，失败：" + ff + " 个",
                            "部分失败", JOptionPane.WARNING_MESSAGE);
                }
            });
        }).start();
    }

    private void startPreview() {
        if (fileListModel.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先添加要转换的文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (outputDirField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请选择输出目录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File file = fileListModel.get(0);
        String baseName = file.getName().replaceAll("\\.[^.]+$", "");
        File outFile = new File(outputDirField.getText(), baseName + ".html");

        try {
            String html = convertToHtml(file);
            boolean saved = HtmlPreviewDialog.showPreview(frame, html, outFile);
            if (saved) {
                statusLabel.setText("已保存：" + outFile.getAbsolutePath());
                statusLabel.setForeground(new Color(0, 140, 0));
                progressBar.setString("已保存 1 个文件");
                progressBar.setValue(100);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "预览失败: " + file.getName(), ex);
            JOptionPane.showMessageDialog(frame,
                    "预览失败：\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== 核心转换逻辑 ====================

    private String convertToHtml(File file) throws IOException {
        String name = file.getName().toLowerCase();
        String html;
        if (name.endsWith(".docx")) {
            html = DocxConverter.convert(file);
        } else if (name.endsWith(".doc")) {
            html = DocConverter.convert(file);
        } else if (name.endsWith(".pdf")) {
            html = PdfToHtmlConverter.convert(file);
        } else {
            throw new IOException("不支持的文件格式: " + file.getName());
        }
        // 应用模板变量替换
        html = applyReplacements(html);
        return html;
    }

    /**
     * 应用手动规则和自动下划线替换
     */
    private String applyReplacements(String html) {
        // 1. 先应用手动规则
        Map<String, String> rules = parseReplaceRules();
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue());
        }

        // 2. 再应用自动下划线替换（跳过已在手动规则中替换过的）
        if (chkAutoUnderline.isSelected()) {
            html = applyAutoUnderline(html);
        }
        return html;
    }

    /**
     * 解析替换规则文本框内容
     */
    private Map<String, String> parseReplaceRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        String text = replaceRulesArea.getText();
        if (text == null || text.isEmpty()) return rules;

        for (String line : text.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int eqIdx = line.indexOf('=');
            if (eqIdx > 0) {
                String key = line.substring(0, eqIdx).trim();
                String val = line.substring(eqIdx + 1).trim();
                if (!key.isEmpty() && !val.isEmpty()) {
                    rules.put(key, val);
                }
            }
        }
        return rules;
    }

    /**
     * 自动将连续下划线 _____ 替换为 ${param1 ! ""}、${param2 ! ""}...
     * 只替换未被替换过的下划线（即 HTML 中仍然存在的 _____）
     */
    private String applyAutoUnderline(String html) {
        Pattern underlinePattern = Pattern.compile("_{2,}");
        Matcher matcher = underlinePattern.matcher(html);
        StringBuilder result = new StringBuilder();
        int paramIndex = 1;
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(html, lastEnd, matcher.start());
            String replacement = "<span class=\"param-placeholder\">${param" + paramIndex + " ! \"\"}</span>";
            result.append(replacement);
            paramIndex++;
            lastEnd = matcher.end();
        }
        result.append(html.substring(lastEnd));
        return result.toString();
    }

    // ==================== 文件列表渲染器 ====================

    private static class FileListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof File f) {
                String ext = f.getName().substring(f.getName().lastIndexOf('.') + 1).toUpperCase();
                setText("[" + ext + "] " + f.getName() + "  (" + formatSize(f.length()) + ")");
            }
            return this;
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }
}
