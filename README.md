# Print Shop Order Manager

A Java Swing desktop application for managing print orders in MySQL. The menu,
forms, order tables, confirmations, payment screens, dashboard, and receipt
preview all appear in desktop dialog windows; the application does not use a
browser or require terminal interaction after launch.

## Features

- Create, browse, search, update, and delete print orders.
- Sortable order table with details and row actions.
- Live bill calculation using the existing rates:
  - Black-and-white: **₹2 per page**
  - Colour: **₹10 per page**
  - Spiral binding: **₹30 per copy**
  - No binding: **₹0**
- Order status tracking: Pending, In Progress, Ready for Pickup, and Delivered.
- Payment history with partial payments and outstanding balance validation.
- Receipt preview, save as TXT, and print.
- Live dashboard statistics from MySQL.

The bill remains `(pages × copies × printing rate) + (copies × binding charge)`.

## Requirements

- JDK 11 or later (`java` and `javac`).
- MySQL Server 8.0 or later.
- The MySQL Connector/J driver at `lib\mysql-connector-j-9.5.0.jar`.

## Database setup

For a new database, run `print_shop.sql` once to create the existing
`print_shop.orders` table. Then apply the payment-history migration below.
The base schema file is unchanged.

For an existing installation, do **not** recreate or reset the database. Back
up the database, then apply the same additive, re-runnable migration:

```powershell
Get-Content .\migrations\001_payment_history.sql | mysql -u root -p
```

The migration creates the payment history table without changing existing
order IDs or deleting orders. It also creates a one-time payment record for
each existing order whose status is `Paid`, treating that legacy status as
payment of the recorded total. Existing unpaid orders remain unchanged.

If `mysql` is not on `PATH`, run the command using the full path to `mysql.exe`.
For example:

```powershell
Get-Content .\migrations\001_payment_history.sql | `
  & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

## Database connection

The app uses the project's existing local MySQL defaults: URL
`jdbc:mysql://localhost:3306/print_shop`, user `root`, and the existing local
password default. No password environment-variable setup is required.

## Compile and launch on Windows

Run these commands from the project directory:

```powershell
javac -encoding UTF-8 -cp ".;lib\*" -d out PrintShop.java
if ($LASTEXITCODE -eq 0) {
    java -cp "out;lib\*" PrintShop
}
```

The program opens its desktop menu after launch. Once it is open, user
interaction takes place in its dialogs rather than in the terminal. If the
database is unavailable or the payment migration has not been applied, the
application reports the problem in a dialog.
