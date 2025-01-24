import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class OnlineQuizApplication {

    public static void main(String[] args) {
        try {
            // Load SQLite driver
            Class.forName("org.sqlite.JDBC");
            
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:quiz.db")) {
                QuizDatabase.initializeDatabase(connection);

                Scanner scanner = new Scanner(System.in);
                UserAuthentication auth = new UserAuthentication(connection);
                QuizManager quizManager = new QuizManager(connection);

                while (true) {
                    System.out.println("\nWelcome to Online Quiz Application");
                    System.out.println("1. Log in");
                    System.out.println("2. Sign up");
                    System.out.println("3. Exit");
                    System.out.print("Choose an option: ");

                    int choice = scanner.nextInt();
                    scanner.nextLine(); // Consume newline

                    if (choice == 3) {
                        System.out.println("Exiting... Goodbye!");
                        break;
                    }

                    String username, password;
                    switch (choice) {
                        case 1: // Login
                            System.out.print("Enter username: ");
                            username = scanner.nextLine();
                            System.out.print("Enter password: ");
                            password = scanner.nextLine();

                            if (auth.login(username, password)) {
                                System.out.println("Login successful!\n");
                                handleUserActions(scanner, quizManager);
                            } else {
                                System.out.println("Invalid credentials. Try again.");
                            }
                            break;

                        case 2: // Sign up
                            System.out.print("Choose a username: ");
                            username = scanner.nextLine();
                            System.out.print("Choose a password: ");
                            password = scanner.nextLine();

                            if (auth.signUp(username, password)) {
                                System.out.println("Account created successfully! You can now log in.");
                            } else {
                                System.out.println("Sign up failed. Username might already exist.");
                            }
                            break;

                        default:
                            System.out.println("Invalid choice. Try again.");
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC Driver not found. Please ensure it is included in the classpath.");
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    private static void handleUserActions(Scanner scanner, QuizManager quizManager) {
        while (true) {
            System.out.println("\nUser Menu:");
            System.out.println("1. Take Quiz");
            System.out.println("2. View Scores");
            System.out.println("3. Add Questions (Admin)");
            System.out.println("4. Log out");
            System.out.print("Choose an option: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            switch (choice) {
                case 1:
                    quizManager.takeQuiz(scanner);
                    break;

                case 2:
                    quizManager.viewScores();
                    break;

                case 3:
                    quizManager.addQuestions(scanner);
                    break;

                case 4:
                    System.out.println("Logging out...");
                    return;

                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }
}

class QuizDatabase {
    public static void initializeDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Create users table
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE, " +
                    "password TEXT)");

            // Create quizzes table
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS quizzes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT)");

            // Create questions table
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS questions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "quiz_id INTEGER, " +
                    "question TEXT, " +
                    "options TEXT, " +
                    "answer TEXT, " +
                    "FOREIGN KEY (quiz_id) REFERENCES quizzes(id))");

            // Create scores table
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS scores (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "quiz_id INTEGER, " +
                    "score INTEGER, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id), " +
                    "FOREIGN KEY (quiz_id) REFERENCES quizzes(id))");
        }
    }
}

class UserAuthentication {
    private final Connection connection;

    public UserAuthentication(Connection connection) {
        this.connection = connection;
    }

    public boolean login(String username, String password) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?")) {
            stmt.setString(1, username);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error during login: " + e.getMessage());
            return false;
        }
    }

    public boolean signUp(String username, String password) {
        try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)");) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error during sign up: " + e.getMessage());
            return false;
        }
    }
}

class QuizManager {
    private final Connection connection;

    public QuizManager(Connection connection) {
        this.connection = connection;
    }

    public void takeQuiz(Scanner scanner) {
        try (Statement stmt = connection.createStatement()) {
            ResultSet quizzes = stmt.executeQuery("SELECT * FROM quizzes");
            List<Integer> quizIds = new ArrayList<>();

            System.out.println("\nAvailable Quizzes:");
            while (quizzes.next()) {
                int id = quizzes.getInt("id");
                quizIds.add(id);
                System.out.println(id + ". " + quizzes.getString("title"));
            }

            if (quizIds.isEmpty()) {
                System.out.println("No quizzes available.");
                return;
            }

            System.out.print("Enter quiz ID to start: ");
            int quizId = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (!quizIds.contains(quizId)) {
                System.out.println("Invalid quiz ID.");
                return;
            }

            ResultSet questions = stmt.executeQuery("SELECT * FROM questions WHERE quiz_id = " + quizId);
            int score = 0, totalQuestions = 0;

            while (questions.next()) {
                totalQuestions++;
                System.out.println("\n" + questions.getString("question"));
                String[] options = questions.getString("options").split(",");
                for (int i = 0; i < options.length; i++) {
                    System.out.println((i + 1) + ". " + options[i]);
                }

                System.out.print("Enter your answer: ");
                int answer = scanner.nextInt();

                if (options[answer - 1].equals(questions.getString("answer"))) {
                    System.out.println("Correct!");
                    score++;
                } else {
                    System.out.println("Incorrect. Correct answer: " + questions.getString("answer"));
                }
            }

            System.out.println("\nQuiz finished! Your score: " + score + "/" + totalQuestions);
        } catch (SQLException e) {
            System.err.println("Error during quiz: " + e.getMessage());
        }
    }

    public void viewScores() {
        // Implementation for viewing scores
    }

    public void addQuestions(Scanner scanner) {
        try (PreparedStatement quizStmt = connection.prepareStatement("SELECT id, title FROM quizzes");
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO questions (quiz_id, question, options, answer) VALUES (?, ?, ?, ?)")) {

            ResultSet quizzes = quizStmt.executeQuery();
            List<Integer> quizIds = new ArrayList<>();

            System.out.println("\nAvailable Quizzes:");
            while (quizzes.next()) {
                int id = quizzes.getInt("id");
                quizIds.add(id);
                System.out.println(id + ". " + quizzes.getString("title"));
            }

            if (quizIds.isEmpty()) {
                System.out.println("No quizzes available.");
                return;
            }

            System.out.print("Enter quiz ID to add questions to: ");
            int quizId = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (!quizIds.contains(quizId)) {
                System.out.println("Invalid quiz ID.");
                return;
            }

            System.out.print("Enter question: ");
            String question = scanner.nextLine();

            System.out.print("Enter options (comma-separated): ");
            String options = scanner.nextLine();

            System.out.print("Enter correct answer: ");
            String answer = scanner.nextLine();

            stmt.setInt(1, quizId);
            stmt.setString(2, question);
            stmt.setString(3, options);
            stmt.setString(4, answer);
            stmt.executeUpdate();

            System.out.println("Question added successfully.");
        } catch (SQLException e) {
            System.err.println("Error during adding question: " + e.getMessage());
        }
    }
}

