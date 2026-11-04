import java.awt.*;
import java.awt.event.*;
import java.math.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

enum BrowserAction { DETAILS, EDIT, STATUS, DELETE }

/** Main menu screen. */
final class MenuDialog extends AppDialog {
    MenuDialog() {
        super(null,"Print Shop Order Manager","Create and manage print orders.",730,530,false);
        JPanel cards=Ui.panel(new GridLayout(4,2,12,10));
        add(cards,"Create New Order","Enter customer and print details; see a live bill.",
                ()->new OrderFormDialog(this,null).setVisible(true));
        add(cards,"View All Orders","Browse, sort, inspect, and manage saved orders.",
                ()->new OrderBrowserDialog(this,false,BrowserAction.DETAILS).setVisible(true));
        add(cards,"Search Order","Find an order by ID or customer name.",
                ()->new OrderBrowserDialog(this,true,BrowserAction.DETAILS).setVisible(true));
        add(cards,"Update Order","Edit saved customer and print details.",
                ()->new OrderBrowserDialog(this,true,BrowserAction.EDIT).setVisible(true));
        add(cards,"Update Order Status","Track production and pickup stages.",
                ()->new OrderBrowserDialog(this,true,BrowserAction.STATUS).setVisible(true));
        add(cards,"Delete Order","Confirm deletion after reviewing an order.",
                ()->new OrderBrowserDialog(this,true,BrowserAction.DELETE).setVisible(true));
        add(cards,"Exit","Close the order manager.",this::dispose);
        body.add(cards);
    }

    private void add(JPanel cards,String title,String description,Runnable action) {
        cards.add(Ui.menuCard(title,description,action));
    }
}

/** Connects order rows to the Swing table. */
final class OrderTableModel extends AbstractTableModel {
    private final String[] columns={"ID","Customer","Phone","Pages","Copies","Print type",
            "Binding","Total","Order status"};
    private List<Order> orders=new ArrayList<>();

    public int getRowCount(){return orders.size();}
    public int getColumnCount(){return columns.length;}
    public String getColumnName(int column){return columns[column];}
    public Object getValueAt(int row,int column) {
        Order o=orders.get(row);
        switch(column) {
            case 0:return o.id; case 1:return o.name; case 2:return o.phone; case 3:return o.pages;
            case 4:return o.copies; case 5:return o.type; case 6:return o.binding;
            case 7:return Order.formatMoney(o.total); case 8:return o.state;
            default:throw new IndexOutOfBoundsException();
        }
    }
    Order getOrder(int row){return orders.get(row);}
    void setOrders(List<Order> rows){orders=rows;fireTableDataChanged();}
}

/** Search, select, and manage saved orders. */
final class OrderBrowserDialog extends AppDialog {
    private final JTextField search=new JTextField();
    private final OrderTableModel model=new OrderTableModel();
    private final JTable table=new JTable(model);
    private final JLabel count=Ui.label(" ");
    private final BrowserAction preferred;

