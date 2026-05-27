package com.toolkit.converter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数变量替换对话框
 * 自动识别 HTML 中的 ${paramN ! ""} 占位符
 * 左侧展示 ProtocolVo / ProtocolRentalVo 字段列表（含注释），点击自动填充到当前输入框
 */
public class ParamReplaceDialog extends JDialog {

    private final JTextArea sourceArea;
    private final Map<String, JTextField> paramFields = new LinkedHashMap<>();
    private JTextField currentFocusField = null;
    private boolean applied = false;

    /* ============================================================
       ProtocolVo 字段定义（含类型与注释）
       ============================================================ */
    private static final List<FieldItem> PROTOCOL_VO_FIELDS = Arrays.asList(
            // ---------- 基础信息 ----------
            new FieldItem("orderId", "Long", "订单ID"),
            new FieldItem("componentOrderId", "String", "组件订单ID"),
            new FieldItem("parentOrderSn", "String", "父订单编号"),
            new FieldItem("protocolType", "String", "协议类型"),
            new FieldItem("contractType", "Integer", "合同类型"),
            new FieldItem("contractName", "String", "合同名称"),
            new FieldItem("contractNo", "String", "合同编号"),
            new FieldItem("mainContractNo", "String", "主合同编号"),
            new FieldItem("mainContractName", "String", "主合同名称"),
            new FieldItem("oldContractNo", "String", "原合同编号"),
            new FieldItem("signDate", "String", "签署日期 (yyyy-MM-dd)"),
            new FieldItem("signDateForChinese", "String", "签署日期中文 (yyyy年MM月dd日)"),
            new FieldItem("oldSignDate", "String", "原签署日期"),
            new FieldItem("hasSign", "int", "是否调用电子签章 (1=使用 2=不使用)"),

            // ---------- 租赁用户信息 ----------
            new FieldItem("memberAccountId", "String", "会员账户ID"),
            new FieldItem("memberId", "Long", "会员ID"),
            new FieldItem("realName", "String", "姓名"),
            new FieldItem("idcardNum", "String", "证件号码"),
            new FieldItem("mobile", "String", "手机号"),
            new FieldItem("address", "String", "收货地址"),
            new FieldItem("email", "String", "邮箱"),
            new FieldItem("censusAddress", "String", "户籍所在地"),
            new FieldItem("workplace", "String", "工作单位"),
            new FieldItem("contactRelation", "String", "紧急联系人关系 (1父母2子女3兄弟姐妹4朋友5配偶6亲戚7同学8同事9其他)"),
            new FieldItem("contactName", "String", "紧急联系人姓名"),
            new FieldItem("contactPhone", "String", "紧急联系人电话"),
            new FieldItem("contactRelation2", "String", "紧急联系人2关系"),
            new FieldItem("contactName2", "String", "紧急联系人2姓名"),
            new FieldItem("contactPhone2", "String", "紧急联系人2电话"),
            new FieldItem("contactRelation3", "String", "紧急联系人3关系"),
            new FieldItem("contactName3", "String", "紧急联系人3姓名"),
            new FieldItem("contactPhone3", "String", "紧急联系人3电话"),

            // ---------- 租赁商户信息 ----------
            new FieldItem("sellerAccountId", "String", "商户账户ID"),
            new FieldItem("sellerId", "Long", "商户ID"),
            new FieldItem("sellerPlatName", "String", "商户平台名称"),
            new FieldItem("sellerName", "String", "商户名称"),
            new FieldItem("sellerCardNum", "String", "商户证件号"),
            new FieldItem("sellerAddress", "String", "商户地址"),
            new FieldItem("sellerMobile", "String", "商户手机号"),
            new FieldItem("sellerEmail", "String", "商户邮箱"),
            new FieldItem("sellerType", "Integer", "商户类型 (默认2)"),
            new FieldItem("sellerIdType", "String", "商户证件类型"),
            new FieldItem("sellerLegalPerson", "String", "企业法人姓名"),
            new FieldItem("sellerLegalPersonIdCardNum", "String", "企业法人证件号"),

            // ---------- 平台方 ----------
            new FieldItem("platformAccountId", "String", "平台账户ID"),
            new FieldItem("platformId", "Long", "平台ID"),
            new FieldItem("platformName", "String", "公司名称"),
            new FieldItem("platformCorp", "String", "平台名称"),
            new FieldItem("platformCardNum", "String", "平台证件号"),
            new FieldItem("platformAddress", "String", "平台地址"),
            new FieldItem("platformMobile", "String", "平台手机号"),
            new FieldItem("platformEmail", "String", "平台邮箱"),
            new FieldItem("platformLegalPerson", "String", "平台法人姓名"),
            new FieldItem("platformLegalPersonIdCardNum", "String", "平台法人证件号"),

            // ---------- 商品信息 ----------
            new FieldItem("orderSn", "String", "订单编号"),
            new FieldItem("goodsSerialNum", "String", "商品序列号"),
            new FieldItem("goodsPriceCH", "String", "商品价格大写"),
            new FieldItem("productColor", "String", "商品颜色"),
            new FieldItem("quality", "String", "成色"),
            new FieldItem("specId", "String", "套餐规格"),
            new FieldItem("productName", "String", "商品名称"),
            new FieldItem("productBrandName", "String", "设备品牌"),
            new FieldItem("productNum", "Integer", "商品数量"),
            new FieldItem("excutePayment", "Integer", "付租方式"),
            new FieldItem("excutePaymentName", "String", "付租方式名称"),
            new FieldItem("rentTypeName", "String", "租赁方式"),
            new FieldItem("depositAllAmt", "String", "总押金"),
            new FieldItem("productPrice", "String", "市场价"),
            new FieldItem("renewalProductPrice", "String", "续租总租金"),
            new FieldItem("buyPrice", "String", "买断价"),
            new FieldItem("moneyOrder", "String", "订单总金额"),
            new FieldItem("renewalBuyPrice", "String", "续租买断价"),
            new FieldItem("buyAmt", "String", "租满后买断金 (买断价-总租金)"),
            new FieldItem("renewalBuyAmt", "String", "续租后买断金 (买断价-总租金)"),
            new FieldItem("signRentAmt", "String", "单期租金"),
            new FieldItem("term", "String", "租赁期限"),
            new FieldItem("rentalAllRent", "String", "总租金"),
            new FieldItem("freeDepositAmt", "String", "减免押金"),
            new FieldItem("aliFreezeDepositAmt", "String", "冻结押金"),
            new FieldItem("rentSafeAmt", "String", "租享优金额"),
            new FieldItem("logisticsAmt", "String", "运费"),
            new FieldItem("payDay", "String", "月支付日"),
            new FieldItem("payTotalMoney", "long", "当前订单以买的方式需要收取的总额(单位分)"),
            new FieldItem("newRentalAllRent", "String", "新总租金"),
            new FieldItem("newBayoutPrice", "String", "新买断价"),

            // ---------- 租赁日期与客服 ----------
            new FieldItem("rentBeginDate", "String", "租赁开始日期"),
            new FieldItem("rentEndDate", "String", "租赁结束日期"),
            new FieldItem("kfMobile", "String", "客服电话"),
            new FieldItem("kfWx", "String", "客服微信"),

            // ---------- 新版合同 ----------
            new FieldItem("threeAmt", "String", "3期总租金"),
            new FieldItem("renewalAmt", "String", "续租租金"),
            new FieldItem("threeTotalAmt", "String", "续租9期总租金"),
            new FieldItem("renewalTotalAmt", "String", "续租总租金"),
            new FieldItem("threeTime", "Date", "三期时间"),

            // ---------- 金融/支付 ----------
            new FieldItem("excuteExpiresType", "Integer", "执行到期类型"),
            new FieldItem("excuteExpiresTypeDesc", "String", "执行到期类型描述"),
            new FieldItem("bankCardName", "String", "银行卡名称"),
            new FieldItem("bankCardNum", "String", "银行卡号"),
            new FieldItem("termTotal", "Integer", "总期数"),
            new FieldItem("mainContractSign", "String", "主合同签署"),
            new FieldItem("unPayAmt", "String", "未付金额"),
            new FieldItem("alipayOpenId", "String", "支付宝OpenID"),
            new FieldItem("loanAmount", "String", "融资本金 (东恒创)"),
            new FieldItem("dhcTotalRepayAmount", "String", "资方合计总应收 (东恒创)"),
            new FieldItem("platformTotalServiceAmount", "String", "系统平台服务费 (东恒创)"),
            new FieldItem("deliverTime", "String", "发货时间"),
            new FieldItem("recieveTime", "String", "签收时间"),

            // ---------- 其他 ----------
            new FieldItem("partNum", "Integer", "合同方数量"),
            new FieldItem("switchOne", "Integer", "开关一"),
            new FieldItem("userEmail", "String", "用户邮箱"),
            new FieldItem("colour", "String", "颜色"),
            new FieldItem("beginPrice", "String", "开始价格"),
            new FieldItem("endPrice", "String", "结束价格"),
            new FieldItem("endTerms", "String", "结束条款"),

            // ---------- ProtocolRentalVo 列表字段（rentalList / renewalList 子项） ----------
            new FieldItem("rentalList", "List", "还租计划列表"),
            new FieldItem("renewalList", "List", "续租计划列表"),
            new FieldItem("dhcLoanTrialModels", "List", "融资账单列表 (东恒创)"),
            new FieldItem("rentalId", "Long", "【子项】还租ID"),
            new FieldItem("rental.term", "Integer", "【子项】期数"),
            new FieldItem("rental.rentBackTime", "String", "【子项】还租日期"),
            new FieldItem("rental.rentAmt", "String", "【子项】租金金额"),
            new FieldItem("rental.payDate", "Long", "【子项】付款日期"),
            new FieldItem("rental.payMoney", "Long", "【子项】付款金额"),
            new FieldItem("rental.payStatus", "Integer", "【子项】付款状态 (1已付完 2否 3首付款等待付款)"),
            new FieldItem("rental.immediatelyDeduction", "Boolean", "【子项】立即扣款")
    );

