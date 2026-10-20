CREATE DATABASE IF NOT EXISTS print_shop;
USE print_shop;

CREATE TABLE IF NOT EXISTS orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    pages INT NOT NULL,
    copies INT NOT NULL,
    print_type VARCHAR(20) NOT NULL,
    binding VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    payment_status VARCHAR(20) NOT NULL
);
