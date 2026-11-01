import java.awt.*;
import java.sql.SQLException;
import javax.swing.*;
import javax.swing.event.*;

/** Shared Swing styles and small UI helpers. */
final class Ui {
    static final Color NAVY=new Color(24,43,76), BLUE=new Color(48,105,190);
    static final Color PALE=new Color(237,244,253), INK=new Color(41,52,69);
    static final Color MUTED=new Color(105,117,134), LINE=new Color(220,227,236);

    private Ui() {}

    static JPanel panel(LayoutManager layout) {
        JPanel p=new JPanel(layout); p.setBackground(Color.WHITE); return p;
    }

    static JLabel label(String text) {
        JLabel l=new JLabel(text); l.setForeground(INK);
        l.setFont(new Font("Segoe UI",Font.PLAIN,13)); return l;
    }

    static JButton button(String text,boolean primary) {
        JButton b=new JButton(text); b.setFont(new Font("Segoe UI",Font.BOLD,12));
        b.setForeground(primary?Color.WHITE:NAVY); b.setBackground(primary?BLUE:Color.WHITE);
        b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary?BLUE:LINE,1,true),
                BorderFactory.createEmptyBorder(8,14,8,14))); return b;
    }

    static void watch(JTextField field,Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){action.run();}
            public void removeUpdate(DocumentEvent e){action.run();}
            public void changedUpdate(DocumentEvent e){action.run();}
        });
    }

    static void addField(JPanel p,int row,String title,Component input) {
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(5,4,5,10);
        c.gridy=row; c.gridx=0; c.anchor=GridBagConstraints.LINE_START; p.add(label(title),c);
        c.gridx=1; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; p.add(input,c);
    }

    static JPanel menuCard(String title,String description,Runnable action) {
        JPanel p=panel(new BorderLayout(0,6));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE,1,true),
                BorderFactory.createEmptyBorder(9,12,9,12)));
        JButton b=button(title,false); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.addActionListener(e->action.run()); JLabel d=label(description); d.setForeground(MUTED);
        p.add(b,BorderLayout.NORTH); p.add(d,BorderLayout.CENTER); return p;
    }

    static void dbAction(Component parent,SqlAction action) {
        try { action.run(); }
        catch(SQLException e) { note(parent,"Database operation failed",OrderRepository.errorMessage(e),2); }
        catch(RuntimeException e) {
            note(parent,"Operation failed",e.getMessage()==null?e.toString():e.getMessage(),2);
        }
    }

    static void note(Component parent,String title,String text,int kind) {
        Window owner=parent instanceof Window?(Window)parent:SwingUtilities.getWindowAncestor(parent);
        boolean success=kind==0;
        AppDialog d=new AppDialog(owner,title,success?"Completed successfully":
                kind==2?"Please review the details":"Print Shop",success?520:470,success?340:260,true);
        JPanel content=panel(new BorderLayout(12,0));
        if(success) {
            JLabel icon=label("\u2713"); icon.setForeground(new Color(38,142,91));
            icon.setFont(new Font("Segoe UI Symbol",Font.BOLD,25)); content.add(icon,BorderLayout.WEST);
        }
        JTextArea msg=new JTextArea(text,success?3:4,36);
        msg.setRows(success?4:5); msg.setWrapStyleWord(true); msg.setLineWrap(!success);
        msg.setEditable(false); msg.setOpaque(false); msg.setForeground(INK);
        msg.setFont(new Font("Segoe UI",Font.PLAIN,14));
        if(success) msg.setPreferredSize(new Dimension(390,100));
        content.add(msg,BorderLayout.CENTER);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(success?new Color(194,229,210):LINE,1,true),
                BorderFactory.createEmptyBorder(14,15,14,15)));
        d.body.add(content,BorderLayout.CENTER);
        JButton ok=button("OK",true); ok.addActionListener(e->d.dispose()); d.foot.add(ok);
        d.getRootPane().setDefaultButton(ok); d.setVisible(true);
    }

    static boolean confirm(Component parent,String title,String text) {
        Window owner=parent instanceof Window?(Window)parent:SwingUtilities.getWindowAncestor(parent);
        boolean[] yes={false};
        AppDialog d=new AppDialog(owner,title,"Confirm to continue.",460,220,true);
        JTextArea msg=new JTextArea(text); msg.setWrapStyleWord(true); msg.setLineWrap(true);
        msg.setEditable(false); msg.setOpaque(false); msg.setForeground(INK);
        msg.setFont(new Font("Segoe UI",Font.PLAIN,14)); d.body.add(msg);
        JButton cancel=button("Cancel",false), ok=button("Confirm",true);
        cancel.addActionListener(e->d.dispose()); ok.addActionListener(e->{yes[0]=true;d.dispose();});
        d.foot.add(cancel); d.foot.add(ok); d.getRootPane().setDefaultButton(cancel);
        d.setVisible(true); return yes[0];
    }

    @FunctionalInterface interface SqlAction { void run() throws SQLException; }
}

/** Common dialog layout used by each screen. */
class AppDialog extends JDialog {
    final JPanel body=Ui.panel(new BorderLayout(12,12));
    final JPanel foot=Ui.panel(new FlowLayout(FlowLayout.RIGHT,8,0));

    AppDialog(Window owner,String title,String subtitle,int width,int height,boolean modal) {
        super(owner,title,modal?ModalityType.APPLICATION_MODAL:ModalityType.MODELESS);
        JPanel root=Ui.panel(new BorderLayout()), header=new JPanel(new BorderLayout(0,5));
        header.setBackground(Ui.NAVY); header.setBorder(BorderFactory.createEmptyBorder(17,22,17,22));
        JLabel name=new JLabel(title); name.setForeground(Color.WHITE);
        name.setFont(new Font("Segoe UI",Font.BOLD,20));
        JLabel detail=new JLabel(subtitle); detail.setForeground(new Color(211,221,236));
        header.add(name,BorderLayout.NORTH); header.add(detail,BorderLayout.CENTER);
        root.add(header,BorderLayout.NORTH);
        body.setBorder(BorderFactory.createEmptyBorder(18,22,16,22)); root.add(body,BorderLayout.CENTER);
        foot.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,Ui.LINE),
                BorderFactory.createEmptyBorder(10,16,10,16)));
        root.add(foot,BorderLayout.SOUTH); setContentPane(root);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(width,height);
        setMinimumSize(getSize()); setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
