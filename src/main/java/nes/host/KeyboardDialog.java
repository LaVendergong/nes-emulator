package nes.host;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 画出键盘：涂色=已绑定。左键点涂色键开始换绑，再按键或点键盘完成。 */
final class KeyboardDialog {
    private KeyboardDialog() {}

    static void show(JFrame parent, KeyBindings keys) {
        JDialog dialog = new JDialog(parent, "切换按键", true);
        JLabel hint = new JLabel("左键点击涂色按键开始换绑，再按键盘或点图上的键。Esc 取消。");
        KeyboardView view = new KeyboardView(keys, hint);
        JPanel chips = chips(keys, view);
        view.onChange(() -> rebuildChips(chips, keys, view));

        JButton reset = new JButton("恢复默认");
        reset.addActionListener(e -> {
            keys.resetDefaults();
            view.cancelWait();
            view.repaint();
            rebuildChips(chips, keys, view);
        });
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dialog.dispose());

        JPanel south = new JPanel();
        south.add(reset);
        south.add(close);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        hint.setAlignmentX(0f);
        north.add(hint);
        north.add(Box.createVerticalStrut(6));
        north.add(legend());

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        chips.setAlignmentX(0f);
        south.setAlignmentX(0f);
        bottom.add(chips);
        bottom.add(south);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(north, BorderLayout.NORTH);
        root.add(view, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        KeyEventDispatcher dispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !dialog.isShowing()) {
                return false;
            }
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                if (view.waiting()) {
                    view.cancelWait();
                } else {
                    dialog.dispose();
                }
                return true;
            }
            if (view.waiting()) {
                view.assign(e.getKeyCode());
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
            }
        });
        dialog.pack();
        Dimension need = dialog.getPreferredSize();
        dialog.setMinimumSize(need);
        dialog.setSize(need);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static JPanel legend() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        for (int pad : new int[] {
                KeyBindings.A, KeyBindings.B, KeyBindings.SELECT, KeyBindings.START,
                KeyBindings.UP, KeyBindings.DOWN, KeyBindings.LEFT, KeyBindings.RIGHT
        }) {
            JLabel box = new JLabel(" " + KeyBindings.padName(pad) + " ");
            box.setOpaque(true);
            box.setBackground(KeyboardView.color(pad));
            box.setForeground(Color.WHITE);
            box.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            p.add(box);
            p.add(Box.createHorizontalStrut(6));
        }
        p.add(hostSwatch());
        return p;
    }

    private static JLabel hostSwatch() {
        JLabel box = new JLabel(" Host ");
        box.setOpaque(true);
        box.setBackground(KeyboardView.HOST);
        box.setForeground(Color.WHITE);
        box.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return box;
    }

    private static JPanel chips(KeyBindings keys, KeyboardView view) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        rebuildChips(p, keys, view);
        return p;
    }

    private static void rebuildChips(JPanel p, KeyBindings keys, KeyboardView view) {
        p.removeAll();
        p.add(new JLabel("当前："));
        for (Map.Entry<Integer, Integer> e : keys.entries()) {
            int code = e.getKey();
            int pad = e.getValue();
            JLabel b = new JLabel(" " + KeyBindings.padName(pad) + "=" + KeyBindings.keyName(code) + " ");
            b.setOpaque(true);
            b.setBackground(KeyboardView.color(pad));
            b.setForeground(Color.WHITE);
            b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent ev) {
                    if (ev.getButton() == MouseEvent.BUTTON1) {
                        view.startWait(code);
                    }
                }
            });
            p.add(b);
            p.add(Box.createHorizontalStrut(4));
        }
        p.revalidate();
        p.repaint();
    }

    private static final class KeyboardView extends JPanel {
        static final Color HOST = new Color(0x55, 0x55, 0x55);
        static final Color FREE = new Color(0xE6, 0xE6, 0xE6);
        private static final int U = 42;
        private static final int G = 5;
        private static final int PAD = 8;

        private final KeyBindings keys;
        private final JLabel hint;
        private final List<Cap> caps = new ArrayList<>();
        private final Dimension board = new Dimension(0, 0);
        private int waitingKey = -1;
        private Runnable onChange = () -> {};

        KeyboardView(KeyBindings keys, JLabel hint) {
            this.keys = keys;
            this.hint = hint;
            layoutCaps();
            int w = 0;
            int h = 0;
            for (Cap c : caps) {
                w = Math.max(w, c.x + c.w);
                h = Math.max(h, c.y + c.h);
            }
            board.setSize(w + PAD, h + PAD);
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    Cap hit = hit(e.getX(), e.getY());
                    if (hit == null) {
                        return;
                    }
                    if (waiting()) {
                        assign(hit.code);
                        return;
                    }
                    if (keys.isBound(hit.code)) {
                        startWait(hit.code);
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(board);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(board);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(board);
        }

        void onChange(Runnable r) {
            onChange = r;
        }

        boolean waiting() {
            return waitingKey >= 0;
        }

        void startWait(int keyCode) {
            if (!keys.isBound(keyCode)) {
                return;
            }
            waitingKey = keyCode;
            hint.setText("正在绑定 " + KeyBindings.padName(keys.mask(keyCode))
                    + "（现 " + KeyBindings.keyName(keyCode) + "）：按键或点击图上的键。Esc 取消。");
            repaint();
            requestFocusInWindow();
        }

        void cancelWait() {
            waitingKey = -1;
            hint.setText("左键点击涂色按键开始换绑，再按键盘或点图上的键。Esc 取消。");
            repaint();
        }

        void assign(int newKey) {
            if (!waiting()) {
                return;
            }
            int old = waitingKey;
            if (newKey == KeyEvent.VK_ESCAPE) {
                cancelWait();
                return;
            }
            if (!keys.rebind(old, newKey)) {
                hint.setText("不能绑到 Host 热键（O/P/空格/R/F5/Esc）。请另选。");
                repaint();
                return;
            }
            cancelWait();
            onChange.run();
        }

        static Color color(int pad) {
            return switch (pad) {
                case KeyBindings.A -> new Color(0xE7, 0x4C, 0x3C);
                case KeyBindings.B -> new Color(0xE6, 0x7E, 0x22);
                case KeyBindings.SELECT -> new Color(0xD4, 0xAC, 0x0D);
                case KeyBindings.START -> new Color(0x1E, 0x84, 0x4A);
                case KeyBindings.UP -> new Color(0x34, 0x98, 0xDB);
                case KeyBindings.DOWN -> new Color(0x29, 0x80, 0xB9);
                case KeyBindings.LEFT -> new Color(0x16, 0xA0, 0x85);
                case KeyBindings.RIGHT -> new Color(0x8E, 0x44, 0xAD);
                default -> FREE;
            };
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Font font = getFont().deriveFont(Font.BOLD, 11f);
            g2.setFont(font);
            for (Cap c : caps) {
                int pad = keys.mask(c.code);
                Color fill;
                Color fg;
                if (KeyBindings.isReserved(c.code)) {
                    fill = HOST;
                    fg = Color.WHITE;
                } else if (pad != 0) {
                    fill = color(pad);
                    fg = Color.WHITE;
                } else {
                    fill = FREE;
                    fg = Color.DARK_GRAY;
                }
                g2.setColor(fill);
                g2.fillRoundRect(c.x, c.y, c.w, c.h, 8, 8);
                if (c.code == waitingKey) {
                    g2.setColor(Color.WHITE);
                    g2.drawRoundRect(c.x, c.y, c.w, c.h, 8, 8);
                    g2.drawRoundRect(c.x + 1, c.y + 1, c.w - 2, c.h - 2, 8, 8);
                } else {
                    g2.setColor(fill.darker());
                    g2.drawRoundRect(c.x, c.y, c.w, c.h, 8, 8);
                }
                g2.setColor(fg);
                int tw = g2.getFontMetrics().stringWidth(c.label);
                int th = g2.getFontMetrics().getAscent();
                g2.drawString(c.label, c.x + (c.w - tw) / 2, c.y + (c.h + th) / 2 - 3);
            }
        }

        private Cap hit(int x, int y) {
            for (int i = caps.size() - 1; i >= 0; i--) {
                Cap c = caps.get(i);
                if (x >= c.x && x < c.x + c.w && y >= c.y && y < c.y + c.h) {
                    return c;
                }
            }
            return null;
        }

        private void layoutCaps() {
            int y = PAD;
            row(y, PAD, new Object[][] {
                    {KeyEvent.VK_BACK_QUOTE, "`", 1f}, {KeyEvent.VK_1, "1", 1f}, {KeyEvent.VK_2, "2", 1f},
                    {KeyEvent.VK_3, "3", 1f}, {KeyEvent.VK_4, "4", 1f}, {KeyEvent.VK_5, "5", 1f},
                    {KeyEvent.VK_6, "6", 1f}, {KeyEvent.VK_7, "7", 1f}, {KeyEvent.VK_8, "8", 1f},
                    {KeyEvent.VK_9, "9", 1f}, {KeyEvent.VK_0, "0", 1f}, {KeyEvent.VK_MINUS, "-", 1f},
                    {KeyEvent.VK_EQUALS, "=", 1f}, {KeyEvent.VK_BACK_SPACE, "⌫", 1.6f}
            });
            y += U + G;
            row(y, PAD, new Object[][] {
                    {KeyEvent.VK_TAB, "Tab", 1.4f}, {KeyEvent.VK_Q, "Q", 1f}, {KeyEvent.VK_W, "W", 1f},
                    {KeyEvent.VK_E, "E", 1f}, {KeyEvent.VK_R, "R", 1f}, {KeyEvent.VK_T, "T", 1f},
                    {KeyEvent.VK_Y, "Y", 1f}, {KeyEvent.VK_U, "U", 1f}, {KeyEvent.VK_I, "I", 1f},
                    {KeyEvent.VK_O, "O", 1f}, {KeyEvent.VK_P, "P", 1f}, {KeyEvent.VK_OPEN_BRACKET, "[", 1f},
                    {KeyEvent.VK_CLOSE_BRACKET, "]", 1f}, {KeyEvent.VK_BACK_SLASH, "\\", 1.2f}
            });
            y += U + G;
            row(y, PAD, new Object[][] {
                    {KeyEvent.VK_CAPS_LOCK, "Caps", 1.7f}, {KeyEvent.VK_A, "A", 1f}, {KeyEvent.VK_S, "S", 1f},
                    {KeyEvent.VK_D, "D", 1f}, {KeyEvent.VK_F, "F", 1f}, {KeyEvent.VK_G, "G", 1f},
                    {KeyEvent.VK_H, "H", 1f}, {KeyEvent.VK_J, "J", 1f}, {KeyEvent.VK_K, "K", 1f},
                    {KeyEvent.VK_L, "L", 1f}, {KeyEvent.VK_SEMICOLON, ";", 1f}, {KeyEvent.VK_QUOTE, "'", 1f},
                    {KeyEvent.VK_ENTER, "Enter", 1.9f}
            });
            y += U + G;
            row(y, PAD, new Object[][] {
                    {KeyEvent.VK_SHIFT, "Shift", 2.2f}, {KeyEvent.VK_Z, "Z", 1f}, {KeyEvent.VK_X, "X", 1f},
                    {KeyEvent.VK_C, "C", 1f}, {KeyEvent.VK_V, "V", 1f}, {KeyEvent.VK_B, "B", 1f},
                    {KeyEvent.VK_N, "N", 1f}, {KeyEvent.VK_M, "M", 1f}, {KeyEvent.VK_COMMA, ",", 1f},
                    {KeyEvent.VK_PERIOD, ".", 1f}, {KeyEvent.VK_SLASH, "/", 1f}, {KeyEvent.VK_SHIFT, "Shift", 2.4f}
            });
            y += U + G;
            row(y, PAD, new Object[][] {
                    {KeyEvent.VK_CONTROL, "Ctrl", 1.5f}, {KeyEvent.VK_ALT, "Alt", 1.3f},
                    {KeyEvent.VK_SPACE, "Space", 7.2f}, {KeyEvent.VK_ALT, "Alt", 1.3f},
                    {KeyEvent.VK_CONTROL, "Ctrl", 1.5f}
            });
            int mainRight = 0;
            for (Cap c : caps) {
                mainRight = Math.max(mainRight, c.x + c.w);
            }
            int ax = mainRight + U;
            int ay = PAD + 2 * (U + G);
            caps.add(new Cap(KeyEvent.VK_UP, "↑", ax + U + G, ay, U, U));
            caps.add(new Cap(KeyEvent.VK_LEFT, "←", ax, ay + U + G, U, U));
            caps.add(new Cap(KeyEvent.VK_DOWN, "↓", ax + U + G, ay + U + G, U, U));
            caps.add(new Cap(KeyEvent.VK_RIGHT, "→", ax + 2 * (U + G), ay + U + G, U, U));
        }

        private void row(int y, int x0, Object[][] keys) {
            int x = x0;
            for (Object[] k : keys) {
                int code = (Integer) k[0];
                String label = (String) k[1];
                float wu = (Float) k[2];
                int w = Math.round(U * wu);
                caps.add(new Cap(code, label, x, y, w, U));
                x += w + G;
            }
        }
    }

    private static final class Cap {
        final int code;
        final String label;
        final int x;
        final int y;
        final int w;
        final int h;

        Cap(int code, String label, int x, int y, int w, int h) {
            this.code = code;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
