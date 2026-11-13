import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Employee Payroll Management System
 * Complete single-file implementation with Swing GUI + SQLite + JDBC
 * All required Java concepts integrated into real functionality.
 */
public class EmployeePayrollSystem {

    // ==================== THEME COLORS ====================
    private static final Color PRIMARY_BLUE = new Color(25, 118, 210);
    private static final Color DARK_BLUE = new Color(13, 71, 161);
    private static final Color LIGHT_BLUE = new Color(227, 242, 253);
    private static final Color WHITE = Color.WHITE;
    private static final Color CARD_BG = new Color(245, 248, 255);
    private static final Color SUCCESS_GREEN = new Color(46, 125, 50);
    private static final Color ERROR_RED = new Color(198, 40, 40);

    // ==================== DATABASE ====================
    private static final String DB_URL = "jdbc:sqlite:payroll.db";
    private static Connection connection;
    private static final ReentrantLock dbLock = new ReentrantLock(); // Synchronization

    // ==================== COLLECTIONS ====================
    private static final List<Employee> employeeCache = new ArrayList<>(); // ArrayList
    private static final Map<Integer, Employee> employeeMap = new HashMap<>(); // HashMap + Generics

    // ==================== CUSTOM EXCEPTION ====================
    static class PayrollException extends Exception {
        public PayrollException(String message) {
            super(message);
        }
    }

    // ==================== INTERFACE ====================
    interface Calculable {
        double calculateGross();
        double calculateNet();
    }

    // ==================== ABSTRACT CLASS ====================
    abstract static class Person {
        protected String name;
        protected String gender;
        protected int age;

        public Person(String name, String gender, int age) {
            this.name = name;
            this.gender = gender;
            this.age = age;
        }

        public abstract String getDisplayInfo(); // Abstraction

        // Encapsulation
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    // ==================== EMPLOYEE (Inheritance) ====================
    static class Employee extends Person implements Calculable {
        private int empId;
        private String department;
        private String designation;
        private String email;
        private String phone;
        private double basicSalary;
        private double hra;
        private double allowances;
        private double bonus;
        private double deductions;

        public Employee(int empId, String name, String gender, int age, String department,
                        String designation, String email, String phone, double basicSalary) {
            super(name, gender, age);
            this.empId = empId;
            this.department = department;
            this.designation = designation;
            this.email = email;
            this.phone = phone;
            this.basicSalary = basicSalary;
            this.hra = basicSalary * 0.20;          // 20% HRA
            this.allowances = basicSalary * 0.10;   // 10% Allowances
            this.bonus = 0;
            this.deductions = basicSalary * 0.05;   // 5% default deduction
        }

        // Method Overriding
        @Override
        public String getDisplayInfo() {
            return empId + " - " + name + " (" + department + ")";
        }

        // Method Overloading
        public void setBonus(double bonus) {
            this.bonus = bonus;
        }

        public void setBonus(double bonus, String reason) {
            this.bonus = bonus;
            // reason can be logged if needed
        }

        @Override
        public double calculateGross() {
            return basicSalary + hra + allowances + bonus;
        }

        @Override
        public double calculateNet() {
            return calculateGross() - deductions;
        }

        // Getters & Setters (Encapsulation)
        public int getEmpId() { return empId; }
        public void setEmpId(int empId) { this.empId = empId; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getDesignation() { return designation; }
        public void setDesignation(String designation) { this.designation = designation; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public double getBasicSalary() { return basicSalary; }
        public void setBasicSalary(double basicSalary) {
            this.basicSalary = basicSalary;
            this.hra = basicSalary * 0.20;
            this.allowances = basicSalary * 0.10;
            this.deductions = basicSalary * 0.05;
        }
        public double getHra() { return hra; }
        public void setHra(double hra) { this.hra = hra; }
        public double getAllowances() { return allowances; }
        public void setAllowances(double allowances) { this.allowances = allowances; }
        public double getBonus() { return bonus; }
        public double getDeductions() { return deductions; }
        public void setDeductions(double deductions) { this.deductions = deductions; }
    }

    // ==================== ATTENDANCE ====================
    static class Attendance {
        private int empId;
        private int workingDays;
        private int presentDays;
        private int absentDays;
        private String monthYear;

        public Attendance(int empId, int workingDays, int presentDays, String monthYear) {
            this.empId = empId;
            this.workingDays = workingDays;
            this.presentDays = presentDays;
            this.absentDays = workingDays - presentDays;
            this.monthYear = monthYear;
        }

        public int getEmpId() { return empId; }
        public int getWorkingDays() { return workingDays; }
        public int getPresentDays() { return presentDays; }
        public int getAbsentDays() { return absentDays; }
        public String getMonthYear() { return monthYear; }
    }

    // ==================== PAYROLL RECORD ====================
    static class PayrollRecord {
        private int empId;
        private String empName;
        private String monthYear;
        private double basic;
        private double hra;
        private double allowances;
        private double bonus;
        private double deductions;
        private double gross;
        private double net;
        private int presentDays;
        private int workingDays;

        public PayrollRecord(int empId, String empName, String monthYear, double basic, double hra,
                             double allowances, double bonus, double deductions, double gross, double net,
                             int presentDays, int workingDays) {
            this.empId = empId;
            this.empName = empName;
            this.monthYear = monthYear;
            this.basic = basic;
            this.hra = hra;
            this.allowances = allowances;
            this.bonus = bonus;
            this.deductions = deductions;
            this.gross = gross;
            this.net = net;
            this.presentDays = presentDays;
            this.workingDays = workingDays;
        }

        // Getters
        public int getEmpId() { return empId; }
        public String getEmpName() { return empName; }
        public String getMonthYear() { return monthYear; }
        public double getBasic() { return basic; }
        public double getHra() { return hra; }
        public double getAllowances() { return allowances; }
        public double getBonus() { return bonus; }
        public double getDeductions() { return deductions; }
        public double getGross() { return gross; }
        public double getNet() { return net; }
        public int getPresentDays() { return presentDays; }
        public int getWorkingDays() { return workingDays; }
    }

    // ==================== DATABASE MANAGER ====================
    static class DatabaseManager {
        public static void initialize() throws SQLException {
            connection = DriverManager.getConnection(DB_URL);
            createTables();
            insertDefaultAdmin();
            loadEmployeesToCache();
        }

        private static void createTables() throws SQLException {
            String adminTable = "CREATE TABLE IF NOT EXISTS admin (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT UNIQUE NOT NULL," +
                    "password TEXT NOT NULL)";

            String empTable = "CREATE TABLE IF NOT EXISTS employees (" +
                    "emp_id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "gender TEXT," +
                    "age INTEGER," +
                    "department TEXT," +
                    "designation TEXT," +
                    "email TEXT," +
                    "phone TEXT," +
                    "basic_salary REAL)";

            String attTable = "CREATE TABLE IF NOT EXISTS attendance (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "emp_id INTEGER," +
                    "working_days INTEGER," +
                    "present_days INTEGER," +
                    "absent_days INTEGER," +
                    "month_year TEXT," +
                    "UNIQUE(emp_id, month_year)," +
                    "FOREIGN KEY(emp_id) REFERENCES employees(emp_id))";

            String payTable = "CREATE TABLE IF NOT EXISTS payroll (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "emp_id INTEGER," +
                    "emp_name TEXT," +
                    "month_year TEXT," +
                    "basic REAL," +
                    "hra REAL," +
                    "allowances REAL," +
                    "bonus REAL," +
                    "deductions REAL," +
                    "gross REAL," +
                    "net REAL," +
                    "present_days INTEGER," +
                    "working_days INTEGER," +
                    "UNIQUE(emp_id, month_year)," +
                    "FOREIGN KEY(emp_id) REFERENCES employees(emp_id))";

            try (Statement st = connection.createStatement()) {
                st.execute(adminTable);
                st.execute(empTable);
                st.execute(attTable);
                st.execute(payTable);
            }
        }

        private static void insertDefaultAdmin() throws SQLException {
            String check = "SELECT COUNT(*) FROM admin WHERE username = ?";
            try (PreparedStatement ps = connection.prepareStatement(check)) {
                ps.setString(1, "admin");
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) == 0) {
                    String insert = "INSERT INTO admin (username, password) VALUES (?, ?)";
                    try (PreparedStatement ins = connection.prepareStatement(insert)) {
                        ins.setString(1, "admin");
                        ins.setString(2, "admin123");
                        ins.executeUpdate();
                    }
                }
            }
        }

