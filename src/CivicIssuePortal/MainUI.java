package CivicIssuePortal;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainUI extends Application {

    private Stage primaryStage;

    private Connection dbConnection;
    private UserActions userActions;
    private AdminActions adminActions;
    private boolean dbAvailable = false;

    // Single small pool for DB calls; daemon threads so they don't block JVM shutdown.
    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final String PRIMARY_COLOR = "#1565C0";
    private final String BACKGROUND_COLOR = "#F5F5F5";
    private final String CARD_STYLE = "-fx-background-color: white; -fx-background-radius: 15; -fx-padding: 25;";
    private final String BUTTON_STYLE = "-fx-background-color: " + PRIMARY_COLOR + "; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 25; -fx-background-radius: 5; -fx-cursor: hand;";
    private final String SIDEBAR_BUTTON_STYLE = "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px; -fx-alignment: CENTER_LEFT; -fx-padding: 12 20; -fx-cursor: hand; -fx-border-width: 0 0 1 0; -fx-border-color: #ffffff22;";

    // Simple text glyphs used as lightweight icons (no external icon-font dependency required).
    private static final String[] USER_MENU_ICONS = {"\uD83C\uDFE0", "\uD83D\uDCDD", "\uD83D\uDCCB", "\u23F3", "\u2705", "\uD83D\uDCAC", "\u270F", "\uD83D\uDCDC", "\uD83D\uDEAA"};
    private static final String[] ADMIN_MENU_ICONS = {"\uD83D\uDCCA", "\uD83D\uDCCB", "\u23F3", "\u2705", "\uD83D\uDD0D", "\uD83D\uDCC8", "\uD83D\uDEE0", "\uD83D\uDCDC", "\uD83D\uDEAA"};

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("Pune Civic Issue Portal");

        try {
            dbConnection = DBConnection.getConnection();
            userActions = new UserActions(dbConnection);
            adminActions = new AdminActions(dbConnection);
            dbAvailable = true;
        } catch (SQLException e) {
            dbAvailable = false;
        }

        showHome();

        this.primaryStage.setMinWidth(1200);
        this.primaryStage.setMinHeight(750);
        this.primaryStage.centerOnScreen();
        this.primaryStage.show();

        if (!dbAvailable) {
            showAlert(Alert.AlertType.ERROR, "Database Unavailable",
                    "Could not connect to the database. Please check that MySQL is running and the " +
                            "credentials in DBConnection.java are correct, then restart the app.\n\n" +
                            "You can still browse the site, but login, registration, and all data operations " +
                            "will be unavailable until the connection is restored.");
        }

        this.primaryStage.setOnCloseRequest(e -> {
            dbExecutor.shutdownNow();
            if (dbConnection != null) {
                try { dbConnection.close(); } catch (SQLException ignored) { }
            }
        });
    }

    // ==================================================================
    //  BACKGROUND DB CALL HELPERS (run off the FX thread, update on it)
    // ==================================================================

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    /** Runs a DB call on a background thread; disables `trigger` while running (may be null); shows alerts on failure. */
    private <T> void runQuery(Node trigger, SqlSupplier<T> supplier, Consumer<T> onSuccess) {
        if (!ensureDbAvailable()) return;
        if (trigger != null) trigger.setDisable(true);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return supplier.get();
            }
        };
        task.setOnSucceeded(e -> {
            if (trigger != null) trigger.setDisable(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            if (trigger != null) trigger.setDisable(false);
            handleTaskFailure(task.getException());
        });
        dbExecutor.submit(task);
    }

    private <T> void runQuery(SqlSupplier<T> supplier, Consumer<T> onSuccess) {
        runQuery(null, supplier, onSuccess);
    }

    private void handleTaskFailure(Throwable ex) {
        if (ex instanceof SQLException se) {
            showAlert(Alert.AlertType.ERROR, "Database Error", SqlErrors.describe(se));
        } else if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            showAlert(Alert.AlertType.WARNING, "Invalid Input", ex.getMessage());
        } else {
            showAlert(Alert.AlertType.ERROR, "Unexpected Error", String.valueOf(ex == null ? "Unknown error." : ex.getMessage()));
        }
    }

    private boolean ensureDbAvailable() {
        if (!dbAvailable) {
            showAlert(Alert.AlertType.ERROR, "Database Unavailable",
                    "No database connection is available. Please restart the application once the database is reachable.");
            return false;
        }
        return true;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinWidth(420);
        alert.showAndWait();
    }

    /** Disables `button` until every field in `fields` is non-blank. */
    private void bindRequiredFields(Button button, TextInputControl... fields) {
        Runnable check = () -> {
            boolean anyBlank = false;
            for (TextInputControl f : fields) {
                if (f.getText() == null || f.getText().trim().isEmpty()) { anyBlank = true; break; }
            }
            button.setDisable(anyBlank);
        };
        for (TextInputControl f : fields) {
            f.textProperty().addListener((obs, oldV, newV) -> check.run());
        }
        check.run();
    }

    /** Renders any QueryResult as a generic, read-only TableView, with color-coded Status cells and a custom empty-state message. */
    private TableView<ObservableList<String>> buildTable(QueryResult qr, String emptyMessage) {
        TableView<ObservableList<String>> table = new TableView<>();
        List<String> columns = qr.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            final int colIndex = i;
            String label = columns.get(i);
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(label);
            col.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(colIndex)));

            if (label.equalsIgnoreCase("Status")) {
                col.setCellFactory(tc -> new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else {
                            setText(item);
                            String color = switch (item.trim().toLowerCase()) {
                                case "resolved" -> "#2e7d32";
                                case "pending" -> "#f9a825";
                                case "rejected" -> "#c62828";
                                default -> "#1565C0"; // e.g. "In Progress"
                            };
                            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                        }
                    }
                });
            }
            table.getColumns().add(col);
        }
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        for (List<String> row : qr.getRows()) {
            rows.add(FXCollections.observableArrayList(row));
        }
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(emptyMessage));
        return table;
    }

    private TableView<ObservableList<String>> buildTable(QueryResult qr) {
        return buildTable(qr, "No records found.");
    }

    // ------------------------------------------------------------
    // NAVIGATION METHODS
    // ------------------------------------------------------------

    private void showHome() {
        VBox root = new VBox();
        root.setStyle("-fx-background-color: " + BACKGROUND_COLOR + ";");

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.getChildren().addAll(createNavBar(), createHeroSection(), createFeatureSection(), createFooter());

        primaryStage.setScene(new Scene(scrollPane, 1200, 750));
    }

    private void showLogin(boolean isAdmin) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: " + BACKGROUND_COLOR + ";");

        VBox loginCard = new VBox(20);
        loginCard.setMaxWidth(400);
        loginCard.setStyle(CARD_STYLE);
        loginCard.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.1)));
        loginCard.setAlignment(Pos.CENTER);

        Text title = new Text(isAdmin ? "Admin Login" : "Citizen Login");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setFill(Color.web(PRIMARY_COLOR));

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        emailField.setPrefHeight(40);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(40);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button loginBtn = new Button("Login");
        loginBtn.setStyle(BUTTON_STYLE);
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        bindRequiredFields(loginBtn, emailField, passwordField);

        loginBtn.setOnAction(e -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            if (!ensureDbAvailable()) return;

            String email = emailField.getText().trim();
            String password = passwordField.getText();
            loginBtn.setDisable(true);

            Task<LoginResult> task = new Task<>() {
                @Override
                protected LoginResult call() throws Exception {
                    return isAdmin ? adminActions.loginAdminGui(email, password) : userActions.loginUserGui(email, password);
                }
            };
            task.setOnSucceeded(ev -> {
                loginBtn.setDisable(false);
                LoginResult result = task.getValue();
                if (result.success) {
                    if (isAdmin) {
                        showAdminDashboard();
                    } else {
                        String who = result.displayName != null ? result.displayName : "";
                        showAlert(Alert.AlertType.INFORMATION, "Welcome",
                                who.isEmpty() ? "Login successful." : "Welcome, " + who + "!");
                        showUserDashboard();
                    }
                } else {
                    errorLabel.setText(result.message);
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                }
            });
            task.setOnFailed(ev -> {
                loginBtn.setDisable(false);
                Throwable ex = task.getException();
                errorLabel.setText(ex instanceof SQLException se ? SqlErrors.describe(se) : String.valueOf(ex.getMessage()));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            });
            dbExecutor.submit(task);
        });

        Button backBtn = new Button("Back to Home");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666666; -fx-cursor: hand;");
        backBtn.setOnAction(e -> showHome());

        loginCard.getChildren().addAll(title, new Label("Enter your credentials"), emailField, passwordField, errorLabel, loginBtn, backBtn);
        root.getChildren().add(loginCard);

        primaryStage.setScene(new Scene(root, 1200, 750));
    }

    private void showRegister() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: " + BACKGROUND_COLOR + ";");

        VBox regCard = new VBox(20);
        regCard.setMaxWidth(600);
        regCard.setStyle(CARD_STYLE);
        regCard.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.1)));

        Text title = new Text("Citizen Registration");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        title.setFill(Color.web(PRIMARY_COLOR));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        TextField nameField = new TextField();
        TextField phoneField = new TextField();
        TextField emailField = new TextField();
        TextField houseField = new TextField();
        TextField streetField = new TextField();
        TextField areaField = new TextField();
        TextField wardField = new TextField();
        TextField pincodeField = new TextField();
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();

        TextField[] fields = {nameField, phoneField, emailField, houseField, streetField, areaField, wardField, pincodeField, usernameField, passwordField};
        String[] labels = {"Full Name", "Phone", "Email", "House Number", "Street", "Area", "Ward Number", "Pincode", "Username", "Password"};
        for (int i = 0; i < labels.length; i++) {
            fields[i].setPromptText(labels[i]);
            fields[i].setPrefHeight(35);
            grid.add(new Label(labels[i]), i % 2 == 0 ? 0 : 2, i / 2);
            grid.add(fields[i], i % 2 == 0 ? 1 : 3, i / 2);
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button regBtn = new Button("Register");
        regBtn.setStyle(BUTTON_STYLE);
        bindRequiredFields(regBtn, nameField, phoneField, emailField, houseField, streetField, areaField, wardField, pincodeField, usernameField, passwordField);

        regBtn.setOnAction(e -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            if (!ensureDbAvailable()) return;
            regBtn.setDisable(true);

            String name = nameField.getText().trim(), phone = phoneField.getText().trim(), email = emailField.getText().trim(),
                    house = houseField.getText().trim(), street = streetField.getText().trim(), area = areaField.getText().trim(),
                    ward = wardField.getText().trim(), pincode = pincodeField.getText().trim(),
                    username = usernameField.getText().trim(), password = passwordField.getText();

            Task<String> task = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return userActions.registerCitizenGui(name, phone, email, house, street, area, ward, pincode, username, password);
                }
            };
            task.setOnSucceeded(ev -> {
                showAlert(Alert.AlertType.INFORMATION, "Registration Successful", task.getValue());
                showLogin(false);
            });
            task.setOnFailed(ev -> {
                regBtn.setDisable(false);
                Throwable ex = task.getException();
                errorLabel.setText(ex instanceof SQLException se ? SqlErrors.describe(se) : String.valueOf(ex.getMessage()));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            });
            dbExecutor.submit(task);
        });

        Button backBtn = new Button("Back");
        backBtn.setStyle("-fx-background-color: #999; -fx-text-fill: white; -fx-padding: 10 25; -fx-background-radius: 5;");
        backBtn.setOnAction(e -> showHome());

        HBox btns = new HBox(10, regBtn, backBtn);
        btns.setAlignment(Pos.CENTER);

        regCard.getChildren().addAll(title, new Separator(), grid, errorLabel, btns);

        ScrollPane sp = new ScrollPane(regCard);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: " + BACKGROUND_COLOR + ";");
        sp.setMaxWidth(700);

        root.getChildren().add(sp);
        primaryStage.setScene(new Scene(root, 1200, 750));
    }

    // ------------------------------------------------------------
    // COMPONENT BUILDERS (unchanged look & feel)
    // ------------------------------------------------------------

    private HBox createNavBar() {
        HBox nav = new HBox(20);
        nav.setPadding(new Insets(15, 50, 15, 50));
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setStyle("-fx-background-color: white; -fx-border-width: 0 0 1 0; -fx-border-color: #ddd;");

        Text logo = new Text("PUNE CIVIC PORTAL");
        logo.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        logo.setFill(Color.web(PRIMARY_COLOR));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox links = new HBox(25);
        links.setAlignment(Pos.CENTER);
        String[] navItems = {"Home", "Citizen Login", "Register", "Admin Login", "About", "Contact"};
        for (String item : navItems) {
            Label l = new Label(item);
            l.setStyle("-fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: #444;");
            l.setOnMouseClicked(e -> {
                if (item.equals("Citizen Login")) showLogin(false);
                else if (item.equals("Admin Login")) showLogin(true);
                else if (item.equals("Register")) showRegister();
                else showHome();
            });
            links.getChildren().add(l);
        }

        nav.getChildren().addAll(logo, spacer, links);
        return nav;
    }

    private HBox createHeroSection() {
        HBox hero = new HBox(50);
        hero.setPadding(new Insets(80, 50, 80, 50));
        hero.setAlignment(Pos.CENTER);
        hero.setStyle("-fx-background-color: white;");

        VBox textContent = new VBox(20);
        textContent.setPrefWidth(600);

        Text title = new Text("Welcome to Pune Civic Issue Portal");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        title.setFill(Color.web("#222"));

        Text subtitle = new Text("Report civic issues quickly. Track progress transparently.\nHelp build a better city.");
        subtitle.setFont(Font.font("Arial", 18));
        subtitle.setFill(Color.web("#666"));

        HBox btns = new HBox(15);
        Button btnReg = new Button("Register Now");
        btnReg.setStyle(BUTTON_STYLE);
        btnReg.setOnAction(e -> showRegister());

        Button btnLogin = new Button("Citizen Login");
        btnLogin.setStyle("-fx-background-color: transparent; -fx-border-color: " + PRIMARY_COLOR + "; -fx-border-radius: 5; -fx-text-fill: " + PRIMARY_COLOR + "; -fx-padding: 10 25; -fx-font-size: 14px; -fx-cursor: hand;");
        btnLogin.setOnAction(e -> showLogin(false));

        btns.getChildren().addAll(btnReg, btnLogin);
        textContent.getChildren().addAll(title, subtitle, btns);

        StackPane imgPlaceholder = new StackPane();
        imgPlaceholder.setPrefSize(400, 300);
        Rectangle rect = new Rectangle(400, 300, Color.web("#E3F2FD"));
        rect.setArcHeight(30);
        rect.setArcWidth(30);
        Text bigIcon = new Text("\uD83C\uDFD9\uFE0F");
        bigIcon.setFont(Font.font(90));
        imgPlaceholder.getChildren().addAll(rect, bigIcon);

        hero.getChildren().addAll(textContent, imgPlaceholder);
        return hero;
    }

    private VBox createFeatureSection() {
        VBox section = new VBox(40);
        section.setPadding(new Insets(60, 50, 60, 50));
        section.setAlignment(Pos.CENTER);

        FlowPane flow = new FlowPane(30, 30);
        flow.setAlignment(Pos.CENTER);

        flow.getChildren().addAll(
                createFeatureCard("\uD83D\uDCE2", "Report Complaint", "Submit new issues for resolution."),
                createFeatureCard("\uD83D\uDCCA", "Track Complaint", "Monitor the status of your reports."),
                createFeatureCard("\uD83D\uDCAC", "Submit Feedback", "Tell us how we resolved your issue."),
                createFeatureCard("\uD83D\uDEE0", "Admin Dashboard", "Management tools for officials.")
        );

        section.getChildren().add(flow);
        return section;
    }

    private VBox createFeatureCard(String icon, String title, String desc) {
        VBox card = new VBox(15);
        card.setPrefSize(250, 200);
        card.setStyle(CARD_STYLE);
        card.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.05)));
        card.setAlignment(Pos.CENTER);

        StackPane iconBadge = new StackPane();
        Rectangle iconBg = new Rectangle(50, 50, Color.web(PRIMARY_COLOR));
        iconBg.setArcHeight(10);
        iconBg.setArcWidth(10);
        Text iconText = new Text(icon);
        iconText.setFont(Font.font(24));
        iconBadge.getChildren().addAll(iconBg, iconText);

        Text t = new Text(title);
        t.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        Text d = new Text(desc);
        d.setFont(Font.font("Arial", 13));
        d.setFill(Color.web("#777"));
        d.setWrappingWidth(200);
        d.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        card.getChildren().addAll(iconBadge, t, d);

        card.setOnMouseEntered(e -> card.setStyle(CARD_STYLE + "-fx-background-color: #E3F2FD;"));
        card.setOnMouseExited(e -> card.setStyle(CARD_STYLE));

        return card;
    }

    private VBox createFooter() {
        VBox footer = new VBox(10);
        footer.setPadding(new Insets(40, 50, 40, 50));
        footer.setStyle("-fx-background-color: #263238;");
        footer.setAlignment(Pos.CENTER);

        Text copy = new Text("© Pune Civic Issue Portal - Government of Maharashtra");
        copy.setFill(Color.WHITE);

        HBox info = new HBox(30);
        info.setAlignment(Pos.CENTER);
        Text emergency = new Text("Emergency Contact: 101 / 108");
        Text email = new Text("Support: support@puneportal.gov.in");
        emergency.setFill(Color.LIGHTGRAY);
        email.setFill(Color.LIGHTGRAY);

        info.getChildren().addAll(emergency, email);
        footer.getChildren().addAll(copy, info);
        return footer;
    }

    // ------------------------------------------------------------
    // USER DASHBOARD
    // ------------------------------------------------------------

    private void showUserDashboard() {
        BorderPane bp = new BorderPane();

        VBox sidebar = new VBox(5);
        sidebar.setPrefWidth(250);
        sidebar.setStyle("-fx-background-color: " + PRIMARY_COLOR + ";");
        sidebar.setPadding(new Insets(20, 0, 0, 0));

        Text brand = new Text("USER PORTAL");
        brand.setFill(Color.WHITE);
        brand.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        VBox brandBox = new VBox(brand);
        brandBox.setPadding(new Insets(0, 20, 30, 20));
        sidebar.getChildren().add(brandBox);

        String[] menu = {"Dashboard", "File Complaint", "All Complaints", "Pending Complaints", "Resolved Complaints", "Submit Feedback", "Update Complaint", "Activity Log", "Logout"};
        StackPane contentArea = new StackPane();
        contentArea.setPadding(new Insets(30));
        contentArea.setStyle("-fx-background-color: #eee;");

        for (int i = 0; i < menu.length; i++) {
            String m = menu[i];
            Button b = new Button("  " + USER_MENU_ICONS[i] + "   " + m);
            b.setStyle(SIDEBAR_BUTTON_STYLE);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> {
                if (m.equals("Logout")) {
                    userActions.logout();
                    showHome();
                } else {
                    updateUserContent(contentArea, m);
                }
            });
            sidebar.getChildren().add(b);
        }

        bp.setLeft(sidebar);
        bp.setCenter(contentArea);
        updateUserContent(contentArea, "Dashboard");

        primaryStage.setScene(new Scene(bp, 1200, 750));
    }

    private void updateUserContent(StackPane area, String title) {
        area.getChildren().clear();
        VBox container = new VBox(20);

        Label head = new Label(title);
        head.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        container.getChildren().add(head);

        switch (title) {
            case "Dashboard" -> {
                container.getChildren().add(new Label("Here is a summary of all your filed complaints."));
                runQuery(() -> userActions.viewAllComplaintsGui(),
                        qr -> container.getChildren().add(buildTable(qr, "You haven't filed any complaints yet.")));
            }
            case "File Complaint" -> {
                VBox form = new VBox(15);
                form.setStyle(CARD_STYLE);

                TextField typeField = new TextField();
                TextArea descArea = new TextArea();
                descArea.setPrefRowCount(3);
                TextField areaField = new TextField();
                TextField wardField = new TextField();
                TextField pincodeField = new TextField();

                Button submitBtn = new Button("Submit");
                submitBtn.setStyle(BUTTON_STYLE);
                bindRequiredFields(submitBtn, typeField, descArea, areaField, wardField, pincodeField);

                submitBtn.setOnAction(e -> runQuery(submitBtn,
                        () -> userActions.fileComplaintGui(typeField.getText().trim(), descArea.getText().trim(),
                                areaField.getText().trim(), wardField.getText().trim(), pincodeField.getText().trim()),
                        msg -> {
                            showAlert(Alert.AlertType.INFORMATION, "Complaint Filed", msg);
                            updateUserContent(area, "All Complaints");
                        }));

                Button clearBtn = new Button("Clear");
                clearBtn.setOnAction(e -> { typeField.clear(); descArea.clear(); areaField.clear(); wardField.clear(); pincodeField.clear(); });

                form.getChildren().addAll(
                        new Label("Complaint Type"), typeField,
                        new Label("Description"), descArea,
                        new Label("Area"), areaField,
                        new Label("Ward Number"), wardField,
                        new Label("Pincode"), pincodeField,
                        new HBox(10, submitBtn, clearBtn)
                );
                container.getChildren().add(form);
            }
            case "All Complaints" -> runQuery(() -> userActions.viewAllComplaintsGui(),
                    qr -> container.getChildren().add(buildTable(qr, "You haven't filed any complaints yet.")));
            case "Pending Complaints" -> runQuery(() -> userActions.viewPendingComplaintsGui(),
                    qr -> container.getChildren().add(buildTable(qr, "No pending complaints.")));
            case "Resolved Complaints" -> runQuery(() -> userActions.viewResolvedComplaintsGui(),
                    qr -> container.getChildren().add(buildTable(qr, "No resolved complaints yet.")));
            case "Submit Feedback" -> {
                VBox form = new VBox(15);
                form.setStyle(CARD_STYLE);

                TextField complaintIdField = new TextField();
                ComboBox<Integer> rating = new ComboBox<>();
                rating.getItems().addAll(1, 2, 3, 4, 5);
                rating.getSelectionModel().selectFirst();
                TextArea commentsArea = new TextArea();
                commentsArea.setPrefRowCount(3);

                Button submitBtn = new Button("Submit Feedback");
                submitBtn.setStyle(BUTTON_STYLE);
                bindRequiredFields(submitBtn, complaintIdField, commentsArea);

                submitBtn.setOnAction(e -> {
                    Integer r = rating.getValue();
                    runQuery(submitBtn,
                            () -> userActions.submitFeedbackGui(complaintIdField.getText().trim(),
                                    r == null ? 0 : r, commentsArea.getText().trim()),
                            msg -> {
                                showAlert(Alert.AlertType.INFORMATION, "Feedback Submitted", msg);
                                updateUserContent(area, "Resolved Complaints");
                            });
                });

                form.getChildren().addAll(
                        new Label("Complaint ID"), complaintIdField,
                        new Label("Rating"), rating,
                        new Label("Comments"), commentsArea,
                        submitBtn
                );
                container.getChildren().add(form);
            }
            case "Update Complaint" -> {
                VBox form = new VBox(15);
                form.setStyle(CARD_STYLE);

                TextField complaintIdField = new TextField();
                ComboBox<String> fieldCombo = new ComboBox<>();
                fieldCombo.getItems().addAll("Complaint_type", "Description", "Area", "Ward_no", "Pincode");
                fieldCombo.getSelectionModel().selectFirst();
                TextField newValueField = new TextField();

                Button submitBtn = new Button("Update");
                submitBtn.setStyle(BUTTON_STYLE);
                bindRequiredFields(submitBtn, complaintIdField, newValueField);

                submitBtn.setOnAction(e -> runQuery(submitBtn,
                        () -> userActions.updateComplaintPartialGui(complaintIdField.getText().trim(),
                                fieldCombo.getValue(), newValueField.getText().trim()),
                        msg -> {
                            showAlert(Alert.AlertType.INFORMATION, "Complaint Updated", msg);
                            updateUserContent(area, "All Complaints");
                        }));

                form.getChildren().addAll(
                        new Label("Complaint ID"), complaintIdField,
                        new Label("Field to Update"), fieldCombo,
                        new Label("New Value"), newValueField,
                        submitBtn
                );
                container.getChildren().add(form);
            }
            case "Activity Log" -> runQuery(() -> userActions.viewActivityLogGui(),
                    qr -> container.getChildren().add(buildTable(qr, "No activity recorded yet.")));
            default -> container.getChildren().add(new Label("Placeholder content for " + title));
        }

        ScrollPane sp = new ScrollPane(container);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");
        area.getChildren().add(sp);
    }

    // ------------------------------------------------------------
    // ADMIN DASHBOARD
    // ------------------------------------------------------------

    private void showAdminDashboard() {
        BorderPane bp = new BorderPane();
        VBox sidebar = new VBox(5);
        sidebar.setPrefWidth(250);
        sidebar.setStyle("-fx-background-color: #2c3e50;");
        sidebar.setPadding(new Insets(20, 0, 0, 0));

        Text brand = new Text("ADMIN PANEL");
        brand.setFill(Color.WHITE);
        brand.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        VBox brandBox = new VBox(brand);
        brandBox.setPadding(new Insets(0, 20, 30, 20));
        sidebar.getChildren().add(brandBox);

        String[] menu = {"Overview", "All Complaints", "Pending Complaints", "Resolved Complaints", "Search by Location", "Generate Reports", "Resolve Complaint", "Resolution History", "Logout"};
        StackPane contentArea = new StackPane();
        contentArea.setPadding(new Insets(30));

        for (int i = 0; i < menu.length; i++) {
            String m = menu[i];
            Button b = new Button("  " + ADMIN_MENU_ICONS[i] + "   " + m);
            b.setStyle(SIDEBAR_BUTTON_STYLE);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> {
                if (m.equals("Logout")) {
                    adminActions.logout();
                    showHome();
                } else {
                    updateAdminContent(contentArea, m);
                }
            });
            sidebar.getChildren().add(b);
        }

        bp.setLeft(sidebar);
        bp.setCenter(contentArea);
        updateAdminContent(contentArea, "Overview");
        primaryStage.setScene(new Scene(bp, 1200, 750));
    }

    private void updateAdminContent(StackPane area, String title) {
        area.getChildren().clear();
        VBox container = new VBox(20);

        switch (title) {
            case "Overview" -> {
                container.getChildren().add(new Label("Admin Overview"));
                FlowPane stats = new FlowPane(20, 20);
                container.getChildren().add(stats);
                runQuery(() -> adminActions.getDashboardStatsGui(), s -> {
                    stats.getChildren().addAll(
                            createStatCard("Total", String.valueOf(s.total)),
                            createStatCard("Pending", String.valueOf(s.pending)),
                            createStatCard("Resolved", String.valueOf(s.resolved)),
                            createStatCard("Today", String.valueOf(s.today)),
                            createStatCard("Avg. Rating", s.avgRatingDisplay())
                    );
                });
                runQuery(() -> adminActions.viewAllComplaintsGui(),
                        qr -> container.getChildren().add(buildTable(qr, "No complaints logged yet.")));
            }
            case "All Complaints" -> {
                container.getChildren().add(new Label(title));
                runQuery(() -> adminActions.viewAllComplaintsGui(),
                        qr -> container.getChildren().add(buildTable(qr, "No complaints logged yet.")));
            }
            case "Pending Complaints" -> {
                container.getChildren().add(new Label(title));
                runQuery(() -> adminActions.viewPendingComplaintsGui(),
                        qr -> container.getChildren().add(buildTable(qr, "No pending complaints.")));
            }
            case "Resolved Complaints" -> {
                container.getChildren().add(new Label(title));
                runQuery(() -> adminActions.viewResolvedComplaintsGui(),
                        qr -> container.getChildren().add(buildTable(qr, "No resolved complaints yet.")));
            }
            case "Search by Location" -> {
                container.getChildren().add(new Label(title));

                HBox form = new HBox(10);
                form.setAlignment(Pos.CENTER_LEFT);
                ComboBox<String> optionCombo = new ComboBox<>();
                optionCombo.getItems().addAll("Area", "Pincode", "Ward Number");
                optionCombo.getSelectionModel().selectFirst();
                TextField valueField = new TextField();
                valueField.setPromptText("Enter value to search");
                Button searchBtn = new Button("Search");
                searchBtn.setStyle(BUTTON_STYLE);
                bindRequiredFields(searchBtn, valueField);
                form.getChildren().addAll(optionCombo, valueField, searchBtn);
                container.getChildren().add(form);

                VBox resultsBox = new VBox();
                container.getChildren().add(resultsBox);

                searchBtn.setOnAction(e -> {
                    int option = optionCombo.getSelectionModel().getSelectedIndex() + 1;
                    String optionLabel = optionCombo.getValue();
                    runQuery(searchBtn, () -> adminActions.searchComplaintsByLocationGui(option, valueField.getText().trim()),
                            qr -> {
                                resultsBox.getChildren().clear();
                                resultsBox.getChildren().add(buildTable(qr, "No complaints found for that " + optionLabel.toLowerCase() + "."));
                            });
                });
            }
            case "Generate Reports" -> {
                container.getChildren().add(new Label(title));

                HBox form = new HBox(10);
                form.setAlignment(Pos.CENTER_LEFT);
                ComboBox<String> reportCombo = new ComboBox<>();
                reportCombo.getItems().addAll("Complaints by Department", "Complaints by Status", "Complaints by Priority",
                        "Complaints by Date Range", "Area-wise Complaints", "Monthly Complaint Report");
                reportCombo.getSelectionModel().selectFirst();

                DatePicker fromPicker = new DatePicker();
                fromPicker.setPromptText("From date");
                fromPicker.setVisible(false);
                fromPicker.setManaged(false);
                DatePicker toPicker = new DatePicker();
                toPicker.setPromptText("To date");
                toPicker.setVisible(false);
                toPicker.setManaged(false);

                reportCombo.setOnAction(e -> {
                    boolean isDateRange = reportCombo.getSelectionModel().getSelectedIndex() == 3;
                    fromPicker.setVisible(isDateRange);
                    fromPicker.setManaged(isDateRange);
                    toPicker.setVisible(isDateRange);
                    toPicker.setManaged(isDateRange);
                });

                Button genBtn = new Button("Generate");
                genBtn.setStyle(BUTTON_STYLE);
                form.getChildren().addAll(reportCombo, fromPicker, toPicker, genBtn);
                container.getChildren().add(form);

                VBox resultsBox = new VBox();
                container.getChildren().add(resultsBox);

                genBtn.setOnAction(e -> {
                    int option = reportCombo.getSelectionModel().getSelectedIndex() + 1;
                    LocalDate from = fromPicker.getValue();
                    LocalDate to = toPicker.getValue();
                    runQuery(genBtn, () -> {
                                if (option == 4 && (from == null || to == null)) {
                                    throw new IllegalArgumentException("Please pick both a from-date and a to-date.");
                                }
                                return adminActions.generateReportGui(option, from == null ? "" : from.toString(), to == null ? "" : to.toString());
                            },
                            qr -> {
                                resultsBox.getChildren().clear();
                                resultsBox.getChildren().add(buildTable(qr, "No data available for this report."));
                            });
                });
            }
            case "Resolve Complaint" -> {
                container.getChildren().add(new Label(title));

                HBox loadRow = new HBox(10);
                loadRow.setAlignment(Pos.CENTER_LEFT);
                TextField complaintIdField = new TextField();
                complaintIdField.setPromptText("Complaint ID");
                Button loadBtn = new Button("Load Complaint");
                loadBtn.setStyle(BUTTON_STYLE);
                bindRequiredFields(loadBtn, complaintIdField);
                loadRow.getChildren().addAll(complaintIdField, loadBtn);
                container.getChildren().add(loadRow);

                VBox summaryBox = new VBox(10);
                summaryBox.setStyle(CARD_STYLE);
                summaryBox.setVisible(false);
                summaryBox.setManaged(false);
                container.getChildren().add(summaryBox);

                final AdminActions.ComplaintSummary[] loadedSummary = new AdminActions.ComplaintSummary[1];

                loadBtn.setOnAction(e -> runQuery(loadBtn, () -> {
                            int id;
                            try {
                                id = Integer.parseInt(complaintIdField.getText().trim());
                            } catch (NumberFormatException ex) {
                                throw new IllegalArgumentException("Complaint ID must be numeric.");
                            }
                            return adminActions.getComplaintSummaryGui(id);
                        },
                        summary -> {
                            loadedSummary[0] = summary;
                            summaryBox.getChildren().clear();

                            Label details = new Label(
                                    "Complaint Type : " + summary.complaintType + "\n" +
                                            "Citizen ID     : " + summary.citizenId + "\n" +
                                            "Officer ID     : " + summary.officerId + "\n" +
                                            "Current Status : " + summary.status + "\n" +
                                            "Description    : " + summary.description
                            );
                            TextArea resolutionDetails = new TextArea();
                            resolutionDetails.setPromptText("Resolution details");
                            resolutionDetails.setPrefRowCount(3);
                            Button confirmBtn = new Button("Confirm Resolve");
                            confirmBtn.setStyle(BUTTON_STYLE);
                            bindRequiredFields(confirmBtn, resolutionDetails);
                            confirmBtn.setOnAction(ev -> runQuery(confirmBtn,
                                    () -> adminActions.resolveComplaintGui(loadedSummary[0], resolutionDetails.getText().trim()),
                                    msg -> {
                                        showAlert(Alert.AlertType.INFORMATION, "Complaint Resolved", msg);
                                        complaintIdField.clear();
                                        updateAdminContent(area, "All Complaints");
                                    }));

                            summaryBox.getChildren().addAll(details, new Label("Resolution Details"), resolutionDetails, confirmBtn);
                            summaryBox.setVisible(true);
                            summaryBox.setManaged(true);
                        }));
            }
            case "Resolution History" -> {
                container.getChildren().add(new Label(title));
                runQuery(() -> adminActions.viewResolutionHistoryGui(),
                        qr -> container.getChildren().add(buildTable(qr, "No complaints have been resolved yet.")));
            }
            default -> container.getChildren().add(new Label("Admin Tool: " + title));
        }

        ScrollPane sp = new ScrollPane(container);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");
        area.getChildren().add(sp);
    }

    private VBox createStatCard(String label, String val) {
        VBox c = new VBox(5);
        c.setPrefSize(180, 100);
        c.setStyle(CARD_STYLE);
        c.setAlignment(Pos.CENTER);
        Text t1 = new Text(label);
        t1.setFill(Color.GRAY);
        Text t2 = new Text(val);
        t2.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        c.getChildren().addAll(t1, t2);
        return c;
    }

    public static void main(String[] args) {
        launch(args);
    }
}