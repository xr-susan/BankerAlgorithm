package banker;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 银行家算法的设计与实现 - 明亮专业主题版
 * 按"20班19号"设置默认参数：进程数=2，资源种类数=3，可用资源总数=29
 */
public class BankerAlgorithm extends JFrame {
    private static final int DEFAULT_PROCESSES = 2;
    private static final int DEFAULT_RESOURCES = 3;
    private static final int DEFAULT_TOTAL = 29;

    // ── 明亮配色方案 ──
    private static final Color BG_DARK       = new Color(245, 247, 250);
    private static final Color BG_CARD       = new Color(255, 255, 255);
    private static final Color BG_INPUT      = new Color(240, 243, 248);
    private static final Color ACCENT_BLUE   = new Color(59, 130, 246);
    private static final Color ACCENT_GREEN  = new Color(34, 197, 94);
    private static final Color ACCENT_RED    = new Color(239, 68, 68);
    private static final Color ACCENT_ORANGE = new Color(249, 115, 22);
    private static final Color ACCENT_PURPLE = new Color(139, 92, 246);
    private static final Color TEXT_PRIMARY  = new Color(30, 41, 59);
    private static final Color TEXT_SECONDARY= new Color(100, 116, 139);
    private static final Color BORDER_COLOR  = new Color(226, 232, 240);
    private static final Color TABLE_HEADER  = new Color(241, 245, 249);
    private static final Color TABLE_ROW_EVEN= new Color(255, 255, 255);
    private static final Color TABLE_ROW_ODD = new Color(248, 250, 252);
    private static final Color TABLE_SELECT  = new Color(59, 130, 246, 40);

    private static String FONT_CN = "Microsoft YaHei UI";
    private static Font FONT_TITLE;
    private static Font FONT_SUBTITLE;
    private static Font FONT_BODY;
    private static final Font FONT_MONO     = new Font("Consolas", Font.PLAIN, 13);
    private static Font FONT_SMALL;

    /**
     * 在 main 中调用，检测可用中文字体并初始化所有字体对象
     */
    private static void initFonts() {
        String[] candidates = {
            "Microsoft YaHei UI", "Microsoft YaHei", "SimHei",
            "SimSun", "宋体", "微软雅黑",
            "Microsoft JhengHei", "PingFang SC", "WenQuanYi Micro Hei",
            "Noto Sans CJK SC", "Source Han Sans SC", "Dialog"
        };
        for (String name : candidates) {
            Font test = new Font(name, Font.PLAIN, 13);
            // 如果 getFamily 匹配，说明字体可用
            if (name.equals(test.getFamily()) || test.canDisplay('银')) {
                FONT_CN = name;
                break;
            }
        }
        FONT_TITLE    = new Font(FONT_CN, Font.BOLD, 22);
        FONT_SUBTITLE = new Font(FONT_CN, Font.BOLD, 14);
        FONT_BODY     = new Font(FONT_CN, Font.PLAIN, 13);
        FONT_SMALL    = new Font(FONT_CN, Font.PLAIN, 11);
    }

    /** 获取安全字体（确保中文可显示） */
    private static Font safeFont(Font base) {
        if (base == null) return new Font(FONT_CN, Font.PLAIN, 13);
        return base;
    }

    private int n = 0;
    private int m = 0;
    private int[] available;
    private int[] resourceTotals;
    private int[][] max;
    private int[][] allocation;
    private int[][] need;

    private JTextField tfProcesses;
    private JTextField tfResources;
    private JTextField tfTotal;
    private JTable tableMax;
    private JTable tableAllocation;
    private JTable tableNeed;
    private JTable tableAvailable;
    private DefaultTableModel modelMax;
    private DefaultTableModel modelAllocation;
    private DefaultTableModel modelNeed;
    private DefaultTableModel modelAvailable;
    private JTextField tfRequestProcess;
    private JTextField[] tfRequest;
    private JTextArea outputArea;
    private JPanel requestPanel;
    private JLabel statusLabel;
    private JLabel statusIndicator;

    public BankerAlgorithm() {
        super("银行家算法 · 死锁避免系统");
        initUI();
    }

