# Print Shop Order Manager

A beginner-friendly Java console program for managing print orders. It stores
orders in MySQL and uses JDBC to add, display, search, update, and delete them.

## Project files

```text
PrintShopOrderManager/
├── PrintShop.java                    Java application
├── print_shop.sql                    Database and table setup
└── lib/
    └── mysql-connector-j-9.5.0.jar   MySQL JDBC driver
```

### What each file does

- **`PrintShop.java`** contains the menu, input checks, billing calculation,
  database connection, and order operations.
- **`print_shop.sql`** creates the `print_shop` database and its `orders`
  table. Run this script once before using the application.
- **`mysql-connector-j-9.5.0.jar`** is MySQL Connector/J. It lets the Java
  program communicate with the MySQL server through JDBC.

## How Java connects to MySQL

JDBC is the database API used by the Java program; it is not a separate
project file. In `PrintShop.java`, the `connect()` method uses
`DriverManager.getConnection()` to open the connection:

```text
jdbc:mysql://localhost:3306/print_shop
```

This URL means the program connects to MySQL on this computer, using port
`3306`, and selects the `print_shop` database. The MySQL username and password
are configured near the top of `PrintShop.java` (`root` and `2007` in the
current setup).

The Connector/J JAR must be included on the Java classpath. The program uses
`PreparedStatement` for SQL operations and `ResultSet` for query results.
Try-with-resources closes connections and other database resources after use.

## Order operations (CRUD)

Select an operation from the console menu:

| Menu option | Action | Java method | Database command |
| --- | --- | --- | --- |
| 1 | Add an order | `addOrder()` | `INSERT` |
| 2 | View all orders | `viewOrders()` | `SELECT` |
| 3 | Search by order ID | `searchOrder()` | `SELECT` |
| 4 | Update an order | `updateOrder()` | `UPDATE` |
| 5 | Delete an order | `deleteOrder()` | `DELETE` |

An order ID is generated automatically. The program asks for confirmation
before deleting an order, and recalculates the bill when an order is updated.

## Requirements

- MySQL Server installed and running.
- Java JDK installed (so `java` and `javac` are available).
- MySQL Connector/J JAR in the project's `lib` folder.

## Create the database

Open **Command Prompt** in the project folder and run:

```bat
mysql -u root -p < print_shop.sql
```

Enter your MySQL `root` password when prompted. If `mysql` is not recognized,
run the command using the full path to `mysql.exe`, for example:

```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p < print_shop.sql
```

## Compile and run

Open **PowerShell** in the project folder:

```powershell
javac -cp ".;lib\*" PrintShop.java
if ($LASTEXITCODE -eq 0) {
    java -cp ".;lib\*" PrintShop
}
```

The configured database username and password in `PrintShop.java` must match
your local MySQL account. If yours are different, update `DB_USER` and
`DB_PASSWORD` near the top of that file.

## What is `PrintShop.class`?

When `javac` compiles `PrintShop.java`, it creates `PrintShop.class`. This file
contains Java bytecode that the Java runtime executes. It is generated output,
not source code, and can be deleted because compiling the program creates it
again.

To keep compiled files out of the project root, compile into an `out` folder
and run the application from there:

```powershell
javac -cp ".;lib\*" -d out PrintShop.java
if ($LASTEXITCODE -eq 0) {
    java -cp "out;lib\*" PrintShop
}
```

## Billing

- Black-and-white printing: **Rs. 2 per page**
- Colour printing: **Rs. 10 per page**
- Spiral binding: **Rs. 30 per copy**
- No binding: **Rs. 0**

The program calculates the total with `BigDecimal`:

```text
(pages × copies × printing rate) + (copies × binding charge)
```

It validates required text and positive page/copy counts, and asks again when
numeric input is invalid.


TO run the Complete project in your system install dependencies and use the following command in your respective terminal:

javac -cp ".;lib\*" -d out PrintShop.java
java -cp "out;lib\*" PrintShop