# Print Shop Order Manager

A small Java Swing desktop application for managing print orders in MySQL.
Screens open in desktop dialogs; no browser or terminal interaction is needed
after launch.

## Features

- Create, search, browse, update, and delete print orders.
- Sortable order table, order details, and order-status tracking.
- Live bill calculation using the existing rates:
  - Black-and-white: **₹2 per page**
  - Colour: **₹10 per page**
  - Spiral binding: **₹30 per copy**
  - No binding: **₹0**
- Order status tracking: Pending, In Progress, Ready for Pickup, and Delivered.

The bill remains `(pages × copies × printing rate) + (copies × binding charge)`.

## Java file guide

The code is grouped into five files so each part has a clear job:

- `PrintShop.java` starts the application.
- `Order.java` stores order details and calculates bills.
- `OrderRepository.java` contains the MySQL queries.
- `Ui.java` provides shared dialog layout, controls, and message helpers.
- `Dialogs.java` contains the menu and order screens.

## Requirements

- JDK 11 or later (`java` and `javac`).
- MySQL Server 8.0 or later.
- The MySQL Connector/J driver at `lib\mysql-connector-j-9.5.0.jar`.

## Database setup

For a new database, run `print_shop.sql` once to create the `print_shop.orders`
table. For an existing installation, use its existing `print_shop` database;
do not recreate or reset it. The payment-history migration is not needed for
this simplified demo.

## Database connection

The app uses the project's existing local MySQL defaults: URL
`jdbc:mysql://localhost:3306/print_shop`, user `root`, and the existing local
password default. No password environment-variable setup is required.

## Compile and launch on Windows

Run these commands from the project directory:

```powershell
$sources = Get-ChildItem -Filter *.java | ForEach-Object { $_.Name }
javac -encoding UTF-8 -cp ".;lib\*" -d out $sources
if ($LASTEXITCODE -eq 0) {
    java -cp "out;lib\*" PrintShop
}
```

The program opens its desktop menu after launch. If the database is unavailable,
the application reports the problem in a dialog.
