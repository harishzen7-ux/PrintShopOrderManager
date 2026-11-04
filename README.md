# Print Shop Order Manager

A Java Swing desktop application for managing print-shop orders. The application
stores order information in a MySQL database and provides a desktop interface
for creating, finding, updating, and tracking orders.

## Features

- Create, search, view, update, and delete print orders.
- Browse orders in a sortable table and track their status.
- Calculate order totals using the configured print and binding rates:
  - Black-and-white printing: **₹2 per page**
  - Colour printing: **₹10 per page**
  - Spiral binding: **₹30 per copy**
  - No binding: **₹0**
- Calculate the total as `(pages × copies × printing rate) + (copies × binding charge)`.
- Track order statuses: Pending, Processing, and Completed.

## Requirements

- JDK 11 or later, including `java` and `javac`.
- MySQL Server 8.0 or later.
- MySQL Connector/J, included at `lib\mysql-connector-j-9.5.0.jar`.

## Set up the database

1. Start MySQL Server.
2. Open `print_shop.sql` in a MySQL client such as MySQL Workbench and execute
   it. This creates the `print_shop` database and its `orders` table if they do
   not already exist.
3. If you already have a `print_shop` database, keep your existing data; do not
   reset or recreate it.

The `migrations\001_payment_history.sql` file is not required by this version
of the application.

## Configure the database connection

By default, the application connects to `jdbc:mysql://localhost:3306/print_shop`
as user `root`. Override the connection settings with environment variables or
Java system properties:

| Setting | Environment variable | Java property |
| --- | --- | --- |
| JDBC URL | `PRINTSHOP_DB_URL` | `printshop.db.url` |
| Username | `PRINTSHOP_DB_USER` | `printshop.db.user` |
| Password | `PRINTSHOP_DB_PASSWORD` | `printshop.db.password` |

Set these values to match your local MySQL configuration before launching the
application.

## Compile and run on Windows

Open PowerShell in the project directory and run:

```powershell
$sources = Get-ChildItem -Filter *.java | ForEach-Object { $_.Name }
 javac -encoding UTF-8 -cp ".;lib\*" -d out $sources
 if ($LASTEXITCODE -eq 0) {
  java -cp "out;lib\*" PrintShop
}
```

The desktop menu opens after launch. If the database cannot be reached, the
application displays an error dialog.

## Source files

- `PrintShop.java` — application entry point.
- `Order.java` — order data and bill calculation.
- `OrderRepository.java` — MySQL data access.
- `Ui.java` — shared interface components and helpers.
- `Dialogs.java` — application menu and order screens.
