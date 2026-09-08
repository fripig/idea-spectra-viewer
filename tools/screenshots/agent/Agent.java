import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.*;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.SearchTextField;

public class Agent {
    static final String OUT = System.getProperty("user.home") + "/agent.out";
    static StringBuilder log = new StringBuilder();

    public static void agentmain(String args, Instrumentation inst) { run(args); }
    public static void agentmain(String args) { run(args); }

    static void run(String args) {
        log.setLength(0);
        try {
            for (String line : args.split(";;")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] c = line.split("\\|");
                log.append("> ").append(line).append("\n");
                exec(c);
            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); log.append(sw);
        }
        try { Files.writeString(Path.of(OUT), log.toString()); } catch (IOException ignored) {}
    }

    static Project project() { return ProjectManager.getInstance().getOpenProjects()[0]; }

    static void edt(Runnable r) throws Exception {
        Throwable[] err = new Throwable[1];
        ApplicationManager.getApplication().invokeAndWait(() -> { try { r.run(); } catch (Throwable t) { err[0] = t; } });
        if (err[0] != null) throw new RuntimeException(err[0]);
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    static void exec(String[] c) throws Exception {
        switch (c[0]) {
            case "windows" -> edt(() -> {
                for (Window w : Window.getWindows()) {
                    if (!w.isShowing()) continue;
                    String title = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : "";
                    log.append(w.getClass().getName()).append(" '").append(title).append("' ").append(w.getBounds()).append("\n");
                    if (w instanceof Dialog) dumpButtons(w, "  ");
                }
            });
            case "sleep" -> sleep(Long.parseLong(c[1]));
            case "frame" -> edt(() -> {
                JFrame f = WindowManager.getInstance().getFrame(project());
                f.setBounds(Integer.parseInt(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4]));
                f.toFront();
                log.append("frame ").append(f.getBounds()).append("\n");
            });
            case "button" -> edt(() -> {
                for (Window w : Window.getWindows()) {
                    if (!w.isShowing()) continue;
                    for (Component comp : all(w)) {
                        if (comp instanceof AbstractButton b && b.getText() != null && b.getText().replace("&", "").equals(c[1])) {
                            b.doClick(); log.append("clicked button ").append(c[1]).append("\n"); return;
                        }
                    }
                }
                log.append("button not found ").append(c[1]).append("\n");
            });
            case "tw" -> edt(() -> {
                ToolWindow tw = ToolWindowManager.getInstance(project()).getToolWindow(c[1]);
                if (tw == null) { log.append("no tool window ").append(c[1]).append("\n"); return; }
                if (c[2].equals("show")) tw.show(null); else tw.hide(null);
            });
            case "activate" -> edt(() -> {
                ToolWindow tw = ToolWindowManager.getInstance(project()).getToolWindow(c[1]);
                tw.activate(null, true);
            });
            case "open" -> edt(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(c[1]));
                if (vf == null) { log.append("no file ").append(c[1]).append("\n"); return; }
                FileEditorManager.getInstance(project()).openFile(vf, true);
            });
            case "expand" -> edt(() -> {   // expand|<id-substring or *depth1>
                JTree tree = spectraTree();
                for (int i = 0; i < tree.getRowCount(); i++) {
                    TreePath p = tree.getPathForRow(i);
                    String id = nodeId(p.getLastPathComponent());
                    boolean match = c[1].equals("*") ? p.getPathCount() == 2 : id.contains(c[1]);
                    if (match) tree.expandRow(i);
                }
            });
            case "collapse" -> edt(() -> {
                JTree tree = spectraTree();
                for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                    TreePath p = tree.getPathForRow(i);
                    if (nodeId(p.getLastPathComponent()).contains(c[1])) tree.collapseRow(i);
                }
            });
            case "select" -> edt(() -> {
                JTree tree = spectraTree();
                int row = rowOf(tree, c[1]);
                tree.setSelectionRow(row);
                tree.scrollRowToVisible(row);
                log.append("selected row ").append(row).append("\n");
            });
            case "dump" -> edt(() -> {
                JTree tree = spectraTree();
                for (int i = 0; i < tree.getRowCount(); i++) log.append(i).append(" ").append(nodeId(tree.getPathForRow(i).getLastPathComponent())).append("\n");
            });
            case "rightclick" -> edt(() -> {
                JTree tree = spectraTree();
                int row = rowOf(tree, c[1]);
                tree.setSelectionRow(row);
                Rectangle r = tree.getRowBounds(row);
                int x = r.x + (c.length > 2 ? Integer.parseInt(c[2]) : 40), y = r.y + r.height / 2;
                long t = System.currentTimeMillis();
                tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON3_DOWN_MASK | InputEvent.META_DOWN_MASK, x, y, 1, true, MouseEvent.BUTTON3));
                tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_RELEASED, t + 10, 0, x, y, 1, true, MouseEvent.BUTTON3));
            });
            case "menus" -> edt(() -> {
                MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
                for (MenuElement e : path) log.append("path: ").append(e.getClass().getName()).append(" ").append(text(e.getComponent())).append("\n");
                for (Window w : Window.getWindows()) if (w.isShowing()) for (Component comp : all(w)) if (comp instanceof JMenuItem mi && mi.isShowing()) log.append("item: ").append(mi.getClass().getSimpleName()).append(" ").append(mi.getText()).append("\n");
            });
            case "submenu" -> edt(() -> {   // submenu|Send to Terminal
                JPopupMenu popup = visiblePopup();
                JMenu menu = (JMenu) findItem(popup, c[1]);
                MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{popup, menu, menu.getPopupMenu()});
            });
            case "highlight" -> edt(() -> {   // highlight|Send to Terminal|/spectra-apply
                JPopupMenu popup = visiblePopup();
                JMenu menu = (JMenu) findItem(popup, c[1]);
                JMenuItem item = findItem(menu.getPopupMenu(), c[2]);
                MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{popup, menu, menu.getPopupMenu(), item});
            });
            case "menuclick" -> edt(() -> {   // menuclick|/spectra-apply
                for (Window w : Window.getWindows()) if (w.isShowing()) for (Component comp : all(w))
                    if (comp instanceof JMenuItem mi && !(mi instanceof JMenu) && mi.isShowing() && mi.getText() != null && mi.getText().startsWith(c[1])) { mi.doClick(); log.append("menu clicked\n"); return; }
                log.append("menu item not found\n");
            });
            case "closemenus" -> edt(() -> MenuSelectionManager.defaultManager().clearSelectedPath());
            case "toolbarbtn" -> edt(() -> {   // toolbarbtn|Filter by Author
                for (Component comp : all(spectraComponent())) {
                    if (comp instanceof ActionButton b) {
                        String t = b.getAction().getTemplatePresentation().getText();
                        if (t != null && t.equals(c[1])) { b.click(); log.append("toolbar clicked\n"); return; }
                    }
                }
                log.append("toolbar button not found\n");
            });
            case "lists" -> edt(() -> {
                for (Window w : Window.getWindows()) if (w.isShowing()) for (Component comp : all(w))
                    if (comp instanceof JList<?> l && l.isShowing()) { log.append("list ").append(l.getClass().getName()).append(" n=").append(l.getModel().getSize()).append("\n"); for (int i = 0; i < l.getModel().getSize(); i++) log.append("  ").append(l.getModel().getElementAt(i)).append("\n"); }
            });
            case "listclick" -> edt(() -> {   // listclick|Alice
                for (Window w : Window.getWindows()) if (w.isShowing()) for (Component comp : all(w))
                    if (comp instanceof JList<?> l && l.isShowing()) for (int i = 0; i < l.getModel().getSize(); i++)
                        if (String.valueOf(l.getModel().getElementAt(i)).contains(c[1])) {
                            Rectangle r = l.getCellBounds(i, i); int x = r.x + r.width / 2, y = r.y + r.height / 2; long t = System.currentTimeMillis();
                            l.dispatchEvent(new MouseEvent(l, MouseEvent.MOUSE_MOVED, t, 0, x, y, 0, false));
                            l.dispatchEvent(new MouseEvent(l, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                            l.dispatchEvent(new MouseEvent(l, MouseEvent.MOUSE_RELEASED, t + 10, 0, x, y, 1, false, MouseEvent.BUTTON1));
                            l.dispatchEvent(new MouseEvent(l, MouseEvent.MOUSE_CLICKED, t + 10, 0, x, y, 1, false, MouseEvent.BUTTON1));
                            log.append("list clicked ").append(i).append("\n"); return;
                        }
                log.append("list item not found\n");
            });
            case "filter" -> edt(() -> {
                for (Component comp : all(spectraComponent())) if (comp instanceof SearchTextField f) { f.setText(c[1]); f.getTextEditor().requestFocusInWindow(); return; }
                log.append("filter field not found\n");
            });
            case "focustree" -> edt(() -> spectraTree().requestFocusInWindow());
            case "focuseditor" -> edt(() -> {
                var e = FileEditorManager.getInstance(project()).getSelectedTextEditor();
                if (e != null) e.getContentComponent().requestFocusInWindow();
            });
            case "expirenotif" -> edt(() -> {
                for (var n : com.intellij.notification.NotificationsManager.getNotificationsManager().getNotificationsOfType(com.intellij.notification.Notification.class, project())) n.expire();
            });
            case "foreground" -> edt(() -> {
                Desktop.getDesktop().requestForeground(true);
                JFrame f = WindowManager.getInstance().getFrame(project()); f.toFront(); f.requestFocus();
            });
            case "splitlayout" -> edt(() -> {
                var ed = FileEditorManager.getInstance(project()).getSelectedEditor();
                log.append("editor ").append(ed == null ? "null" : ed.getClass().getName()).append("\n");
                if (ed instanceof com.intellij.openapi.fileEditor.TextEditorWithPreview t) t.setLayout(com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW);
            });
            case "esc" -> edt(() -> {
                Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                log.append("focus ").append(f == null ? "null" : f.getClass().getName()).append("\n");
                if (f == null) return; long t = System.currentTimeMillis();
                f.dispatchEvent(new KeyEvent(f, KeyEvent.KEY_PRESSED, t, 0, KeyEvent.VK_ESCAPE, (char) 27));
                f.dispatchEvent(new KeyEvent(f, KeyEvent.KEY_RELEASED, t + 5, 0, KeyEvent.VK_ESCAPE, (char) 27));
            });
            default -> log.append("unknown ").append(c[0]).append("\n");
        }
    }

    static JComponent spectraComponent() {
        return ToolWindowManager.getInstance(project()).getToolWindow("Spectra").getComponent();
    }

    static JTree spectraTree() {
        for (Component comp : all(spectraComponent())) if (comp instanceof JTree t) return t;
        throw new IllegalStateException("no tree");
    }

    static int rowOf(JTree tree, String idPart) {
        for (int i = 0; i < tree.getRowCount(); i++) if (nodeId(tree.getPathForRow(i).getLastPathComponent()).contains(idPart)) return i;
        throw new IllegalStateException("no row " + idPart);
    }

    static String nodeId(Object node) {
        Object o = node instanceof DefaultMutableTreeNode d ? d.getUserObject() : node;
        if (o == null) return "null";
        try { return String.valueOf(o.getClass().getMethod("getId").invoke(o)); } catch (Exception e) { return String.valueOf(o); }
    }

    static JPopupMenu visiblePopup() {
        for (MenuElement e : MenuSelectionManager.defaultManager().getSelectedPath()) if (e instanceof JPopupMenu p) return p;
        for (Window w : Window.getWindows()) if (w.isShowing()) for (Component comp : all(w)) if (comp instanceof JPopupMenu p && p.isShowing()) return p;
        throw new IllegalStateException("no popup");
    }

    static JMenuItem findItem(JPopupMenu popup, String prefix) {
        for (Component comp : popup.getComponents()) if (comp instanceof JMenuItem mi && mi.getText() != null && mi.getText().startsWith(prefix)) return mi;
        StringBuilder sb = new StringBuilder();
        for (Component comp : popup.getComponents()) sb.append(comp.getClass().getSimpleName()).append(":").append(text(comp)).append(",");
        throw new IllegalStateException("no item " + prefix + " in " + sb);
    }

    static String text(Component comp) { return comp instanceof AbstractButton b ? b.getText() : comp instanceof JLabel l ? l.getText() : ""; }

    static void dumpButtons(Container w, String indent) {
        for (Component comp : all(w)) if (comp instanceof AbstractButton b) log.append(indent).append("[").append(b.getText()).append("]\n");
    }

    static List<Component> all(Component root) {
        List<Component> out = new ArrayList<>();
        Deque<Component> q = new ArrayDeque<>(); q.add(root);
        while (!q.isEmpty()) { Component x = q.poll(); out.add(x); if (x instanceof Container ct) for (Component ch : ct.getComponents()) q.add(ch); }
        return out;
    }
}