        public static boolean authenticate(String username, String password) {
            dbLock.lock();
            try {
                String sql = "SELECT * FROM admin WHERE username = ? AND password = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, password);
                    ResultSet rs = ps.executeQuery();
                    return rs.next();
                }
            } catch (SQLException e) {
                return false;
            } finally {
                dbLock.unlock();
            }
        }

        public static void loadEmployeesToCache() {
            employeeCache.clear();
            employeeMap.clear();
            dbLock.lock();
            try {
                String sql = "SELECT * FROM employees ORDER BY emp_id";
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        Employee emp = new Employee(
                                rs.getInt("emp_id"),
                                rs.getString("name"),
                                rs.getString("gender"),
                                rs.getInt("age"),
                                rs.getString("department"),
                                rs.getString("designation"),
                                rs.getString("email"),
                                rs.getString("phone"),
                                rs.getDouble("basic_salary")
                        );
                        employeeCache.add(emp);
                        employeeMap.put(emp.getEmpId(), emp);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                dbLock.unlock();
            }
        }

        public static boolean addEmployee(Employee emp) throws PayrollException {
            dbLock.lock();
            try {
                String sql = "INSERT INTO employees (emp_id, name, gender, age, department, designation, email, phone, basic_salary) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, emp.getEmpId());
                    ps.setString(2, emp.getName());
                    ps.setString(3, emp.getGender());
                    ps.setInt(4, emp.getAge());
                    ps.setString(5, emp.getDepartment());
                    ps.setString(6, emp.getDesignation());
                    ps.setString(7, emp.getEmail());
                    ps.setString(8, emp.getPhone());
                    ps.setDouble(9, emp.getBasicSalary());
                    ps.executeUpdate();
                    employeeCache.add(emp);
                    employeeMap.put(emp.getEmpId(), emp);
                    return true;
                }
            } catch (SQLException e) {
                throw new PayrollException("Failed to add employee: " + e.getMessage());
            } finally {
                dbLock.unlock();
            }
        }

