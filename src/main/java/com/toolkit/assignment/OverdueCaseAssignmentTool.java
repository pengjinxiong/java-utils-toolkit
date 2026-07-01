package com.toolkit.assignment;

import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * 月初逾期分案工具
 * M1/M2：全员混合分案，尽量不分到原催收员所在公司，所有人均分
 * M3：按公司占比分案，只分到公司层面
 */
public class OverdueCaseAssignmentTool {

    private static final Logger logger = LoggerFactory.getLogger(OverdueCaseAssignmentTool.class);

    /** Excel 列索引 */
    private static final int COL_ORDER_NO = 0;
    private static final int COL_CUSTOMER_NAME = 1;
    private static final int COL_COLLECTOR = 2;
    private static final int COL_OVERDUE_DAYS = 3;
    private static final int COL_REPAID_PERIODS = 4;
    private static final int COL_OVERDUE_AMOUNT = 5;
    private static final int COL_COMPANY = 6;

    /** 结果表列定义 */
    private static final String[] RESULT_COLUMNS = {"订单号", "客户姓名", "原催收员", "逾期天数", "已还期数", "逾期金额", "归属公司", "分案人员"};
    private static final int RES_COL_ORDER = 0;
    private static final int RES_COL_NAME = 1;
    private static final int RES_COL_ORIG = 2;
    private static final int RES_COL_DAYS = 3;
    private static final int RES_COL_PAID = 4;
    private static final int RES_COL_AMOUNT = 5;
    private static final int RES_COL_COMPANY = 6;
    private static final int RES_COL_ASSIGNED = 7;

    private static final String TAB_M3 = "M3 分案";
    private static final String[] TAB_NAMES = {"M1 分案", "M2 分案", TAB_M3};

    // ==================== 主题颜色常量 ====================
    private static final Color C_PRIMARY       = new Color(30, 80, 160);
    private static final Color C_PRIMARY_HOVER = new Color(20, 58, 130);
    private static final Color C_SUCCESS       = new Color(34, 139, 70);
    private static final Color C_SUCCESS_HOVER = new Color(22, 110, 52);
    private static final Color C_BG            = new Color(243, 246, 252);
    private static final Color C_CARD          = Color.WHITE;
    private static final Color C_BORDER        = new Color(208, 218, 238);
    private static final Color C_TABLE_HEADER  = new Color(38, 85, 168);
    private static final Color C_TABLE_ROW_ALT = new Color(235, 242, 255);
    private static final Color C_TABLE_SEL     = new Color(170, 208, 255);
    private static final Color C_TEXT_HINT     = new Color(140, 150, 170);
    private static final Font  F_TITLE         = new Font("微软雅黑", Font.BOLD, 20);
    private static final Font  F_LABEL         = new Font("微软雅黑", Font.PLAIN, 13);
    private static final Font  F_BOLD_13       = new Font("微软雅黑", Font.BOLD, 13);

