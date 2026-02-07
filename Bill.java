import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new POSFrame());
    }
}

class POSFrame extends JFrame {
    CardLayout cards = new CardLayout();
    JPanel root = new JPanel(cards);
    DashboardPanel dashboard;
    BillingPanel billingPanel;
    InventoryPanel inventoryPanel;

    public POSFrame() {
        setTitle("Simple Bill Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        dashboard = new DashboardPanel(this);
        billingPanel = new BillingPanel(this);
        inventoryPanel = new InventoryPanel(this);

        root.add(new LoginPanel(this), "login");
        root.add(dashboard, "dashboard");
        root.add(billingPanel, "billing");
        root.add(inventoryPanel, "inventory");

        setContentPane(root);
        setVisible(true);
    }

    void show(String name) {
        cards.show(root, name);
        if (name.equals("billing")) billingPanel.requestFocusForTable();
    }
}

class LoginPanel extends JPanel {
    public LoginPanel(POSFrame frame) {
        setLayout(new BorderLayout());
        // Background
        JLabel bg = new JLabel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int w = getWidth();
                int h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, new Color(58,123,213), w, h, new Color(58,213,150));
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
            }
        };
        bg.setLayout(new GridBagLayout());

        JPanel loginBox = new JPanel(new GridBagLayout());
        loginBox.setPreferredSize(new Dimension(360, 220));
        loginBox.setBackground(new Color(255,255,255,230));
        loginBox.setOpaque(true);
        loginBox.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,10,8,10);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        loginBox.add(new JLabel("Username:"), c);
        c.gridx = 1; JTextField user = new JTextField(15); loginBox.add(user,c);
        c.gridx = 0; c.gridy = 1; loginBox.add(new JLabel("Password:"), c);
        c.gridx = 1; JPasswordField pass = new JPasswordField(15); loginBox.add(pass,c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.anchor = GridBagConstraints.CENTER;
        JButton login = new JButton("Login");
        login.addActionListener(e -> frame.show("dashboard"));
        loginBox.add(login, c);

        bg.add(loginBox);
        add(bg, BorderLayout.CENTER);
    }
}

class DashboardPanel extends JPanel {
    public DashboardPanel(POSFrame frame) {
        setLayout(new BorderLayout());

        // Top bar
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        top.add(new JLabel("Welcome to Simple Store - Dashboard"), BorderLayout.WEST);
        add(top, BorderLayout.NORTH);

        // Center with icons (buttons)
        JPanel center = new JPanel();
        center.setLayout(new FlowLayout(FlowLayout.CENTER, 60, 80));

        JButton billingBtn = createIconButton("Billing", Color.ORANGE);
        billingBtn.addActionListener(e -> frame.show("billing"));
        JButton invBtn = createIconButton("Inventory", Color.CYAN);
        invBtn.addActionListener(e -> frame.show("inventory"));

        center.add(billingBtn);
        center.add(invBtn);

        add(center, BorderLayout.CENTER);
    }

    private JButton createIconButton(String text, Color c) {
        JButton b = new JButton(text);
        b.setPreferredSize(new Dimension(200,160));
        b.setBackground(c);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 18f));
        return b;
    }
}

class BillingPanel extends JPanel {
    POSFrame parent;
    JTextField custName = new JTextField(16);
    JTextField custPhone = new JTextField(12);
    DefaultTableModel itemsModel;
    JTable itemsTable;
    JTextField codeField = new JTextField(10);
    JTextField nameField = new JTextField(12);
    JTextField qtyField = new JTextField(6);
    JTextField priceField = new JTextField(8);

    long lastBillId = 0;
    String lastPrinted = null;

