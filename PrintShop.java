import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class PrintShop {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/print_shop";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "2007";
    private static final Scanner input = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Print Shop Order Manager");
        try {
            boolean running = true;
            while (running) {
                System.out.println("\n1. Add order");
                System.out.println("2. View all orders");
                System.out.println("3. Search order by ID");
                System.out.println("4. Update order");
                System.out.println("5. Delete order");
                System.out.println("6. Exit");
                int choice = readChoice("Choose an option: ", 1, 6);

                switch (choice) {
                    case 1:
                        addOrder();
                        break;
                    case 2:
                        viewOrders();
                        break;
                    case 3:
                        searchOrder();
                        break;
                    case 4:
                        updateOrder();
                        break;
                    case 5:
                        deleteOrder();
                        break;
                    case 6:
                        running = false;
                        System.out.println("Goodbye.");
                        break;
                    default:
                        break;
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static void addOrder() throws SQLException {
        String name = readText("Customer name: ");
        String phone = readText("Phone number: ");
        int pages = readPositiveInt("Number of pages: ");
        int copies = readPositiveInt("Number of copies: ");
        String printType = readPrintType();
        String binding = readBinding();
        String orderStatus = readOrderStatus();
        String paymentStatus = readPaymentStatus();
        BigDecimal total = calculateTotal(pages, copies, printType, binding);

        String sql = "INSERT INTO orders (customer_name, phone_number, pages, copies, "
                + "print_type, binding, total_amount, order_status, payment_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, phone);
            statement.setInt(3, pages);
            statement.setInt(4, copies);
            statement.setString(5, printType);
            statement.setString(6, binding);
            statement.setBigDecimal(7, total);
            statement.setString(8, orderStatus);
            statement.setString(9, paymentStatus);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Order was saved, but its ID was not returned.");
                }
                System.out.println("Order added. ID: " + keys.getInt(1)
                        + ". Total: Rs. " + total.toPlainString());
            }
        }
    }

    private static void viewOrders() throws SQLException {
        String sql = "SELECT * FROM orders ORDER BY order_id";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            printHeader();
            boolean found = false;
            while (results.next()) {
                found = true;
                printOrder(results);
            }
            if (!found) {
                System.out.println("No orders found.");
            }
        }
    }

    private static void searchOrder() throws SQLException {
        int id = readPositiveInt("Order ID: ");
        String sql = "SELECT * FROM orders WHERE order_id = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                printHeader();
                if (results.next()) {
                    printOrder(results);
                } else {
                    System.out.println("Order ID " + id + " does not exist.");
                }
            }
        }
    }

    private static void updateOrder() throws SQLException {
        int id = readPositiveInt("Order ID to update: ");
        String name = readText("Customer name: ");
        String phone = readText("Phone number: ");
        int pages = readPositiveInt("Number of pages: ");
        int copies = readPositiveInt("Number of copies: ");
        String printType = readPrintType();
        String binding = readBinding();
        String orderStatus = readOrderStatus();
        String paymentStatus = readPaymentStatus();
        BigDecimal total = calculateTotal(pages, copies, printType, binding);

        String sql = "UPDATE orders SET customer_name = ?, phone_number = ?, pages = ?, "
                + "copies = ?, print_type = ?, binding = ?, total_amount = ?, "
                + "order_status = ?, payment_status = ? WHERE order_id = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, phone);
            statement.setInt(3, pages);
            statement.setInt(4, copies);
            statement.setString(5, printType);
            statement.setString(6, binding);
            statement.setBigDecimal(7, total);
            statement.setString(8, orderStatus);
            statement.setString(9, paymentStatus);
            statement.setInt(10, id);
            if (statement.executeUpdate() == 0) {
                System.out.println("Order ID " + id + " does not exist.");
            } else {
                System.out.println("Order updated. New total: Rs. " + total.toPlainString());
            }
        }
    }

    private static void deleteOrder() throws SQLException {
        int id = readPositiveInt("Order ID to delete: ");
        if (!readYesNo("Delete order " + id + "? (y/n): ")) {
            System.out.println("Deletion cancelled.");
            return;
        }
        String sql = "DELETE FROM orders WHERE order_id = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            if (statement.executeUpdate() == 0) {
                System.out.println("Order ID " + id + " does not exist.");
            } else {
                System.out.println("Order deleted.");
            }
        }
    }

    private static BigDecimal calculateTotal(int pages, int copies, String type, String binding) {
        BigDecimal rate = type.equals("Colour")
                ? new BigDecimal("10") : new BigDecimal("2");
        BigDecimal printing = BigDecimal.valueOf(pages)
                .multiply(BigDecimal.valueOf(copies)).multiply(rate);
        BigDecimal bindingCost = binding.equals("Spiral")
                ? BigDecimal.valueOf(copies).multiply(new BigDecimal("30"))
                : BigDecimal.ZERO;
        return printing.add(bindingCost).setScale(2);
    }

    private static void printHeader() {
        System.out.printf("%-5s %-20s %-16s %6s %6s %-16s %-9s %10s %-12s %-10s%n",
                "ID", "Customer", "Phone", "Pages", "Copies", "Print type",
                "Binding", "Total", "Order status", "Payment");
        System.out.println("--------------------------------------------------------------------------------------------------------------");
    }

    private static void printOrder(ResultSet row) throws SQLException {
        System.out.printf("%-5d %-20s %-16s %6d %6d %-16s %-9s %10s %-12s %-10s%n",
                row.getInt("order_id"), fit(row.getString("customer_name"), 20),
                fit(row.getString("phone_number"), 16), row.getInt("pages"),
                row.getInt("copies"), fit(row.getString("print_type"), 16),
                fit(row.getString("binding"), 9),
                row.getBigDecimal("total_amount").toPlainString(),
                fit(row.getString("order_status"), 12),
                fit(row.getString("payment_status"), 10));
    }

    private static String fit(String value, int width) {
        if (value.length() <= width) {
            return value;
        }
        return value.substring(0, width - 3) + "...";
    }

    private static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = input.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Value cannot be empty.");
        }
    }

    private static int readPositiveInt(String prompt) {
        while (true) {
            int value = readChoice(prompt, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (value > 0) {
                return value;
            }
            System.out.println("Enter a number greater than zero.");
        }
    }

    private static int readChoice(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(input.nextLine().trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid whole number.");
                continue;
            }
            System.out.println("Choose a number from " + min + " to " + max + ".");
        }
    }

    private static String readPrintType() {
        System.out.println("1. Black-and-white  2. Colour");
        return readChoice("Print type: ", 1, 2) == 1 ? "Black-and-white" : "Colour";
    }

    private static String readBinding() {
        System.out.println("1. None  2. Spiral");
        return readChoice("Binding: ", 1, 2) == 1 ? "None" : "Spiral";
    }

    private static String readOrderStatus() {
        System.out.println("1. Pending  2. Printing  3. Ready  4. Delivered");
        int choice = readChoice("Order status: ", 1, 4);
        String[] statuses = {"Pending", "Printing", "Ready", "Delivered"};
        return statuses[choice - 1];
    }

    private static String readPaymentStatus() {
        System.out.println("1. Unpaid  2. Paid");
        return readChoice("Payment status: ", 1, 2) == 1 ? "Unpaid" : "Paid";
    }

    private static boolean readYesNo(String prompt) {
        while (true) {
            String answer = readText(prompt);
            if (answer.equalsIgnoreCase("y")) {
                return true;
            }
            if (answer.equalsIgnoreCase("n")) {
                return false;
            }
            System.out.println("Enter y or n.");
        }
    }
}
