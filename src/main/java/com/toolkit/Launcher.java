package com.toolkit;

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

    // 工具注册表
    private static final ToolInfo[] TOOLS = {
            new ToolInfo("PDF 批量合并工具", "将多个文件夹中的图片和PDF按顺序合并", e -> PdfMergerTool.main(null)),
            // 未来可以添加更多工具
            // new ToolInfo("图片压缩工具", "批量压缩图片大小", e -> ImageCompressorTool.main(null)),
            // new ToolInfo("文件重命名工具", "批量重命名文件", e -> FileRenamerTool.main(null)),
            // new ToolInfo("二维码生成器", "生成二维码图片", e -> QrCodeTool.main(null)),
    };

    public static void main(String[] args) {
        // 设置系统属性，优化Windows高DPI支持
        System.setProperty("sun.java2d.uiScale", "1.0");
        System.setProperty("prism.allowHalveContent", "true");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            try {
                new Launcher().createAndShowGUI();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "启动失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame(APP_NAME + " v" + VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setMinimumSize(new Dimension(450, 350));
        frame.setLocationRelativeTo(null);

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 标题
        JLabel titleLabel = new JLabel(APP_NAME);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 工具列表
        DefaultListModel<ToolInfo> listModel = new DefaultListModel<>();
        for (ToolInfo tool : TOOLS) {
            listModel.addElement(tool);
        }

        JList<ToolInfo> toolList = new JList<>(listModel);
        toolList.setCellRenderer(new ToolListRenderer());
        toolList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        toolList.setFixedCellHeight(60);
        toolList.setBorder(BorderFactory.createTitledBorder("可用工具"));

        JScrollPane scrollPane = new JScrollPane(toolList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton btnRun = new JButton("启动选中工具");
        JButton btnExit = new JButton("退出");

        btnRun.addActionListener(e -> {
            ToolInfo selected = toolList.getSelectedValue();
            if (selected != null) {
                frame.setVisible(false);
                selected.action().accept(null);
                // 工具关闭后重新显示主界面（可选）
                // frame.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(frame,
                        "请先选择一个工具", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnExit.addActionListener(e -> System.exit(0));

        bottomPanel.add(btnRun);
        bottomPanel.add(btnExit);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // 工具信息记录类
    private record ToolInfo(String name, String description, Consumer<Void> action) {
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Consumer<Void> action() { return action; }
    }

    // 自定义列表渲染器
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