    public BillingPanel(POSFrame parent) {
        this.parent = parent;
        setLayout(new BorderLayout());

        // Top-right customer details
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel customerPanel = new JPanel(new GridBagLayout());
        customerPanel.setBorder(BorderFactory.createTitledBorder("Customer Details"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4); c.gridx = 0; c.gridy = 0; customerPanel.add(new JLabel("Name:"), c);
        c.gridx = 1; customerPanel.add(custName, c);
        c.gridx = 0; c.gridy = 1; customerPanel.add(new JLabel("Phone Number:"), c);
        c.gridx = 1; customerPanel.add(custPhone, c);

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> parent.show("dashboard"));
        top.add(backBtn, BorderLayout.WEST);
        top.add(customerPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Center body: entry and items table
        JPanel center = new JPanel(new BorderLayout());
        JPanel entry = new JPanel();
        entry.setBorder(BorderFactory.createTitledBorder("Enter code/item and quantity"));
        entry.add(new JLabel("Code:")); entry.add(codeField);
        entry.add(new JLabel("Item:")); entry.add(nameField);
        entry.add(new JLabel("Price:")); entry.add(priceField);
        entry.add(new JLabel("Qty:")); entry.add(qtyField);
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> addItem());
        entry.add(addBtn);

        // when user presses Enter in code field, try to fill name from inventory
        codeField.addActionListener(ev -> {
            String code = codeField.getText().trim();
            if(code.isEmpty()) return;
            try {
                String nameFromInv = null;
                String priceFromInv = null;
                if(parent != null && parent.inventoryPanel != null) {
                    nameFromInv = parent.inventoryPanel.findItemNameByCode(code);
                    priceFromInv = parent.inventoryPanel.findItemPriceByCode(code);
                }
                if(nameFromInv != null) nameField.setText(nameFromInv);
                if(priceFromInv != null) priceField.setText(priceFromInv);
                // if qty and price provided, add item immediately
                if(qtyField.getText().trim().length()>0 && priceField.getText().trim().length()>0) {
                    addItem();
                }
            } catch(Exception ex) { /* ignore */ }
        });

        center.add(entry, BorderLayout.NORTH);

        itemsModel = new DefaultTableModel(new Object[]{"Code","Item","Price","Qty","Total"},0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        itemsTable = new JTable(itemsModel);
        JScrollPane sp = new JScrollPane(itemsTable);
        center.add(sp, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        // Bottom toolbar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton printBtn = new JButton("Print");
        printBtn.addActionListener(e -> onPrint());
        bottom.add(printBtn);
        JButton resetBtn = new JButton("Reset");
        resetBtn.addActionListener(e -> {
            custName.setText("");
            custPhone.setText("");
            itemsModel.setRowCount(0);
            codeField.setText("");
            nameField.setText("");
            priceField.setText("");
            qtyField.setText("");
            lastPrinted = null;
        });
        bottom.add(resetBtn);
        JButton reprintBtn = new JButton("Reprint Last");
        reprintBtn.addActionListener(e -> {
            if(lastPrinted != null) showBillText(lastPrinted);
            else if(lastBillId>0) {
                String txt = readBillFromDisk(lastBillId);
                if(txt!=null) showBillText(txt);
                else JOptionPane.showMessageDialog(BillingPanel.this, "No previous bill to reprint.");
            } else JOptionPane.showMessageDialog(BillingPanel.this, "No previous bill to reprint.");
        });
        bottom.add(reprintBtn);
        JButton historyBtn = new JButton("History");
        historyBtn.addActionListener(e -> showHistoryDialog());
        bottom.add(historyBtn);
        add(bottom, BorderLayout.SOUTH);

        // load last bill id from disk (if any)
        loadLastBillId();

        setupKeyBindings();
    }

    void addItem(){
        try{
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            int qty = Integer.parseInt(qtyField.getText().trim());
            double price = Double.parseDouble(priceField.getText().trim());
            double total = qty * price;
            itemsModel.addRow(new Object[]{code, name, qty, String.format("%.2f", price), String.format("%.2f", total)});
            codeField.setText(""); nameField.setText(""); qtyField.setText(""); priceField.setText("");
        } catch(Exception ex){
            JOptionPane.showMessageDialog(this, "Invalid entry: " + ex.getMessage());
        }
    }

    void setupKeyBindings(){
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "print");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "hold");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "reprint");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "exit");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "delete");

        am.put("print", new AbstractAction(){ public void actionPerformed(ActionEvent e){ onPrint(); }});
        am.put("hold", new AbstractAction(){ public void actionPerformed(ActionEvent e){ JOptionPane.showMessageDialog(BillingPanel.this, "Bill held."); }});
        am.put("reprint", new AbstractAction(){ public void actionPerformed(ActionEvent e){ if(lastPrinted!=null) showBillText(lastPrinted); else JOptionPane.showMessageDialog(BillingPanel.this, "No previous bill to reprint."); }});
        am.put("exit", new AbstractAction(){ public void actionPerformed(ActionEvent e){ int r = JOptionPane.showConfirmDialog(BillingPanel.this, "Exit billing?", "Confirm", JOptionPane.YES_NO_OPTION); if(r==JOptionPane.YES_OPTION) System.exit(0); }});
        am.put("delete", new AbstractAction(){ public void actionPerformed(ActionEvent e){ int r = itemsTable.getSelectedRow(); if(r>=0) itemsModel.removeRow(r); }});
    }

    void onPrint(){
        // payment options dialog with keys c (cash), v (card), b (upi)
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Select Payment Mode", Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout());
        JLabel l = new JLabel("Press C=Cash, V=Card, B=UPI", SwingConstants.CENTER);
        l.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        d.add(l, BorderLayout.CENTER);
        d.setSize(320,150);
        d.setLocationRelativeTo(this);

        d.addKeyListener(new KeyAdapter(){
            public void keyPressed(KeyEvent e){
                char kc = Character.toLowerCase(e.getKeyChar());
                if(kc=='c') { d.dispose(); selectCash(); }
                else if(kc=='v') { d.dispose(); selectCard(); }
                else if(kc=='b') { d.dispose(); selectUpi(); }
            }
        });
        d.setFocusable(true);
        d.setVisible(true);
    }

    void selectCash(){
        double total = computeTotal();
        String givenStr = JOptionPane.showInputDialog(this, "Amount given:", String.format("%.2f", total));
        if(givenStr==null) return;
        try{
            double given = Double.parseDouble(givenStr);
            double change = given - total;
            String msg = String.format("Change to return: %.2f", change<0?0:change);
            JOptionPane.showMessageDialog(this, msg);
            printBill("Cash", given, change);
        } catch(Exception ex){ JOptionPane.showMessageDialog(this, "Invalid amount"); }
    }

    void selectCard(){
        JOptionPane.showMessageDialog(this, "Card selected (processing simulated)");
        printBill("Card", 0, 0);
    }
    void selectUpi(){
        JOptionPane.showMessageDialog(this, "UPI selected (processing simulated)");
        printBill("UPI", 0, 0);
    }

    double computeTotal(){
        double sum=0;
        for(int i=0;i<itemsModel.getRowCount();i++){
            sum += Double.parseDouble(itemsModel.getValueAt(i,4).toString());
        }
        return Math.round(sum*100.0)/100.0;
    }

    void printBill(String mode, double given, double change){
        StringBuilder sb = new StringBuilder();
        sb.append(centerText("STORE NAME")).append("\n");
        sb.append("Date: "+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) +"\n");
        lastBillId++;
        sb.append("Bill ID: " + lastBillId + "\n");
        sb.append("Customer: " + custName.getText() + "  Phone: " + custPhone.getText() + "\n\n");
        sb.append(String.format("%-20s %-6s %-8s %-8s\n", "Item", "Qty", "Price", "Total"));
        sb.append("------------------------------------------------\n");
        for(int i=0;i<itemsModel.getRowCount();i++){
            String item = itemsModel.getValueAt(i,1).toString();
            String qty = itemsModel.getValueAt(i,2).toString();
            String price = itemsModel.getValueAt(i,3).toString();
            String tot = itemsModel.getValueAt(i,4).toString();
            sb.append(String.format("%-20s %-6s %-8s %-8s\n", item, qty, price, tot));
        }
        sb.append("\n");
        double total = computeTotal();
        sb.append(String.format("Total: %.2f\n", total));
        sb.append("Payment Mode: " + mode + "\n");
        if(mode.equals("Cash")){
            sb.append(String.format("Amount Given: %.2f\n", given));
            sb.append(String.format("Balance Return: %.2f\n", Math.max(0.0, change)));
        }

        String billText = sb.toString();
        lastPrinted = billText;
        showBillText(billText);

        // save bill to disk
        saveBillToDisk(billText, lastBillId);

        // decrement inventory quantities (if codes present)
        for(int i=0;i<itemsModel.getRowCount();i++){
            try{
                String code = itemsModel.getValueAt(i,0).toString();
                int qty = Integer.parseInt(itemsModel.getValueAt(i,2).toString());
                if(code!=null && !code.trim().isEmpty() && parent!=null && parent.inventoryPanel!=null){
                    parent.inventoryPanel.decrementQuantity(code.trim(), qty);
                }
            }catch(Exception ex){ }
        }

        // after printing, clear items
        itemsModel.setRowCount(0);
    }

    private void loadLastBillId(){
        try{
            Path bills = Paths.get("bills");
            if(!Files.exists(bills)) return;
            long max = 0;
            try (java.util.stream.Stream<Path> s = Files.list(bills)){
                for(Path p: (Iterable<Path>)s::iterator){
                    String fn = p.getFileName().toString();
                    if(fn.startsWith("bill_") && fn.endsWith(".txt")){
                        String mid = fn.substring(5, fn.length()-4);
                        try{ long v = Long.parseLong(mid); if(v>max) max=v; }catch(Exception ex){}
                    }
                }
            }
            lastBillId = max;
        }catch(Exception ex){ /* ignore */ }
    }

    private void saveBillToDisk(String text, long id){
        try{
            Path bills = Paths.get("bills");
            if(!Files.exists(bills)) Files.createDirectories(bills);
            // lastBillId already incremented in printBill
            Path f = bills.resolve("bill_"+id+".txt");
            Files.write(f, Collections.singletonList(text), StandardCharsets.UTF_8);
        }catch(IOException ex){ JOptionPane.showMessageDialog(this, "Failed to save bill: "+ex.getMessage()); }
    }

    private String readBillFromDisk(long id){
        try{
            Path f = Paths.get("bills").resolve("bill_"+id+".txt");
            if(Files.exists(f)) return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        }catch(Exception ex){ }
        return null;
    }

    // History UI: list saved bill files and allow viewing/reprinting/exporting summaries
    private void showHistoryDialog(){
        try{
            Path billsDir = Paths.get("bills");
            if(!Files.exists(billsDir)){
                JOptionPane.showMessageDialog(this, "No saved bills found.");
                return;
            }
            java.util.List<Path> files = new ArrayList<>();
            try(java.util.stream.Stream<Path> s = Files.list(billsDir)){
                s.filter(p->p.getFileName().toString().startsWith("bill_") && p.getFileName().toString().endsWith(".txt")).forEach(files::add);
            }
            if(files.isEmpty()){
                JOptionPane.showMessageDialog(this, "No saved bills found.");
                return;
            }

            // build display entries
            DefaultListModel<String> lm = new DefaultListModel<>();
            java.util.Map<String,Path> map = new LinkedHashMap<>();
            files.sort((a,b)->b.getFileName().toString().compareTo(a.getFileName().toString()));
            for(Path p: files){
                String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                String id = "?";
                String date = "?";
                String total = "?";
                String cust = "";
                for(String ln: content.split("\n")){
                    if(ln.startsWith("Bill ID:")) id = ln.substring(Math.min(8, ln.length())).trim();
                    if(ln.startsWith("Date:")) date = ln.substring(Math.min(5, ln.length())).trim();
                    if(ln.startsWith("Total:")) total = ln.substring(Math.min(6, ln.length())).trim();
                    if(ln.startsWith("Customer:")) cust = ln.substring(Math.min(9, ln.length())).trim();
                }
                String key = String.format("Bill %s | %s | %s | %s", id, date, total, cust);
                lm.addElement(key);
                map.put(key, p);
            }

            // keep a copy of all keys for filtering
            java.util.List<String> allKeys = new ArrayList<>();
            for(int i=0;i<lm.getSize();i++) allKeys.add(lm.getElementAt(i));

            JList<String> list = new JList<>(lm);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane sp = new JScrollPane(list);
            sp.setPreferredSize(new Dimension(700,400));

            // Search UI: text field + Search and Clear buttons
            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.add(new JLabel("Search:"));
            JTextField searchField = new JTextField(30);
            searchPanel.add(searchField);
            JButton searchBtn = new JButton("Search");
            JButton clearBtn = new JButton("Clear");
            searchPanel.add(searchBtn);
            searchPanel.add(clearBtn);

            // buttons for actions on selected bill
            JButton view = new JButton("View");
            JButton reprint = new JButton("Reprint");
            JButton exportDate = new JButton("Export Day Summary");

            view.addActionListener(ev->{
                String sel = list.getSelectedValue();
                if(sel==null) { JOptionPane.showMessageDialog(this, "Select a bill first."); return; }
                try{ String txt = new String(Files.readAllBytes(map.get(sel)), StandardCharsets.UTF_8); showBillText(txt); }catch(Exception ex){ JOptionPane.showMessageDialog(this, "Failed to read bill."); }
            });
            reprint.addActionListener(ev->{
                String sel = list.getSelectedValue();
                if(sel==null) { JOptionPane.showMessageDialog(this, "Select a bill first."); return; }
                try{ String txt = new String(Files.readAllBytes(map.get(sel)), StandardCharsets.UTF_8); showBillText(txt); }catch(Exception ex){ JOptionPane.showMessageDialog(this, "Failed to read bill."); }
            });
            exportDate.addActionListener(ev->{
                String sel = list.getSelectedValue();
                if(sel==null) { JOptionPane.showMessageDialog(this, "Select a bill to derive the date."); return; }
                // extract date part yyyy-MM-dd from the selected entry
                String[] parts = sel.split("\\|");
                if(parts.length<2) { JOptionPane.showMessageDialog(this, "Cannot determine date."); return; }
                String datePart = parts[1].trim();
                if(datePart.length()>=10) datePart = datePart.substring(0,10);
                exportSummaryForDate(datePart);
            });

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            btns.add(view); btns.add(reprint); btns.add(exportDate);

            // Search filtering action
            ActionListener doFilter = ae -> {
                String q = searchField.getText().trim().toLowerCase();
                lm.clear();
                if(q.length()==0){
                    for(String k: allKeys) lm.addElement(k);
                } else {
                    for(String k: allKeys){
                        if(k.toLowerCase().contains(q)) lm.addElement(k);
                    }
                }
            };
            searchBtn.addActionListener(doFilter);
            clearBtn.addActionListener(e -> { searchField.setText(""); doFilter.actionPerformed(null); });

            JPanel container = new JPanel(new BorderLayout());
            container.add(searchPanel, BorderLayout.NORTH);
            container.add(sp, BorderLayout.CENTER);
            container.add(btns, BorderLayout.SOUTH);

            JOptionPane.showMessageDialog(this, container, "Bill History", JOptionPane.PLAIN_MESSAGE);

        }catch(Exception ex){ JOptionPane.showMessageDialog(this, "Failed to open history: "+ex.getMessage()); }
    }

    private void exportSummaryForDate(String yyyyMmDd){
        try{
            Path billsDir = Paths.get("bills");
            if(!Files.exists(billsDir)) { JOptionPane.showMessageDialog(this, "No bills found."); return; }
            java.util.List<String> rows = new ArrayList<>();
            rows.add("BillID,Date,Customer,Total,PaymentMode");
            double grand = 0.0;
            try(java.util.stream.Stream<Path> s = Files.list(billsDir)){
                for(Path p: (Iterable<Path>)s::iterator){
                    String fn = p.getFileName().toString();
                    if(!fn.startsWith("bill_")||!fn.endsWith(".txt")) continue;
                    String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    String id="", date="", cust="", total="", mode="";
                    for(String ln: content.split("\n")){
                        if(ln.startsWith("Bill ID:")) id = ln.substring(Math.min(8, ln.length())).trim();
                        if(ln.startsWith("Date:")) date = ln.substring(Math.min(5, ln.length())).trim();
                        if(ln.startsWith("Total:")) total = ln.substring(Math.min(6, ln.length())).trim();
                        if(ln.startsWith("Customer:")) cust = ln.substring(Math.min(13, ln.length())).trim();
                        if(ln.startsWith("Payment Mode:")) mode = ln.substring(Math.min(13, ln.length())).trim();
                    }
                    if(date.startsWith(yyyyMmDd)){
                        rows.add(String.format("%s,%s,%s,%s,%s", id, date, cust.replace(","," "), total.replace(","," "), mode));
                        try{ grand += Double.parseDouble(total.replaceAll("[^0-9.\\-]","")); }catch(Exception ex){}
                    }
                }
            }
            if(rows.size()<=1){ JOptionPane.showMessageDialog(this, "No bills for date " + yyyyMmDd); return; }
            Path out = billsDir.resolve("summary_"+yyyyMmDd+".csv");
            Files.write(out, rows, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Exported summary to: " + out.toAbsolutePath().toString() + "\nGrand Total: " + String.format("%.2f", grand));
        }catch(Exception ex){ JOptionPane.showMessageDialog(this, "Failed to export summary: "+ex.getMessage()); }
    }

    void showBillText(String billText){
        JTextArea ta = new JTextArea(billText);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ta.setEditable(false);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(600,400));
        JOptionPane.showMessageDialog(this, sp, "Printed Bill", JOptionPane.PLAIN_MESSAGE);
    }

    String centerText(String s){
        int width = 40;
        int p = Math.max(0, (width - s.length())/2);
        return String.join("", Collections.nCopies(p, " ")) + s;
    }

    public void requestFocusForTable(){
        itemsTable.requestFocusInWindow();
    }
}

