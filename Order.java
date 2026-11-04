import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;

/** The information and bill calculations for one print order. */
final class Order {
    static final BigDecimal MAX_TOTAL = new BigDecimal("99999999.99");

    int id;
    int pages;
    int copies;
    String name;
    String phone;
    String type;
    String binding;
    String state;
    BigDecimal total;

    Order(ResultSet row) throws SQLException {
        id = row.getInt("order_id");
        name = row.getString("customer_name");
        phone = row.getString("phone_number");
        pages = row.getInt("pages");
        copies = row.getInt("copies");
        type = row.getString("print_type");
        binding = row.getString("binding");
        total = row.getBigDecimal("total_amount");
        state = displayStatus(row.getString("order_status"));
    }

    Order(String name, String phone, int pages, int copies, String type, String binding) {
        this.name = name;
        this.phone = phone;
        this.pages = pages;
        this.copies = copies;
        this.type = type;
        this.binding = binding;
        total = calculateBill(pages, copies, type, binding);
    }

    static BigDecimal calculateBill(int pages, int copies, String type, String binding) {
        BigDecimal rate = "Colour".equals(type) ? BigDecimal.TEN : new BigDecimal("2");
        BigDecimal printing = rate.multiply(BigDecimal.valueOf(pages))
                .multiply(BigDecimal.valueOf(copies));
        BigDecimal bindingCost = "Spiral".equals(binding)
                ? new BigDecimal("30").multiply(BigDecimal.valueOf(copies))
                : BigDecimal.ZERO;
        return printing.add(bindingCost).setScale(2);
    }

    static String formatMoney(BigDecimal amount) {
        return "\u20B9" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String displayStatus(String value) {
        if ("Printing".equalsIgnoreCase(value) || "In Progress".equalsIgnoreCase(value)) return "Processing";
        if ("Ready".equalsIgnoreCase(value) || "Ready for Pickup".equalsIgnoreCase(value)
                || "Delivered".equalsIgnoreCase(value)) return "Completed";
        return value;
    }

}
