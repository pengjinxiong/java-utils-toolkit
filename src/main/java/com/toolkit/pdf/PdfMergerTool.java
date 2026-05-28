package com.toolkit.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
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
    private JTable mainTable;
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

    // ========== 颜色常量 ==========
    private static final Color C_GRAD_TOP    = new Color(30, 80, 160);
    private static final Color C_GRAD_BOT    = new Color(20, 50, 120);
    private static final Color C_ACCENT      = new Color(41, 128, 185);
    private static final Color C_ACCENT_HOV  = new Color(52, 152, 219);
    private static final Color C_BTN_BG      = new Color(245, 247, 250);
    private static final Color C_BTN_BORDER  = new Color(210, 215, 225);
    private static final Color C_TABLE_HEAD  = new Color(235, 239, 245);
    private static final Color C_ROW_ALT     = new Color(248, 250, 253);

    private void createAndShowGUI(Runnable onBack) {
        frame = new JFrame("PDF 批量合并工具");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(980, 680);
        frame.setMinimumSize(new Dimension(780, 520));
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
        // ---- 数据模型 ----
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return col == COL_COUNT ? Integer.class : String.class;
            }
        };

        mainTable = new JTable(tableModel);
        mainTable.setRowHeight(28);
        mainTable.setShowGrid(false);
        mainTable.setIntercellSpacing(new Dimension(0, 0));
        mainTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mainTable.setSelectionBackground(new Color(210, 228, 255));
        mainTable.setSelectionForeground(Color.BLACK);
        mainTable.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        // 表头
        mainTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 13));
        mainTable.getTableHeader().setBackground(C_TABLE_HEAD);
        mainTable.getTableHeader().setForeground(new Color(60, 70, 90));
        mainTable.getTableHeader().setReorderingAllowed(false);
        mainTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BTN_BORDER));
        // 列宽
        mainTable.getColumnModel().getColumn(COL_PATH).setPreferredWidth(300);
        mainTable.getColumnModel().getColumn(COL_COUNT).setPreferredWidth(55);
        mainTable.getColumnModel().getColumn(COL_STATUS).setPreferredWidth(130);
        mainTable.getColumnModel().getColumn(COL_OUTPUT).setPreferredWidth(340);
        // 渲染器
        mainTable.getColumnModel().getColumn(COL_STATUS).setCellRenderer(new StatusCellRenderer());
        mainTable.setDefaultRenderer(Object.class, new StripedTableRenderer());
        mainTable.setDefaultRenderer(Integer.class, new StripedTableRenderer());

        setupDragAndDrop(mainTable);

        JScrollPane tableScroll = new JScrollPane(mainTable);
        tableScroll.getViewport().setBackground(Color.WHITE);
        tableScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, C_BTN_BORDER),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        // 表格区域外包一层带标题
        JPanel tableCard = new JPanel(new BorderLayout());
        tableCard.setBackground(Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BTN_BORDER, 1, true),
                new EmptyBorder(0, 0, 0, 0)));
        JLabel tableTitle = new JLabel("  待合并文件夹列表（拖拽文件夹到此处，或使用按钮添加）");
        tableTitle.setFont(new Font("微软雅黑", Font.BOLD, 12));
        tableTitle.setForeground(new Color(80, 90, 110));
        tableTitle.setOpaque(true);
        tableTitle.setBackground(C_TABLE_HEAD);
        tableTitle.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, C_BTN_BORDER),
                new EmptyBorder(6, 4, 6, 4)));
        tableCard.add(tableTitle, BorderLayout.NORTH);
        tableCard.add(tableScroll, BorderLayout.CENTER);

        // 主布局
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBackground(new Color(242, 245, 250));
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        mainPanel.add(createHeaderPanel(onBack), BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new BorderLayout(0, 8));
        centerWrapper.setOpaque(false);
        centerWrapper.setBorder(new EmptyBorder(8, 14, 0, 14));
        centerWrapper.add(createToolbarPanel(), BorderLayout.NORTH);
        centerWrapper.add(tableCard, BorderLayout.CENTER);
        mainPanel.add(centerWrapper, BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        frame.add(mainPanel);
        refreshMergeButton();
    }

    /** 渐变顶部标题栏 */
    private JPanel createHeaderPanel(Runnable onBack) {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, C_GRAD_TOP, 0, getHeight(), C_GRAD_BOT));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 72));
        header.setBorder(new EmptyBorder(0, 18, 0, 14));

        JLabel title = new JLabel("PDF 批量合并工具");
        title.setFont(new Font("微软雅黑", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("支持 JPG / PNG / PDF · 拖拽批量处理 · 自定义输出目录");
        sub.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        sub.setForeground(new Color(180, 210, 255));

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        left.add(title);
        left.add(sub);
        header.add(left, BorderLayout.WEST);

        if (onBack != null) {
            JButton btnBack = makeGhostButton("← 返回主界面");
            btnBack.addActionListener(e -> frame.dispose());
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(btnBack);
            header.add(right, BorderLayout.EAST);
        }
        return header;
    }

    /** 工具栏（添加/移除按钮行） */
    private JPanel createToolbarPanel() {
        JButton btnAddFolder = makeToolButton("＋ 添加文件夹", C_ACCENT, C_ACCENT_HOV);
        JButton btnAddParent = makeToolButton("＋ 添加父目录", C_ACCENT, C_ACCENT_HOV);
        JButton btnRemove    = makeToolButton("－ 移除选中",   new Color(190,50,50), new Color(210,70,70));
        JButton btnClear     = makeToolButton("清空列表",      new Color(130,130,130), new Color(160,160,160));

        btnAddFolder.addActionListener(e -> addFoldersByDialog());
        btnAddParent.addActionListener(e -> addParentFolder());
        btnRemove.addActionListener(e -> removeSelectedRows());
        btnClear.addActionListener(e -> clearAllRows());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(btnAddFolder);
        bar.add(btnAddParent);
        bar.add(btnRemove);
        bar.add(btnClear);
        return bar;
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

    private JPanel createBottomPanel() {
        // 外层卡片
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, C_BTN_BORDER),
                new EmptyBorder(12, 16, 12, 16)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        // 输出目录行
        JLabel lblOut = new JLabel("输出目录：");
        lblOut.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        card.add(lblOut, g);

        outputDirField = new JTextField();
        outputDirField.setEditable(false);
        outputDirField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        outputDirField.setBackground(C_BTN_BG);
        outputDirField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BTN_BORDER),
                new EmptyBorder(3, 6, 3, 6)));
        outputDirField.setToolTipText("PDF 输出目录（默认与第一个输入文件夹同级）");
        g.gridx = 1; g.weightx = 1.0;
        card.add(outputDirField, g);

        JButton btnOutputDir = makeSmallButton("选择目录");
        g.gridx = 2; g.weightx = 0;
        card.add(btnOutputDir, g);

        // 复选框
        chkOutputToSelf = new JCheckBox("输出到各自原文件夹内（忽略上方目录）");
        chkOutputToSelf.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        chkOutputToSelf.setOpaque(false);
        g.gridx = 1; g.gridy = 1; g.gridwidth = 2;
        card.add(chkOutputToSelf, g);
        g.gridwidth = 1;

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("等待开始");
        progressBar.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        progressBar.setForeground(C_ACCENT);
        progressBar.setPreferredSize(new Dimension(0, 22));
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3; g.weightx = 1.0;
        card.add(progressBar, g);
        g.gridwidth = 1;

        // 状态标签
        statusLabel = new JLabel("请添加文件夹并选择输出目录", SwingConstants.LEFT);
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(130, 130, 130));
        g.gridx = 0; g.gridy = 3; g.gridwidth = 2; g.weightx = 1.0;
        card.add(statusLabel, g);

        // 开始按钮
        btnMerge = new JButton("开始批量合并");
        btnMerge.setEnabled(false);
        btnMerge.setFont(new Font("微软雅黑", Font.BOLD, 14));
        btnMerge.setBackground(new Color(39, 174, 96));
        btnMerge.setForeground(Color.WHITE);
        btnMerge.setOpaque(true);
        btnMerge.setFocusPainted(false);
        btnMerge.setBorderPainted(false);
        btnMerge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnMerge.setPreferredSize(new Dimension(150, 34));
        btnMerge.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btnMerge.isEnabled()) btnMerge.setBackground(new Color(46, 204, 113));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btnMerge.isEnabled()) btnMerge.setBackground(new Color(39, 174, 96));
            }
        });
        g.gridx = 2; g.gridy = 3; g.gridwidth = 1; g.weightx = 0;
        card.add(btnMerge, g);

        // 事件
        btnOutputDir.addActionListener(e -> selectOutputDirectory());
        chkOutputToSelf.addActionListener(e -> {
            outputDirField.setEnabled(!chkOutputToSelf.isSelected());
            btnOutputDir.setEnabled(!chkOutputToSelf.isSelected());
            refreshMergeButton();
        });
        btnMerge.addActionListener(e -> startMerge());

        return card;
    }

    /** 工具栏小圆角按钮（带颜色） */
    private JButton makeToolButton(String text, Color bg, Color hover) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? hover : bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 20, 30));
        return btn;
    }

    /** 底部小灰按钮 */
    private JButton makeSmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setBackground(C_BTN_BG);
        btn.setForeground(new Color(60, 70, 90));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BTN_BORDER),
                new EmptyBorder(3, 10, 3, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** 头部透明幽灵按钮 */
    private JButton makeGhostButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setForeground(new Color(180, 210, 255));
        btn.setBackground(new Color(255, 255, 255, 30));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setForeground(Color.WHITE); }
            @Override public void mouseExited(MouseEvent e)  { btn.setForeground(new Color(180, 210, 255)); }
        });
        return btn;
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
        if (mainTable != null) {
            int[] selectedRows = mainTable.getSelectedRows();
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                tableModel.removeRow(selectedRows[i]);
            }
        }
        refreshMergeButton();
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
                } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".png") || name.endsWith(".webp")) {
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
                    lw.endsWith(".png") || lw.endsWith(".pdf") || lw.endsWith(".webp");
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
        PDImageXObject pdImage;
        float pw, ph;

        // 优先让 PDFBox 直接加载
        try {
            pdImage = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
            pw = pdImage.getWidth() * 72f / 96f;
            ph = pdImage.getHeight() * 72f / 96f;
        } catch (Exception e1) {
            // fallback：尝试用 ImageIO 解码
            BufferedImage img = readImageIgnoringIccProfile(imageFile);
            if (img == null) {
                throw new IOException("无法读取图片：" + imageFile.getName());
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            pdImage = PDImageXObject.createFromByteArray(document, baos.toByteArray(), imageFile.getName());
            pw = img.getWidth() * 72f / 96f;
            ph = img.getHeight() * 72f / 96f;
        }

        PDPage page = new PDPage(new PDRectangle(pw, ph));
        document.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            cs.drawImage(pdImage, 0, 0, pw, ph);
        }
    }

    /**
     * 读取图片，支持 WebP(即使后缀是 .png)、PNG 异常 ICC Profile 等各种情况。
     * 针对 Edge/Chrome 截图和微信保存图片的实际格式可能与后缀不符的情况。
     */
    private static BufferedImage readImageIgnoringIccProfile(File file) throws IOException {
        // 检测文件真实格式（读取文件头魔数）
        byte[] header = new byte[12];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            int read = fis.read(header);
            if (read < 4) throw new IOException("文件过小：" + file.getName());
        }

        boolean isWebP = header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46  // RIFF
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;    // WEBP

        if (isWebP) {
            // WebP：用 webp-imageio 解码（逊过 ImageIO SPI 自动注册）
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) return img;
            } catch (Exception ignored) {}
            // fallback：尝试按文件内容强制读取
            try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
                if (iis != null) {
                    Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
                    while (it.hasNext()) {
                        ImageReader r = it.next();
                        try {
                            r.setInput(iis, true, true);
                            BufferedImage img = r.read(0, r.getDefaultReadParam());
                            if (img != null) return img;
                        } catch (Exception ignored) {
                        } finally {
                            r.dispose();
                        }
                    }
                }
            }
            throw new IOException("无法解码 WebP 图片（请确保 webp-imageio 已加入依赖）：" + file.getName());
        }

        // 普通图片：尝试正常读取
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) return img;
        } catch (Exception ignored) {}

        // 忽略 ICC Profile 强制解码
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return reader.read(0, reader.getDefaultReadParam());
            } catch (IIOException iioEx) {
                try {
                    reader.setInput(ImageIO.createImageInputStream(file), true, true);
                    BufferedImage raw = reader.read(0, reader.getDefaultReadParam());
                    if (raw != null) return raw;
                } catch (Exception ignored) {}
                return null;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
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

    // ========== 表格渲染器 ==========

    /** 斑马条纹 + 字体统一 */
    private static class StripedTableRenderer extends DefaultTableCellRenderer {
        private static final Color ROW_ALT = new Color(248, 250, 253);
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setFont(new Font("微软雅黑", Font.PLAIN, 12));
            setBorder(new EmptyBorder(0, 8, 0, 8));
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                setForeground(new Color(50, 60, 80));
            }
            return this;
        }
    }

    /** 状态列着色渲染器 */
    private static class StatusCellRenderer extends StripedTableRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String v = value == null ? "" : value.toString();
            if (!isSelected) {
                if (v.startsWith(STATUS_SUCCESS)) {
                    setForeground(new Color(39, 174, 96));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (v.startsWith(STATUS_FAILED)) {
                    setForeground(new Color(192, 57, 43));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (v.equals(STATUS_RUNNING)) {
                    setForeground(new Color(41, 128, 185));
                } else if (v.equals(STATUS_SKIPPED)) {
                    setForeground(new Color(230, 126, 34));
                } else {
                    setForeground(new Color(150, 150, 160));
                }
            }
            return this;
        }
    }
}