        public static boolean updateEmployee(Employee emp) throws PayrollException {
            dbLock.lock();
            try {
                String sql = "UPDATE employees SET name=?, gender=?, age=?, department=?, designation=?, email=?, phone=?, basic_salary=? WHERE emp_id=?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, emp.getName());
                    ps.setString(2, emp.getGender());
                    ps.setInt(3, emp.getAge());
                    ps.setString(4, emp.getDepartment());
                    ps.setString(5, emp.getDesignation());
                    ps.setString(6, emp.getEmail());
                    ps.setString(7, emp.getPhone());
                    ps.setDouble(8, emp.getBasicSalary());
                    ps.setInt(9, emp.getEmpId());
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        employeeMap.put(emp.getEmpId(), emp);
                        for (int i = 0; i < employeeCache.size(); i++) {
                            if (employeeCache.get(i).getEmpId() == emp.getEmpId()) {
                                employeeCache.set(i, emp);
                                break;
                            }
                        }
                        return true;
                    }
                    return false;
                }
            } catch (SQLException e) {
                throw new PayrollException("Failed to update employee: " + e.getMessage());
            } finally {
                dbLock.unlock();
            }
        }

        public static boolean deleteEmployee(int empId) throws PayrollException {
            dbLock.lock();
            try {
                String sql = "DELETE FROM employees WHERE emp_id = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, empId);
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        employeeMap.remove(empId);
                        employeeCache.removeIf(e -> e.getEmpId() == empId);
                        return true;
                    }
                    return false;
                }
            } catch (SQLException e) {
                throw new PayrollException("Failed to delete employee: " + e.getMessage());
            } finally {
                dbLock.unlock();
            }
        }

        public static boolean saveAttendance(Attendance att) throws PayrollException {
            dbLock.lock();
            try {
                String sql = "INSERT OR REPLACE INTO attendance (emp_id, working_days, present_days, absent_days, month_year) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, att.getEmpId());
                    ps.setInt(2, att.getWorkingDays());
                    ps.setInt(3, att.getPresentDays());
                    ps.setInt(4, att.getAbsentDays());
                    ps.setString(5, att.getMonthYear());
                    ps.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                throw new PayrollException("Failed to save attendance: " + e.getMessage());
            } finally {
                dbLock.unlock();
            }
        }

        public static Attendance getAttendance(int empId, String monthYear) {
            dbLock.lock();
            try {
                String sql = "SELECT * FROM attendance WHERE emp_id = ? AND month_year = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, empId);
                    ps.setString(2, monthYear);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return new Attendance(
                                rs.getInt("emp_id"),
                                rs.getInt("working_days"),
                                rs.getInt("present_days"),
                                rs.getString("month_year")
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                dbLock.unlock();
            }
            return null;
        }

        public static boolean savePayroll(PayrollRecord pr) throws PayrollException {
            dbLock.lock();
            try {
                // Prevent duplicate
                String check = "SELECT COUNT(*) FROM payroll WHERE emp_id = ? AND month_year = ?";
                try (PreparedStatement cps = connection.prepareStatement(check)) {
                    cps.setInt(1, pr.getEmpId());
                    cps.setString(2, pr.getMonthYear());
                    ResultSet rs = cps.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new PayrollException("Payroll already exists for this employee and month.");
                    }
                }

                String sql = "INSERT INTO payroll (emp_id, emp_name, month_year, basic, hra, allowances, bonus, deductions, gross, net, present_days, working_days) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, pr.getEmpId());
                    ps.setString(2, pr.getEmpName());
                    ps.setString(3, pr.getMonthYear());
                    ps.setDouble(4, pr.getBasic());
                    ps.setDouble(5, pr.getHra());
                    ps.setDouble(6, pr.getAllowances());
                    ps.setDouble(7, pr.getBonus());
                    ps.setDouble(8, pr.getDeductions());
                    ps.setDouble(9, pr.getGross());
                    ps.setDouble(10, pr.getNet());
                    ps.setInt(11, pr.getPresentDays());
                    ps.setInt(12, pr.getWorkingDays());
                    ps.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                throw new PayrollException("Failed to save payroll: " + e.getMessage());
            } finally {
                dbLock.unlock();
            }
        }

        public static List<PayrollRecord> getAllPayroll() {
            List<PayrollRecord> list = new ArrayList<>();
            dbLock.lock();
            try {
                String sql = "SELECT * FROM payroll ORDER BY id DESC";
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        list.add(new PayrollRecord(
                                rs.getInt("emp_id"),
                                rs.getString("emp_name"),
                                rs.getString("month_year"),
                                rs.getDouble("basic"),
                                rs.getDouble("hra"),
                                rs.getDouble("allowances"),
                                rs.getDouble("bonus"),
                                rs.getDouble("deductions"),
                                rs.getDouble("gross"),
                                rs.getDouble("net"),
                                rs.getInt("present_days"),
                                rs.getInt("working_days")
                        ));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                dbLock.unlock();
            }
            return list;
        }

        public static List<PayrollRecord> searchPayroll(int empId) {
            List<PayrollRecord> list = new ArrayList<>();
            dbLock.lock();
            try {
                String sql = "SELECT * FROM payroll WHERE emp_id = ? ORDER BY id DESC";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, empId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        list.add(new PayrollRecord(
                                rs.getInt("emp_id"),
                                rs.getString("emp_name"),
                                rs.getString("month_year"),
                                rs.getDouble("basic"),
                                rs.getDouble("hra"),
                                rs.getDouble("allowances"),
                                rs.getDouble("bonus"),
                                rs.getDouble("deductions"),
                                rs.getDouble("gross"),
                                rs.getDouble("net"),
                                rs.getInt("present_days"),
                                rs.getInt("working_days")
                        ));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                dbLock.unlock();
            }
            return list;
        }

        public static int getTotalEmployees() {
            return employeeCache.size();
        }

        public static double getTotalMonthlyPayroll() {
            double total = 0;
            for (Employee e : employeeCache) {
                total += e.calculateNet();
            }
            return total;
        }

        public static Map<String, Integer> getDepartmentCount() {
            Map<String, Integer> map = new HashMap<>();
            for (Employee e : employeeCache) {
                map.put(e.getDepartment(), map.getOrDefault(e.getDepartment(), 0) + 1);
            }
            return map;
        }

        public static void close() {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== BACKGROUND THREAD FOR REFRESH ====================
    static class DataRefreshThread extends Thread {
        private final Runnable task;
        private volatile boolean running = true;

        public DataRefreshThread(Runnable task) {
            this.task = task;
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(30000); // every 30 seconds
                    if (running) {
                        SwingUtilities.invokeLater(task);
                    }
                } catch (InterruptedException e) {
                    running = false;
                }
            }
        }

        public void stopRefresh() {
            running = false;
            interrupt();
        }
    }

    // ==================== MAIN APPLICATION FRAME ====================
    static class MainFrame extends JFrame {
        private CardLayout cardLayout;
        private JPanel mainPanel;
        private JPanel sidebar;
        private JLabel lblTotalEmp, lblTotalPayroll, lblDepartments;
        private DefaultTableModel empTableModel, payrollTableModel;
        private JTable empTable, payrollTable;
        private DataRefreshThread refreshThread;

        // Form fields
        private JTextField txtEmpId, txtName, txtAge, txtEmail, txtPhone, txtBasic;
        private JComboBox<String> cmbGender, cmbDept, cmbDesig;
        private JTextField txtSearchEmp;
        private JTextField txtAttEmpId, txtWorkingDays, txtPresentDays, txtMonthYear;
        private JLabel lblAbsent;
        private JTextField txtSalEmpId, txtBonus, txtExtraDeduction;
        private JTextArea txtSalarySlip;
        private JTextField txtPaySearch;

        public MainFrame() {
            setTitle("Employee Payroll Management System");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1200, 750);
            setLocationRelativeTo(null);
            setMinimumSize(new Dimension(1000, 650));

            cardLayout = new CardLayout();
            mainPanel = new JPanel(cardLayout);

            // Login panel first
            mainPanel.add(createLoginPanel(), "LOGIN");
            mainPanel.add(createMainAppPanel(), "APP");

            add(mainPanel);
            cardLayout.show(mainPanel, "LOGIN");

            // Start background refresh
            refreshThread = new DataRefreshThread(this::refreshDashboard);
            refreshThread.start();
        }

        private JPanel createLoginPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(PRIMARY_BLUE);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(DARK_BLUE, 2, true),
                    new EmptyBorder(40, 50, 40, 50)
            ));
            card.setPreferredSize(new Dimension(400, 380));

            JLabel title = new JLabel("Payroll System");
            title.setFont(new Font("Segoe UI", Font.BOLD, 28));
            title.setForeground(DARK_BLUE);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel subtitle = new JLabel("Admin Login");
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            subtitle.setForeground(PRIMARY_BLUE);
            subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            JTextField txtUser = new JTextField(20);
            txtUser.setMaximumSize(new Dimension(300, 35));
            txtUser.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            styleTextField(txtUser);

            JPasswordField txtPass = new JPasswordField(20);
            txtPass.setMaximumSize(new Dimension(300, 35));
            txtPass.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            styleTextField(txtPass);

            JButton btnLogin = createStyledButton("LOGIN", PRIMARY_BLUE);
            btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnLogin.setMaximumSize(new Dimension(300, 40));

            JLabel lblError = new JLabel(" ");
            lblError.setForeground(ERROR_RED);
            lblError.setAlignmentX(Component.CENTER_ALIGNMENT);
            lblError.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            btnLogin.addActionListener(e -> {
                String user = txtUser.getText().trim();
                String pass = new String(txtPass.getPassword());
                if (user.isEmpty() || pass.isEmpty()) {
                    lblError.setText("Please enter username and password");
                    return;
                }
                if (DatabaseManager.authenticate(user, pass)) {
                    lblError.setText(" ");
                    refreshDashboard();
                    loadEmployeeTable();
                    loadPayrollTable();
                    cardLayout.show(mainPanel, "APP");
                } else {
                    lblError.setText("Invalid username or password");
                    txtPass.setText("");
                }
            });

            // Enter key support
            txtPass.addActionListener(e -> btnLogin.doClick());

            card.add(title);
            card.add(Box.createVerticalStrut(5));
            card.add(subtitle);
            card.add(Box.createVerticalStrut(25));
            card.add(createLabeledField("Username", txtUser));
            card.add(Box.createVerticalStrut(15));
            card.add(createLabeledField("Password", txtPass));
            card.add(Box.createVerticalStrut(20));
            card.add(btnLogin);
            card.add(Box.createVerticalStrut(10));
            card.add(lblError);

            panel.add(card);
            return panel;
        }

        private JPanel createLabeledField(String label, JComponent field) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(WHITE);
            p.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(l);
            p.add(Box.createVerticalStrut(4));
            p.add(field);
            return p;
        }

        private JPanel createMainAppPanel() {
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(LIGHT_BLUE);

            // Sidebar
            sidebar = new JPanel();
            sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
            sidebar.setBackground(DARK_BLUE);
            sidebar.setPreferredSize(new Dimension(220, 0));
            sidebar.setBorder(new EmptyBorder(20, 10, 20, 10));

            JLabel appTitle = new JLabel("  PAYROLL");
            appTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
            appTitle.setForeground(WHITE);
            appTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(appTitle);
            sidebar.add(Box.createVerticalStrut(30));

            String[] menuItems = {"Dashboard", "Employees", "Attendance", "Salary Calc", "Salary Slip", "Payroll History", "Logout"};
            for (String item : menuItems) {
                JButton btn = createSidebarButton(item);
                sidebar.add(btn);
                sidebar.add(Box.createVerticalStrut(8));
            }

            // Content area with cards
            JPanel content = new JPanel(new CardLayout());
            content.setBackground(LIGHT_BLUE);
            content.setBorder(new EmptyBorder(15, 15, 15, 15));

            content.add(createDashboardPanel(), "Dashboard");
            content.add(createEmployeePanel(), "Employees");
            content.add(createAttendancePanel(), "Attendance");
            content.add(createSalaryCalcPanel(), "Salary Calc");
            content.add(createSalarySlipPanel(), "Salary Slip");
            content.add(createPayrollHistoryPanel(), "Payroll History");

            // Sidebar button actions
            Component[] comps = sidebar.getComponents();
            for (Component c : comps) {
                if (c instanceof JButton) {
                    JButton b = (JButton) c;
                    b.addActionListener(e -> {
                        String cmd = b.getText().trim();
                        if (cmd.equals("Logout")) {
                            int conf = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
                            if (conf == JOptionPane.YES_OPTION) {
                                cardLayout.show(mainPanel, "LOGIN");
                            }
                        } else {
                            CardLayout cl = (CardLayout) content.getLayout();
                            cl.show(content, cmd);
                            if (cmd.equals("Dashboard")) refreshDashboard();
                            if (cmd.equals("Employees")) loadEmployeeTable();
                            if (cmd.equals("Payroll History")) loadPayrollTable();
                        }
                    });
                }
            }

            root.add(sidebar, BorderLayout.WEST);
            root.add(content, BorderLayout.CENTER);
            return root;
        }

        private JButton createSidebarButton(String text) {
            JButton btn = new JButton("  " + text);
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            btn.setForeground(WHITE);
            btn.setBackground(DARK_BLUE);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            btn.setMaximumSize(new Dimension(200, 40));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { btn.setBackground(PRIMARY_BLUE); }
                public void mouseExited(MouseEvent e) { btn.setBackground(DARK_BLUE); }
            });
            return btn;
        }

        private JButton createStyledButton(String text, Color bg) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            btn.setBackground(bg);
            btn.setForeground(WHITE);
            btn.setFocusPainted(false);
            btn.setBorder(new EmptyBorder(8, 18, 8, 18));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return btn;
        }

        private void styleTextField(JTextField tf) {
            tf.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(200, 200, 200), 1),
                    new EmptyBorder(5, 8, 5, 8)
            ));
        }

        // ==================== DASHBOARD ====================
        private JPanel createDashboardPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(LIGHT_BLUE);

            JLabel heading = new JLabel("Dashboard Overview");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 24));
            heading.setForeground(DARK_BLUE);
            panel.add(heading, BorderLayout.NORTH);

            JPanel cards = new JPanel(new GridLayout(1, 3, 20, 0));
            cards.setBackground(LIGHT_BLUE);
            cards.setBorder(new EmptyBorder(20, 0, 20, 0));

            lblTotalEmp = createDashboardCard("Total Employees", "0", PRIMARY_BLUE);
            lblTotalPayroll = createDashboardCard("Est. Monthly Payroll", "₹ 0.00", SUCCESS_GREEN);
            lblDepartments = createDashboardCard("Departments", "0", new Color(123, 31, 162));

            cards.add(lblTotalEmp);
            cards.add(lblTotalPayroll);
            cards.add(lblDepartments);

            JPanel info = new JPanel(new BorderLayout());
            info.setBackground(WHITE);
            info.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220), 1),
                    new EmptyBorder(15, 15, 15, 15)
            ));
            JTextArea infoArea = new JTextArea();
            infoArea.setEditable(false);
            infoArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            infoArea.setText("Welcome to the Employee Payroll Management System.\n\n" +
                    "• Use the sidebar to navigate between modules.\n" +
                    "• Add employees first, then mark attendance.\n" +
                    "• Calculate salary and generate slips.\n" +
                    "• Save monthly payroll records (duplicates are prevented).\n\n" +
                    "All data is stored in SQLite and persists across restarts.");
            info.add(infoArea, BorderLayout.CENTER);

            panel.add(cards, BorderLayout.CENTER);
            panel.add(info, BorderLayout.SOUTH);
            return panel;
        }

        private JLabel createDashboardCard(String title, String value, Color accent) {
            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(accent, 3, true),
                    new EmptyBorder(20, 20, 20, 20)
            ));
            JLabel t = new JLabel(title);
            t.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            t.setForeground(Color.GRAY);
            JLabel v = new JLabel(value);
            v.setFont(new Font("Segoe UI", Font.BOLD, 26));
            v.setForeground(accent);
            card.add(t, BorderLayout.NORTH);
            card.add(v, BorderLayout.CENTER);

            // We return a wrapper that holds the value label for easy update
            JLabel wrapper = new JLabel();
            wrapper.setLayout(new BorderLayout());
            wrapper.add(card);
            wrapper.putClientProperty("valueLabel", v);
            return wrapper;
        }

        private void refreshDashboard() {
            int total = DatabaseManager.getTotalEmployees();
            double payroll = DatabaseManager.getTotalMonthlyPayroll();
            Map<String, Integer> depts = DatabaseManager.getDepartmentCount();

            updateCardValue(lblTotalEmp, String.valueOf(total));
            updateCardValue(lblTotalPayroll, String.format("₹ %.2f", payroll));
            updateCardValue(lblDepartments, String.valueOf(depts.size()));
        }

        private void updateCardValue(JLabel wrapper, String value) {
            JLabel v = (JLabel) wrapper.getClientProperty("valueLabel");
            if (v != null) v.setText(value);
        }

        // ==================== EMPLOYEE MANAGEMENT ====================
        private JPanel createEmployeePanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(LIGHT_BLUE);

            JLabel heading = new JLabel("Employee Management");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
            heading.setForeground(DARK_BLUE);
            panel.add(heading, BorderLayout.NORTH);

            // Form
            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(WHITE);
            form.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(15, 15, 15, 15)
            ));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 8, 5, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            txtEmpId = new JTextField(10);
            txtName = new JTextField(15);
            cmbGender = new JComboBox<>(new String[]{"Male", "Female", "Other"});
            txtAge = new JTextField(5);
            cmbDept = new JComboBox<>(new String[]{"HR", "IT", "Finance", "Marketing", "Operations", "Sales"});
            cmbDesig = new JComboBox<>(new String[]{"Manager", "Senior Developer", "Developer", "Analyst", "Executive", "Intern"});
            txtEmail = new JTextField(18);
            txtPhone = new JTextField(12);
            txtBasic = new JTextField(10);

            styleTextField(txtEmpId); styleTextField(txtName); styleTextField(txtAge);
            styleTextField(txtEmail); styleTextField(txtPhone); styleTextField(txtBasic);

            int row = 0;
            addFormRow(form, gbc, row++, "Employee ID:", txtEmpId);
            addFormRow(form, gbc, row++, "Name:", txtName);
            addFormRow(form, gbc, row++, "Gender:", cmbGender);
            addFormRow(form, gbc, row++, "Age:", txtAge);
            addFormRow(form, gbc, row++, "Department:", cmbDept);
            addFormRow(form, gbc, row++, "Designation:", cmbDesig);
            addFormRow(form, gbc, row++, "Email:", txtEmail);
            addFormRow(form, gbc, row++, "Phone:", txtPhone);
            addFormRow(form, gbc, row++, "Basic Salary:", txtBasic);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            btnPanel.setBackground(WHITE);
            JButton btnAdd = createStyledButton("Add", PRIMARY_BLUE);
            JButton btnUpdate = createStyledButton("Update", new Color(255, 152, 0));
            JButton btnDelete = createStyledButton("Delete", ERROR_RED);
            JButton btnClear = createStyledButton("Clear", Color.GRAY);
            btnPanel.add(btnAdd); btnPanel.add(btnUpdate); btnPanel.add(btnDelete); btnPanel.add(btnClear);

            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            form.add(btnPanel, gbc);

            // Search + Table
            JPanel right = new JPanel(new BorderLayout(5, 5));
            right.setBackground(LIGHT_BLUE);

            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.setBackground(LIGHT_BLUE);
            txtSearchEmp = new JTextField(15);
            styleTextField(txtSearchEmp);
            JButton btnSearch = createStyledButton("Search", PRIMARY_BLUE);
            JButton btnShowAll = createStyledButton("Show All", Color.GRAY);
            searchPanel.add(new JLabel("Search (ID/Name):"));
            searchPanel.add(txtSearchEmp);
            searchPanel.add(btnSearch);
            searchPanel.add(btnShowAll);

            empTableModel = new DefaultTableModel(
                    new String[]{"ID", "Name", "Gender", "Age", "Dept", "Designation", "Email", "Phone", "Basic"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };
            empTable = new JTable(empTableModel);
            empTable.setRowHeight(28);
            empTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            empTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
            empTable.getTableHeader().setBackground(PRIMARY_BLUE);
            empTable.getTableHeader().setForeground(WHITE);
            empTable.setSelectionBackground(LIGHT_BLUE);
            JScrollPane scroll = new JScrollPane(empTable);

            right.add(searchPanel, BorderLayout.NORTH);
            right.add(scroll, BorderLayout.CENTER);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, form, right);
            split.setDividerLocation(380);
            split.setResizeWeight(0.35);
            panel.add(split, BorderLayout.CENTER);

            // Actions
            btnAdd.addActionListener(e -> addEmployeeAction());
            btnUpdate.addActionListener(e -> updateEmployeeAction());
            btnDelete.addActionListener(e -> deleteEmployeeAction());
            btnClear.addActionListener(e -> clearEmployeeForm());
            btnSearch.addActionListener(e -> searchEmployeeAction());
            btnShowAll.addActionListener(e -> loadEmployeeTable());

            empTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && empTable.getSelectedRow() >= 0) {
                    int rowIdx = empTable.getSelectedRow();
                    txtEmpId.setText(empTableModel.getValueAt(rowIdx, 0).toString());
                    txtName.setText(empTableModel.getValueAt(rowIdx, 1).toString());
                    cmbGender.setSelectedItem(empTableModel.getValueAt(rowIdx, 2).toString());
                    txtAge.setText(empTableModel.getValueAt(rowIdx, 3).toString());
                    cmbDept.setSelectedItem(empTableModel.getValueAt(rowIdx, 4).toString());
                    cmbDesig.setSelectedItem(empTableModel.getValueAt(rowIdx, 5).toString());
                    txtEmail.setText(empTableModel.getValueAt(rowIdx, 6).toString());
                    txtPhone.setText(empTableModel.getValueAt(rowIdx, 7).toString());
                    txtBasic.setText(empTableModel.getValueAt(rowIdx, 8).toString());
                }
            });

            return panel;
        }

        private void addFormRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            form.add(l, gbc);
            gbc.gridx = 1;
            form.add(field, gbc);
        }

        private void addEmployeeAction() {
            try {
                int id = Integer.parseInt(txtEmpId.getText().trim());
                String name = txtName.getText().trim();
                if (name.isEmpty()) throw new PayrollException("Name is required");
                int age = Integer.parseInt(txtAge.getText().trim());
                if (age < 18 || age > 70) throw new PayrollException("Age must be between 18 and 70");
                double basic = Double.parseDouble(txtBasic.getText().trim());
                if (basic <= 0) throw new PayrollException("Basic salary must be positive");

                if (employeeMap.containsKey(id)) {
                    throw new PayrollException("Employee ID already exists");
                }

                Employee emp = new Employee(id, name, cmbGender.getSelectedItem().toString(), age,
                        cmbDept.getSelectedItem().toString(), cmbDesig.getSelectedItem().toString(),
                        txtEmail.getText().trim(), txtPhone.getText().trim(), basic);

                DatabaseManager.addEmployee(emp);
                loadEmployeeTable();
                clearEmployeeForm();
                refreshDashboard();
                JOptionPane.showMessageDialog(this, "Employee added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numeric values for ID, Age and Salary", "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (PayrollException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void updateEmployeeAction() {
            try {
                int id = Integer.parseInt(txtEmpId.getText().trim());
                if (!employeeMap.containsKey(id)) {
                    throw new PayrollException("Employee not found. Cannot update.");
                }
                String name = txtName.getText().trim();
                if (name.isEmpty()) throw new PayrollException("Name is required");
                int age = Integer.parseInt(txtAge.getText().trim());
                double basic = Double.parseDouble(txtBasic.getText().trim());

                Employee emp = new Employee(id, name, cmbGender.getSelectedItem().toString(), age,
                        cmbDept.getSelectedItem().toString(), cmbDesig.getSelectedItem().toString(),
                        txtEmail.getText().trim(), txtPhone.getText().trim(), basic);

                DatabaseManager.updateEmployee(emp);
                loadEmployeeTable();
                refreshDashboard();
                JOptionPane.showMessageDialog(this, "Employee updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid numeric input", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (PayrollException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void deleteEmployeeAction() {
            try {
                int id = Integer.parseInt(txtEmpId.getText().trim());
                int conf = JOptionPane.showConfirmDialog(this, "Delete employee ID " + id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (conf == JOptionPane.YES_OPTION) {
                    if (DatabaseManager.deleteEmployee(id)) {
                        loadEmployeeTable();
                        clearEmployeeForm();
                        refreshDashboard();
                        JOptionPane.showMessageDialog(this, "Employee deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Employee not found", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter a valid Employee ID", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (PayrollException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void searchEmployeeAction() {
            String key = txtSearchEmp.getText().trim().toLowerCase();
            empTableModel.setRowCount(0);
            for (Employee e : employeeCache) {
                if (String.valueOf(e.getEmpId()).contains(key) || e.getName().toLowerCase().contains(key)) {
                    empTableModel.addRow(new Object[]{
                            e.getEmpId(), e.getName(), e.getGender(), e.getAge(),
                            e.getDepartment(), e.getDesignation(), e.getEmail(), e.getPhone(),
                            String.format("%.2f", e.getBasicSalary())
                    });
                }
            }
        }

        private void loadEmployeeTable() {
            empTableModel.setRowCount(0);
            for (Employee e : employeeCache) {
                empTableModel.addRow(new Object[]{
                        e.getEmpId(), e.getName(), e.getGender(), e.getAge(),
                        e.getDepartment(), e.getDesignation(), e.getEmail(), e.getPhone(),
                        String.format("%.2f", e.getBasicSalary())
                });
            }
        }

        private void clearEmployeeForm() {
            txtEmpId.setText("");
            txtName.setText("");
            txtAge.setText("");
            txtEmail.setText("");
            txtPhone.setText("");
            txtBasic.setText("");
            cmbGender.setSelectedIndex(0);
            cmbDept.setSelectedIndex(0);
            cmbDesig.setSelectedIndex(0);
        }

        // ==================== ATTENDANCE ====================
        private JPanel createAttendancePanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(LIGHT_BLUE);

            JLabel heading = new JLabel("Attendance Management");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
            heading.setForeground(DARK_BLUE);
            panel.add(heading, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(WHITE);
            form.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(25, 25, 25, 25)
            ));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(8, 10, 8, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            txtAttEmpId = new JTextField(10);
            txtWorkingDays = new JTextField(10);
            txtPresentDays = new JTextField(10);
            txtMonthYear = new JTextField(10);
            txtMonthYear.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy")));
            lblAbsent = new JLabel("0");
            lblAbsent.setFont(new Font("Segoe UI", Font.BOLD, 14));

            styleTextField(txtAttEmpId); styleTextField(txtWorkingDays);
            styleTextField(txtPresentDays); styleTextField(txtMonthYear);

            int r = 0;
            addFormRow(form, gbc, r++, "Employee ID:", txtAttEmpId);
            addFormRow(form, gbc, r++, "Working Days:", txtWorkingDays);
            addFormRow(form, gbc, r++, "Present Days:", txtPresentDays);
            addFormRow(form, gbc, r++, "Month-Year (MM-yyyy):", txtMonthYear);

            gbc.gridx = 0; gbc.gridy = r;
            form.add(new JLabel("Absent Days:"), gbc);
            gbc.gridx = 1;
            form.add(lblAbsent, gbc);
            r++;

            JButton btnCalcAbsent = createStyledButton("Calculate Absent", PRIMARY_BLUE);
            JButton btnSaveAtt = createStyledButton("Save Attendance", SUCCESS_GREEN);
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bp.setBackground(WHITE);
            bp.add(btnCalcAbsent);
            bp.add(btnSaveAtt);
            gbc.gridx = 0; gbc.gridy = r; gbc.gridwidth = 2;
            form.add(bp, gbc);

            // Auto calculate on change
            KeyAdapter calcListener = new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    try {
                        int w = Integer.parseInt(txtWorkingDays.getText().trim());
                        int p = Integer.parseInt(txtPresentDays.getText().trim());
                        lblAbsent.setText(String.valueOf(Math.max(0, w - p)));
                    } catch (Exception ignored) {
                        lblAbsent.setText("0");
                    }
                }
            };
            txtWorkingDays.addKeyListener(calcListener);
            txtPresentDays.addKeyListener(calcListener);

            btnCalcAbsent.addActionListener(e -> {
                try {
                    int w = Integer.parseInt(txtWorkingDays.getText().trim());
                    int p = Integer.parseInt(txtPresentDays.getText().trim());
                    if (p > w) throw new PayrollException("Present days cannot exceed working days");
                    lblAbsent.setText(String.valueOf(w - p));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter valid numbers", "Error", JOptionPane.ERROR_MESSAGE);
                } catch (PayrollException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            btnSaveAtt.addActionListener(e -> {
                try {
                    int empId = Integer.parseInt(txtAttEmpId.getText().trim());
                    if (!employeeMap.containsKey(empId)) {
                        throw new PayrollException("Employee ID not found");
                    }
                    int w = Integer.parseInt(txtWorkingDays.getText().trim());
                    int p = Integer.parseInt(txtPresentDays.getText().trim());
                    if (w <= 0 || p < 0 || p > w) {
                        throw new PayrollException("Invalid attendance values");
                    }
                    String my = txtMonthYear.getText().trim();
                    if (!my.matches("\\d{2}-\\d{4}")) {
                        throw new PayrollException("Month-Year format should be MM-yyyy");
                    }

                    Attendance att = new Attendance(empId, w, p, my);
                    DatabaseManager.saveAttendance(att);
                    JOptionPane.showMessageDialog(this, "Attendance saved successfully!\nAbsent Days: " + att.getAbsentDays(),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter valid numeric values", "Error", JOptionPane.ERROR_MESSAGE);
                } catch (PayrollException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER));
            center.setBackground(LIGHT_BLUE);
            center.add(form);
            panel.add(center, BorderLayout.CENTER);
            return panel;
        }

        // ==================== SALARY CALCULATION ====================
        private JPanel createSalaryCalcPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(LIGHT_BLUE);

            JLabel heading = new JLabel("Salary Calculation");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
            heading.setForeground(DARK_BLUE);
            panel.add(heading, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(WHITE);
            form.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(20, 20, 20, 20)
            ));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 8, 6, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            txtSalEmpId = new JTextField(10);
            txtBonus = new JTextField(10);
            txtBonus.setText("0");
            txtExtraDeduction = new JTextField(10);
            txtExtraDeduction.setText("0");
            styleTextField(txtSalEmpId); styleTextField(txtBonus); styleTextField(txtExtraDeduction);

            JTextArea resultArea = new JTextArea(12, 35);
            resultArea.setEditable(false);
            resultArea.setFont(new Font("Consolas", Font.PLAIN, 13));
            resultArea.setBorder(new EmptyBorder(10, 10, 10, 10));

            int r = 0;
            addFormRow(form, gbc, r++, "Employee ID:", txtSalEmpId);
            addFormRow(form, gbc, r++, "Bonus (₹):", txtBonus);
            addFormRow(form, gbc, r++, "Extra Deduction (₹):", txtExtraDeduction);

            JButton btnCalculate = createStyledButton("Calculate Salary", PRIMARY_BLUE);
            JButton btnSavePayroll = createStyledButton("Save to Payroll", SUCCESS_GREEN);
            JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bp.setBackground(WHITE);
            bp.add(btnCalculate);
            bp.add(btnSavePayroll);
            gbc.gridx = 0; gbc.gridy = r; gbc.gridwidth = 2;
            form.add(bp, gbc);

            // Result display
            JPanel resultPanel = new JPanel(new BorderLayout());
            resultPanel.setBackground(WHITE);
            resultPanel.setBorder(BorderFactory.createTitledBorder("Salary Breakdown"));
            resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, form, resultPanel);
            split.setDividerLocation(350);
            panel.add(split, BorderLayout.CENTER);

            final PayrollRecord[] lastCalculated = {null};

            btnCalculate.addActionListener(e -> {
                try {
                    int empId = Integer.parseInt(txtSalEmpId.getText().trim());
                    Employee emp = employeeMap.get(empId);
                    if (emp == null) throw new PayrollException("Employee not found");

                    double bonus = Double.parseDouble(txtBonus.getText().trim());
                    double extraDed = Double.parseDouble(txtExtraDeduction.getText().trim());

                    emp.setBonus(bonus);
                    emp.setDeductions(emp.getBasicSalary() * 0.05 + extraDed);

                    // Attendance-based adjustment
                    String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy"));
                    Attendance att = DatabaseManager.getAttendance(empId, currentMonth);
                    double attendanceFactor = 1.0;
                    int present = 0, working = 0;
                    if (att != null) {
                        present = att.getPresentDays();
                        working = att.getWorkingDays();
                        if (working > 0) {
                            attendanceFactor = (double) present / working;
                        }
                    }

                    double gross = emp.calculateGross();
                    double net = emp.calculateNet() * attendanceFactor;

                    DecimalFormat df = new DecimalFormat("#,##0.00");
                    StringBuilder sb = new StringBuilder();
                    sb.append("========== SALARY BREAKDOWN ==========\n\n");
                    sb.append("Employee ID   : ").append(emp.getEmpId()).append("\n");
                    sb.append("Name          : ").append(emp.getName()).append("\n");
                    sb.append("Department    : ").append(emp.getDepartment()).append("\n");
                    sb.append("Designation   : ").append(emp.getDesignation()).append("\n\n");
                    sb.append("Basic Salary  : ₹ ").append(df.format(emp.getBasicSalary())).append("\n");
                    sb.append("HRA (20%)     : ₹ ").append(df.format(emp.getHra())).append("\n");
                    sb.append("Allowances    : ₹ ").append(df.format(emp.getAllowances())).append("\n");
                    sb.append("Bonus         : ₹ ").append(df.format(emp.getBonus())).append("\n");
                    sb.append("--------------------------------------\n");
                    sb.append("Gross Salary  : ₹ ").append(df.format(gross)).append("\n");
                    sb.append("Deductions    : ₹ ").append(df.format(emp.getDeductions())).append("\n");
                    if (att != null) {
                        sb.append("Attendance    : ").append(present).append("/").append(working)
                                .append(" (").append(String.format("%.1f", attendanceFactor * 100)).append("%)\n");
                    }
                    sb.append("--------------------------------------\n");
                    sb.append("NET SALARY    : ₹ ").append(df.format(net)).append("\n");
                    sb.append("======================================\n");

                    resultArea.setText(sb.toString());

                    lastCalculated[0] = new PayrollRecord(empId, emp.getName(), currentMonth,
                            emp.getBasicSalary(), emp.getHra(), emp.getAllowances(), emp.getBonus(),
                            emp.getDeductions(), gross, net, present, working);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter valid numbers", "Error", JOptionPane.ERROR_MESSAGE);
                } catch (PayrollException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            btnSavePayroll.addActionListener(e -> {
                if (lastCalculated[0] == null) {
                    JOptionPane.showMessageDialog(this, "Calculate salary first", "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                try {
                    DatabaseManager.savePayroll(lastCalculated[0]);
                    loadPayrollTable();
                    JOptionPane.showMessageDialog(this, "Payroll record saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (PayrollException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            return panel;
        }

        // ==================== SALARY SLIP ====================
        private JPanel createSalarySlipPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(LIGHT_BLUE);

            JLabel heading = new JLabel("Salary Slip Generation");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
            heading.setForeground(DARK_BLUE);
            panel.add(heading, BorderLayout.NORTH);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.setBackground(LIGHT_BLUE);
            JTextField txtSlipEmpId = new JTextField(10);
            styleTextField(txtSlipEmpId);
            JButton btnGenerate = createStyledButton("Generate Slip", PRIMARY_BLUE);
            JButton btnSaveFile = createStyledButton("Save as Text File", SUCCESS_GREEN);
            top.add(new JLabel("Employee ID:"));
            top.add(txtSlipEmpId);
            top.add(btnGenerate);
            top.add(btnSaveFile);

            txtSalarySlip = new JTextArea();
            txtSalarySlip.setEditable(false);
            txtSalarySlip.setFont(new Font("Consolas", Font.PLAIN, 13));
            txtSalarySlip.setBorder(new EmptyBorder(15, 15, 15, 15));
            JScrollPane scroll = new JScrollPane(txtSalarySlip);

            panel.add(top, BorderLayout.NORTH);
            panel.add(scroll, BorderLayout.CENTER);

            btnGenerate.addActionListener(e -> {
                try {
                    int empId = Integer.parseInt(txtSlipEmpId.getText().trim());
                    Employee emp = employeeMap.get(empId);
                    if (emp == null) throw new PayrollException("Employee not found");

                    String month = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
                    String currentMY = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy"));
                    Attendance att = DatabaseManager.getAttendance(empId, currentMY);

                    double factor = 1.0;
                    if (att != null && att.getWorkingDays() > 0) {
                        factor = (double) att.getPresentDays() / att.getWorkingDays();
                    }

                    DecimalFormat df = new DecimalFormat("#,##0.00");
                    StringBuilder sb = new StringBuilder();
                    sb.append("╔══════════════════════════════════════════════════════╗\n");
                    sb.append("║           EMPLOYEE SALARY SLIP                       ║\n");
                    sb.append("║           ").append(String.format("%-40s", month)).append("║\n");
                    sb.append("╠══════════════════════════════════════════════════════╣\n");
                    sb.append("║ Employee ID    : ").append(String.format("%-35s", emp.getEmpId())).append("║\n");
                    sb.append("║ Name           : ").append(String.format("%-35s", emp.getName())).append("║\n");
                    sb.append("║ Department     : ").append(String.format("%-35s", emp.getDepartment())).append("║\n");
                    sb.append("║ Designation    : ").append(String.format("%-35s", emp.getDesignation())).append("║\n");
                    sb.append("║ Email          : ").append(String.format("%-35s", emp.getEmail())).append("║\n");
                    sb.append("╠══════════════════════════════════════════════════════╣\n");
                    sb.append("║ EARNINGS                                             ║\n");
                    sb.append("║   Basic Salary : ₹ ").append(String.format("%-32s", df.format(emp.getBasicSalary()))).append("║\n");
                    sb.append("║   HRA          : ₹ ").append(String.format("%-32s", df.format(emp.getHra()))).append("║\n");
                    sb.append("║   Allowances   : ₹ ").append(String.format("%-32s", df.format(emp.getAllowances()))).append("║\n");
                    sb.append("║   Bonus        : ₹ ").append(String.format("%-32s", df.format(emp.getBonus()))).append("║\n");
                    sb.append("║   Gross        : ₹ ").append(String.format("%-32s", df.format(emp.calculateGross()))).append("║\n");
                    sb.append("╠══════════════════════════════════════════════════════╣\n");
                    sb.append("║ DEDUCTIONS                                           ║\n");
                    sb.append("║   Deductions   : ₹ ").append(String.format("%-32s", df.format(emp.getDeductions()))).append("║\n");
                    if (att != null) {
                        sb.append("║   Attendance   : ").append(String.format("%-35s",
                                att.getPresentDays() + "/" + att.getWorkingDays() + " days")).append("║\n");
                    }
                    sb.append("╠══════════════════════════════════════════════════════╣\n");
                    sb.append("║ NET SALARY     : ₹ ").append(String.format("%-32s",
                            df.format(emp.calculateNet() * factor))).append("║\n");
                    sb.append("╚══════════════════════════════════════════════════════╝\n");
                    sb.append("\nGenerated on: ").append(LocalDate.now()).append("\n");
                    sb.append("This is a system generated salary slip.\n");

                    txtSalarySlip.setText(sb.toString());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter valid Employee ID", "Error", JOptionPane.ERROR_MESSAGE);
                } catch (PayrollException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            btnSaveFile.addActionListener(e -> {
                String content = txtSalarySlip.getText();
                if (content == null || content.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Generate a slip first", "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File("SalarySlip.txt"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                        fw.write(content);
                        JOptionPane.showMessageDialog(this, "Salary slip saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            return panel;
        }

        // ==================== PAYROLL HISTORY ====================
        private JPanel createPayrollHistoryPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBackground(LIGHT_BLUE);

            JLabel heading = new JLabel("Payroll History");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
            heading.setForeground(DARK_BLUE);
            panel.add(heading, BorderLayout.NORTH);

            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.setBackground(LIGHT_BLUE);
            txtPaySearch = new JTextField(12);
            styleTextField(txtPaySearch);
            JButton btnSearchPay = createStyledButton("Search by Emp ID", PRIMARY_BLUE);
            JButton btnShowAllPay = createStyledButton("Show All", Color.GRAY);
            searchPanel.add(new JLabel("Employee ID:"));
            searchPanel.add(txtPaySearch);
            searchPanel.add(btnSearchPay);
            searchPanel.add(btnShowAllPay);

            payrollTableModel = new DefaultTableModel(
                    new String[]{"Emp ID", "Name", "Month", "Basic", "HRA", "Allowances", "Bonus", "Deductions", "Gross", "Net", "Present", "Working"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };
            payrollTable = new JTable(payrollTableModel);
            payrollTable.setRowHeight(26);
            payrollTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            payrollTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
            payrollTable.getTableHeader().setBackground(PRIMARY_BLUE);
            payrollTable.getTableHeader().setForeground(WHITE);

            panel.add(searchPanel, BorderLayout.NORTH);
            panel.add(new JScrollPane(payrollTable), BorderLayout.CENTER);

            btnSearchPay.addActionListener(e -> {
                try {
                    int id = Integer.parseInt(txtPaySearch.getText().trim());
                    List<PayrollRecord> list = DatabaseManager.searchPayroll(id);
                    payrollTableModel.setRowCount(0);
                    for (PayrollRecord pr : list) {
                        addPayrollRow(pr);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Enter valid Employee ID", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            btnShowAllPay.addActionListener(e -> loadPayrollTable());

            return panel;
        }

        private void loadPayrollTable() {
            payrollTableModel.setRowCount(0);
            for (PayrollRecord pr : DatabaseManager.getAllPayroll()) {
                addPayrollRow(pr);
            }
        }

        private void addPayrollRow(PayrollRecord pr) {
            DecimalFormat df = new DecimalFormat("0.00");
            payrollTableModel.addRow(new Object[]{
                    pr.getEmpId(), pr.getEmpName(), pr.getMonthYear(),
                    df.format(pr.getBasic()), df.format(pr.getHra()), df.format(pr.getAllowances()),
                    df.format(pr.getBonus()), df.format(pr.getDeductions()),
                    df.format(pr.getGross()), df.format(pr.getNet()),
                    pr.getPresentDays(), pr.getWorkingDays()
            });
        }
    }

    // ==================== MAIN METHOD ====================
    public static void main(String[] args) {
        try {
            // Load SQLite driver
            Class.forName("org.sqlite.JDBC");
            DatabaseManager.initialize();

            // Optional: set system look and feel for better native feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            SwingUtilities.invokeLater(() -> {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            });

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(DatabaseManager::close));

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Failed to start application:\n" + e.getMessage() +
                            "\n\nMake sure sqlite-jdbc jar is in the classpath.",
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
