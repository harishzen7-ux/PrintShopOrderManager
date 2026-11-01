USE print_shop;

CREATE TABLE IF NOT EXISTS order_payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);

-- Existing "Paid" orders predate payment records; retain their paid status
-- by recording the full order total once. Re-running this migration is safe.
INSERT INTO order_payments (order_id, amount)
SELECT o.order_id, o.total_amount
FROM orders o
WHERE o.payment_status = 'Paid'
  AND NOT EXISTS (
      SELECT 1
      FROM order_payments p
      WHERE p.order_id = o.order_id
  );