    /* ============================================================
       内部类：字段项
       ============================================================ */
    private static class FieldItem {
        final String name;
        final String type;
        final String comment;

        FieldItem(String name, String type, String comment) {
            this.name = name;
            this.type = type;
            this.comment = comment;
        }

        @Override
        public String toString() {
            return name + "  (" + type + ")  " + comment;
        }
    }

    /* ============================================================
       构造函数
       ============================================================ */
    private ParamReplaceDialog(JDialog parent, JTextArea sourceArea) {
        super(parent, "变量替换", true);
        this.sourceArea = sourceArea;
        setSize(950, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // 提取所有 param 占位符
        List<String> params = extractParams(sourceArea.getText());
        if (params.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "当前 HTML 中没有发现 ${paramN ! \"\"} 占位符",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            return;
        }

        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // ---------- 顶部说明 ----------
        JLabel hint = new JLabel("右侧填写替换内容，左侧点击 ProtocolVo 字段可自动填入当前输入框；留空则不替换，保留原占位符");
        hint.setFont(new Font("PingFang SC", Font.PLAIN, 12));
        hint.setForeground(Color.DARK_GRAY);
        add(hint, BorderLayout.NORTH);

        // ---------- 中间分割面板 ----------
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildFieldPanel(), buildParamPanel(params));
        split.setDividerLocation(360);
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);