    OrderBrowserDialog(Window owner,boolean filtered,BrowserAction preferred) {
        super(owner,filtered?"Search Orders":"All Orders",
                "Select an order to view or manage it.",1080,585,true);
        this.preferred=preferred;
        JPanel top=Ui.panel(new BorderLayout(8,0));
        if(filtered) {
            search.setToolTipText("Order ID or customer name"); top.add(search);
            JButton find=Ui.button("Search",true); find.addActionListener(e->refresh());
            top.add(find,BorderLayout.EAST); search.addActionListener(e->refresh());
        }
        JButton reload=Ui.button("Refresh",false); reload.addActionListener(e->refresh());
        if(!filtered) top.add(reload,BorderLayout.EAST);
        else {
            JPanel tools=Ui.panel(new FlowLayout(FlowLayout.RIGHT)); tools.add(reload);
            top.add(tools,BorderLayout.SOUTH);
        }
        table.setRowHeight(27); table.setFont(new Font("Segoe UI",Font.PLAIN,12));
        table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true); table.setGridColor(Ui.LINE);
        table.getTableHeader().setFont(new Font("Segoe UI",Font.BOLD,12));
        table.getTableHeader().setReorderingAllowed(false);
        JPanel listing=Ui.panel(new BorderLayout(0,6));
        listing.add(new JScrollPane(table)); listing.add(count,BorderLayout.SOUTH);
        body.add(top,BorderLayout.NORTH); body.add(listing);
        addAction("Details",this::details); addAction("Edit",this::edit);
        addAction("Status",this::changeStatus); addAction("Delete",this::remove);
        JButton close=Ui.button("Close",true); close.addActionListener(e->dispose()); foot.add(close);
        table.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)perform(preferred);}
        });
        refresh();
    }

    private void addAction(String title,Runnable action) {
        JButton b=Ui.button(title,false); b.addActionListener(e->action.run()); foot.add(b);
    }

    private void perform(BrowserAction action) {
        switch(action) {
            case DETAILS:details();break; case EDIT:edit();break;
            case STATUS:changeStatus();break; case DELETE:remove();break;
            default:break;
        }
    }

    private void refresh() {
        Ui.dbAction(this,()->{
            List<Order> rows=OrderRepository.findOrders(search.getText()); model.setOrders(rows);
            count.setText(rows.isEmpty()?"No orders found.":rows.size()+" order(s)");
        });
    }

    private Order selected() {
        int row=table.getSelectedRow();
        if(row<0){Ui.note(this,"Select an order","Choose an order in the table first.",1);return null;}
        return model.getOrder(table.convertRowIndexToModel(row));
    }

    private void details() {
        Order o=selected(); if(o==null)return;
        new OrderDetailsDialog(this,o).setVisible(true);
    }

    private void edit() {
        Order o=selected(); if(o==null)return;
        new OrderFormDialog(this,o).setVisible(true); refresh();
    }

    private void changeStatus() {
        Order o=selected(); if(o==null)return;
        JComboBox<String> status=new JComboBox<>(new String[]{"Pending","Processing","Completed"});
        status.setSelectedItem(o.state);
        AppDialog d=new AppDialog(this,"Update Order Status","Choose the current production stage.",470,360,true);
        JPanel form=Ui.panel(new GridLayout(0,1,10,10));
        JLabel customer=Ui.label("Order #"+o.id+" — "+o.name);
        customer.setFont(new Font("Segoe UI",Font.BOLD,14));
        form.add(customer); form.add(Ui.label("Order status")); form.add(status);
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.LINE,1,true),
                BorderFactory.createEmptyBorder(14,16,14,16))); d.body.add(form);
        boolean[] confirmed={false};
        JButton cancel=Ui.button("Cancel",false), save=Ui.button("Save Status",true);
        cancel.addActionListener(e->d.dispose()); save.addActionListener(e->{confirmed[0]=true;d.dispose();});
        d.foot.add(cancel); d.foot.add(save); d.getRootPane().setDefaultButton(save); d.setVisible(true);
        if(confirmed[0]) Ui.dbAction(this,()->{
            String value=(String)status.getSelectedItem(); OrderRepository.updateStatus(o.id,value);
            Ui.note(this,"Status updated","Order #"+o.id+" is now "+value+".",0); refresh();
        });
    }

    private void remove() {
        Order o=selected(); if(o==null)return;
        if(Ui.confirm(this,"Delete order?","Delete order #"+o.id+" for "+o.name+"? This cannot be undone."))
            Ui.dbAction(this,()->{
                OrderRepository.deleteOrder(o.id); Ui.note(this,"Order deleted","Order #"+o.id+" was deleted.",0);
                refresh();
            });
    }
}

/** Creates an order or edits its print details. */
final class OrderFormDialog extends AppDialog {
    private final Order old;
    private final JTextField name=new JTextField(22), phone=new JTextField(22);
    private final JSpinner pages=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
    private final JSpinner copies=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
    private final JComboBox<String> type=new JComboBox<>(new String[]{"Black-and-white","Colour"});
    private final JComboBox<String> binding=new JComboBox<>(new String[]{"None","Spiral"});
    private final JLabel printing=new JLabel(), bindingCost=new JLabel(), total=new JLabel();
    private final JButton save;

    OrderFormDialog(Window owner,Order order) {
        super(owner,order==null?"Create New Order":"Update Order",
                "Print cost and total recalculate as you change the form.",550,500,true);
        old=order;
        if(order!=null) {
            name.setText(order.name); phone.setText(order.phone); pages.setValue(order.pages);
            copies.setValue(order.copies); type.setSelectedItem(order.type); binding.setSelectedItem(order.binding);
        }
        JPanel form=Ui.panel(new GridBagLayout());
        Ui.addField(form,0,"Customer name",name); Ui.addField(form,1,"Phone number",phone);
        Ui.addField(form,2,"Pages per copy",pages); Ui.addField(form,3,"Copies",copies);
        Ui.addField(form,4,"Print type",type); Ui.addField(form,5,"Binding",binding);
        body.add(form,BorderLayout.NORTH);
        JPanel bill=Ui.panel(new GridBagLayout()); bill.setBackground(Ui.PALE);
        bill.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
        addBillLine(bill,0,"Printing",printing); addBillLine(bill,1,"Binding",bindingCost);
        addBillLine(bill,2,"Total",total); body.add(bill,BorderLayout.CENTER);
        Ui.watch(name,this::recalculate); Ui.watch(phone,this::recalculate);
        pages.addChangeListener(e->recalculate()); copies.addChangeListener(e->recalculate());
        type.addActionListener(e->recalculate()); binding.addActionListener(e->recalculate());
        JButton cancel=Ui.button("Cancel",false); cancel.addActionListener(e->dispose()); foot.add(cancel);
        save=Ui.button(order==null?"Create Order":"Save Changes",true);
        save.addActionListener(e->submit()); foot.add(save); getRootPane().setDefaultButton(save);
        recalculate();
    }

