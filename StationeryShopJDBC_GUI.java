import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;

// Product class to represent product data
class Product {
    private int id;
    private String name;
    private int price;

    public Product(int id, String name, int price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return id + ". " + name + " (Rupees " + price + ")";
    }
}

// Main class for the Stationery Shop application
public class StationeryShopJDBC_GUI {

    private static final String DB_URL = "jdbc:mysql://localhost:3307/syitdb";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "";

    private static JFrame frame;
    private static JComboBox<String> productComboBox;
    private static JTextField quantityField;
    private static JTextArea orderTextArea;
    private static JButton addButton, removeButton, finalizeButton;

    private static java.util.List<OrderItem> orderItems = new java.util.ArrayList<>();
    private static int customerId = -1;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Stationery Shop");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);
            frame.setLayout(new BorderLayout());

            // Input Panel
            JPanel inputPanel = new JPanel();
            inputPanel.setLayout(new GridLayout(3, 2));

            JLabel customerLabel = new JLabel("Enter customer name:");
            JTextField customerField = new JTextField();

            JLabel productLabel = new JLabel("Select product:");
            productComboBox = new JComboBox<>();
            loadProducts();

            JLabel quantityLabel = new JLabel("Enter quantity:");
            quantityField = new JTextField();

            inputPanel.add(customerLabel);
            inputPanel.add(customerField);
            inputPanel.add(productLabel);
            inputPanel.add(productComboBox);
            inputPanel.add(quantityLabel);
            inputPanel.add(quantityField);

            frame.add(inputPanel, BorderLayout.NORTH);

            // Order Text Area
            orderTextArea = new JTextArea();
            orderTextArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(orderTextArea);
            frame.add(scrollPane, BorderLayout.CENTER);

            // Button Panel
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout());

            addButton = new JButton("Add Item");
            removeButton = new JButton("Remove Item");
            finalizeButton = new JButton("Finalize Order");

            buttonPanel.add(addButton);
            buttonPanel.add(removeButton);
            buttonPanel.add(finalizeButton);

            frame.add(buttonPanel, BorderLayout.SOUTH);

            // Add Action Listeners
            addButton.addActionListener(e -> addItem(customerField.getText()));
            removeButton.addActionListener(e -> removeItem());
            finalizeButton.addActionListener(e -> finalizeOrder(customerField.getText()));

            frame.setVisible(true);
        });
    }

    // Load products from the database
    private static void loadProducts() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String query = "SELECT * FROM products";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                boolean dataFound = false;
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    int price = rs.getInt("price");
                    productComboBox.addItem(new Product(id, name, price).toString());
                    dataFound = true;
                }
                if (!dataFound) {
                    JOptionPane.showMessageDialog(frame, "No products found in the database.");
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "Error loading products: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add item to the order
    private static void addItem(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter a customer name.");
            return;
        }

        if (customerId == -1) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
                customerId = addCustomer(conn, customerName);
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(frame, "Error adding customer: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        String selectedProduct = (String) productComboBox.getSelectedItem();
        if (selectedProduct == null) {
            JOptionPane.showMessageDialog(frame, "Please select a product.");
            return;
        }

        try {
            int productId = Integer.parseInt(selectedProduct.substring(0, selectedProduct.indexOf(".")));
            String name = selectedProduct.substring(selectedProduct.indexOf(" ") + 1, selectedProduct.indexOf("(")).trim();
            int price = Integer.parseInt(selectedProduct.substring(selectedProduct.indexOf("Rupees") + 6, selectedProduct.indexOf(")")).trim());
            int quantity = Integer.parseInt(quantityField.getText().trim());

            if (quantity <= 0) {
                throw new NumberFormatException();
            }

            Product product = new Product(productId, name, price);
            orderItems.add(new OrderItem(product, quantity));
            orderTextArea.append("Added: " + product.getName() + " x" + quantity + "\n");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid quantity (greater than 0).");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error processing product: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Remove item from the order
    private static void removeItem() {
        String itemToRemove = JOptionPane.showInputDialog(frame, "Enter item name to remove:");

        if (itemToRemove != null && !itemToRemove.isEmpty()) {
            boolean found = false;
            for (Iterator<OrderItem> iterator = orderItems.iterator(); iterator.hasNext();) {
                OrderItem item = iterator.next();
                if (item.getProduct().getName().equalsIgnoreCase(itemToRemove)) {
                    iterator.remove();
                    orderTextArea.append("Removed: " + item.getProduct().getName() + "\n");
                    found = true;
                    break;
                }
            }
            if (!found) {
                JOptionPane.showMessageDialog(frame, "Item not found in the order.");
            }
        }
    }

    // Finalize the order and save to the database
    private static void finalizeOrder(String customerName) {
        if (orderItems.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No items in the order to finalize.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            for (OrderItem item : orderItems) {
                addOrder(conn, customerId, item.getProduct().getId(), item.getQuantity());
            }
            printOrderSummary(conn, customerId);
            orderItems.clear();
            orderTextArea.setText("");
            JOptionPane.showMessageDialog(frame, "Order finalized successfully.");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(frame, "Error finalizing order: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add a new customer to the database
    private static int addCustomer(Connection conn, String name) throws SQLException {
        String query = "INSERT INTO customers (name) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve customer ID.");
    }

    // Add an order to the database
    private static void addOrder(Connection conn, int customerId, int productId, int quantity) throws SQLException {
        String query = "INSERT INTO orders (customer_id, product_id, quantity) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, customerId);
            pstmt.setInt(2, productId);
            pstmt.setInt(3, quantity);
            pstmt.executeUpdate();
        }
    }

    // Print the order summary
    private static void printOrderSummary(Connection conn, int customerId) throws SQLException {
        String query = "SELECT c.name AS customer_name, p.name AS product_name, o.quantity, (p.price * o.quantity) AS total_price " +
                "FROM orders o " +
                "JOIN customers c ON o.customer_id = c.id " +
                "JOIN products p ON o.product_id = p.id " +
                "WHERE o.customer_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, customerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                int totalAmount = 0;
                while (rs.next()) {
                    String productName = rs.getString("product_name");
                    int quantity = rs.getInt("quantity");
                    int totalPrice = rs.getInt("total_price");
                    totalAmount += totalPrice;
                    orderTextArea.append("Product: " + productName + ", Quantity: " + quantity + ", Total: " + totalPrice + "\n");
                }
                orderTextArea.append("Grand Total: " + totalAmount + "\n");
            }
        }
    }
}

// OrderItem class to represent items in an order
class OrderItem {
    private Product product;
    private int quantity;

    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }
}