    // ════════════════════════════════════════════════════════════════
    //  UI 初始化
    // ════════════════════════════════════════════════════════════════

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 860);
        setLocationRelativeTo(null);
        setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(240, 243, 248), 0, getHeight(), BG_DARK);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
        applyGlobalStyle();
        setVisible(true);
    }

    private void applyGlobalStyle() {
        UIManager.put("ToolTip.background", BG_CARD);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER_COLOR));
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.track", BG_DARK);
        UIManager.put("ScrollBar.thumb", new Color(200, 208, 218));
    }

    // ── 顶部标题栏 ──
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(255, 255, 255), getWidth(), 0, new Color(241, 245, 249));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // 底部渐变色
                g2.setPaint(new GradientPaint(0, getHeight() - 2, ACCENT_BLUE, getWidth(), getHeight() - 2, ACCENT_PURPLE));
                g2.fillRect(0, getHeight() - 2, getWidth(), 3);
                g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 64));
        header.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));

        // 左侧标题
        JPanel titleArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleArea.setOpaque(false);
        JLabel icon = new JLabel("◆"); // ◆ 黑色菱形
        icon.setFont(new Font(FONT_CN, Font.BOLD, 22));
        icon.setForeground(ACCENT_BLUE);
        titleArea.add(icon);
        JLabel title = new JLabel("银行家算法");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);
        titleArea.add(title);
        JLabel subtitle = new JLabel("Deadlock Avoidance System");
        subtitle.setFont(FONT_SMALL);
        subtitle.setForeground(TEXT_SECONDARY);
        titleArea.add(subtitle);
        header.add(titleArea, BorderLayout.WEST);

        // 右侧参数面板
        JPanel params = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 14));
        params.setOpaque(false);
        params.add(paramLabel("进程数"));
        tfProcesses = paramField(String.valueOf(DEFAULT_PROCESSES));
        params.add(tfProcesses);
        params.add(paramLabel("资源种类"));
        tfResources = paramField(String.valueOf(DEFAULT_RESOURCES));
        params.add(tfResources);
        params.add(paramLabel("资源总数"));
        tfTotal = paramField(String.valueOf(DEFAULT_TOTAL));
        params.add(tfTotal);
        params.add(accentButton("初始化", ACCENT_BLUE, e -> initSystem()));
        params.add(accentButton("载入示例", ACCENT_PURPLE, e -> loadDemo()));
        header.add(params, BorderLayout.EAST);

        return header;
    }

    // ── 主内容区 ──
    private JPanel buildMainContent() {
        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));

        // 左侧：矩阵卡片（上下排列）
        JPanel matrixPanel = new JPanel(new GridLayout(4, 1, 0, 8));
        matrixPanel.setOpaque(false);

        modelMax = new DefaultTableModel();
        tableMax = createStyledTable(modelMax, true);
        matrixPanel.add(card("最大需求矩阵  Max", tableMax, ACCENT_ORANGE));

        modelAllocation = new DefaultTableModel();
        tableAllocation = createStyledTable(modelAllocation, true);
        matrixPanel.add(card("已分配矩阵  Allocation", tableAllocation, ACCENT_GREEN));

        modelNeed = new DefaultTableModel();
        tableNeed = createStyledTable(modelNeed, false);
        matrixPanel.add(card("需求矩阵  Need", tableNeed, ACCENT_BLUE));

        modelAvailable = new DefaultTableModel();
        tableAvailable = createStyledTable(modelAvailable, false);
        matrixPanel.add(card("可用资源  Available", tableAvailable, ACCENT_PURPLE));

        // 右侧：输出 + 请求
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setOpaque(false);

        // 输出控制台
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(FONT_BODY);
        outputArea.setBackground(new Color(248, 250, 252));
        outputArea.setForeground(new Color(30, 41, 59));
        outputArea.setCaretColor(ACCENT_BLUE);
        outputArea.setMargin(new Insets(12, 12, 12, 12));
        outputArea.setSelectionColor(new Color(59, 130, 246, 50));
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        outScroll.getViewport().setBackground(new Color(248, 250, 252));
        rightPanel.add(card("运行输出  Console", outScroll, new Color(180, 220, 160)), BorderLayout.CENTER);

        // 请求面板 + 按钮
        JPanel bottomRight = new JPanel(new BorderLayout(0, 8));
        bottomRight.setOpaque(false);
        requestPanel = new JPanel();
        requestPanel.setLayout(new BoxLayout(requestPanel, BoxLayout.Y_AXIS));
        requestPanel.setOpaque(false);
        bottomRight.add(card("资源请求  Request", requestPanel, ACCENT_ORANGE), BorderLayout.CENTER);
        bottomRight.add(buildActionButtons(), BorderLayout.SOUTH);
        rightPanel.add(bottomRight, BorderLayout.SOUTH);

        // 分割面板
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, matrixPanel, rightPanel);
        split.setDividerLocation(620);
        split.setDividerSize(4);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setBackground(BG_DARK);
        main.add(split, BorderLayout.CENTER);

        return main;
    }

    // ── 底部操作按钮栏 ──
    private JPanel buildActionButtons() {
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        btnBar.setOpaque(false);
        btnBar.add(actionButton("安全性检测", ACCENT_GREEN, "[+]", e -> checkSafety()));
        btnBar.add(actionButton("提交请求", ACCENT_BLUE, "[>]", e -> processRequest()));
        btnBar.add(actionButton("重置状态", ACCENT_RED, "[R]", e -> resetState()));
        btnBar.add(actionButton("清空输出", TEXT_SECONDARY, "[X]", e -> outputArea.setText("")));
        return btnBar;
    }

    // ── 状态栏 ──
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(241, 245, 249));
        bar.setPreferredSize(new Dimension(0, 30));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 16, 0, 16)));

        statusIndicator = new JLabel("●");
        statusIndicator.setForeground(ACCENT_GREEN);
        statusIndicator.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        statusLabel = new JLabel("就绪 · 等待初始化");
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(TEXT_SECONDARY);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        left.setOpaque(false);
        left.add(statusIndicator);
        left.add(statusLabel);
        bar.add(left, BorderLayout.WEST);

        JLabel version = new JLabel("· 0班19号");
        version.setFont(FONT_SMALL);
        version.setForeground(TEXT_SECONDARY);
        bar.add(version, BorderLayout.EAST);

        return bar;
    }

    // ════════════════════════════════════════════════════════════════
    //  自定义组件工厂
    // ════════════════════════════════════════════════════════════════

    /** 卡片容器 */
    private JPanel card(String title, JComponent content, Color accent) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 卡片圆角背景
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                // 顶部强调色条
                g2.setColor(accent);
                g2.fillRoundRect(12, 0, getWidth() - 24, 3, 2, 2);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 8, 6, 8));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FONT_SUBTITLE);
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 0));
        card.add(titleLabel, BorderLayout.NORTH);

        content.setOpaque(false);
        if (content instanceof JScrollPane) {
            ((JScrollPane) content).getViewport().setOpaque(false);
        }
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    /** 创建样式化表格 */
    private JTable createStyledTable(DefaultTableModel model, boolean editable) {
        JTable table = new JTable(model) {
            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                if (c instanceof JComponent) ((JComponent) c).setOpaque(true);
                if (isRowSelected(row)) {
                    c.setBackground(TABLE_SELECT);
                } else {
                    c.setBackground(row % 2 == 0 ? TABLE_ROW_EVEN : TABLE_ROW_ODD);
                }
                if (c instanceof JLabel) {
                    ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                    if (col == 0) {
                        ((JLabel) c).setForeground(ACCENT_BLUE);
                        ((JLabel) c).setFont(FONT_BODY.deriveFont(Font.BOLD));
                    } else {
                        ((JLabel) c).setForeground(TEXT_PRIMARY);
                    }
                }
                return c;
            }

            @Override public Component prepareEditor(TableCellEditor editor, int row, int col) {
                Component c = super.prepareEditor(editor, row, col);
                if (c instanceof JTextField) {
                    c.setFont(FONT_MONO);
                    c.setBackground(BG_INPUT);
                    c.setForeground(TEXT_PRIMARY);
                    ((JTextField) c).setCaretColor(ACCENT_BLUE);
                    ((JTextField) c).setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(ACCENT_BLUE, 1),
                            BorderFactory.createEmptyBorder(2, 8, 2, 8)));
                }
                return c;
            }
        };
        table.setRowHeight(28);
        table.setFont(FONT_BODY);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(new Color(226, 232, 240));
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionBackground(TABLE_SELECT);
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setFocusable(false);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setEnabled(editable);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // 表头样式
        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HEADER);
        header.setForeground(TEXT_SECONDARY);
        header.setFont(FONT_SUBTITLE);
        header.setPreferredSize(new Dimension(0, 32));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_COLOR));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

        return table;
    }

    /** 参数标签 */
    private JLabel paramLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    /** 参数输入框 */
    private JTextField paramField(String text) {
        JTextField tf = new JTextField(text, 4) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
                if (hasFocus()) {
                    Graphics2D g3 = (Graphics2D) g.create();
                    g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g3.setColor(ACCENT_BLUE);
                    g3.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g3.dispose();
                }
            }
        };
        tf.setOpaque(false);
        tf.setFont(FONT_BODY);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(ACCENT_BLUE);
        tf.setBackground(BG_INPUT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        tf.setHorizontalAlignment(SwingConstants.CENTER);
        return tf;
    }

    /** 强调色圆角按钮（标题栏用） */
    private JButton accentButton(String text, Color bg, ActionListener listener) {
        Font btnFont = FONT_BODY.deriveFont(Font.BOLD);
        JButton btn = new JButton(text) {
            boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                Color c = hover ? bg.brighter() : bg;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Color.WHITE);
                g2.setFont(safeFont(btnFont));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(btnFont);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(90, 32));
        btn.addActionListener(listener);
        return btn;
    }

    /** 操作按钮（底部用，带图标） */
    private JButton actionButton(String text, Color bg, String icon, ActionListener listener) {
        Font btnFont = FONT_BODY.deriveFont(Font.BOLD);
        JButton btn = new JButton(icon + "  " + text) {
            boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                Color bgC = hover
                        ? new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 50)
                        : new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 25);
                g2.setColor(bgC);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(hover ? bg : new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 120));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 24, 24);
                g2.setColor(hover ? bg.brighter() : bg);
                g2.setFont(safeFont(btnFont));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(btnFont);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(140, 38));
        btn.addActionListener(listener);
        return btn;
    }

    // ════════════════════════════════════════════════════════════════
    //  业务逻辑（与原版一致）
    // ════════════════════════════════════════════════════════════════

    private void initSystem() {
        try {
            n = Integer.parseInt(tfProcesses.getText().trim());
            m = Integer.parseInt(tfResources.getText().trim());
            int total = Integer.parseInt(tfTotal.getText().trim());
            if (n <= 0 || m <= 0 || total <= 0) {
                error("参数必须为正整数。");
                return;
            }
            available = new int[m];
            resourceTotals = new int[m];
            max = new int[n][m];
            allocation = new int[n][m];
            need = new int[n][m];
            int base = total / m;
            int remain = total % m;
            for (int j = 0; j < m; j++) {
                resourceTotals[j] = base + (j < remain ? 1 : 0);
                available[j] = resourceTotals[j];
            }
            setupTables();
            buildRequestPanel();
            log("[OK] 系统初始化完成：进程数 = " + n + "，资源种类数 = " + m + "，资源总数 = " + total);
            log("     请在表格中填写 Max 和 Allocation，然后执行安全性检测或提交资源请求。");
            setStatus("已初始化 · " + n + " 进程 · " + m + " 种资源", ACCENT_GREEN);
        } catch (NumberFormatException ex) {
            error("请输入合法数字。");
        }
    }

    private void setupTables() {
        String[] cols = new String[m];
        for (int j = 0; j < m; j++) cols[j] = "R" + j;
        setupModel(modelMax, cols);
        setupModel(modelAllocation, cols);
        setupModel(modelNeed, cols);
        setupAvailableModel(cols);
    }

    private void setupModel(DefaultTableModel model, String[] cols) {
        model.setColumnCount(0);
        model.setRowCount(0);
        model.addColumn("进程");
        for (String col : cols) model.addColumn(col);
        for (int i = 0; i < n; i++) {
            Object[] row = new Object[m + 1];
            row[0] = "P" + i;
            for (int j = 1; j <= m; j++) row[j] = 0;
            model.addRow(row);
        }
    }

    private void setupAvailableModel(String[] cols) {
        modelAvailable.setColumnCount(0);
        modelAvailable.setRowCount(0);
        for (String col : cols) modelAvailable.addColumn(col);
        Object[] row = new Object[m];
        for (int j = 0; j < m; j++) row[j] = available[j];
        modelAvailable.addRow(row);
    }

    private void buildRequestPanel() {
        requestPanel.removeAll();
        requestPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row1.setOpaque(false);
        JLabel lbl = new JLabel("进程号  P");
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT_SECONDARY);
        row1.add(lbl);
        tfRequestProcess = paramField("0");
        tfRequestProcess.setPreferredSize(new Dimension(50, 28));
        row1.add(tfRequestProcess);
        requestPanel.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.setOpaque(false);
        JLabel lbl2 = new JLabel("请求量");
        lbl2.setFont(FONT_BODY);
        lbl2.setForeground(TEXT_SECONDARY);
        row2.add(lbl2);
        tfRequest = new JTextField[m];
        for (int j = 0; j < m; j++) {
            JLabel rl = new JLabel("R" + j);
            rl.setFont(FONT_BODY);
            rl.setForeground(ACCENT_BLUE);
            row2.add(rl);
            tfRequest[j] = paramField("0");
            tfRequest[j].setPreferredSize(new Dimension(50, 28));
            row2.add(tfRequest[j]);
        }
        requestPanel.add(row2);
        requestPanel.revalidate();
        requestPanel.repaint();
    }

    private void loadDemo() {
        tfProcesses.setText(String.valueOf(DEFAULT_PROCESSES));
        tfResources.setText(String.valueOf(DEFAULT_RESOURCES));
        tfTotal.setText(String.valueOf(DEFAULT_TOTAL));
        initSystem();
        int[][] demoMax = {{9, 3, 2}, {3, 2, 2}};
        int[][] demoAlloc = {{0, 1, 0}, {2, 0, 0}};
        for (int i = 0; i < demoMax.length; i++) {
            for (int j = 0; j < demoMax[i].length; j++) {
                modelMax.setValueAt(demoMax[i][j], i, j + 1);
                modelAllocation.setValueAt(demoAlloc[i][j], i, j + 1);
            }
        }
        int total = Integer.parseInt(tfTotal.getText().trim());
        int base = total / m;
        int remain = total % m;
        for (int j = 0; j < m; j++) {
            int totalForType = base + (j < remain ? 1 : 0);
            int allocated = 0;
            for (int i = 0; i < n; i++) {
                allocated += demoAlloc[i][j];
                need[i][j] = demoMax[i][j] - demoAlloc[i][j];
            }
            available[j] = totalForType - allocated;
        }
        refreshNeedAndAvailable();
        log("[DEMO] 示例数据已载入。");
        setStatus("已载入示例数据", ACCENT_PURPLE);
    }

    private boolean readTables() {
        try {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    max[i][j] = Integer.parseInt(modelMax.getValueAt(i, j + 1).toString().trim());
                    allocation[i][j] = Integer.parseInt(modelAllocation.getValueAt(i, j + 1).toString().trim());
                }
            }
        } catch (NumberFormatException ex) {
            error("表格中存在无效数据，请检查。");
            return false;
        }

        // 需求矩阵与可用资源统一交给 BankerSolver 推导，界面只负责展示结果。
        try {
            need = BankerSolver.reshape(BankerSolver.computeNeed(max, allocation), n, m);
            available = BankerSolver.computeAvailable(resourceTotals, allocation);
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
            return false;
        }
        refreshNeedAndAvailable();
        return true;
    }

    private void refreshNeedAndAvailable() {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                modelNeed.setValueAt(need[i][j], i, j + 1);
        for (int j = 0; j < m; j++)
            modelAvailable.setValueAt(available[j], 0, j);
    }

    private void checkSafety() {
        if (n == 0) { error("请先初始化系统。"); return; }
        if (!readTables()) return;
        log("\n======== 安全性检测  Safety Check ========");
        BankerSolver.SafetyReport report = BankerSolver.checkSafety(available, allocation, need);
        logSteps(report);
        if (report.safe()) {
            log("\n[SAFE] 系统处于安全状态");
            log("       安全序列：" + joinSequence(report.sequence()));
            setStatus("安全状态 · " + joinSequence(report.sequence()), ACCENT_GREEN);
        } else {
            log("\n[WARN] 系统处于不安全状态，无可用安全序列");
            setStatus("不安全状态", ACCENT_RED);
        }
    }

    /** 把安全性检测的每一步渲染成对齐的日志表格。 */
    private void logSteps(BankerSolver.SafetyReport report) {
        if (report.steps().isEmpty()) return;
        log("  步骤  进程  Need               Work");
        log("  " + "─".repeat(50));
        for (BankerSolver.Step step : report.steps()) {
            log(String.format("   #%-2d  P%-2d  %-18s %s → %s",
                    step.order(), step.process(), BankerSolver.format(step.need()),
                    BankerSolver.format(step.workBefore()), BankerSolver.format(step.workAfter())));
        }
        log("  " + "─".repeat(50));
    }

    private void processRequest() {
        if (n == 0) { error("请先初始化系统。"); return; }
        if (!readTables()) return;
        int pid;
        int[] request = new int[m];
        try {
            pid = Integer.parseInt(tfRequestProcess.getText().trim());
            if (pid < 0 || pid >= n) {
                error("进程号超出范围 [0, " + (n - 1) + "]。");
                return;
            }
            for (int j = 0; j < m; j++) {
                request[j] = Integer.parseInt(tfRequest[j].getText().trim());
                if (request[j] < 0) throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            error("资源请求输入无效。");
            return;
        }

        log("\n======== 银行家算法 · P" + pid + " 资源请求处理 ========");
        log("  Request = " + BankerSolver.format(request));

        BankerSolver.RequestReport report =
                BankerSolver.evaluateRequest(available, allocation, need, pid, request);

        switch (report.status()) {
            case EXCEEDS_NEED -> {
                int j = report.violatingResource();
                log("  [FAIL] 请求超出最大需求：R" + j + " 请求=" + request[j]
                        + " > Need=" + need[pid][j]);
                setStatus("请求被拒绝：超出需求", ACCENT_RED);
            }
            case INSUFFICIENT_RESOURCES -> {
                log("  [WAIT] 可用资源不足，P" + pid + " 需要等待");
                setStatus("等待中：资源不足", ACCENT_ORANGE);
            }
            case UNSAFE -> {
                log("  [FAIL] 拒绝分配！该请求会使系统进入不安全状态。");
                setStatus("分配被拒绝：不安全", ACCENT_RED);
            }
            case GRANTED -> {
                log("  试探分配 → Available = " + BankerSolver.format(report.available()));
                log("             Allocation[" + pid + "] = "
                        + BankerSolver.format(report.allocation()[pid]));
                log("             Need[" + pid + "] = " + BankerSolver.format(report.need()[pid]));
                logSteps(report.safety());

                available = report.available();
                allocation = report.allocation();
                need = report.need();
                refreshNeedAndAvailable();

                log("\n  [OK] 允许分配！");
                log("       安全序列：" + joinSequence(report.safety().sequence()));
                setStatus("分配成功 · " + joinSequence(report.safety().sequence()), ACCENT_GREEN);
            }
        }
    }

    private void resetState() {
        if (n == 0) return;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                modelMax.setValueAt(0, i, j + 1);
                modelAllocation.setValueAt(0, i, j + 1);
                modelNeed.setValueAt(0, i, j + 1);
                max[i][j] = 0; allocation[i][j] = 0; need[i][j] = 0;
            }
        for (int j = 0; j < m; j++) {
            available[j] = resourceTotals[j];
            modelAvailable.setValueAt(available[j], 0, j);
        }
        log("[RESET] 状态已重置。");
        setStatus("已重置", TEXT_SECONDARY);
    }

    // ════════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════════

    private void log(String msg) {
        outputArea.append(msg + "\n");
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusIndicator.setForeground(color);
    }

    private String joinSequence(List<Integer> seq) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seq.size(); i++) {
            sb.append("P").append(seq.get(i));
            if (i < seq.size() - 1) sb.append(" → ");
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  入口
    // ════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        // 全局启用文本抗锯齿
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}


            // 检测可用中文字体（优先使用 canDisplay 验证）
            String cnFontName = "Microsoft YaHei UI";
            String[] candidates = {
                "Microsoft YaHei UI", "Microsoft YaHei", "SimHei",
                "微软雅黑", "SimSun", "宋体",
                "Noto Sans CJK SC", "Source Han Sans SC", "Dialog"
            };
            for (String name : candidates) {
                Font test = new Font(name, Font.PLAIN, 13);
                if (name.equals(test.getFamily()) || test.canDisplay("银".charAt(0))) {
                    cnFontName = name;
                    break;
                }
            }
            Font cn = new Font(cnFontName, Font.PLAIN, 13);
            Font cnBold = new Font(cnFontName, Font.BOLD, 13);
            String[] keys = {
                "defaultFont", "Button.font", "Label.font", "TextField.font",
                "TextArea.font", "Table.font", "TableHeader.font",
                "TitledBorder.font", "OptionPane.messageFont", "OptionPane.buttonFont",
                "Menu.font", "MenuItem.font", "ComboBox.font", "Spinner.font",
                "CheckBox.font", "RadioButton.font", "TabbedPane.font", "List.font", "Tree.font"
            };
            for (String key : keys) {
                UIManager.put(key, key.contains("Header") || key.contains("Button") ? cnBold : cn);
            }

            initFonts();
            new BankerAlgorithm();
        });
    }
}

