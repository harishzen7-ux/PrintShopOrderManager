import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Starts the Print Shop Order Manager application. */
public class PrintShop {
    private PrintShop() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("OptionPane.background", Color.WHITE);
            UIManager.put("Panel.background", Color.WHITE);
            new MenuDialog().setVisible(true);
        });
    }
}
