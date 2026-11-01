import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

public class PrintShop {
    private static final String URL = setting("printshop.db.url", "PRINTSHOP_DB_URL",
            "jdbc:mysql://localhost:3306/print_shop");
    private static final String USER = setting("printshop.db.user", "PRINTSHOP_DB_USER", "root");
    private static final String PASS = setting("printshop.db.password", "PRINTSHOP_DB_PASSWORD", "2007");
    private static final Color NAVY = new Color(24, 43, 76), BLUE = new Color(48, 105, 190);
    private static final Color PALE = new Color(237, 244, 253), INK = new Color(41, 52, 69);
    private static final Color MUTED = new Color(105, 117, 134), LINE = new Color(220, 227, 236);
    private static final BigDecimal MAX_TOTAL = new BigDecimal("99999999.99");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PrintShop() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("OptionPane.background", Color.WHITE);
            UIManager.put("Panel.background", Color.WHITE);
            new Menu().setVisible(true);
        });
    }

    private static String setting(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.trim().isEmpty()) value = System.getenv(env);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static Connection db() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    private static String errorText(SQLException e) {
        String text = e.getMessage() == null ? "Database operation failed." : e.getMessage();
        return PASS.isEmpty() ? text : text.replace(PASS, "********");
    }

    private static void dbAction(Component parent, SqlAction action) {
        try {
            action.run();
        } catch (SQLException e) {
            note(parent, "Database operation failed", errorText(e), 2);
        } catch (RuntimeException e) {
            note(parent, "Operation failed", e.getMessage() == null ? e.toString() : e.getMessage(), 2);
        }
    }

    private interface SqlAction { void run() throws SQLException; }

    private static BigDecimal bill(int pages, int copies, String type, String binding) {
        BigDecimal rate = "Colour".equals(type) ? BigDecimal.TEN : new BigDecimal("2");
        BigDecimal print = rate.multiply(BigDecimal.valueOf(pages)).multiply(BigDecimal.valueOf(copies));
        BigDecimal bind = "Spiral".equals(binding)
                ? new BigDecimal("30").multiply(BigDecimal.valueOf(copies)) : BigDecimal.ZERO;
        return print.add(bind).setScale(2);
    }

    private static String cash(BigDecimal amount) {
        return "\u20B9" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String status(String value) {
        if ("Printing".equalsIgnoreCase(value)) return "In Progress";
        if ("Ready".equalsIgnoreCase(value)) return "Ready for Pickup";
        return value;
    }

    private static String payStatus(BigDecimal paid, BigDecimal total) {
        return paid.compareTo(total) >= 0 ? "Paid" : paid.signum() > 0 ? "Partially Paid" : "Unpaid";
    }

    private static final class Order {
        int id, pages, copies;
        String name, phone, type, binding, state, payState;
        BigDecimal total;
        Order(ResultSet r) throws SQLException {
            id = r.getInt("order_id"); name = r.getString("customer_name");
            phone = r.getString("phone_number"); pages = r.getInt("pages");
            copies = r.getInt("copies"); type = r.getString("print_type");
            binding = r.getString("binding"); total = r.getBigDecimal("total_amount");
            state = status(r.getString("order_status")); payState = r.getString("payment_status");
        }
        Order(String n, String p, int pg, int cp, String t, String b) {
            name=n; phone=p; pages=pg; copies=cp; type=t; binding=b; total=bill(pg,cp,t,b);
        }
    }

    private static List<Order> orders(String filter) throws SQLException {
        boolean search = filter != null && !filter.trim().isEmpty();
        String sql = "SELECT * FROM orders " + (search
                ? "WHERE CAST(order_id AS CHAR) LIKE ? OR customer_name LIKE ? " : "")
                + "ORDER BY order_id DESC";
        List<Order> rows = new ArrayList<>();
        try (Connection c=db(); PreparedStatement s=c.prepareStatement(sql)) {
            if (search) { s.setString(1,"%"+filter.trim()+"%"); s.setString(2,"%"+filter.trim()+"%"); }
            try (ResultSet r=s.executeQuery()) { while (r.next()) rows.add(new Order(r)); }
        }
        return rows;
    }

    private static BigDecimal paid(Connection c, int id) throws SQLException {
        try (PreparedStatement s=c.prepareStatement(
                "SELECT COALESCE(SUM(amount),0.00) FROM order_payments WHERE order_id=?")) {
            s.setInt(1,id);
            try (ResultSet r=s.executeQuery()) { r.next(); return r.getBigDecimal(1).setScale(2); }
        }
    }

    private static BigDecimal paid(int id) throws SQLException {
        try (Connection c=db()) { return paid(c,id); }
    }

    private static int create(Order o) throws SQLException {
        String sql="INSERT INTO orders (customer_name,phone_number,pages,copies,print_type,binding,"
                +"total_amount,order_status,payment_status) VALUES (?,?,?,?,?,?,?,'Pending','Unpaid')";
        try (Connection c=db(); PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(s,o); s.executeUpdate();
            try (ResultSet r=s.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("Order saved, but its ID was not returned.");
                return r.getInt(1);
            }
        }
    }

    private static void bind(PreparedStatement s, Order o) throws SQLException {
        s.setString(1,o.name); s.setString(2,o.phone); s.setInt(3,o.pages); s.setInt(4,o.copies);
        s.setString(5,o.type); s.setString(6,o.binding); s.setBigDecimal(7,o.total);
    }

    private static void update(Order o) throws SQLException {
        try (Connection c=db()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement lock=c.prepareStatement(
                        "SELECT order_id FROM orders WHERE order_id=? FOR UPDATE")) {
                    lock.setInt(1,o.id);
                    try (ResultSet r=lock.executeQuery()) {
                        if (!r.next()) throw new SQLException("Order no longer exists.");
                    }
                }
                BigDecimal amount=paid(c,o.id);
                try (PreparedStatement s=c.prepareStatement(
                        "UPDATE orders SET customer_name=?,phone_number=?,pages=?,copies=?,print_type=?,"
                                +"binding=?,total_amount=?,payment_status=? WHERE order_id=?")) {
                    bind(s,o); s.setString(8,payStatus(amount,o.total)); s.setInt(9,o.id);
                    if (s.executeUpdate()==0) throw new SQLException("Order no longer exists.");
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }

    private static void updateStatus(int id, String value) throws SQLException {
        try (Connection c=db(); PreparedStatement s=c.prepareStatement(
                "UPDATE orders SET order_status=? WHERE order_id=?")) {
            s.setString(1,value); s.setInt(2,id);
            if (s.executeUpdate()==0) throw new SQLException("Order no longer exists.");
        }
    }

    private static void delete(int id) throws SQLException {
        try (Connection c=db(); PreparedStatement s=c.prepareStatement("DELETE FROM orders WHERE order_id=?")) {
            s.setInt(1,id); if (s.executeUpdate()==0) throw new SQLException("Order no longer exists.");
        }
    }

    private static BigDecimal recordPayment(int id, BigDecimal amount) throws SQLException {
        try (Connection c=db()) {
            c.setAutoCommit(false);
            try {
                BigDecimal total;
                try (PreparedStatement s=c.prepareStatement(
                        "SELECT total_amount FROM orders WHERE order_id=? FOR UPDATE")) {
                    s.setInt(1,id);
                    try (ResultSet r=s.executeQuery()) {
                        if (!r.next()) throw new SQLException("Order no longer exists.");
                        total=r.getBigDecimal(1);
                    }
                }
                BigDecimal already=paid(c,id), balance=total.subtract(already);
                if (amount.signum()<=0) throw new SQLException("Payment must be greater than zero.");
                if (amount.compareTo(balance)>0) throw new SQLException("Payment exceeds balance of "+cash(balance)+".");
                try (PreparedStatement s=c.prepareStatement(
                        "INSERT INTO order_payments(order_id,amount) VALUES (?,?)")) {
                    s.setInt(1,id); s.setBigDecimal(2,amount); s.executeUpdate();
                }
                try (PreparedStatement s=c.prepareStatement(
                        "UPDATE orders SET payment_status=? WHERE order_id=?")) {
                    s.setString(1,payStatus(already.add(amount),total)); s.setInt(2,id); s.executeUpdate();
                }
                c.commit(); return total.subtract(already).subtract(amount).max(BigDecimal.ZERO);
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }

    private static Object[] dashboard() throws SQLException {
        String sql="SELECT COUNT(*),SUM(order_status='Pending'),"
                +"SUM(LOWER(order_status) IN ('printing','in progress')),"
                +"SUM(LOWER(order_status) IN ('ready','ready for pickup')),"
                +"SUM(order_status='Delivered'),COALESCE(SUM(GREATEST(o.total_amount-COALESCE(p.paid,0),0)),0)"
                +" FROM orders o LEFT JOIN (SELECT order_id,SUM(amount) paid FROM order_payments "
                +"GROUP BY order_id) p ON p.order_id=o.order_id";
        try (Connection c=db(); PreparedStatement s=c.prepareStatement(sql); ResultSet r=s.executeQuery()) {
            r.next(); return new Object[]{r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4),
                    r.getInt(5),r.getBigDecimal(6)};
        }
    }

    private static JPanel panel(LayoutManager layout) {
        JPanel p=new JPanel(layout); p.setBackground(Color.WHITE); return p;
    }

    private static JLabel label(String text) {
        JLabel l=new JLabel(text); l.setForeground(INK); l.setFont(new Font("Segoe UI",Font.PLAIN,13)); return l;
    }

    private static JButton button(String text, boolean primary) {
        JButton b=new JButton(text); b.setFont(new Font("Segoe UI",Font.BOLD,12));
        b.setForeground(primary?Color.WHITE:NAVY); b.setBackground(primary?BLUE:Color.WHITE);
        b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary?BLUE:LINE,1,true),
                BorderFactory.createEmptyBorder(8,14,8,14))); return b;
    }

    private static JPanel header(String title,String subtitle) {
        JPanel p=new JPanel(new BorderLayout(0,5)); p.setBackground(NAVY);
        p.setBorder(BorderFactory.createEmptyBorder(17,22,17,22));
        JLabel t=new JLabel(title); t.setForeground(Color.WHITE); t.setFont(new Font("Segoe UI",Font.BOLD,20));
        JLabel d=new JLabel(subtitle); d.setForeground(new Color(211,221,236));
        p.add(t,BorderLayout.NORTH); p.add(d,BorderLayout.CENTER); return p;
    }

    private static class Dialog extends JDialog {
        final JPanel body=panel(new BorderLayout(12,12));
        final JPanel foot=panel(new FlowLayout(FlowLayout.RIGHT,8,0));
        Dialog(Window owner,String title,String subtitle,int w,int h,boolean modal) {
            super(owner,title,modal?ModalityType.APPLICATION_MODAL:ModalityType.MODELESS);
            JPanel root=panel(new BorderLayout()); root.add(header(title,subtitle),BorderLayout.NORTH);
            body.setBorder(BorderFactory.createEmptyBorder(18,22,16,22)); root.add(body,BorderLayout.CENTER);
            foot.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,LINE),
                    BorderFactory.createEmptyBorder(10,16,10,16))); root.add(foot,BorderLayout.SOUTH);
            setContentPane(root); setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(w,h);
            setMinimumSize(new Dimension(w,h)); setLocationRelativeTo(owner);
            getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
    }

    private static void note(Component parent,String title,String text,int kind) {
        Window owner=parent instanceof Window?(Window)parent:SwingUtilities.getWindowAncestor(parent);
        boolean success=kind==0;
        Dialog d=new Dialog(owner,title,success?"Completed successfully":
                kind==2?"Please review the details":"Print Shop",success?520:470,success?340:260,true);
        JPanel content=panel(new BorderLayout(12,0));
        if(success){
            JLabel icon=label("\u2713"); icon.setForeground(new Color(38,142,91));
            icon.setFont(new Font("Segoe UI Symbol",Font.BOLD,25)); content.add(icon,BorderLayout.WEST);
        }
        JTextArea msg=new JTextArea(text,success?3:4,36);
        msg.setRows(success?4:5);
        msg.setWrapStyleWord(true); msg.setLineWrap(!success);
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

    private static boolean confirm(Component parent,String title,String text) {
        Window owner=parent instanceof Window?(Window)parent:SwingUtilities.getWindowAncestor(parent);
        final boolean[] yes={false};
        Dialog d=new Dialog(owner,title,"Confirm to continue.",460,220,true);
        JTextArea msg=new JTextArea(text); msg.setWrapStyleWord(true); msg.setLineWrap(true);
        msg.setEditable(false); msg.setOpaque(false); d.body.add(msg);
        JButton cancel=button("Cancel",false), ok=button("Confirm",true);
        cancel.addActionListener(e->d.dispose()); ok.addActionListener(e->{yes[0]=true;d.dispose();});
        d.foot.add(cancel); d.foot.add(ok); d.getRootPane().setDefaultButton(cancel); d.setVisible(true);
        return yes[0];
    }

    private static JPanel menuCard(String title,String description,Runnable run) {
        JPanel p=panel(new BorderLayout(0,6));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE,1,true),
                BorderFactory.createEmptyBorder(9,12,9,12)));
        JButton b=button(title,false); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.addActionListener(e->run.run()); JLabel desc=label(description); desc.setForeground(MUTED);
        p.add(b,BorderLayout.NORTH); p.add(desc,BorderLayout.CENTER); return p;
    }

    private enum BrowserAction { DETAILS, EDIT, STATUS, PAYMENT, RECEIPT, DELETE }

    private static final class Menu extends Dialog {
        Menu() {
            super(null,"Print Shop Order Manager","Manage orders, payments, receipts, and daily activity.",
                    730,640,false);
            JPanel grid=panel(new GridLayout(5,2,12,10));
            addCard(grid,"Create New Order","Enter customer and print details; see a live bill.",
                    ()->new OrderForm(this,null).setVisible(true));
            addCard(grid,"View All Orders","Browse, sort, inspect, and manage saved orders.",
                    ()->new Browser(this,false,BrowserAction.DETAILS).setVisible(true));
            addCard(grid,"Search Order","Find an order by ID or customer name.",
                    ()->new Browser(this,true,BrowserAction.DETAILS).setVisible(true));
            addCard(grid,"Update Order","Edit print details while retaining payment history.",
                    ()->new Browser(this,true,BrowserAction.EDIT).setVisible(true));
            addCard(grid,"Update Order Status","Track production and pickup stages.",
                    ()->new Browser(this,true,BrowserAction.STATUS).setVisible(true));
            addCard(grid,"Record Payment","Record partial or final payments.",
                    ()->new Browser(this,true,BrowserAction.PAYMENT).setVisible(true));
            addCard(grid,"Generate Receipt","Preview, save, or print an order receipt.",
                    ()->new Browser(this,true,BrowserAction.RECEIPT).setVisible(true));
            addCard(grid,"Delete Order","Confirm deletion after reviewing an order.",
                    ()->new Browser(this,true,BrowserAction.DELETE).setVisible(true));
            addCard(grid,"Dashboard Summary","See live order counts and outstanding payments.",
                    ()->new Dashboard(this).setVisible(true));
            addCard(grid,"Exit","Close the order manager.",this::dispose);
            body.add(grid);
        }
        private void addCard(JPanel grid,String title,String desc,Runnable run) {
            grid.add(menuCard(title,desc,run));
        }
    }

    private static final class Model extends AbstractTableModel {
        final String[] cols={"ID","Customer","Phone","Pages","Copies","Print type","Binding",
                "Total","Order status","Payment status"};
        List<Order> data=new ArrayList<>();
        public int getRowCount(){return data.size();}
        public int getColumnCount(){return cols.length;}
        public String getColumnName(int c){return cols[c];}
        public Object getValueAt(int r,int c){
            Order o=data.get(r);
            switch(c){case 0:return o.id;case 1:return o.name;case 2:return o.phone;case 3:return o.pages;
                case 4:return o.copies;case 5:return o.type;case 6:return o.binding;case 7:return cash(o.total);
                case 8:return o.state;case 9:return o.payState;default:throw new IndexOutOfBoundsException();}
        }
        void fill(List<Order> rows){data=rows;fireTableDataChanged();}
    }

    private static final class Browser extends Dialog {
        final JTextField search=new JTextField();
        final Model model=new Model();
        final JTable table=new JTable(model);
        final JLabel count=label(" ");
        final BrowserAction preferred;
        Browser(Window owner,boolean filtered,BrowserAction preferred) {
            super(owner,filtered?"Search Orders":"All Orders",
                    "Select an order to view or manage it.",1080,585,true);
            this.preferred=preferred;
            JPanel top=panel(new BorderLayout(8,0));
            if(filtered){
                search.setToolTipText("Order ID or customer name"); top.add(search);
                JButton find=button("Search",true); find.addActionListener(e->refresh());
                top.add(find,BorderLayout.EAST); search.addActionListener(e->refresh());
            }
            JButton reload=button("Refresh",false); reload.addActionListener(e->refresh());
            if(!filtered) top.add(reload,BorderLayout.EAST);
            else { JPanel tools=panel(new FlowLayout(FlowLayout.RIGHT)); tools.add(reload); top.add(tools,BorderLayout.SOUTH); }
            table.setRowHeight(27); table.setFont(new Font("Segoe UI",Font.PLAIN,12));
            table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setFillsViewportHeight(true); table.setGridColor(LINE);
            table.getTableHeader().setFont(new Font("Segoe UI",Font.BOLD,12));
            table.getTableHeader().setReorderingAllowed(false);
            JPanel listing=panel(new BorderLayout(0,6));
            listing.add(new JScrollPane(table),BorderLayout.CENTER); listing.add(count,BorderLayout.SOUTH);
            body.add(top,BorderLayout.NORTH); body.add(listing,BorderLayout.CENTER);
            actionButton("Details",()->details()); actionButton("Edit",()->edit());
            actionButton("Status",()->changeStatus()); actionButton("Payment",()->payment());
            actionButton("Receipt",()->receipt()); actionButton("Delete",()->remove());
            JButton close=button("Close",true); close.addActionListener(e->dispose()); foot.add(close);
            table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2) perform(preferred);
            }});
            refresh();
        }
        private void actionButton(String title,Runnable run){
            JButton b=button(title,false); b.addActionListener(e->run.run()); foot.add(b);
        }
        private void perform(BrowserAction action) {
            switch(action) {
                case DETAILS: details(); break;
                case EDIT: edit(); break;
                case STATUS: changeStatus(); break;
                case PAYMENT: payment(); break;
                case RECEIPT: receipt(); break;
                case DELETE: remove(); break;
                default: break;
            }
        }
        private void refresh(){dbAction(this,()->{
            model.fill(orders(search.getText()));
            count.setText(model.data.isEmpty()?"No orders found.":model.data.size()+" order(s)");
        });}
        private Order selected(){
            int row=table.getSelectedRow();
            if(row<0){note(this,"Select an order","Choose an order in the table first.",1);return null;}
            return model.data.get(table.convertRowIndexToModel(row));
        }
        private void details(){
            Order o=selected(); if(o==null)return;
            dbAction(this,()->new Details(this,o,paid(o.id)).setVisible(true));
        }
        private void edit(){
            Order o=selected(); if(o==null)return;
            new OrderForm(this,o).setVisible(true); refresh();
        }
        private void changeStatus(){
            Order o=selected(); if(o==null)return;
            String[] options={"Pending","In Progress","Ready for Pickup","Delivered"};
            JComboBox<String> pick=new JComboBox<>(options); pick.setSelectedItem(o.state);
            JPanel form=panel(new GridLayout(0,1,6,6)); form.add(label("Order #"+o.id+" — "+o.name));
            form.add(pick);
            if(JOptionPane.showConfirmDialog(this,form,"Update Order Status",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
                dbAction(this,()->{updateStatus(o.id,(String)pick.getSelectedItem());
                    note(this,"Status updated","Order #"+o.id+" is now "+pick.getSelectedItem()+".",0);refresh();});
            }
        }
        private void payment(){
            Order o=selected(); if(o==null)return;
            dbAction(this,()->{new Payment(this,o,paid(o.id)).setVisible(true);refresh();});
        }
        private void receipt(){
            Order o=selected(); if(o==null)return;
            dbAction(this,()->new Receipt(this,o,paid(o.id)).setVisible(true));
        }
        private void remove(){
            Order o=selected(); if(o==null)return;
            if(confirm(this,"Delete order?","Delete order #"+o.id+" for "+o.name+"? This cannot be undone."))
                dbAction(this,()->{delete(o.id);note(this,"Order deleted","Order #"+o.id+" was deleted.",0);refresh();});
        }
    }

    private static void field(JPanel p,int row,String title,Component input) {
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(5,4,5,10);
        c.gridy=row; c.gridx=0; c.anchor=GridBagConstraints.LINE_START; p.add(label(title),c);
        c.gridx=1; c.weightx=1; c.fill=GridBagConstraints.HORIZONTAL; p.add(input,c);
    }

    private static final class OrderForm extends Dialog {
        final Order old;
        final JTextField name=new JTextField(22), phone=new JTextField(22);
        final JSpinner pages=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
        final JSpinner copies=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
        final JComboBox<String> type=new JComboBox<>(new String[]{"Black-and-white","Colour"});
        final JComboBox<String> binding=new JComboBox<>(new String[]{"None","Spiral"});
        final JLabel print=new JLabel(), bind=new JLabel(), total=new JLabel();
        final JButton save;
        OrderForm(Window owner,Order order){
            super(owner,order==null?"Create New Order":"Update Order",
                    "Print cost and total recalculate as you change the form.",550,500,true);
            old=order;
            if(order!=null){name.setText(order.name);phone.setText(order.phone);
                pages.setValue(order.pages);copies.setValue(order.copies);
                type.setSelectedItem(order.type);binding.setSelectedItem(order.binding);}
            JPanel form=panel(new GridBagLayout()); field(form,0,"Customer name",name);
            field(form,1,"Phone number",phone);field(form,2,"Pages per copy",pages);
            field(form,3,"Copies",copies);field(form,4,"Print type",type);field(form,5,"Binding",binding);
            body.add(form,BorderLayout.NORTH);
            JPanel bill=panel(new GridBagLayout());bill.setBackground(PALE);
            bill.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
            billLine(bill,0,"Printing",print);billLine(bill,1,"Binding",bind);billLine(bill,2,"Total",total);
            body.add(bill,BorderLayout.CENTER);
            DocumentListener dl=new DocumentListener(){public void insertUpdate(DocumentEvent e){recalc();}
                public void removeUpdate(DocumentEvent e){recalc();}public void changedUpdate(DocumentEvent e){recalc();}};
            name.getDocument().addDocumentListener(dl);phone.getDocument().addDocumentListener(dl);
            pages.addChangeListener(e->recalc());copies.addChangeListener(e->recalc());
            type.addActionListener(e->recalc());binding.addActionListener(e->recalc());
            JButton cancel=button("Cancel",false);cancel.addActionListener(e->dispose());foot.add(cancel);
            save=button(order==null?"Create Order":"Save Changes",true);save.addActionListener(e->submit());
            foot.add(save);getRootPane().setDefaultButton(save);recalc();
        }
        private void billLine(JPanel p,int row,String title,JLabel value){
            GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(4,4,4,4);
            c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.LINE_START;p.add(label(title),c);
            c.gridx=1;c.weightx=1;c.anchor=GridBagConstraints.LINE_END;p.add(value,c);
        }
        private void recalc(){
            BigDecimal rate="Colour".equals(type.getSelectedItem())?BigDecimal.TEN:new BigDecimal("2");
            BigDecimal p=rate.multiply(BigDecimal.valueOf((Integer)pages.getValue()))
                    .multiply(BigDecimal.valueOf((Integer)copies.getValue()));
            BigDecimal b="Spiral".equals(binding.getSelectedItem())
                    ?new BigDecimal("30").multiply(BigDecimal.valueOf((Integer)copies.getValue())):BigDecimal.ZERO;
            print.setText(cash(p));bind.setText(cash(b));total.setText(cash(p.add(b)));
            total.setFont(new Font("Segoe UI",Font.BOLD,16));total.setForeground(BLUE);
        }
        private void submit(){
            String n=name.getText().trim(),ph=phone.getText().trim();
            if(n.isEmpty()||ph.isEmpty()){note(this,"Required fields","Enter a customer name and phone number.",1);return;}
            if(n.length()>100||ph.length()>30){note(this,"Input too long",
                    "Name allows 100 characters; phone allows 30.",1);return;}
            Order o=new Order(n,ph,(Integer)pages.getValue(),(Integer)copies.getValue(),
                    (String)type.getSelectedItem(),(String)binding.getSelectedItem());
            if(o.total.compareTo(MAX_TOTAL)>0){note(this,"Bill too large",
                    "The total exceeds the database amount limit.",1);return;}
            if(old!=null&&!confirm(this,"Save changes?","Update order #"+old.id
                    +"? Existing payment records will be retained."))return;
            save.setEnabled(false);
            try{
                if(old==null){int id=create(o);note(this,"Order confirmed",
                        "Your order has been confirmed.\nOrder ID: "+id+"\nTotal amount: "+cash(o.total),0);}
                else{o.id=old.id;PrintShop.update(o);note(this,"Order updated successfully",
                        "Order ID: "+o.id+"\nNew total: "+cash(o.total),0);}
                dispose();
            }catch(SQLException e){note(this,"Database operation failed",errorText(e),2);save.setEnabled(true);}
        }
    }

    private static final class Details extends Dialog {
        Details(Window owner,Order o,BigDecimal amount){
            super(owner,"Order Details","Complete saved order information.",460,430,true);
            JPanel p=panel(new GridBagLayout());
            String[][] rows={{"Order ID",String.valueOf(o.id)},{"Customer",o.name},{"Phone",o.phone},
                    {"Pages / copies",o.pages+" / "+o.copies},{"Print type",o.type},{"Binding",o.binding},
                    {"Total",cash(o.total)},{"Paid",cash(amount)},
                    {"Outstanding",cash(o.total.subtract(amount).max(BigDecimal.ZERO))},
                    {"Order status",o.state},{"Payment status",payStatus(amount,o.total)}};
            for(int i=0;i<rows.length;i++){
                GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(5,5,5,12);
                c.gridy=i;c.gridx=0;c.anchor=GridBagConstraints.LINE_START;p.add(label(rows[i][0]),c);
                c.gridx=1;JLabel v=label(rows[i][1]);v.setFont(new Font("Segoe UI",Font.BOLD,13));p.add(v,c);
            }
            body.add(p);JButton close=button("Close",true);close.addActionListener(e->dispose());foot.add(close);
        }
    }

    private static final class Payment extends Dialog {
        final JTextField amount=new JTextField(14);final JLabel balance=label("Enter a payment amount");
        final Order order;final BigDecimal already;final JButton submit;
        Payment(Window owner,Order o,BigDecimal paidAmount){
            super(owner,"Record Payment","Order #"+o.id+" — "+o.name,460,350,true);
            order=o;already=paidAmount;JPanel form=panel(new GridBagLayout());
            summary(form,0,"Order total",cash(o.total));summary(form,1,"Amount paid",cash(already));
            summary(form,2,"Outstanding",cash(o.total.subtract(already).max(BigDecimal.ZERO)));
            field(form,3,"Payment amount",amount);summary(form,4,"Balance after payment","");
            GridBagConstraints c=new GridBagConstraints();c.gridx=1;c.gridy=4;c.anchor=GridBagConstraints.LINE_START;
            form.add(balance,c);body.add(form);
            amount.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){calc();}
                public void removeUpdate(DocumentEvent e){calc();}public void changedUpdate(DocumentEvent e){calc();}});
            JButton cancel=button("Cancel",false);cancel.addActionListener(e->dispose());foot.add(cancel);
            submit=button("Record Payment",true);submit.addActionListener(e->save());foot.add(submit);
            getRootPane().setDefaultButton(submit);
        }
        private void summary(JPanel p,int row,String title,String value){
            GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(5,4,5,8);
            c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.LINE_START;p.add(label(title),c);
            c.gridx=1;JLabel v=label(value);v.setFont(new Font("Segoe UI",Font.BOLD,13));p.add(v,c);
        }
        private void calc(){
            try{BigDecimal b=order.total.subtract(already).subtract(new BigDecimal(amount.getText().trim()));
                balance.setText(b.signum()<0?"Exceeds balance":cash(b));balance.setForeground(b.signum()<0?Color.RED:BLUE);}
            catch(NumberFormatException e){balance.setText("Enter a payment amount");balance.setForeground(MUTED);}
        }
        private void save(){
            BigDecimal value;
            try{value=new BigDecimal(amount.getText().trim()).setScale(2);}
            catch(NumberFormatException|ArithmeticException e){
                note(this,"Invalid payment","Enter a positive amount with no more than two decimals.",1);return;}
            submit.setEnabled(false);
            try{BigDecimal remaining=recordPayment(order.id,value);note(this,"Payment recorded",
                    "Amount: "+cash(value)+"\nOutstanding: "+cash(remaining)+"\nStatus: "
                            +payStatus(order.total.subtract(remaining),order.total),0);dispose();}
            catch(SQLException e){note(this,"Payment not recorded",errorText(e),2);submit.setEnabled(true);}
        }
    }

    private static final class Receipt extends Dialog {
        final Order order;final BigDecimal amount;final JTextArea text=new JTextArea();
        Receipt(Window owner,Order o,BigDecimal paidAmount){
            super(owner,"Receipt Preview","Preview, save, or print this receipt.",590,540,true);
            order=o;amount=paidAmount;text.setText(receiptText());text.setEditable(false);
            text.setFont(new Font("Consolas",Font.PLAIN,13));text.setCaretPosition(0);
            body.add(new JScrollPane(text));
            JButton preview=button("Preview",false);preview.addActionListener(e->{text.setText(receiptText());text.setCaretPosition(0);});
            JButton save=button("Save as TXT",false);save.addActionListener(e->saveFile());
            JButton print=button("Print",true);print.addActionListener(e->printFile());
            JButton close=button("Close",false);close.addActionListener(e->dispose());
            foot.add(preview);foot.add(save);foot.add(print);foot.add(close);
        }
        private String receiptText(){
            BigDecimal rate="Colour".equals(order.type)?BigDecimal.TEN:new BigDecimal("2");
            BigDecimal printing=rate.multiply(BigDecimal.valueOf(order.pages)).multiply(BigDecimal.valueOf(order.copies));
            BigDecimal binding="Spiral".equals(order.binding)?new BigDecimal("30")
                    .multiply(BigDecimal.valueOf(order.copies)):BigDecimal.ZERO;
            return "             PRINT SHOP ORDER MANAGER\n                       RECEIPT\n"
                    +"----------------------------------------------------\nOrder ID:              "+order.id
                    +"\nDate:                  "+LocalDateTime.now().format(DATE)
                    +"\nCustomer:              "+order.name+"\nPhone:                 "+order.phone
                    +"\n----------------------------------------------------\nPrint type:            "+order.type
                    +"\nPages / copies:        "+order.pages+" / "+order.copies
                    +"\nPrinting ("+order.pages+" x "+order.copies+" x "+cash(rate)+"): "+cash(printing)
                    +"\nBinding ("+order.binding+"):          "+cash(binding)
                    +"\nTotal bill:            "+cash(order.total)+"\nAmount paid:           "+cash(amount)
                    +"\nOutstanding:           "+cash(order.total.subtract(amount).max(BigDecimal.ZERO))
                    +"\nPayment status:        "+payStatus(amount,order.total)
                    +"\nOrder status:          "+order.state+"\n----------------------------------------------------\n";
        }
        private void saveFile(){
            JFileChooser fc=new JFileChooser();fc.setSelectedFile(new File("receipt-"+order.id+".txt"));
            if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            File f=fc.getSelectedFile();
            if(f.exists()&&!confirm(this,"Replace file?","The selected file exists. Replace it?"))return;
            try{Files.write(f.toPath(),receiptText().getBytes(StandardCharsets.UTF_8));
                note(this,"Receipt saved",f.getAbsolutePath(),0);}
            catch(IOException e){note(this,"Could not save receipt",e.getMessage(),2);}
        }
        private void printFile(){
            PrinterJob job=PrinterJob.getPrinterJob();job.setJobName("Receipt "+order.id);
            job.setPrintable(text.getPrintable(null,null));if(!job.printDialog())return;
            try{job.print();note(this,"Receipt printed","Sent receipt #"+order.id+" to the printer.",0);}
            catch(PrinterException e){note(this,"Could not print receipt",e.getMessage(),2);}
        }
    }

    private static final class Dashboard extends Dialog {
        final JPanel cards=panel(new GridLayout(2,3,12,12));
        Dashboard(Window owner){
            super(owner,"Dashboard Summary","Live totals from MySQL orders and payment records.",700,390,true);
            body.add(cards);JButton reload=button("Refresh",false);reload.addActionListener(e->refresh());
            JButton close=button("Close",true);close.addActionListener(e->dispose());
            foot.add(reload);foot.add(close);refresh();
        }
        private void refresh(){
            dbAction(this,()->{
                Object[] s=dashboard();String[] titles={"Total orders","Pending","In progress",
                        "Ready for pickup","Delivered","Outstanding"};
                cards.removeAll();
                for(int i=0;i<s.length;i++)cards.add(metric(titles[i],
                        i==5?cash((BigDecimal)s[i]):String.valueOf(s[i])));
                cards.revalidate();cards.repaint();
            });
        }
        private JPanel metric(String title,String value){
            JPanel p=panel(new BorderLayout(0,8));p.setBackground(PALE);
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE,1,true),
                    BorderFactory.createEmptyBorder(14,15,14,15)));
            JLabel a=label(title),b=label(value);a.setForeground(MUTED);
            b.setForeground(NAVY);b.setFont(new Font("Segoe UI",Font.BOLD,22));
            p.add(a,BorderLayout.NORTH);p.add(b,BorderLayout.CENTER);return p;
        }
    }
}
