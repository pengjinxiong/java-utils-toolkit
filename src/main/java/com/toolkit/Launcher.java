package com.toolkit;

import com.toolkit.assignment.OverdueCaseAssignmentTool;
import com.toolkit.converter.DocToHtmlTool;
import com.toolkit.pdf.PdfMergerTool;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * 工具集启动器
 * 提供统一的工具选择界面
 */
public class Launcher {

    private static final String VERSION = "1.0.0";
    private static final String APP_NAME = "Java 实用工具集";

    /** 全局唯一 Launcher 窗口，供子工具调用 showLauncher() 时复用 */
    private static JFrame launcherFrame;

    /** 子工具关闭时调用此方法回到主界面 */
    public static void showLauncher() {
        if (launcherFrame != null) {
            launcherFrame.setVisible(true);
            launcherFrame.toFront();
        }
    }

    // 工具注册表
    private static final ToolInfo[] TOOLS = {
            new ToolInfo("PDF 批量合并工具", "将多个文件夹中的图片和PDF按顺序合并",
                    e -> PdfMergerTool.launch(Launcher::showLauncher)),
            new ToolInfo("文档转 HTML 工具", "doc/docx/pdf 转换为 Java 可转 PDF 的 HTML",
                    e -> DocToHtmlTool.launch(Launcher::showLauncher)),
            new ToolInfo("月初逾期分案工具", "M1/M2/M3逾期案件分案，打乱均分且不与原催收员重复",
                    e -> OverdueCaseAssignmentTool.launch(Launcher::showLauncher)),
    };

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        System.setProperty("prism.allowHalveContent", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            try {
                new Launcher().createAndShowGUI();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "启动失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createAndShowGUI() {
        launcherFrame = new JFrame(APP_NAME + " v" + VERSION);
        launcherFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        launcherFrame.setSize(500, 400);
        launcherFrame.setMinimumSize(new Dimension(450, 350));
        launcherFrame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel(APP_NAME);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        DefaultListModel<ToolInfo> listModel = new DefaultListModel<>();
        for (ToolInfo tool : TOOLS) listModel.addElement(tool);

        JList<ToolInfo> toolList = new JList<>(listModel);
        toolList.setCellRenderer(new ToolListRenderer());
        toolList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        toolList.setFixedCellHeight(60);
        toolList.setBorder(BorderFactory.createTitledBorder("可用工具"));

        JScrollPane scrollPane = new JScrollPane(toolList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnRun = new JButton("启动选中工具");
        JButton btnExit = new JButton("退出");

        btnRun.addActionListener(e -> {
            ToolInfo selected = toolList.getSelectedValue();
            if (selected != null) {
                launcherFrame.setVisible(false);
                selected.action().accept(null);
            } else {
                JOptionPane.showMessageDialog(launcherFrame,
                        "请先选择一个工具", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        // 双击也可启动
        toolList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && toolList.getSelectedValue() != null) {
                    btnRun.doClick();
                }
            }
        });

        btnExit.addActionListener(e -> System.exit(0));
        bottomPanel.add(btnRun);
        bottomPanel.add(btnExit);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        launcherFrame.add(mainPanel);
        launcherFrame.setVisible(true);
    }

    private record ToolInfo(String name, String description, Consumer<Void> action) {
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Consumer<Void> action() { return action; }
    }

    private static class ToolListRenderer extends JPanel implements ListCellRenderer<ToolInfo> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel descLabel = new JLabel();

        public ToolListRenderer() {
            setLayout(new BorderLayout(5, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            nameLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
            descLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            descLabel.setForeground(Color.GRAY);
            add(nameLabel, BorderLayout.NORTH);
            add(descLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ToolInfo> list,
                ToolInfo value, int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.getName());
            descLabel.setText(value.getDescription());
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
                descLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
                descLabel.setForeground(Color.GRAY);
            }
            setEnabled(list.isEnabled());
            return this;
        }
    }
}