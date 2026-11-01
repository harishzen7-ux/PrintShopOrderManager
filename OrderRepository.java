import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes print orders in the MySQL database. */
final class OrderRepository {
    private static final String URL = setting("printshop.db.url", "PRINTSHOP_DB_URL",
            "jdbc:mysql://localhost:3306/print_shop");
    private static final String USER = setting("printshop.db.user", "PRINTSHOP_DB_USER", "root");
    private static final String PASSWORD = setting("printshop.db.password", "PRINTSHOP_DB_PASSWORD", "2007");

    private OrderRepository() {}

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.trim().isEmpty()) value = System.getenv(environment);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    static String errorMessage(SQLException error) {
        String message = error.getMessage() == null ? "Database operation failed." : error.getMessage();
        return PASSWORD.isEmpty() ? message : message.replace(PASSWORD, "********");
    }

    static List<Order> findOrders(String filter) throws SQLException {
        boolean searching = filter != null && !filter.trim().isEmpty();
        String sql = "SELECT * FROM orders " + (searching
                ? "WHERE CAST(order_id AS CHAR) LIKE ? OR customer_name LIKE ? "
                : "") + "ORDER BY order_id DESC";
        List<Order> orders = new ArrayList<>();

        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (searching) {
                String query = "%" + filter.trim() + "%";
                statement.setString(1, query);
                statement.setString(2, query);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) orders.add(new Order(rows));
            }
        }
        return orders;
    }

    static int createOrder(Order order) throws SQLException {
        String sql = "INSERT INTO orders (customer_name,phone_number,pages,copies,print_type,binding,"
                + "total_amount,order_status,payment_status) VALUES (?,?,?,?,?,?,?,'Pending','Unpaid')";
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindOrder(statement, order);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Order saved, but its ID was not returned.");
                return keys.getInt(1);
            }
        }
    }

    private static void bindOrder(PreparedStatement statement, Order order) throws SQLException {
        statement.setString(1, order.name);
        statement.setString(2, order.phone);
        statement.setInt(3, order.pages);
        statement.setInt(4, order.copies);
        statement.setString(5, order.type);
        statement.setString(6, order.binding);
        statement.setBigDecimal(7, order.total);
    }

    static void updateOrder(Order order) throws SQLException {
        String sql = "UPDATE orders SET customer_name=?,phone_number=?,pages=?,copies=?,print_type=?,"
                + "binding=?,total_amount=? WHERE order_id=?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOrder(statement, order);
            statement.setInt(8, order.id);
            if (statement.executeUpdate() == 0) throw new SQLException("Order no longer exists.");
        }
    }

    static void updateStatus(int orderId, String status) throws SQLException {
        String sql = "UPDATE orders SET order_status=? WHERE order_id=?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, orderId);
            if (statement.executeUpdate() == 0) throw new SQLException("Order no longer exists.");
        }
    }

    static void deleteOrder(int orderId) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM orders WHERE order_id=?")) {
            statement.setInt(1, orderId);
            if (statement.executeUpdate() == 0) throw new SQLException("Order no longer exists.");
        }
    }

}