class InventoryPanel extends JPanel {
    POSFrame parent;
    DefaultTableModel invModel;
    JTable invTable;
    TableRowSorter<DefaultTableModel> sorter;
    private final Path inventoryFile;

    public InventoryPanel(POSFrame parent){
        this.parent = parent;
        setLayout(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createTitledBorder("Inventory"));

        // file to persist inventory (instance field)
        this.inventoryFile = Paths.get("inventory.csv");

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("Search:"));
        JTextField searchField = new JTextField(20);
        searchRow.add(searchField);
        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> parent.show("dashboard"));
        top.add(backBtn, BorderLayout.WEST);
        top.add(searchRow, BorderLayout.NORTH);

        add(top, BorderLayout.NORTH);

        invModel = new DefaultTableModel(new Object[]{"Code","Item","Quantity","Price","Last Updated"},0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        // load from disk if available, otherwise add sample data and save
        if (Files.exists(inventoryFile)) {
            try {
                java.util.List<String> lines = Files.readAllLines(inventoryFile, StandardCharsets.UTF_8);
                for (String ln : lines) {
                    if (ln.trim().isEmpty()) continue;
                    String[] parts = ln.split(",", -1);
                    if (parts.length >= 4) {
                        String code = parts[0];
                        String item = parts[1];
                        int qty = 0;
                        try { qty = Integer.parseInt(parts[2]); } catch (Exception ex) {}
                        String price = "0.00";
                        String last = "";
                        if (parts.length >= 5) {
                            price = parts[3];
                            last = parts[4];
                        } else {
                            // older format: code,item,qty,last
                            last = parts[3];
                        }
                        invModel.addRow(new Object[]{code, item, qty, price, last});
                    }
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load inventory: " + ex.getMessage());
            }
        } else {
            invModel.addRow(new Object[]{"A001","Soap", 120, "30.00", nowStr()});
            invModel.addRow(new Object[]{"A002","Shampoo", 80, "120.00", nowStr()});
            invModel.addRow(new Object[]{"A003","Toothpaste", 60, "80.00", nowStr()});
            invModel.addRow(new Object[]{"A004","Laptop", 6, "50000.00", nowStr()});
            invModel.addRow(new Object[]{"A005","Mouse", 25, "800.00", nowStr()});
            saveInventory();
        }

        invTable = new JTable(invModel);
        sorter = new TableRowSorter<>(invModel);
        invTable.setRowSorter(sorter);

        add(new JScrollPane(invTable), BorderLayout.CENTER);

        JPanel update = new JPanel(new FlowLayout(FlowLayout.LEFT));
        update.setBorder(BorderFactory.createTitledBorder("Update Inventory (add quantity / set price)"));
        JTextField codeField = new JTextField(8);
        JTextField qtyField = new JTextField(6);
        JTextField nameField = new JTextField(12);
        JTextField priceField = new JTextField(8);

        JButton addBtn = new JButton("Add Quantity");
        JButton priceBtn = new JButton("Set Price");
        update.add(new JLabel("Code:")); update.add(codeField);
        update.add(new JLabel("Qty to add:")); update.add(qtyField);
        update.add(new JLabel("Item Name:")); update.add(nameField);
        update.add(new JLabel("Price:")); update.add(priceField);
        update.add(addBtn);
        update.add(priceBtn);
        JButton editPriceSelectedBtn = new JButton("Edit Selected Price");
        update.add(editPriceSelectedBtn);

        editPriceSelectedBtn.addActionListener(ev -> {
            int viewRow = invTable.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(this, "Select an inventory row to edit its price.");
                return;
            }
            int modelRow = invTable.convertRowIndexToModel(viewRow);
            String current = invModel.getValueAt(modelRow, 3).toString();
            String input = JOptionPane.showInputDialog(this, "Enter new price:", current);
            if (input == null) return;
            try {
                double p = Double.parseDouble(input.trim());
                invModel.setValueAt(String.format("%.2f", p), modelRow, 3);
                invModel.setValueAt(nowStr(), modelRow, 4);
                saveInventory();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid price: " + ex.getMessage());
            }
        });

        addBtn.addActionListener(e -> {
            String code = codeField.getText().trim();
                try{
                    int add = Integer.parseInt(qtyField.getText().trim());
                    String nameItem = nameField.getText().trim();
                    boolean found=false;
                    for(int i=0;i<invModel.getRowCount();i++){
                        if(invModel.getValueAt(i,0).toString().equalsIgnoreCase(code)){
                            int cur = Integer.parseInt(invModel.getValueAt(i,2).toString());
                            invModel.setValueAt(cur + add, i, 2);
                            invModel.setValueAt(nowStr(), i, 4);
                            found=true; break;
                        }
                    }
                    if(!found){
                        invModel.addRow(new Object[]{code, nameItem.isEmpty()?"New Item":nameItem, add, "0.00", nowStr()});
                    }
                    // persist inventory after change
                    saveInventory();
                } catch(Exception ex){ JOptionPane.showMessageDialog(this, "Invalid quantity"); }
        });

        priceBtn.addActionListener(ev -> {
            String code = codeField.getText().trim();
            String priceTxt = priceField.getText().trim();
            if(code.isEmpty()){ JOptionPane.showMessageDialog(this, "Enter code to set price for."); return; }
            if(priceTxt.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a price value."); return; }
            try{
                double price = Double.parseDouble(priceTxt);
                boolean found=false;
                for(int i=0;i<invModel.getRowCount();i++){
                    if(invModel.getValueAt(i,0).toString().equalsIgnoreCase(code)){
                        invModel.setValueAt(String.format("%.2f", price), i, 3);
                        invModel.setValueAt(nowStr(), i, 4);
                        found=true; break;
                    }
                }
                if(!found){
                    String nameItem = nameField.getText().trim();
                    invModel.addRow(new Object[]{code, nameItem.isEmpty()?"New Item":nameItem, 0, String.format("%.2f", price), nowStr()});
                }
                saveInventory();
            }catch(Exception ex){ JOptionPane.showMessageDialog(this, "Invalid price: " + ex.getMessage()); }
        });

        add(update, BorderLayout.SOUTH);
        // search behaviour
        searchField.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){ apply(); }
            public void removeUpdate(DocumentEvent e){ apply(); }
            public void changedUpdate(DocumentEvent e){ apply(); }
            void apply(){
                String txt = searchField.getText();
                if(txt.trim().length()==0) sorter.setRowFilter(null);
                else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(txt)));
            }
        });
    }

    private void saveInventory() {
        try {
            java.util.List<String> out = new ArrayList<>();
            for (int i = 0; i < invModel.getRowCount(); i++) {
                String code = invModel.getValueAt(i, 0).toString().replace(",", " ");
                String item = invModel.getValueAt(i, 1).toString().replace(",", " ");
                String qty = invModel.getValueAt(i, 2).toString();
                String price = invModel.getValueAt(i, 3).toString().replace(",", " ");
                String last = invModel.getValueAt(i, 4).toString().replace(",", " ");
                out.add(code + "," + item + "," + qty + "," + price + "," + last);
            }
            Files.write(inventoryFile, out, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save inventory: " + ex.getMessage());
        }
    }
    
    // Helper: find item name by code (case-insensitive), returns null if not found
    public synchronized String findItemNameByCode(String code) {
        if (code == null) return null;
        for (int i = 0; i < invModel.getRowCount(); i++) {
            if (invModel.getValueAt(i,0).toString().equalsIgnoreCase(code)) {
                return invModel.getValueAt(i,1).toString();
            }
        }
        return null;
    }

    // Helper: find item price by code (case-insensitive), returns null if not found
    public synchronized String findItemPriceByCode(String code) {
        if (code == null) return null;
        for (int i = 0; i < invModel.getRowCount(); i++) {
            if (invModel.getValueAt(i,0).toString().equalsIgnoreCase(code)) {
                return invModel.getValueAt(i,3).toString();
            }
        }
        return null;
    }

    // Helper: decrement quantity for a code by amount (clamps at 0). Returns true if item found.
    public synchronized boolean decrementQuantity(String code, int amount) {
        if (code == null) return false;
        for (int i = 0; i < invModel.getRowCount(); i++) {
            if (invModel.getValueAt(i,0).toString().equalsIgnoreCase(code)) {
                try {
                    int cur = Integer.parseInt(invModel.getValueAt(i,2).toString());
                    int next = Math.max(0, cur - amount);
                    invModel.setValueAt(next, i, 2);
                    invModel.setValueAt(nowStr(), i, 4);
                    saveInventory();
                } catch (Exception ex) {
                    // ignore parse errors
                }
                return true;
            }
        }
        return false;
    }
    String nowStr(){ return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
}