    private void addBillLine(JPanel p,int row,String title,JLabel value) {
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(4,4,4,4);
        c.gridy=row; c.gridx=0; c.anchor=GridBagConstraints.LINE_START; p.add(Ui.label(title),c);
        c.gridx=1; c.weightx=1; c.anchor=GridBagConstraints.LINE_END; p.add(value,c);
    }

    private void recalculate() {
        BigDecimal rate="Colour".equals(type.getSelectedItem())?BigDecimal.TEN:new BigDecimal("2");
        BigDecimal print=rate.multiply(BigDecimal.valueOf((Integer)pages.getValue()))
                .multiply(BigDecimal.valueOf((Integer)copies.getValue()));
        BigDecimal bind="Spiral".equals(binding.getSelectedItem())
                ?new BigDecimal("30").multiply(BigDecimal.valueOf((Integer)copies.getValue())):BigDecimal.ZERO;
        printing.setText(Order.formatMoney(print)); bindingCost.setText(Order.formatMoney(bind));
        total.setText(Order.formatMoney(print.add(bind)));
        total.setFont(new Font("Segoe UI",Font.BOLD,16)); total.setForeground(Ui.BLUE);
    }

    private void submit() {
        String customer=name.getText().trim(), number=phone.getText().trim();
        if(customer.isEmpty()||number.isEmpty()) {
            Ui.note(this,"Required fields","Enter a customer name and phone number.",1); return;
        }
        if(customer.length()>100||number.length()>30) {
            Ui.note(this,"Input too long","Name allows 100 characters; phone allows 30.",1); return;
        }
        Order order=new Order(customer,number,(Integer)pages.getValue(),(Integer)copies.getValue(),
                (String)type.getSelectedItem(),(String)binding.getSelectedItem());
        if(order.total.compareTo(Order.MAX_TOTAL)>0) {
            Ui.note(this,"Bill too large","The total exceeds the database amount limit.",1); return;
        }
        if(old!=null&&!Ui.confirm(this,"Save changes?","Update order #"+old.id+"?"))return;
        save.setEnabled(false);
        try {
            if(old==null) {
                int id=OrderRepository.createOrder(order);
                Ui.note(this,"Order confirmed","Your order has been confirmed.\nOrder ID: "+id
                        +"\nTotal amount: "+Order.formatMoney(order.total),0);
            } else {
                order.id=old.id; OrderRepository.updateOrder(order);
                Ui.note(this,"Order updated successfully","Order ID: "+order.id
                        +"\nNew total: "+Order.formatMoney(order.total),0);
            }
            dispose();
        } catch(SQLException e) {
            Ui.note(this,"Database operation failed",OrderRepository.errorMessage(e),2); save.setEnabled(true);
        }
    }
}

/** Displays all saved information for one order. */
final class OrderDetailsDialog extends AppDialog {
    OrderDetailsDialog(Window owner,Order o) {
        super(owner,"Order Details","Complete saved order information.",460,370,true);
        JPanel details=Ui.panel(new GridBagLayout());
        String[][] rows={{"Order ID",String.valueOf(o.id)},{"Customer",o.name},{"Phone",o.phone},
                {"Pages / copies",o.pages+" / "+o.copies},{"Print type",o.type},{"Binding",o.binding},
                {"Total",Order.formatMoney(o.total)},{"Order status",o.state}};
        for(int i=0;i<rows.length;i++) {
            GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(5,5,5,12);
            c.gridy=i; c.gridx=0; c.anchor=GridBagConstraints.LINE_START;
            details.add(Ui.label(rows[i][0]),c); c.gridx=1;
            JLabel value=Ui.label(rows[i][1]); value.setFont(new Font("Segoe UI",Font.BOLD,13));
            details.add(value,c);
        }
        body.add(details); JButton close=Ui.button("Close",true);
        close.addActionListener(e->dispose()); foot.add(close);
    }
}