    private JFrame frame;
    private JTabbedPane tabbedPane;
    private final Map<String, TabState> tabStates = new HashMap<>();

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new OverdueCaseAssignmentTool().createAndShowGUI(null));
    }

    /** 从 Launcher 启动，关闭或点击返回时调用 onBack */
    public static void launch(Runnable onBack) {
        SwingUtilities.invokeLater(() -> new OverdueCaseAssignmentTool().createAndShowGUI(onBack));
    }

    private void createAndShowGUI(Runnable onBack) {
        frame = new JFrame("月初逾期分案工具");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1100, 780);
        frame.setMinimumSize(new Dimension(920, 600));
        frame.setLocationRelativeTo(null);
        frame.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        // 关闭窗口时回到主界面
        if (onBack != null) {
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) { onBack.run(); }
            });
        }
        initComponents(onBack);
        frame.setVisible(true);
    }

    private void initComponents(Runnable onBack) {
        // 全局背景色
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(C_BG);

        // ---- 顶部标题栏 ----
        JPanel headerPanel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 65, 150),
                        getWidth(), 0, new Color(55, 120, 210));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(14, 24, 14, 24));

        JLabel titleLabel = new JLabel("月初逾期分案工具");
        titleLabel.setFont(F_TITLE);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JLabel subLabel = new JLabel("案件智能分配 · 多维均衡");
        subLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        subLabel.setForeground(new Color(180, 210, 255));
        subLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        // 返回主界面按钒
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.setOpaque(false);
        if (onBack != null) {
            JButton btnBack = new JButton("← 返回主界面");
            btnBack.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            btnBack.setForeground(new Color(180, 210, 255));
            btnBack.setBackground(new Color(255, 255, 255, 30));
            btnBack.setOpaque(true);
            btnBack.setBorderPainted(false);
            btnBack.setFocusPainted(false);
            btnBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnBack.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { btnBack.setForeground(Color.WHITE); }
                @Override public void mouseExited(MouseEvent e) { btnBack.setForeground(new Color(180, 210, 255)); }
            });
            btnBack.addActionListener(e -> frame.dispose());
            headerRight.add(subLabel);
            headerRight.add(btnBack);
        } else {
            headerRight.add(subLabel);
        }
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(headerRight, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // ---- Tab 内容区 ----
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(F_BOLD_13);
        tabbedPane.setBackground(C_BG);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
        for (String tabName : TAB_NAMES) {
            TabState state = new TabState(tabName.equals(TAB_M3));
            tabStates.put(tabName, state);
            tabbedPane.addTab(tabName, createTabPanel(tabName, state));
        }
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        frame.add(mainPanel);
    }

    // ==================== 构建 Tab 面板 ====================

    private JPanel createTabPanel(String tabName, TabState state) {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setBackground(C_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ---- 输入区卡片 ----
        JPanel inputCard = new JPanel(new GridBagLayout());
        inputCard.setBackground(C_CARD);
        inputCard.setBorder(new RoundedCardBorder(C_BORDER, 10, tabName + " - 配置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 第1行：Excel文件
        JLabel lblExcel = new JLabel("案件 Excel：");
        lblExcel.setFont(F_LABEL);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        inputCard.add(lblExcel, gbc);
        state.filePathField = new JTextField(35);
        state.filePathField.setEditable(false);
        state.filePathField.setFont(F_LABEL);
        state.filePathField.setBackground(new Color(248, 250, 255));
        state.filePathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER, 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputCard.add(state.filePathField, gbc);
        JButton btnChooseFile = styledButton("选择文件", C_PRIMARY, C_PRIMARY_HOVER, 90, 30);
        gbc.gridx = 2; gbc.weightx = 0;
        inputCard.add(btnChooseFile, gbc);

        // 第2行：密码（初始隐藏）
        JLabel pwdLabel = new JLabel("文件密码：");
        pwdLabel.setFont(F_LABEL);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        inputCard.add(pwdLabel, gbc);
        state.passwordField = new JPasswordField(20);
        state.passwordField.setFont(F_LABEL);
        state.passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER, 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        gbc.gridx = 1; gbc.weightx = 1.0;
        inputCard.add(state.passwordField, gbc);
        state.passwordLabel = pwdLabel;
        state.passwordRow = state.passwordField;
        pwdLabel.setVisible(false);
        state.passwordField.setVisible(false);
        JButton btnReadData = styledButton("读取数据", C_PRIMARY, C_PRIMARY_HOVER, 90, 30);
        gbc.gridx = 2; gbc.weightx = 0;
        inputCard.add(btnReadData, gbc);

        // 第3行：分案配置（动态）
        String labelText = state.isM3 ? "各公司占比(%)：" : "本月分案人员：";
        JLabel lblPersonnel = new JLabel(labelText);
        lblPersonnel.setFont(F_LABEL);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        inputCard.add(lblPersonnel, gbc);
        gbc.anchor = GridBagConstraints.CENTER;

        state.personnelContainer = new JPanel();
        state.personnelContainer.setLayout(new BoxLayout(state.personnelContainer, BoxLayout.Y_AXIS));
        state.personnelContainer.setBackground(C_CARD);
        String hint = state.isM3 ? "请先读取数据，将为每个公司生成占比输入框（合计需=100%）"
                                 : "请先读取数据，将按公司生成分案人员输入框";
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setFont(F_LABEL);
        hintLabel.setForeground(C_TEXT_HINT);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        state.personnelContainer.add(hintLabel);

        state.personnelScrollPane = new JScrollPane(state.personnelContainer);
        state.personnelScrollPane.setPreferredSize(new Dimension(0, 130));
        state.personnelScrollPane.setBorder(BorderFactory.createLineBorder(C_BORDER, 1, true));
        state.personnelScrollPane.getViewport().setBackground(C_CARD);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.BOTH;
        inputCard.add(state.personnelScrollPane, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JButton btnAssign = styledButton("开始分案", C_SUCCESS, C_SUCCESS_HOVER, 100, 80);
        btnAssign.setFont(new Font("微软雅黑", Font.BOLD, 14));
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.BOTH;
        inputCard.add(btnAssign, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 第4行：状态栏
        state.statusLabel = new JLabel("  请选择案件 Excel 文件");
        state.statusLabel.setFont(F_LABEL);
        state.statusLabel.setForeground(C_TEXT_HINT);
        state.statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3; gbc.weightx = 1.0;
        inputCard.add(state.statusLabel, gbc);
        gbc.gridwidth = 1;

        panel.add(inputCard, BorderLayout.NORTH);

        // ---- 结果表格卡片 ----
        state.tableModel = new DefaultTableModel(RESULT_COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable resultTable = new JTable(state.tableModel);
        resultTable.setFont(F_LABEL);
        resultTable.setRowHeight(28);
        resultTable.setShowGrid(false);
        resultTable.setIntercellSpacing(new Dimension(0, 0));
        resultTable.setSelectionBackground(C_TABLE_SEL);
        resultTable.setSelectionForeground(Color.BLACK);
        resultTable.setBackground(C_CARD);
        // 交替行颜色渲染器
        DefaultTableCellRenderer stripedRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setFont(F_LABEL);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (sel) {
                    setBackground(C_TABLE_SEL);
                } else {
                    setBackground(r % 2 == 0 ? C_CARD : C_TABLE_ROW_ALT);
                }
                return this;
            }
        };
        for (int i = 0; i < RESULT_COLUMNS.length; i++) {
            resultTable.getColumnModel().getColumn(i).setCellRenderer(stripedRenderer);
        }
        // 表头样式
        JTableHeader header = resultTable.getTableHeader();
        header.setFont(F_BOLD_13);
        header.setBackground(C_TABLE_HEADER);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 34));
        header.setReorderingAllowed(false);
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
        // 列宽
        resultTable.getColumnModel().getColumn(RES_COL_ORDER).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(RES_COL_NAME).setPreferredWidth(80);
        resultTable.getColumnModel().getColumn(RES_COL_ORIG).setPreferredWidth(80);
        resultTable.getColumnModel().getColumn(RES_COL_DAYS).setPreferredWidth(70);
        resultTable.getColumnModel().getColumn(RES_COL_PAID).setPreferredWidth(70);
        resultTable.getColumnModel().getColumn(RES_COL_AMOUNT).setPreferredWidth(100);
        resultTable.getColumnModel().getColumn(RES_COL_COMPANY).setPreferredWidth(120);
        resultTable.getColumnModel().getColumn(RES_COL_ASSIGNED).setPreferredWidth(120);

        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setBorder(new RoundedCardBorder(C_BORDER, 10, "分案结果"));
        tableScroll.getViewport().setBackground(C_CARD);
        panel.add(tableScroll, BorderLayout.CENTER);

        // ---- 底部栏 ----
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(C_CARD);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        state.lblStats = new JLabel("");
        state.lblStats.setFont(F_LABEL);
        state.lblStats.setForeground(new Color(22, 110, 52));
        state.statsTextArea = new JTextArea(4, 40);
        state.statsTextArea.setEditable(false);
        state.statsTextArea.setLineWrap(true);
        state.statsTextArea.setWrapStyleWord(true);
        state.statsTextArea.setFont(F_LABEL);
        state.statsTextArea.setForeground(new Color(45, 55, 70));
        state.statsTextArea.setBackground(C_CARD);
        state.statsTextArea.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        JScrollPane statsScroll = new JScrollPane(state.statsTextArea);
        statsScroll.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        state.dayStatsTableModel = new DefaultTableModel(new String[]{"分案人员", "合计"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable dayStatsTable = new JTable(state.dayStatsTableModel);
        dayStatsTable.setFont(F_LABEL);
        dayStatsTable.setRowHeight(24);
        dayStatsTable.getTableHeader().setFont(F_BOLD_13);
        dayStatsTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane dayStatsScroll = new JScrollPane(dayStatsTable);
        dayStatsScroll.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JTabbedPane statsTabs = new JTabbedPane();
        statsTabs.setFont(F_BOLD_13);
        statsTabs.addTab("人员汇总", statsScroll);
        statsTabs.addTab("逾期天数分布", dayStatsScroll);
        statsTabs.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(C_BORDER, 1, true),
                "分配统计",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                F_BOLD_13,
                new Color(38, 85, 168)));
        statsTabs.setPreferredSize(new Dimension(0, 116));
        JPanel statsPanel = new JPanel(new BorderLayout(0, 4));
        statsPanel.setOpaque(false);
        statsPanel.add(state.lblStats, BorderLayout.NORTH);
        statsPanel.add(statsTabs, BorderLayout.CENTER);
        bottomPanel.add(statsPanel, BorderLayout.CENTER);
        JButton btnExport = styledButton("导出结果 Excel", C_PRIMARY, C_PRIMARY_HOVER, 130, 32);
        btnExport.setEnabled(false);
        bottomPanel.add(btnExport, BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // 事件
        btnChooseFile.addActionListener(e -> chooseFile(state));
        btnReadData.addActionListener(e -> readData(state));
        btnAssign.addActionListener(e -> startAssignment(tabName, state));
        btnExport.addActionListener(e -> exportResult(tabName, state));

        state.btnExport = btnExport;
        return panel;
    }

    /**
     * 创建样式化按钮：圆角、悬停变色、macOS 兼容
     */
    private JButton styledButton(String text, Color normalColor, Color hoverColor, int w, int h) {
        JButton btn = new JButton(text);
        btn.setFont(F_BOLD_13);
        btn.setForeground(Color.WHITE);
        btn.setBackground(normalColor);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (w > 0 && h > 0) btn.setPreferredSize(new Dimension(w, h));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(hoverColor);
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(normalColor);
            }
        });
        return btn;
    }

    /**
     * 圆角卡片边框，带可选标题
     */
    private static class RoundedCardBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final String title;
        private static final Font TITLE_FONT = new Font("微软雅黑", Font.BOLD, 12);

        RoundedCardBorder(Color color, int radius, String title) {
            this.color = color; this.radius = radius; this.title = title;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x + 1, y + 10, w - 3, h - 12, radius, radius);
            if (title != null && !title.isEmpty()) {
                g2.setFont(TITLE_FONT);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(title);
                // 绘制白色遮罩擦除边框线段
                g2.setColor(c.getBackground() == null ? Color.WHITE : c.getBackground());
                g2.fillRect(x + 14, y + 4, tw + 8, fm.getHeight());
                // 绘制标题文字
                g2.setColor(new Color(38, 85, 168));
                g2.drawString(title, x + 18, y + 4 + fm.getAscent());
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) { return new Insets(18, 12, 10, 12); }
        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(18, 12, 10, 12); return insets;
        }
    }

    // ==================== 动态构建配置面板 ====================

    /**
     * 读取数据后动态重建配置区：
     *  M1/M2：一个统一的分案人员输入框（所有公司人员混合）
     *  M3：每个公司一个占比输入框，下方显示合计校验
     */
    private void rebuildPersonnelPanel(TabState state,
                                       Map<String, Set<String>> companyCollectors,
                                       Map<String, Integer> companyCaseCount,
                                       Map<String, Map<String, Integer>> collectorCaseCount) {
        state.personnelContainer.removeAll();
        state.companyPersonnelAreas.clear();
        state.companyRatioFields.clear();

        if (state.isM3) {
            // M3：每个公司一行：公司信息 + 占比输入框
            JPanel ratioPanel = new JPanel(new GridBagLayout());
            ratioPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(3, 4, 3, 4);

            // 表头
            g.gridy = 0; g.gridx = 0; g.anchor = GridBagConstraints.WEST;
            ratioPanel.add(bold("归属公司"), g);
            g.gridx = 1;
            ratioPanel.add(bold("案件数"), g);
            g.gridx = 2;
            ratioPanel.add(bold("原催收员(案件数)"), g);
            g.gridx = 3;
            ratioPanel.add(bold("占比(%)"), g);

            int row = 1;
            for (Map.Entry<String, Set<String>> entry : companyCollectors.entrySet()) {
                String company = entry.getKey();
                Set<String> collectors = entry.getValue();
                int total = companyCaseCount.getOrDefault(company, 0);

                // 催收员+案件数
                StringBuilder collectorInfo = new StringBuilder();
                Map<String, Integer> ccMap = collectorCaseCount.getOrDefault(company, Collections.emptyMap());
                for (String c : collectors) {
                    collectorInfo.append(c).append("(").append(ccMap.getOrDefault(c, 0)).append(") ");
                }

                g.gridy = row; g.gridx = 0; g.anchor = GridBagConstraints.WEST; g.weightx = 0;
                ratioPanel.add(new JLabel(company), g);
                g.gridx = 1;
                ratioPanel.add(new JLabel(String.valueOf(total)), g);
                g.gridx = 2; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL;
                ratioPanel.add(new JLabel(collectorInfo.toString().trim()), g);
                g.fill = GridBagConstraints.NONE; g.weightx = 0;

                JTextField ratioField = new JTextField("0", 5);
                g.gridx = 3;
                ratioPanel.add(ratioField, g);
                state.companyRatioFields.put(company, ratioField);

                row++;
            }

            // 合计行 + 验证标签
            g.gridy = row; g.gridx = 0; g.gridwidth = 3; g.anchor = GridBagConstraints.EAST;
            ratioPanel.add(new JLabel("合计："), g);
            g.gridx = 3; g.gridwidth = 1; g.anchor = GridBagConstraints.WEST;
            state.ratioSumLabel = new JLabel("0 %");
            state.ratioSumLabel.setForeground(Color.RED);
            ratioPanel.add(state.ratioSumLabel, g);

            // 实时计算合计
            for (JTextField f : state.companyRatioFields.values()) {
                f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { updateRatioSum(state); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { updateRatioSum(state); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { updateRatioSum(state); }
                });
            }

            state.personnelContainer.add(ratioPanel);

        } else {
            // M1/M2：按公司分组输入分案人员，每个公司独立输入框，便于跨公司软约束
            JPanel gridPanel = new JPanel(new GridBagLayout());
            gridPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(2, 4, 2, 4);

            // 表头
            g.gridy = 0; g.gridx = 0; g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.NONE; g.weightx = 0;
            gridPanel.add(bold("归属公司"), g);
            g.gridx = 1; gridPanel.add(bold("案件数"), g);
            g.gridx = 2; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL;
            gridPanel.add(bold("原催收员（案件数）"), g);
            g.fill = GridBagConstraints.NONE; g.weightx = 0;
            g.gridx = 3;
            gridPanel.add(bold("本月分案人员（每行一人）"), g);

            int row = 1;
            for (Map.Entry<String, Set<String>> entry : companyCollectors.entrySet()) {
                String company = entry.getKey();
                int total = companyCaseCount.getOrDefault(company, 0);
                Map<String, Integer> ccMap = collectorCaseCount.getOrDefault(company, Collections.emptyMap());
                StringBuilder sb = new StringBuilder();
                for (String c : entry.getValue()) sb.append(c).append("(").append(ccMap.getOrDefault(c, 0)).append(")  ");

                g.gridy = row; g.gridx = 0; g.anchor = GridBagConstraints.NORTHWEST; g.fill = GridBagConstraints.NONE; g.weightx = 0;
                gridPanel.add(new JLabel(company), g);
                g.gridx = 1;
                gridPanel.add(new JLabel(String.valueOf(total)), g);
                g.gridx = 2; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL;
                gridPanel.add(new JLabel(sb.toString().trim()), g);
                g.fill = GridBagConstraints.NONE; g.weightx = 0;

                JTextArea area = new JTextArea(3, 20);
                area.setLineWrap(true);
                area.setToolTipText("输入" + company + "的分案人员，每行一人");
                g.gridx = 3; g.fill = GridBagConstraints.BOTH; g.weightx = 1.0;
                gridPanel.add(new JScrollPane(area), g);
                g.fill = GridBagConstraints.NONE; g.weightx = 0;

                state.companyPersonnelAreas.put(company, area);
                row++;
            }

            state.personnelContainer.add(gridPanel);
        }

        state.personnelContainer.revalidate();
        state.personnelContainer.repaint();
        int height = state.isM3 ? Math.min(60 + companyCollectors.size() * 34, 240)
                                 : Math.min(60 + companyCollectors.size() * 80, 320);
        // 确保高度不小于一行最小可用高度
        height = Math.max(height, state.isM3 ? 80 : 120);
        state.personnelScrollPane.setPreferredSize(new Dimension(0, height));
        // 向上逐层传播 revalidate，确保 NORTH 区域重新计算高度
        Component c = state.personnelScrollPane;
        while (c != null) {
            if (c instanceof JPanel) ((JPanel) c).revalidate();
            if (c == frame.getContentPane()) break;
            c = c.getParent();
        }
        frame.getContentPane().revalidate();
        frame.getContentPane().repaint();
    }

    private void updateRatioSum(TabState state) {
        double sum = 0;
        for (JTextField f : state.companyRatioFields.values()) {
            try { sum += Double.parseDouble(f.getText().trim()); } catch (NumberFormatException ignored) {}
        }
        boolean ok = Math.abs(sum - 100.0) < 0.001;
        state.ratioSumLabel.setText(String.format("%.1f %%", sum));
        state.ratioSumLabel.setForeground(ok ? new Color(0, 140, 0) : Color.RED);
    }

    private JLabel bold(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    // ==================== 文件选择 ====================

    private void chooseFile(TabState state) {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        chooser.setDialogTitle("选择案件Excel文件");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            state.selectedFile = chooser.getSelectedFile();
            state.filePathField.setText(state.selectedFile.getAbsolutePath());

            // 检测是否加密
            boolean encrypted = isExcelEncrypted(state.selectedFile);
            state.fileEncrypted = encrypted;
            state.passwordLabel.setVisible(encrypted);
            state.passwordField.setVisible(encrypted);
            if (!encrypted) {
                // 将密码清空，防止上次输入的密码乲扰
                state.passwordField.setText("");
                state.statusLabel.setText("已选择文件（无密码），可直接点击「读取数据」");
            } else {
                state.statusLabel.setText("已选择文件（加密），请输入密码后点击「读取数据」");
            }
            state.statusLabel.setForeground(Color.BLUE);
            // 重新排版以适应显隐变化
            state.passwordLabel.getParent().revalidate();
            state.passwordLabel.getParent().repaint();
        }
    }

    /** 尝试用 POIFSFileSystem 打开来判断 Excel 是否加密 */
    private boolean isExcelEncrypted(File file) {
        try (POIFSFileSystem fs = new POIFSFileSystem(file)) {
            new EncryptionInfo(fs); // 加密文件才能成功解析
            return true;
        } catch (Exception e) {
            return false; // 明文文件或非 OLE2 格式，直接返回 false
        }
    }

    // ==================== 读取加密Excel ====================

    private void readData(TabState state) {
        if (state.selectedFile == null) {
            JOptionPane.showMessageDialog(frame, "请先选择Excel文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 加密文件才需要密码
        final String password;
        if (state.fileEncrypted) {
            password = new String(state.passwordField.getPassword()).trim();
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "该文件已加密，请输入密码", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else {
            password = null;
        }
        new SwingWorker<List<String[]>, Void>() {
            @Override
            protected List<String[]> doInBackground() throws Exception {
                if (state.fileEncrypted) {
                    return readEncryptedExcel(state.selectedFile, password);
                } else {
                    return readPlainExcel(state.selectedFile);
                }
            }
            @Override
            protected void done() {
                try {
                    List<String[]> rows = get();
                    state.caseRows = rows;

                    // 按公司分组统计：公司->催收员集合、公司->案件数、公司->催收员->案件数
                    Map<String, Set<String>> companyCollectors = new LinkedHashMap<>();
                    Map<String, Integer> companyCaseCount = new LinkedHashMap<>();
                    Map<String, Map<String, Integer>> collectorCaseCount = new LinkedHashMap<>();

                    for (String[] row : rows) {
                        String company = row[COL_COMPANY];
                        if (company == null || company.isEmpty()) company = "未知公司";
                        String collector = row[COL_COLLECTOR];
                        companyCollectors.computeIfAbsent(company, k -> new LinkedHashSet<>()).add(collector);
                        companyCaseCount.merge(company, 1, Integer::sum);
                        collectorCaseCount.computeIfAbsent(company, k -> new LinkedHashMap<>())
                                .merge(collector, 1, Integer::sum);
                    }

                    // 状态栏：公司[催收员(案件数)...] 格式
                    StringBuilder sb = new StringBuilder("读取成功！共 " + rows.size() + " 条案件   ");
                    companyCollectors.forEach((company, collectors) -> {
                        sb.append("[").append(company).append(": ").append(companyCaseCount.get(company)).append("条  ");
                        Map<String, Integer> ccMap = collectorCaseCount.get(company);
                        for (String c : collectors) sb.append(c).append("(").append(ccMap.get(c)).append(") ");
                        sb.append("]  ");
                    });
                    state.statusLabel.setText(sb.toString());
                    state.statusLabel.setForeground(new Color(0, 140, 0));

                    rebuildPersonnelPanel(state, companyCollectors, companyCaseCount, collectorCaseCount);

                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    if (msg == null || msg.isEmpty()) msg = ex.getClass().getSimpleName();
                    state.statusLabel.setText("读取失败：" + msg);
                    state.statusLabel.setForeground(Color.RED);
                    logger.error("读取Excel失败", ex);
                    if (msg.contains("password") || msg.contains("Password") || msg.contains("密码")) {
                        JOptionPane.showMessageDialog(frame, "密码错误，请检查后重试", "密码错误", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(frame, "读取文件失败：\n" + msg, "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    private List<String[]> readEncryptedExcel(File file, String password) throws Exception {
        byte[] decryptedBytes;
        POIFSFileSystem fs = new POIFSFileSystem(file);
        try {
            EncryptionInfo info = new EncryptionInfo(fs);
            Decryptor decryptor = Decryptor.getInstance(info);
            if (!decryptor.verifyPassword(password)) throw new RuntimeException("文件密码错误");
            try (InputStream dataStream = decryptor.getDataStream(fs);
                 XSSFWorkbook poiWb = new XSSFWorkbook(dataStream)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                poiWb.write(baos);
                decryptedBytes = baos.toByteArray();
            }
        } finally { fs.close(); }

        List<String[]> rows = new ArrayList<>();
        try (ReadableWorkbook wb = new ReadableWorkbook(new ByteArrayInputStream(decryptedBytes))) {
            Sheet sheet = wb.getFirstSheet();
            for (Row row : sheet.read()) {
                if (row.getRowNum() == 1) continue;
                String orderNo = getCellText(row, COL_ORDER_NO);
                if (orderNo == null || orderNo.trim().isEmpty()) continue;
                rows.add(new String[]{
                        orderNo.trim(),
                        getCellText(row, COL_CUSTOMER_NAME).trim(),
                        getCellText(row, COL_COLLECTOR).trim(),
                        getCellText(row, COL_OVERDUE_DAYS).trim(),
                        getCellText(row, COL_REPAID_PERIODS).trim(),
                        getCellText(row, COL_OVERDUE_AMOUNT).trim(),
                        getCellText(row, COL_COMPANY).trim()
                });
            }
        }
        return rows;
    }

    /** 直接用 FastExcel 读取明文 xlsx（无密码） */
    private List<String[]> readPlainExcel(File file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (ReadableWorkbook wb = new ReadableWorkbook(file)) {
            Sheet sheet = wb.getFirstSheet();
            for (Row row : sheet.read()) {
                if (row.getRowNum() == 1) continue;
                String orderNo = getCellText(row, COL_ORDER_NO);
                if (orderNo == null || orderNo.trim().isEmpty()) continue;
                rows.add(new String[]{
                        orderNo.trim(),
                        getCellText(row, COL_CUSTOMER_NAME).trim(),
                        getCellText(row, COL_COLLECTOR).trim(),
                        getCellText(row, COL_OVERDUE_DAYS).trim(),
                        getCellText(row, COL_REPAID_PERIODS).trim(),
                        getCellText(row, COL_OVERDUE_AMOUNT).trim(),
                        getCellText(row, COL_COMPANY).trim()
                });
            }
        }
        return rows;
    }

    private String getCellText(Row row, int colIdx) {
        if (colIdx >= row.getCellCount()) return "";
        Cell cell = row.getCell(colIdx);
        return cell == null ? "" : cell.getText();
    }

    // ==================== 分案入口 ====================

    private void startAssignment(String tabName, TabState state) {
        if (state.caseRows == null || state.caseRows.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先读取案件数据", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (state.isM3) {
            startAssignmentM3(state);
        } else {
            startAssignmentM1M2(state);
        }
    }

    // ==================== M1/M2 分案：全局混合，跨公司均分 ====================

    private void startAssignmentM1M2(TabState state) {
        if (state.companyPersonnelAreas.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先读取数据", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 从各公司输入框收集分案人员，同时建立精准的 person->company 映射
        List<String> personnel = new ArrayList<>();
        Map<String, String> personnelToCompany = new HashMap<>();  // person -> 其所属公司
        for (Map.Entry<String, JTextArea> entry : state.companyPersonnelAreas.entrySet()) {
            String company = entry.getKey();
            String text = entry.getValue().getText().trim();
            if (text.isEmpty()) continue;
            for (String p : parsePersonnel(text)) {
                personnel.add(p);
                personnelToCompany.put(p, company);
            }
        }
        if (personnel.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请至少为一个公司填写分案人员", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (personnel.size() < 2) {
            JOptionPane.showMessageDialog(frame, "分案人员至少需要2人", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 构建原催收员->所属公司映射（用于软约束判断案件原公司）
        Map<String, String> collectorToCompany = new HashMap<>();
        for (String[] row : state.caseRows) {
            String company = row[COL_COMPANY];
            if (company == null || company.isEmpty()) company = "未知公司";
            collectorToCompany.put(row[COL_COLLECTOR], company);
        }

        try {
            List<String[]> result = assignCasesM1M2(state.caseRows, personnel, collectorToCompany, personnelToCompany);
            state.assignedResult = result;
            state.tableModel.setRowCount(0);
            for (String[] row : result) state.tableModel.addRow(row);
            state.btnExport.setEnabled(true);

            // 统计
            Map<String, Integer> personCount = new LinkedHashMap<>();
            for (String[] row : result) personCount.merge(row[RES_COL_ASSIGNED], 1, Integer::sum);
            StringBuilder sb = new StringBuilder("分案完成！各人案件数：");
            personCount.forEach((n, c) -> sb.append(n).append("(").append(c).append(") "));
            state.lblStats.setText(sb.toString());
            state.statsTextArea.setText(buildM1M2PersonStatsText(result));
            state.statsTextArea.setCaretPosition(0);
            updateM1M2DayStatsTable(state.dayStatsTableModel, result);
            showM1M2DayStatsDialog(result);
            state.statusLabel.setText(sb.toString());
            state.statusLabel.setForeground(new Color(0, 140, 0));

            // 按公司统计弹窗
            Map<String, Map<String, Integer>> companyStats = new LinkedHashMap<>();
            for (String[] row : result) {
                String co = row[RES_COL_COMPANY].isEmpty() ? "未知公司" : row[RES_COL_COMPANY];
                companyStats.computeIfAbsent(co, k -> new LinkedHashMap<>()).merge(row[RES_COL_ASSIGNED], 1, Integer::sum);
            }
            showCompanyStatsDialog(companyStats, result.size());

        } catch (Exception ex) {
            state.statusLabel.setText("分案失败：" + ex.getMessage());
            state.statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(frame, "分案失败：\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * M1/M2 分案算法：
     * 1. 所有案件按逾期天数分组处理
     * 2. 硬约束：分案人员 != 原催收员（姓名不同）
     * 3. 软约束：尽量让分案人员与原催收员不属于同一公司
     * 4. 按输入人员顺序领取从小到大的逾期天数案件；优先保证每个逾期天数组内均分，再兼顾总数量
     */
    private List<String[]> assignCasesM1M2(List<String[]> caseRows, List<String> personnel,
                                            Map<String, String> collectorToCompany,
                                            Map<String, String> personnelToCompany) {
        int total = caseRows.size();
        int n = personnel.size();
        int base = total / n, rem = total % n;
    
        List<PersonQuota> quotas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            quotas.add(new PersonQuota(personnel.get(i), base + (i < rem ? 1 : 0), i));
        }

        Map<String, List<Integer>> indicesByOverdueDay = new TreeMap<>(this::compareOverdueDayKey);
        for (int i = 0; i < total; i++) {
            indicesByOverdueDay.computeIfAbsent(overdueDayKey(caseRows.get(i)), k -> new ArrayList<>()).add(i);
        }
    
        // personnelToCompany 已由调用方传入，直接使用，无需再推断
        String[] assignments = new String[total];
        Map<String, PersonQuota> quotaByName = new HashMap<>();
        for (PersonQuota pq : quotas) quotaByName.put(pq.name, pq);

        for (Map.Entry<String, List<Integer>> dayEntry : indicesByOverdueDay.entrySet()) {
            String overdueDay = dayEntry.getKey();
            Map<String, Integer> dayAssignedCount = new HashMap<>();
            for (int idx : dayEntry.getValue()) {
                String[] curRow = caseRows.get(idx);
                String origCollector = curRow[COL_COLLECTOR];
                String origCompany   = collectorToCompany.getOrDefault(origCollector, "");

                // 优先按逾期天数组内均分，再用当前总单量做次级数量均衡
                PersonQuota best = pickBest(quotas, origCollector, origCompany, personnelToCompany,
                        dayAssignedCount, true, dayEntry.getValue().size());

                // 降级：允许同公司
                if (best == null) {
                    best = pickBest(quotas, origCollector, origCompany, personnelToCompany,
                            dayAssignedCount, false, dayEntry.getValue().size());
                }

                if (best == null) {
                    boolean swapped = trySwapM1M2(assignments, caseRows, quotas, quotaByName, idx, curRow, origCollector);
                    if (!swapped) throw new RuntimeException("无法为订单 " + curRow[COL_ORDER_NO] + " 分配");
                    rebuildDayAssignedCount(dayAssignedCount, assignments, caseRows, overdueDay);
                    continue;
                }
                best.addCase(curRow);
                assignments[idx] = best.name;
                dayAssignedCount.merge(best.name, 1, Integer::sum);
            }
        }
        rebalanceTotalsM1M2(assignments, caseRows, quotas, personnelToCompany, collectorToCompany);

        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String[] orig = caseRows.get(i);
            result.add(new String[]{
                    orig[COL_ORDER_NO], orig[COL_CUSTOMER_NAME], orig[COL_COLLECTOR], orig[COL_OVERDUE_DAYS],
                    orig[COL_REPAID_PERIODS], orig[COL_OVERDUE_AMOUNT],
                    orig.length > COL_COMPANY ? orig[COL_COMPANY] : "",
                    assignments[i]
            });
        }
        return result;
    }

    private void rebuildDayAssignedCount(Map<String, Integer> dayAssignedCount, String[] assignments,
                                         List<String[]> caseRows, String overdueDay) {
        dayAssignedCount.clear();
        for (int i = 0; i < assignments.length; i++) {
            if (assignments[i] == null) continue;
            if (overdueDay.equals(overdueDayKey(caseRows.get(i)))) {
                dayAssignedCount.merge(assignments[i], 1, Integer::sum);
            }
        }
    }

    private String overdueDayKey(String[] row) {
        String raw = row[COL_OVERDUE_DAYS] == null ? "" : row[COL_OVERDUE_DAYS].trim();
        if (raw.isEmpty()) return "未知";
        double value = parseDouble(raw);
        if (value > 0 || "0".equals(raw) || "0.0".equals(raw)) return formatStatNumber(value);
        return raw;
    }

    private int compareOverdueDayKey(String a, String b) {
        double av = parseDouble(a);
        double bv = parseDouble(b);
        boolean an = isNumericText(a);
        boolean bn = isNumericText(b);
        if (an && bn) return Double.compare(av, bv);
        if (an) return -1;
        if (bn) return 1;
        return a.compareTo(b);
    }

    private boolean isNumericText(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            Double.parseDouble(value.trim().replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 从候选中选出最优人选
     * requireDiffCompany=true 时只看不同公司的候选人，否则全部候选人都看
     * 选择规则：优先让当前逾期天数组内均分，其次让总订单数量更均衡，最后按输入顺序兜底
     */
    private PersonQuota pickBest(List<PersonQuota> quotas,
                                  String origCollector, String origCompany,
                                  Map<String, String> personnelToCompany,
                                  Map<String, Integer> dayAssignedCount,
                                  boolean requireDiffCompany,
                                  int dayTotal) {
        PersonQuota best = pickBest(quotas, origCollector, origCompany, personnelToCompany,
                dayAssignedCount, requireDiffCompany, dayTotal, true);
        if (best != null) return best;
        return pickBest(quotas, origCollector, origCompany, personnelToCompany,
                dayAssignedCount, requireDiffCompany, dayTotal, false);
    }

    private PersonQuota pickBest(List<PersonQuota> quotas,
                                  String origCollector, String origCompany,
                                  Map<String, String> personnelToCompany,
                                  Map<String, Integer> dayAssignedCount,
                                  boolean requireDiffCompany,
                                  int dayTotal,
                                  boolean enforceDayLimit) {
        PersonQuota best = null;
        int bestDayCount = Integer.MAX_VALUE;
        int bestAssigned = Integer.MAX_VALUE;
        int maxAllowedDayCount = (dayTotal + quotas.size() - 1) / quotas.size();
        for (PersonQuota pq : quotas) {
            if (pq.name.equals(origCollector)) continue;
            if (requireDiffCompany) {
                String pCompany = personnelToCompany.getOrDefault(pq.name, "");
                boolean diffCompany = !pCompany.equals(origCompany)
                        || pCompany.isEmpty() || origCompany.isEmpty();
                if (!diffCompany) continue;
            }
            int dayCount = dayAssignedCount.getOrDefault(pq.name, 0);
            if (enforceDayLimit && dayCount >= maxAllowedDayCount) continue;
            if (best == null
                    || dayCount < bestDayCount
                    || (dayCount == bestDayCount && pq.assigned < bestAssigned)
                    || (dayCount == bestDayCount && pq.assigned == bestAssigned
                        && pq.order < best.order)) {
                best = pq;
                bestDayCount = dayCount;
                bestAssigned = pq.assigned;
            }
        }
        return best;
    }

    private void rebalanceTotalsM1M2(String[] assignments, List<String[]> caseRows,
                                      List<PersonQuota> quotas,
                                      Map<String, String> personnelToCompany,
                                      Map<String, String> collectorToCompany) {
        int maxMoves = caseRows.size() * quotas.size();
        for (int i = 0; i < maxMoves; i++) {
            boolean moved = tryRebalanceOneMove(assignments, caseRows, quotas,
                    personnelToCompany, collectorToCompany, true);
            if (!moved) {
                moved = tryRebalanceOneMove(assignments, caseRows, quotas,
                        personnelToCompany, collectorToCompany, false);
            }
            if (!moved) return;
            if (maxAssigned(quotas) - minAssigned(quotas) <= 1) return;
        }
    }

    private boolean tryRebalanceOneMove(String[] assignments, List<String[]> caseRows,
                                         List<PersonQuota> quotas,
                                         Map<String, String> personnelToCompany,
                                         Map<String, String> collectorToCompany,
                                         boolean requireDiffCompany) {
        List<PersonQuota> highList = new ArrayList<>(quotas);
        highList.sort((a, b) -> Integer.compare(b.assigned, a.assigned));
        List<PersonQuota> lowList = new ArrayList<>(quotas);
        lowList.sort(Comparator.comparingInt((PersonQuota p) -> p.assigned).thenComparingInt(p -> p.order));

        Map<String, Map<String, Integer>> dayCounts = buildPersonDayCounts(assignments, caseRows);
        for (PersonQuota high : highList) {
            for (PersonQuota low : lowList) {
                if (high == low || high.assigned - low.assigned <= 1) continue;
                int idx = findMovableCase(assignments, caseRows, high, low, dayCounts,
                        personnelToCompany, collectorToCompany, requireDiffCompany);
                if (idx < 0) continue;
                high.removeCase(caseRows.get(idx));
                low.addCase(caseRows.get(idx));
                assignments[idx] = low.name;
                return true;
            }
        }
        return false;
    }

    private int findMovableCase(String[] assignments, List<String[]> caseRows,
                                PersonQuota high, PersonQuota low,
                                Map<String, Map<String, Integer>> dayCounts,
                                Map<String, String> personnelToCompany,
                                Map<String, String> collectorToCompany,
                                boolean requireDiffCompany) {
        for (int i = 0; i < assignments.length; i++) {
            if (!high.name.equals(assignments[i])) continue;
            String[] row = caseRows.get(i);
            String origCollector = row[COL_COLLECTOR];
            if (low.name.equals(origCollector)) continue;
            String origCompany = collectorToCompany.getOrDefault(origCollector, "");
            if (requireDiffCompany) {
                String lowCompany = personnelToCompany.getOrDefault(low.name, "");
                boolean diffCompany = !lowCompany.equals(origCompany)
                        || lowCompany.isEmpty() || origCompany.isEmpty();
                if (!diffCompany) continue;
            }
            String dayKey = overdueDayKey(row);
            int highDayCount = dayCounts.getOrDefault(high.name, Collections.emptyMap()).getOrDefault(dayKey, 0);
            int lowDayCount = dayCounts.getOrDefault(low.name, Collections.emptyMap()).getOrDefault(dayKey, 0);
            if (highDayCount <= lowDayCount) continue;
            return i;
        }
        return -1;
    }

    private Map<String, Map<String, Integer>> buildPersonDayCounts(String[] assignments, List<String[]> caseRows) {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        for (int i = 0; i < assignments.length; i++) {
            if (assignments[i] == null) continue;
            String dayKey = overdueDayKey(caseRows.get(i));
            counts.computeIfAbsent(assignments[i], k -> new HashMap<>()).merge(dayKey, 1, Integer::sum);
        }
        return counts;
    }

    private int maxAssigned(List<PersonQuota> quotas) {
        return quotas.stream().mapToInt(q -> q.assigned).max().orElse(0);
    }

    private int minAssigned(List<PersonQuota> quotas) {
        return quotas.stream().mapToInt(q -> q.assigned).min().orElse(0);
    }

    private String buildM1M2PersonStatsText(List<String[]> result) {
        Map<String, AssignmentStat> stats = new LinkedHashMap<>();
        for (String[] row : result) {
            String person = row[RES_COL_ASSIGNED];
            AssignmentStat stat = stats.computeIfAbsent(person, k -> new AssignmentStat());
            stat.count++;
            stat.totalDays += parseDouble(row[RES_COL_DAYS]);
            stat.totalPeriods += parseDouble(row[RES_COL_PAID]);
            stat.totalAmount += parseDouble(row[RES_COL_AMOUNT]);
        }

        StringBuilder sb = new StringBuilder();
        stats.forEach((person, stat) -> {
            if (sb.length() > 0) sb.append('\n');
            sb.append(person)
                    .append("：订单数量 ").append(stat.count)
                    .append("，逾期天数 ").append(formatStatNumber(stat.totalDays))
                    .append("，已还期数 ").append(formatStatNumber(stat.totalPeriods))
                    .append("，逾期金额 ").append(formatMoney(stat.totalAmount));
        });
        return sb.toString();
    }

    private void updateM1M2DayStatsTable(DefaultTableModel model, List<String[]> result) {
        buildM1M2DayStatsModel(model, result);
    }

    private DefaultTableModel buildM1M2DayStatsModel(List<String[]> result) {
        DefaultTableModel model = new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        buildM1M2DayStatsModel(model, result);
        return model;
    }

    private void buildM1M2DayStatsModel(DefaultTableModel model, List<String[]> result) {
        Set<String> dayKeys = new TreeSet<>(this::compareOverdueDayKey);
        Map<String, Map<String, Integer>> personDayCounts = new LinkedHashMap<>();
        for (String[] row : result) {
            String person = row[RES_COL_ASSIGNED];
            String dayKey = overdueDayKey(row);
            dayKeys.add(dayKey);
            personDayCounts.computeIfAbsent(person, k -> new LinkedHashMap<>())
                    .merge(dayKey, 1, Integer::sum);
        }

        List<String> columns = new ArrayList<>();
        columns.add("分案人员");
        columns.add("合计");
        for (String dayKey : dayKeys) columns.add(dayKey + "天");

        Object[][] data = new Object[personDayCounts.size()][columns.size()];
        int rowIdx = 0;
        for (Map.Entry<String, Map<String, Integer>> entry : personDayCounts.entrySet()) {
            Map<String, Integer> dayCount = entry.getValue();
            int total = dayCount.values().stream().mapToInt(Integer::intValue).sum();
            data[rowIdx][0] = entry.getKey();
            data[rowIdx][1] = total;
            int colIdx = 2;
            for (String dayKey : dayKeys) {
                data[rowIdx][colIdx++] = dayCount.getOrDefault(dayKey, 0);
            }
            rowIdx++;
        }
        model.setDataVector(data, columns.toArray());
    }

    private void showM1M2DayStatsDialog(List<String[]> result) {
        JDialog dialog = new JDialog(frame, "逾期天数分布", false);
        dialog.setSize(720, 460);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("逾期天数分布 - 每人每天分配数量");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        dialog.add(title, BorderLayout.NORTH);

        JTable table = new JTable(buildM1M2DayStatsModel(result));
        table.setFont(F_LABEL);
        table.setRowHeight(26);
        table.getTableHeader().setFont(F_BOLD_13);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        if (table.getColumnCount() > 0) table.getColumnModel().getColumn(0).setPreferredWidth(120);
        if (table.getColumnCount() > 1) table.getColumnModel().getColumn(1).setPreferredWidth(70);
        for (int i = 2; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(70);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton btnClose = new JButton("关闭");
        btnClose.addActionListener(e -> dialog.dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        bp.add(btnClose);
        dialog.add(bp, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private String formatStatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatMoney(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** 将字符串解析为 double，无法解析时返回 0.0 */
    private double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(s.trim().replace(",", "")); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private boolean trySwapM1M2(String[] assignments, List<String[]> caseRows, List<PersonQuota> quotas,
                                  Map<String, PersonQuota> quotaByName,
                                  int currentIdx, String[] currentRow, String origCollector) {
        PersonQuota onlyAvail = null;
        for (PersonQuota pq : quotas) {
            if (pq.remaining() > 0) { onlyAvail = pq; break; }
        }
        if (onlyAvail == null) return false;
        for (int i = 0; i < assignments.length; i++) {
            if (i == currentIdx || assignments[i] == null) continue;
            String assignedPerson = assignments[i];
            if (assignedPerson.equals(onlyAvail.name)) continue;
            String iOrigCollector = caseRows.get(i)[COL_COLLECTOR];
            if (!onlyAvail.name.equals(iOrigCollector) && !assignedPerson.equals(origCollector)) {
                PersonQuota assignedQuota = quotaByName.get(assignedPerson);
                if (assignedQuota == null) continue;
                assignedQuota.removeCase(caseRows.get(i));
                assignedQuota.addCase(currentRow);
                onlyAvail.addCase(caseRows.get(i));
                assignments[currentIdx] = assignedPerson;
                assignments[i] = onlyAvail.name;
                return true;
            }
        }
        return false;
    }

    // ==================== M3 分案：按比例分到公司层面 ====================

    private void startAssignmentM3(TabState state) {
        if (state.companyRatioFields.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先读取数据", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 解析占比
        Map<String, Double> companyRatios = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<String, JTextField> entry : state.companyRatioFields.entrySet()) {
            String company = entry.getKey();
            String text = entry.getValue().getText().trim();
            double ratio;
            try {
                ratio = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(frame, "公司「" + company + "」的占比格式不正确", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ratio < 0) {
                JOptionPane.showMessageDialog(frame, "公司「" + company + "」的占比不能为负数", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            companyRatios.put(company, ratio);
            sum += ratio;
        }
        if (Math.abs(sum - 100.0) > 0.5) {
            JOptionPane.showMessageDialog(frame, String.format("各公司占比合计为 %.1f%%，必须等于100%%", sum),
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<String[]> result = assignCasesM3(state.caseRows, companyRatios);
            state.assignedResult = result;
            state.tableModel.setRowCount(0);
            for (String[] row : result) state.tableModel.addRow(row);
            state.btnExport.setEnabled(true);

            // 统计：按分配公司
            Map<String, Integer> companyCount = new LinkedHashMap<>();
            for (String[] row : result) companyCount.merge(row[RES_COL_ASSIGNED], 1, Integer::sum);
            StringBuilder sb = new StringBuilder("M3分案完成！各公司分配案件数：");
            companyCount.forEach((c, cnt) -> sb.append(c).append("(").append(cnt).append(") "));
            state.lblStats.setText(sb.toString());
            state.statusLabel.setText(sb.toString());
            state.statusLabel.setForeground(new Color(0, 140, 0));

            // 统计弹窗（M3只到公司层面，分案人员列=公司名）
            Map<String, Map<String, Integer>> companyStats = new LinkedHashMap<>();
            for (String[] row : result) {
                String assignedCo = row[RES_COL_ASSIGNED];
                String origCo = row[RES_COL_COMPANY].isEmpty() ? "未知" : row[RES_COL_COMPANY];
                companyStats.computeIfAbsent(assignedCo, k -> new LinkedHashMap<>()).merge(origCo, 1, Integer::sum);
            }
            showM3StatsDialog(companyStats, result.size(), companyRatios);

        } catch (Exception ex) {
            state.statusLabel.setText("分案失败：" + ex.getMessage());
            state.statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(frame, "分案失败：\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * M3 分案算法：按比例将所有案件分配到对应公司
     * 结果中“分案人员”列填写分配到的公司名
     * 1. 最大余数法精确计算每个目标公司的配额
     * 2. 贪心分配：对每条案件，优先分给配额未满且非原归属公司的目标公司（配额最多者优先）
     *    若所有未满配额的目标公司都与原归属公司相同，则降级选配额最多的那个
     */
    private List<String[]> assignCasesM3(List<String[]> caseRows, Map<String, Double> companyRatios) {
        int total = caseRows.size();
        List<String> companies = new ArrayList<>(companyRatios.keySet());
        int n = companies.size();

        // 最大余数法计算每个目标公司的配额
        double sumRatio = companyRatios.values().stream().mapToDouble(Double::doubleValue).sum();
        double[] exact = new double[n];
        int[] quota = new int[n];
        int floorSum = 0;
        for (int i = 0; i < n; i++) {
            exact[i] = total * companyRatios.get(companies.get(i)) / sumRatio;
            quota[i] = (int) Math.floor(exact[i]);
            floorSum += quota[i];
        }
        // 按余数降序补足剩余名额
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(exact[b] - quota[b], exact[a] - quota[a]));
        int remaining = total - floorSum;
        for (int i = 0; i < remaining; i++) quota[order[i]]++;

        // 转为 PersonQuota 方便复用 remaining() 接口
        List<PersonQuota> quotas = new ArrayList<>();
        for (int i = 0; i < n; i++) quotas.add(new PersonQuota(companies.get(i), quota[i], i));

        // 打乱案件顺序，贪心分配
        List<String[]> shuffled = new ArrayList<>(caseRows);
        Collections.shuffle(shuffled, new Random());

        String[] assignments = new String[total];
        for (int idx = 0; idx < total; idx++) {
            String origCompany = shuffled.get(idx)[COL_COMPANY];
            if (origCompany == null) origCompany = "";

            // 优先：配额未满 && 非原归属公司，取配额最大者
            PersonQuota best = null;
            for (PersonQuota pq : quotas) {
                if (pq.remaining() <= 0) continue;
                if (pq.name.equals(origCompany)) continue;
                if (best == null || pq.remaining() > best.remaining()) best = pq;
            }

            // 降级：全部未满配额都是原公司，则不限制公司
            if (best == null) {
                for (PersonQuota pq : quotas) {
                    if (pq.remaining() <= 0) continue;
                    if (best == null || pq.remaining() > best.remaining()) best = pq;
                }
            }

            if (best == null) throw new RuntimeException("分配配额计算异常，请重试");
            best.assigned++;
            assignments[idx] = best.name;
        }

        // 组装结果
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String[] orig = shuffled.get(i);
            result.add(new String[]{
                    orig[COL_ORDER_NO], orig[COL_CUSTOMER_NAME], orig[COL_COLLECTOR], orig[COL_OVERDUE_DAYS],
                    orig[COL_REPAID_PERIODS], orig[COL_OVERDUE_AMOUNT],
                    orig.length > COL_COMPANY ? orig[COL_COMPANY] : "",
                    assignments[i]
            });
        }
        return result;
    }

    // ==================== 工具方法 ====================

    private List<String> parsePersonnel(String text) {
        List<String> list = new ArrayList<>();
        for (String line : text.split("[\\n\\r]+")) {
            String t = line.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    // ==================== 统计弹窗 ====================

    private void showCompanyStatsDialog(Map<String, Map<String, Integer>> companyStats, int totalCases) {
        JDialog dialog = new JDialog(frame, "分案统计 - 按归属公司分组", false);
        dialog.setSize(560, 460);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("分案完成！共 " + totalCases + " 条案件，按归属公司统计如下：");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        dialog.add(title, BorderLayout.NORTH);

        DefaultTableModel statsModel = new DefaultTableModel(new String[]{"归属公司", "分案人员", "案件数"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Map.Entry<String, Map<String, Integer>> ce : companyStats.entrySet()) {
            String company = ce.getKey();
            Map<String, Integer> personMap = ce.getValue();
            int companyTotal = personMap.values().stream().mapToInt(Integer::intValue).sum();
            for (Map.Entry<String, Integer> pe : personMap.entrySet()) {
                statsModel.addRow(new Object[]{company, pe.getKey(), pe.getValue()});
            }
            statsModel.addRow(new Object[]{"【" + company + " 合计】", "", companyTotal});
        }

        JTable statsTable = new JTable(statsModel);
        statsTable.setRowHeight(24);
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        statsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        statsTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                String cv = (String) t.getValueAt(r, 0);
                if (cv != null && cv.startsWith("【")) {
                    setFont(getFont().deriveFont(Font.BOLD));
                    if (!sel) setBackground(new Color(220, 235, 255));
                } else {
                    setFont(getFont().deriveFont(Font.PLAIN));
                    if (!sel) setBackground(Color.WHITE);
                }
                return this;
            }
        });
        dialog.add(new JScrollPane(statsTable), BorderLayout.CENTER);

        JButton btnClose = new JButton("关闭");
        btnClose.addActionListener(e -> dialog.dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        bp.add(btnClose);
        dialog.add(bp, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showM3StatsDialog(Map<String, Map<String, Integer>> companyStats, int totalCases,
                                    Map<String, Double> companyRatios) {
        JDialog dialog = new JDialog(frame, "M3 分案统计 - 按公司占比分配", false);
        dialog.setSize(560, 400);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout(8, 8));

        JLabel title = new JLabel("M3分案完成！共 " + totalCases + " 条案件，按公司占比分配结果：");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        dialog.add(title, BorderLayout.NORTH);

        DefaultTableModel m = new DefaultTableModel(new String[]{"分配公司", "设定占比(%)", "实际分配(条)", "实际占比(%)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Map.Entry<String, Map<String, Integer>> ce : companyStats.entrySet()) {
            String co = ce.getKey();
            int cnt = ce.getValue().values().stream().mapToInt(Integer::intValue).sum();
            double setRatio = companyRatios.getOrDefault(co, 0.0);
            double actualRatio = totalCases > 0 ? cnt * 100.0 / totalCases : 0;
            m.addRow(new Object[]{co, String.format("%.1f", setRatio), cnt, String.format("%.2f", actualRatio)});
        }
        JTable t = new JTable(m);
        t.setRowHeight(26);
        dialog.add(new JScrollPane(t), BorderLayout.CENTER);

        JButton btnClose = new JButton("关闭");
        btnClose.addActionListener(e -> dialog.dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        bp.add(btnClose);
        dialog.add(bp, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ==================== 导出结果 ====================

    private void exportResult(String tabName, TabState state) {
        if (state.assignedResult == null || state.assignedResult.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "没有可导出的分案结果", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        chooser.setDialogTitle("保存分案结果");
        chooser.setSelectedFile(new File(System.getProperty("user.home"), tabName.replace(" ", "") + "_分案结果.xlsx"));
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File outFile = chooser.getSelectedFile();
            if (!outFile.getName().endsWith(".xlsx"))
                outFile = new File(outFile.getParent(), outFile.getName() + ".xlsx");
            try {
                writeResultToExcel(state.assignedResult, outFile);
                JOptionPane.showMessageDialog(frame, "导出成功！\n文件路径：" + outFile.getAbsolutePath(),
                        "完成", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                logger.error("导出Excel失败", ex);
                JOptionPane.showMessageDialog(frame, "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void writeResultToExcel(List<String[]> result, File outFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outFile);
             Workbook wb = new Workbook(fos, "月初逾期分案工具", "1.0")) {
            Worksheet ws = wb.newWorksheet("分案结果");
            for (int c = 0; c < RESULT_COLUMNS.length; c++) {
                ws.value(0, c, RESULT_COLUMNS[c]);
                ws.style(0, c).bold().fillColor("CCE5FF").set();
            }
            for (int r = 0; r < result.size(); r++) {
                for (int c = 0; c < result.get(r).length; c++) {
                    ws.value(r + 1, c, result.get(r)[c]);
                }
            }
            ws.width(0, 250); ws.width(1, 100); ws.width(2, 100);
            ws.width(3, 100); ws.width(4, 100); ws.width(5, 120); ws.width(6, 150); ws.width(7, 150);
        }
    }

    // ==================== 内部类 ====================

    private static class AssignmentStat {
        int count;
        double totalDays;
        double totalPeriods;
        double totalAmount;
    }

    private static class PersonQuota {
        final String name;
        final int quota;
        final int order;
        int assigned;
        // 三项指标累计用于兜底交换时保持人员负载状态同步
        double totalDays;
        double totalPeriods;
        double totalAmount;

        PersonQuota(String name, int quota, int order) {
            this.name = name;
            this.quota = quota;
            this.order = order;
        }
        int remaining() { return quota - assigned; }

        void addCase(String[] row) {
            assigned++;
            totalDays    += parseDoubleStatic(row[COL_OVERDUE_DAYS]);
            totalPeriods += parseDoubleStatic(row[COL_REPAID_PERIODS]);
            totalAmount  += parseDoubleStatic(row[COL_OVERDUE_AMOUNT]);
        }

        void removeCase(String[] row) {
            assigned--;
            totalDays    -= parseDoubleStatic(row[COL_OVERDUE_DAYS]);
            totalPeriods -= parseDoubleStatic(row[COL_REPAID_PERIODS]);
            totalAmount  -= parseDoubleStatic(row[COL_OVERDUE_AMOUNT]);
        }

        private static double parseDoubleStatic(String s) {
            if (s == null || s.trim().isEmpty()) return 0.0;
            try { return Double.parseDouble(s.trim().replace(",", "")); }
            catch (NumberFormatException e) { return 0.0; }
        }
    }

    private static class TabState {
        final boolean isM3;
        File selectedFile;
        boolean fileEncrypted = false;   // 选文件后检测，控制密码行显隐
        JTextField filePathField;
        JPasswordField passwordField;
        JLabel passwordLabel;            // 「文件密码：」标签
        JComponent passwordRow;          // 密码输入框所在容器行（用于整体显隐）
        JPanel personnelContainer;
        JScrollPane personnelScrollPane;
        JLabel statusLabel;
        JLabel lblStats;
        JTextArea statsTextArea;
        DefaultTableModel dayStatsTableModel;
        JLabel ratioSumLabel;   // M3 合计显示
        JButton btnExport;
        DefaultTableModel tableModel;
        List<String[]> caseRows;
        List<String[]> assignedResult;
        /** M1/M2: 公司 -> 分案人员输入框 */
        Map<String, JTextArea> companyPersonnelAreas = new LinkedHashMap<>();
        /** M3: 公司 -> 占比输入框 */
        Map<String, JTextField> companyRatioFields = new LinkedHashMap<>();

        TabState(boolean isM3) { this.isM3 = isM3; }
    }
}
