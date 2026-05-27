package com.toolkit.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 批量合并工具
 * 功能：
 *  1. 支持多文件夹同时处理
 *  2. 支持拖拽文件夹到列表
 *  3. 输出目录可配置
 *  4. 表格展示处理结果
 *  5. 支持选择父目录扫描子文件夹
 */
public class PdfMergerTool {

    private static final Logger LOGGER = Logger.getLogger(PdfMergerTool.class.getName());

    // 表格列定义
    private static final String[] COLUMNS = {"文件夹路径", "文件数", "状态", "输出路径"};
    private static final int COL_PATH = 0;
    private static final int COL_COUNT = 1;
    private static final int COL_STATUS = 2;
    private static final int COL_OUTPUT = 3;

    // 状态常量
    private static final String STATUS_PENDING = "等待";
    private static final String STATUS_RUNNING = "处理中...";
    private static final String STATUS_SUCCESS = "成功";
    private static final String STATUS_FAILED = "失败";
    private static final String STATUS_SKIPPED = "跳过（空）";

    private JFrame frame;
    private DefaultTableModel tableModel;
    private JTextField outputDirField;
    private JCheckBox chkOutputToSelf;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton btnMerge;

    private final AtomicBoolean merging = new AtomicBoolean(false);

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new PdfMergerTool().createAndShowGUI(null));
    }

    /** 从 Launcher 启动 */
    public static void launch(Runnable onBack) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new PdfMergerTool().createAndShowGUI(onBack));
    }

    private void createAndShowGUI(Runnable onBack) {
        frame = new JFrame("PDF 批量合并工具 v2.0");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(950, 650);
        frame.setMinimumSize(new Dimension(750, 500));
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
        // 数据模型
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
            @Override
            public Class<?> getColumnClass(int col) {
                return col == COL_COUNT ? Integer.class : String.class;
            }
        };

        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(COL_PATH).setPreferredWidth(300);
        table.getColumnModel().getColumn(COL_COUNT).setPreferredWidth(60);
        table.getColumnModel().getColumn(COL_STATUS).setPreferredWidth(120);
        table.getColumnModel().getColumn(COL_OUTPUT).setPreferredWidth(320);

        // 状态列着色
        table.getColumnModel().getColumn(COL_STATUS).setCellRenderer(new StatusCellRenderer());

        // 拖拽支持
        setupDragAndDrop(table);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "待合并文件夹列表（拖拽或按钮添加）",
                TitledBorder.LEFT, TitledBorder.TOP));

        // 顶部按钒区
        JPanel topPanel = createTopPanel(onBack);

        // 底部面板
        JPanel bottomPanel = createBottomPanel();

        // 主布局
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(tableScroll, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);

        // 初始状态
        refreshMergeButton();
    }

    private void setupDragAndDrop(JTable table) {
        new DropTarget(table, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> dropped = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : dropped) {
                        if (f.isDirectory()) {
                            addFolderToTable(f);
                        }
                    }
                    dtde.dropComplete(true);
                    refreshMergeButton();
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                    LOGGER.log(Level.WARNING, "拖拽失败: {0}", ex.getMessage());
                }
            }
        }, true);
    }

    private JPanel createTopPanel(Runnable onBack) {
        JButton btnAddFolder = new JButton("📁 添加文件夹");
        JButton btnAddParent = new JButton("📂 添加父目录（扫描子文件夹）");
        JButton btnRemove = new JButton("❌ 移除选中");
        JButton btnClear = new JButton("🗑️ 清空列表");

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.add(btnAddFolder);
        panel.add(btnAddParent);
        panel.add(btnRemove);
        panel.add(btnClear);
        // 返回主界面按钒（靠右）
        if (onBack != null) {
            JPanel wrapPanel = new JPanel(new BorderLayout());
            wrapPanel.add(panel, BorderLayout.WEST);
            JButton btnBack = new JButton("← 返回主界面");
            btnBack.addActionListener(e -> frame.dispose());
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
            rightPanel.add(btnBack);
            wrapPanel.add(rightPanel, BorderLayout.EAST);
            btnAddFolder.addActionListener(e -> addFoldersByDialog());
            btnAddParent.addActionListener(e -> addParentFolder());
            btnRemove.addActionListener(e -> removeSelectedRows());
            btnClear.addActionListener(e -> clearAllRows());
            return wrapPanel;
        }

        // 按钮事件
        btnAddFolder.addActionListener(e -> addFoldersByDialog());
        btnAddParent.addActionListener(e -> addParentFolder());
        btnRemove.addActionListener(e -> removeSelectedRows());
        btnClear.addActionListener(e -> clearAllRows());

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 输出目录行
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("输出目录："), gbc);

        outputDirField = new JTextField(35);
        outputDirField.setEditable(false);
        outputDirField.setToolTipText("PDF 输出目录（默认与第一个输入文件夹同级）");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(outputDirField, gbc);

        JButton btnOutputDir = new JButton("选择目录");
        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(btnOutputDir, gbc);

        // 输出到各自原文件夹选项
        chkOutputToSelf = new JCheckBox("输出到各自原文件夹内（忽略上方目录）");
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        panel.add(chkOutputToSelf, gbc);
        gbc.gridwidth = 1;

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("等待开始");
        progressBar.setPreferredSize(new Dimension(400, 25));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        panel.add(progressBar, gbc);
        gbc.gridwidth = 1;

        // 状态标签
        statusLabel = new JLabel("请添加文件夹并选择输出目录", SwingConstants.LEFT);
        statusLabel.setForeground(Color.GRAY);
        gbc.gridy = 3;
        panel.add(statusLabel, gbc);

        // 开始按钮
        btnMerge = new JButton("🚀 开始批量合并");
        btnMerge.setEnabled(false);
        btnMerge.setFont(btnMerge.getFont().deriveFont(Font.BOLD, 14f));
        btnMerge.setBackground(new Color(50, 150, 50));
        btnMerge.setForeground(Color.WHITE);
        btnMerge.setFocusPainted(false);
        btnMerge.setPreferredSize(new Dimension(160, 35));
        gbc.gridy = 4;
        panel.add(btnMerge, gbc);

        // 按钮事件
        btnOutputDir.addActionListener(e -> selectOutputDirectory());
        chkOutputToSelf.addActionListener(e -> {
            outputDirField.setEnabled(!chkOutputToSelf.isSelected());
            btnOutputDir.setEnabled(!chkOutputToSelf.isSelected());
            refreshMergeButton();
        });
        btnMerge.addActionListener(e -> startMerge());

        return panel;
    }

    private void addFolderToTable(File folder) {
        String path = folder.getAbsolutePath();
        // 去重检查
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (path.equals(tableModel.getValueAt(i, COL_PATH))) {
                return;
            }
        }
        List<File> files = getSortedFiles(folder);
        tableModel.addRow(new Object[]{path, files.size(), STATUS_PENDING, ""});
    }

    private void addFoldersByDialog() {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("选择一个或多个文件夹（可多选）");

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                addFolderToTable(f);
            }
            autoFillOutputDir(chooser.getSelectedFiles());
            refreshMergeButton();
        }
    }

    private void addParentFolder() {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择父目录（将自动扫描其下所有子文件夹）");

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File parent = chooser.getSelectedFile();
            File[] subs = parent.listFiles(File::isDirectory);

            if (subs == null || subs.length == 0) {
                JOptionPane.showMessageDialog(frame,
                        "所选目录下没有子文件夹！", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            Arrays.sort(subs, Comparator.comparing(File::getName));
            for (File sub : subs) {
                addFolderToTable(sub);
            }

            if (subs.length > 0) {
                autoFillOutputDir(new File[]{subs[0]});
            }
            refreshMergeButton();

            JOptionPane.showMessageDialog(frame,
                    "已添加 " + subs.length + " 个子文件夹", "完成", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void removeSelectedRows() {
        JTable table = findTable();
        if (table != null) {
            int[] selectedRows = table.getSelectedRows();
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                tableModel.removeRow(selectedRows[i]);
            }
        }
        refreshMergeButton();
    }

    private JTable findTable() {
        if (frame == null) return null;
        JPanel mainPanel = (JPanel) frame.getContentPane().getComponent(0);
        JScrollPane scrollPane = (JScrollPane) mainPanel.getComponent(1);
        return (JTable) scrollPane.getViewport().getView();
    }

    private void clearAllRows() {
        tableModel.setRowCount(0);
        refreshMergeButton();
    }

    private void selectOutputDirectory() {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择 PDF 统一输出目录");

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            refreshMergeButton();
        }
    }

    private void autoFillOutputDir(File[] selectedFiles) {
        if (outputDirField.getText().isEmpty() && selectedFiles.length > 0) {
            File parent = selectedFiles[0].getParentFile();
            if (parent != null) {
                outputDirField.setText(parent.getAbsolutePath());
            }
        }
    }

    private void refreshMergeButton() {
        boolean hasRows = tableModel.getRowCount() > 0;
        boolean hasOutput = chkOutputToSelf.isSelected() || !outputDirField.getText().isEmpty();
        btnMerge.setEnabled(hasRows && hasOutput && !merging.get());
    }

    private void startMerge() {
        if (merging.get()) return;
        merging.set(true);

        boolean toSelf = chkOutputToSelf.isSelected();
        String globalOut = outputDirField.getText();

        // 检查输出目录（如果不是输出到各自文件夹）
        if (!toSelf) {
            File outDir = new File(globalOut);
            if (!outDir.exists()) {
                if (!outDir.mkdirs()) {
                    JOptionPane.showMessageDialog(frame,
                            "无法创建输出目录：" + globalOut, "错误", JOptionPane.ERROR_MESSAGE);
                    merging.set(false);
                    return;
                }
            }
            if (!outDir.isDirectory() || !outDir.canWrite()) {
                JOptionPane.showMessageDialog(frame,
                        "输出目录无效或不可写：" + globalOut, "错误", JOptionPane.ERROR_MESSAGE);
                merging.set(false);
                return;
            }
        }

        // 收集当前所有行数据
        int total = tableModel.getRowCount();
        String[] folderPaths = new String[total];
        for (int i = 0; i < total; i++) {
            folderPaths[i] = (String) tableModel.getValueAt(i, COL_PATH);
        }

        // 重置状态
        for (int i = 0; i < total; i++) {
            tableModel.setValueAt(STATUS_PENDING, i, COL_STATUS);
            tableModel.setValueAt("", i, COL_OUTPUT);
        }

        setControlsEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("开始中...");

        new Thread(() -> {
            int success = 0, fail = 0, skipped = 0;

            for (int i = 0; i < total; i++) {
                final int idx = i;
                String folderPath = folderPaths[i];
                File folder = new File(folderPath);
                String folderName = folder.getName();

                // 计算输出路径
                String outputPdf;
                if (toSelf) {
                    outputPdf = folderPath + File.separator + folderName + ".pdf";
                } else {
                    outputPdf = globalOut + File.separator + sanitizeFileName(folderName) + ".pdf";
                }

                // 计算文件数量
                List<File> files = getSortedFiles(folder);
                final int fileCount = files.size();

                SwingUtilities.invokeLater(() -> {
                    tableModel.setValueAt(fileCount, idx, COL_COUNT);
                    tableModel.setValueAt(STATUS_RUNNING, idx, COL_STATUS);
                    statusLabel.setText("处理 (" + (idx + 1) + "/" + total + ")：" + folderName);
                    progressBar.setString("处理中 " + (idx + 1) + "/" + total);
                });

                if (fileCount == 0) {
                    skipped++;
                    final String op = outputPdf;
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setValueAt(STATUS_SKIPPED, idx, COL_STATUS);
                        tableModel.setValueAt(op, idx, COL_OUTPUT);
                    });
                } else {
                    final String op = outputPdf;
                    try {
                        mergeFilesToPdf(folderPath, outputPdf);
                        success++;
                        SwingUtilities.invokeLater(() -> {
                            tableModel.setValueAt(STATUS_SUCCESS, idx, COL_STATUS);
                            tableModel.setValueAt(op, idx, COL_OUTPUT);
                        });
                    } catch (Exception ex) {
                        fail++;
                        String errMsg = ex.getMessage();
                        LOGGER.log(Level.SEVERE, "合并失败: " + folderPath, ex);
                        SwingUtilities.invokeLater(() -> {
                            tableModel.setValueAt(STATUS_FAILED + ": " + truncateError(errMsg), idx, COL_STATUS);
                            tableModel.setValueAt(op, idx, COL_OUTPUT);
                        });
                    }
                }

                final int prog = (int) ((i + 1) * 100.0 / total);
                SwingUtilities.invokeLater(() -> progressBar.setValue(prog));
            }

            final int fs = success, ff = fail, fsk = skipped;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(100);
                setControlsEnabled(true);
                refreshMergeButton();

                if (ff == 0) {
                    progressBar.setString("全部完成");
                    statusLabel.setForeground(new Color(0, 140, 0));
                    statusLabel.setText("完成！成功 " + fs + " 个" + (fsk > 0 ? "，跳过 " + fsk + " 个" : ""));
                    JOptionPane.showMessageDialog(frame,
                            "批量合并完成！\n成功：" + fs + " 个" + (fsk > 0 ? "\n跳过（空）：" + fsk + " 个" : ""),
                            "完成", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    progressBar.setString("完成（含失败）");
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("完成：成功 " + fs + " 个，失败 " + ff + " 个" + (fsk > 0 ? "，跳过 " + fsk + " 个" : ""));
                    JOptionPane.showMessageDialog(frame,
                            "批量合并完成\n成功：" + fs + " 个，失败：" + ff + " 个" + (fsk > 0 ? "\n跳过（空）：" + fsk + " 个" : "") +
                                    "\n请查看列表中红色行了解失败详情。",
                            "部分失败", JOptionPane.WARNING_MESSAGE);
                }
                merging.set(false);
            });
        }).start();
    }

    private void setControlsEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            btnMerge.setEnabled(enabled && !merging.get());
        });
    }

    private String sanitizeFileName(String name) {
        // 移除Windows文件名中不允许的字符
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String truncateError(String error) {
        if (error == null) return "未知错误";
        return error.length() > 50 ? error.substring(0, 47) + "..." : error;
    }

    // ==================== PDF处理核心方法 ====================

    /**
     * 将文件夹中的图片和PDF按数字顺序合并成一个PDF
     */
    public static void mergeFilesToPdf(String sourceFolderPath, String outputPdfPath) throws IOException {
        File folder = new File(sourceFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("无效的文件夹路径：" + sourceFolderPath);
        }

        List<File> files = getSortedFiles(folder);
        if (files.isEmpty()) {
            throw new IllegalStateException("文件夹中没有找到任何 jpg/png/pdf 文件");
        }

        List<PDDocument> documents = new ArrayList<>();
        try {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                PDDocument doc;
                if (name.endsWith(".pdf")) {
                    doc = Loader.loadPDF(file);
                    documents.add(doc);
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    doc = new PDDocument();
                    addImageToPdf(doc, file);
                    documents.add(doc);
                }
            }
            mergePdfDocuments(documents, outputPdfPath);
        } finally {
            for (PDDocument doc : documents) {
                try {
                    doc.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 获取按文件名数字排序的文件列表
     */
    public static List<File> getSortedFiles(File folder) {
        File[] files = folder.listFiles((dir, name) -> {
            String lw = name.toLowerCase();
            return lw.endsWith(".jpg") || lw.endsWith(".jpeg") ||
                    lw.endsWith(".png") || lw.endsWith(".pdf");
        });

        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort((f1, f2) -> {
            int num1 = extractNumber(f1.getName());
            int num2 = extractNumber(f2.getName());
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
            return f1.getName().compareTo(f2.getName());
        });
        return list;
    }

    private static int extractNumber(String fileName) {
        Matcher m = Pattern.compile("(\\d+)").matcher(fileName);
        int last = 0;
        while (m.find()) {
            try {
                last = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return last;
    }

    private static void addImageToPdf(PDDocument document, File imageFile) throws IOException {
        BufferedImage img = ImageIO.read(imageFile);
        if (img == null) {
            throw new IOException("无法读取图片：" + imageFile.getName());
        }

        // 保持图片原始尺寸，300dpi转换
        float pw = img.getWidth() * 72f / 96f;
        float ph = img.getHeight() * 72f / 96f;
        PDPage page = new PDPage(new PDRectangle(pw, ph));
        document.addPage(page);

        PDImageXObject pdImage = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            cs.drawImage(pdImage, 0, 0, pw, ph);
        }
    }

    private static void mergePdfDocuments(List<PDDocument> documents, String outputPath) throws IOException {
        File outFile = new File(outputPath);
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        try (PDDocument merged = new PDDocument()) {
            for (PDDocument doc : documents) {
                merger.appendDocument(merged, doc);
            }
            merged.save(outputPath);
        }
    }

    // 自定义渲染器
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String v = value == null ? "" : value.toString();

            if (!isSelected) {
                if (v.startsWith(STATUS_SUCCESS)) {
                    setForeground(new Color(0, 140, 0));
                } else if (v.startsWith(STATUS_FAILED)) {
                    setForeground(Color.RED);
                } else if (v.equals(STATUS_RUNNING)) {
                    setForeground(new Color(0, 80, 200));
                } else if (v.equals(STATUS_SKIPPED)) {
                    setForeground(new Color(255, 140, 0));
                } else {
                    setForeground(Color.GRAY);
                }
            } else {
                setForeground(table.getSelectionForeground());
            }
            return this;
        }
    }
}