        // ---------- 底部按钮 ----------
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnApply = new JButton("应用替换");
        JButton btnCancel = new JButton("取消");
        btnApply.setBackground(new Color(50, 120, 200));
        btnApply.setForeground(Color.WHITE);
        btnApply.setFocusPainted(false);
        btnApply.setPreferredSize(new Dimension(100, 32));
        btnCancel.setPreferredSize(new Dimension(80, 32));
        btnPanel.add(btnApply);
        btnPanel.add(btnCancel);
        add(btnPanel, BorderLayout.SOUTH);

        btnApply.addActionListener(e -> applyReplacement());
        btnCancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(btnApply);
    }

    /* ============================================================
       左侧面板：ProtocolVo 字段列表 + 搜索
       ============================================================ */
    private JPanel buildFieldPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("ProtocolVo 字段 (点击填入)"));

        // 搜索框
        JTextField searchField = new JTextField();
        searchField.setToolTipText("输入关键字过滤字段");
        panel.add(searchField, BorderLayout.NORTH);

        // 字段列表
        DefaultListModel<FieldItem> listModel = new DefaultListModel<>();
        for (FieldItem item : PROTOCOL_VO_FIELDS) {
            listModel.addElement(item);
        }

        JList<FieldItem> fieldList = new JList<>(listModel);
        fieldList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fieldList.setFont(new Font("Consolas", Font.PLAIN, 12));
        fieldList.setFixedCellHeight(22);
        fieldList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                FieldItem item = (FieldItem) value;
                if (!isSelected) {
                    label.setForeground(Color.DARK_GRAY);
                }
                label.setToolTipText("<html><b>" + item.name + "</b> (" + item.type + ")<br/>" + item.comment + "</html>");
                return label;
            }
        });

        // 点击字段自动填入当前输入框
        fieldList.addListSelectionListener(new ListSelectionListener() {
            private boolean adjusting = false;

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || adjusting) return;
                FieldItem item = fieldList.getSelectedValue();
                if (item != null && currentFocusField != null) {
                    adjusting = true;
                    currentFocusField.setText("${" + item.name + " ! \"\"}");
                    currentFocusField.requestFocusInWindow();
                    adjusting = false;
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(fieldList);
        panel.add(listScroll, BorderLayout.CENTER);

        // 搜索过滤
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String keyword = searchField.getText().trim().toLowerCase();
                listModel.clear();
                for (FieldItem item : PROTOCOL_VO_FIELDS) {
                    if (keyword.isEmpty()
                            || item.name.toLowerCase().contains(keyword)
                            || item.comment.toLowerCase().contains(keyword)
                            || item.type.toLowerCase().contains(keyword)) {
                        listModel.addElement(item);
                    }
                }
            }
        });

        // 统计标签
        JLabel countLabel = new JLabel("共 " + PROTOCOL_VO_FIELDS.size() + " 个字段");
        countLabel.setFont(new Font("PingFang SC", Font.PLAIN, 11));
        countLabel.setForeground(Color.GRAY);
        countLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        panel.add(countLabel, BorderLayout.SOUTH);

        return panel;
    }

    /* ============================================================
       右侧面板：param 占位符输入区
       ============================================================ */
    private JPanel buildParamPanel(List<String> params) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("变量替换 (共 " + params.size() + " 个)"));

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        for (int i = 0; i < params.size(); i++) {
            String param = params.get(i);

            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            JLabel label = new JLabel(param + "：");
            label.setFont(new Font("Consolas", Font.BOLD, 13));
            label.setPreferredSize(new Dimension(160, 26));
            fieldsPanel.add(label, gbc);

            gbc.gridx = 1; gbc.weightx = 1.0;
            JTextField field = new JTextField(30);
            field.setFont(new Font("PingFang SC", Font.PLAIN, 13));
            fieldsPanel.add(field, gbc);

            // 焦点跟踪：记录当前正在编辑的输入框
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    currentFocusField = field;
                }
            });

            paramFields.put(param, field);
        }

        // 填充底部空间
        gbc.gridy = params.size();
        gbc.weighty = 1.0;
        fieldsPanel.add(Box.createVerticalGlue(), gbc);

        JScrollPane scroll = new JScrollPane(fieldsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        wrapper.add(scroll, BorderLayout.CENTER);

        return wrapper;
    }

    /* ============================================================
       工具方法
       ============================================================ */

    /**
     * 从 HTML 中提取所有 ${paramN ! ""} 占位符，按顺序去重
     */
    private static List<String> extractParams(String html) {
        Pattern pattern = Pattern.compile("\\$\\{param\\d+ ! \"\"\\}");
        Matcher matcher = pattern.matcher(html);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        while (matcher.find()) {
            set.add(matcher.group());
        }
        return new ArrayList<>(set);
    }

    /**
     * 执行替换：将 sourceArea 中的 ${paramN ! ""} 替换为用户输入的值
     * 空白输入框跳过不替换；替换完成后清理未被替换的 param-placeholder 标红样式
     */
    private void applyReplacement() {
        String html = sourceArea.getText();
        for (Map.Entry<String, JTextField> entry : paramFields.entrySet()) {
            String param = entry.getKey();
            String value = entry.getValue().getText();
            if (!value.isEmpty()) {
                html = html.replace(param, value);
            }
        }
        // 清理未被替换的 param-placeholder 包装，还原原本颜色
        html = html.replaceAll("<span class=\"param-placeholder\">(.*?)</span>", "$1");
        sourceArea.setText(html);
        sourceArea.setCaretPosition(0);
        applied = true;
        dispose();
    }

    /**
     * 显示变量替换对话框
     *
     * @return true 表示用户点击了应用替换
     */
    public static boolean showDialog(JDialog parent, JTextArea sourceArea) {
        ParamReplaceDialog dialog = new ParamReplaceDialog(parent, sourceArea);
        if (!dialog.paramFields.isEmpty()) {
            dialog.setVisible(true);
        }
        return dialog.applied;
    }
}
