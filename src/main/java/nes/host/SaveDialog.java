package nes.host;

import nes.save.SaveStore;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** 槽位列表。有档的格子点击即读档继续。 */
final class SaveDialog {
    private SaveDialog() {}

    static boolean show(JFrame parent, Session session, Runnable[] refreshHook) {
        boolean[] loaded = {false};
        JDialog dialog = new JDialog(parent, "存档 — " + session.romName(), true);
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        Runnable rebuild = () -> {
            list.removeAll();
            int limit = SaveStore.slotLimit();
            for (int i = 1; i <= limit; i++) {
                list.add(row(session, i, dialog, loaded));
            }
            list.revalidate();
            list.repaint();
        };
        refreshHook[0] = rebuild;
        rebuild.run();

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                SaveStore.slotLimit(), SaveStore.MIN_SLOTS, SaveStore.MAX_SLOTS, 1));
        spinner.addChangeListener(e -> {
            SaveStore.setSlotLimit((Integer) spinner.getValue());
            rebuild.run();
        });
        JPanel top = new JPanel();
        top.add(new JLabel("槽位上限"));
        top.add(spinner);
        top.add(new JLabel("点击有档槽继续；保存可覆盖。"));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(520, 420));
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        refreshHook[0] = null;
        return loaded[0];
    }

    private static JPanel row(Session session, int slot, JDialog dialog, boolean[] loaded) {
        SaveStore.Slot info = SaveStore.meta(session.romName(), slot);
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(new EmptyBorder(4, 4, 4, 4));
        row.add(new JLabel(String.format("%02d", slot)));
        row.add(Box.createHorizontalStrut(8));
        Thumb thumb = new Thumb(info);
        row.add(thumb);
        row.add(Box.createHorizontalStrut(8));
        JLabel text = new JLabel(info.empty ? "空槽" : SaveStore.formatTime(info.time));
        row.add(text);
        row.add(Box.createHorizontalGlue());
        if (!info.empty) {
            JButton load = new JButton("读取");
            load.addActionListener(e -> loadSlot(session, slot, dialog, loaded));
            row.add(load);
            row.add(Box.createHorizontalStrut(4));
            thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter click = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    loadSlot(session, slot, dialog, loaded);
                }
            };
            thumb.addMouseListener(click);
            text.addMouseListener(click);
        }
        JButton save = new JButton(info.empty ? "保存" : "覆盖");
        save.addActionListener(e -> session.requestSave(slot));
        row.add(save);
        return row;
    }

    private static void loadSlot(Session session, int slot, JDialog dialog, boolean[] loaded) {
        try {
            session.requestLoadState(SaveStore.readState(session.romName(), slot));
            loaded[0] = true;
            dialog.dispose();
        } catch (IOException e) {
            System.err.println("无法读取存档：" + e.getMessage());
        }
    }

    private static final class Thumb extends JPanel {
        private final BufferedImage image;

        Thumb(SaveStore.Slot info) {
            if (info.empty || info.width == 0) {
                image = null;
            } else {
                image = new BufferedImage(info.width, info.height, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, info.width, info.height, info.thumb, 0, info.width);
            }
            setPreferredSize(new Dimension(96, 90));
            setMaximumSize(new Dimension(96, 90));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                g.setColor(Color.DARK_GRAY);
                g.fillRect(0, 0, getWidth(), getHeight());
                return;
            }
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
