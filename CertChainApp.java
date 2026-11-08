package certchain.ui;

import certchain.core.Core.*;
import certchain.engine.Engine.*;
import certchain.ui.Screens.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.effect.Glow;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The main window. A JavaFX application skeleton (Unit 5): extends Application, overrides start() and stop().
 *
 * Run with:  java --module-path <javafx lib> --add-modules javafx.controls -cp out:lib/sqlite-jdbc.jar certchain.ui.CertChainApp
 */
public class CertChainApp extends Application {
    private CertChainService service;
    private AppContext ctx;
    private Stage stage;
    private VerifyPage verifyPage;
    private IssuerPage issuerPage;
    private AuthorityPage authorityPage;
    private LedgerPage ledgerPage;
    private StackPane pageHolder = new StackPane();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, RadioButton> navButtons = new HashMap<>();
    private Label chainChip = new Label("Chain: checking...");
    private ProgressIndicator spinner = new ProgressIndicator();
    private Label busyLabel = new Label("Mining block...");
    private Label statusBlocks = new Label();

    @Override
    public void start(Stage primary) {
        this.stage = primary;
        File dataDir = new File(System.getProperty("certchain.data", "certchain-data"));
        try {
            service = new CertChainService(dataDir);
        } catch (Exception e) {
            Ui.alert(Alert.AlertType.ERROR, "CertChain cannot start", Ui.message(e)
                    + "\n\nMake sure sqlite-jdbc-3.x.jar is in the lib folder and on the classpath.");
            Platform.exit();
            return;
        }
        URL css = CertChainApp.class.getResource("style.css");
        if (css != null) {
            Ui.setCss(css.toExternalForm());
        }
        ctx = new AppContext(service, stage);
        ctx.setLogoutAction(this::showLogin);
        wireServiceOnce();

        stage.setTitle("CertChain - Blockchain Certificate Verification");
        stage.getIcons().add(Icons.image("verify", 64, new java.awt.Color(0x0B6B5F)));
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        showLogin();
        stage.show();
    }

    @Override
    public void stop() {
        if (service != null) {
            service.shutdown();
        }
    }

    // ------------------------------------------------------------------ login and logout
    private Scene newScene(javafx.scene.Parent root) {
        double w = stage.getScene() == null ? 1320 : stage.getScene().getWidth();
        double h = stage.getScene() == null ? 860 : stage.getScene().getHeight();
        Scene scene = new Scene(root, w, h);
        if (!Ui.css().isEmpty()) {
            scene.getStylesheets().add(Ui.css());
        }
        return scene;
    }

    /** Shows the sign-in screen and forgets everything about the previous user. */
    public void showLogin() {
        service.lockSigningKey(ctx.account());
        ctx.setAccount(null);
        ctx.setPageSwitcher(key -> { });
        ctx.setRefresher(() -> { });
        ctx.setBusyListener(busy -> { });
        ctx.setChainListener(report -> { });
        pages.clear();
        navButtons.clear();
        verifyPage = null;
        issuerPage = null;
        authorityPage = null;
        ledgerPage = null;
        stage.setScene(newScene(new LoginView(service, this::enterApp)));
    }

    /** Builds the portal that belongs to the account's role: same look, different pages. */
    private void enterApp(Account account) {
        ctx.setAccount(account);
        pageHolder = new StackPane();
        chainChip = new Label("Chain: checking...");
        spinner = new ProgressIndicator();
        busyLabel = new Label("Mining block...");
        statusBlocks = new Label();
        pages.clear();
        navButtons.clear();

        Role role = account.getRole();
        String first;
        if (role == Role.VERIFIER) {
            verifyPage = new VerifyPage(ctx);
            pages.put("verify", verifyPage);
            first = "verify";
        } else if (role == Role.ISSUER) {
            issuerPage = new IssuerPage(ctx);
            pages.put("issuer", issuerPage);
            first = "issuer";
        } else {
            authorityPage = new AuthorityPage(ctx);
            pages.put("authority", authorityPage);
            first = "authority";
        }
        ledgerPage = new LedgerPage(ctx);
        pages.put("ledger", ledgerPage);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(role), buildToolBar(role)));
        root.setLeft(buildSidebar(account));
        root.setCenter(pageHolder);
        root.setBottom(buildStatusBar());
        wireShell();
        stage.setScene(newScene(root));
        showPage(first);
    }

    // ------------------------------------------------------------------ account settings
    /** Lets the signed-in user change the username and the password (the password change needs an e-mail OTP). */
    private void openSettings() {
        Account me = ctx.account();
        if (me == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Account settings");
        dialog.setHeaderText("Signed in as " + me.getUsername());
        dialog.getDialogPane().getStylesheets().add(Ui.css());

        PasswordField oldPass = new PasswordField();
        oldPass.setId("oldPassField");
        oldPass.setPromptText("Current password (required)");
        javafx.scene.control.TextField newUser = Ui.field("newUserField", "New username (leave empty to keep)");
        javafx.scene.control.TextField email = Ui.field("settingsEmailField", "E-mail for OTP codes");
        email.setText(me.getEmail());
        email.setEditable(me.getEmail().isEmpty());
        PasswordField newPass = new PasswordField();
        newPass.setId("newPassField");
        newPass.setPromptText("New password (leave empty to keep)");
        PasswordField confirm = new PasswordField();
        confirm.setId("confirmPassField");
        confirm.setPromptText("Repeat new password");
        javafx.scene.control.TextField otp = Ui.field("settingsOtpField", "6-digit code (for a new password)");
        Button sendOtp = Ui.button("Send OTP", "btn-secondary", null);
        sendOtp.setId("settingsSendOtp");
        Label message = new Label();
        message.setWrapText(true);
        message.setId("settingsMessage");
        sendOtp.setOnAction(ev -> {
            sendOtp.setDisable(true);
            Async.run(() -> service.requestChangeOtp(me, email.getText()), demo -> {
                sendOtp.setDisable(false);
                message.setStyle("-fx-text-fill: #0E7C6B;");
                String to = me.getEmail().isEmpty() ? email.getText().trim() : Account.maskEmail(me.getEmail());
                message.setText(demo.isEmpty() ? "OTP sent to " + to
                        : "DEMO MODE (no mail server set up): your OTP is " + demo);
            }, ex -> {
                sendOtp.setDisable(false);
                message.setStyle("-fx-text-fill: #B3261E;");
                message.setText(Ui.message(ex));
            });
        });
        HBox otpLine = new HBox(10, otp, sendOtp);
        HBox.setHgrow(otp, Priority.ALWAYS);

        VBox box = new VBox(10, Ui.labeled("Current password", oldPass), new Separator(),
                Ui.labeled("Change username", newUser), Ui.labeled("E-mail", email),
                Ui.labeled("Change password", newPass), confirm, Ui.labeled("OTP sent to your e-mail", otpLine), message);
        box.setPadding(new Insets(10));
        box.setPrefWidth(420);
        dialog.getDialogPane().setContent(box);
        ButtonType save = new ButtonType("Save changes", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume();
            try {
                Account current = me;
                boolean wantUser = !newUser.getText().trim().isEmpty();
                boolean wantPass = !newPass.getText().isEmpty();
                if (!wantUser && !wantPass) {
                    throw new ValidationException("Enter a new username or a new password");
                }
                if (wantPass && !newPass.getText().equals(confirm.getText())) {
                    throw new ValidationException("The two new passwords are not the same");
                }
                if (wantPass) {
                    current = service.changePassword(current, oldPass.getText(), newPass.getText(), otp.getText(),
                            email.getText());
                }
                if (wantUser) {
                    current = service.changeUsername(current, wantPass ? newPass.getText() : oldPass.getText(),
                            newUser.getText());
                }
                dialog.close();
                Ui.alert(Alert.AlertType.INFORMATION, "Saved", "Your account was updated. You are now signed in as \""
                        + current.getUsername() + "\".");
                enterApp(current);
            } catch (Exception ex) {
                message.setText(Ui.message(ex));
                message.setStyle("-fx-text-fill: #B3261E;");
            }
        });
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------ wiring
    /** Things that exist once for the whole run: the log and the background watchdog thread. */
    private void wireServiceOnce() {
        List<String> old = service.getLog().getLines();
        for (String line : old) {
            ctx.logItems().add(0, line);
        }
        service.getLog().addListener(line -> Platform.runLater(() -> ctx.logItems().add(0, line)));
        // background thread that re-validates the chain every 4 seconds
        service.startWatchdog(4000, new ResultHandler<ValidationReport>() {
            @Override
            public void onSuccess(ValidationReport report) {
                Platform.runLater(() -> ctx.reportChain(report));
            }

            @Override
            public void onError(Exception error) {
                // the watchdog just tries again at the next round
            }
        });
    }

    /** Connects the pages of the current portal to the window (runs after every sign-in). */
    private void wireShell() {
        ctx.setPageSwitcher(this::showPage);
        ctx.setRefresher(() -> {
            if (verifyPage != null) {
                verifyPage.refreshStats();
            }
            if (authorityPage != null) {
                authorityPage.onShow();
            }
            ledgerPage.rebuild();
            statusBlocks.setText("Blocks: " + service.blockCount());
        });
        ctx.setBusyListener(busy -> {
            spinner.setVisible(busy);
            busyLabel.setVisible(busy);
        });
        ctx.setChainListener(report -> {
            chainChip.getStyleClass().removeAll("chip-idle", "chip-ok", "chip-bad");
            if (report.isValid()) {
                chainChip.setText("Chain valid");
                chainChip.getStyleClass().add("chip-ok");
            } else {
                chainChip.setText("Chain BROKEN: block #" + report.getBadBlockIndex());
                chainChip.getStyleClass().add("chip-bad");
                Anim.shake(chainChip);
            }
            ledgerPage.showChainReport(report);
        });
        statusBlocks.setText("Blocks: " + service.blockCount());
    }

    public void showPage(String key) {
        Page page = pages.get(key);
        if (page == null) {
            return;
        }
        pageHolder.getChildren().setAll(page);
        Anim.fadeSlideIn(page);
        page.onShow(); // runtime polymorphism: each Page subclass reacts in its own way
        RadioButton button = navButtons.get(key);
        if (button != null) {
            button.setSelected(true);
        }
    }

    // ------------------------------------------------------------------ sidebar
    private VBox buildSidebar(Account account) {
        Role role = account.getRole();
        ImageView logo = Icons.view("verify", 38, Icons.WHITE);
        Anim.breathe(logo, javafx.scene.paint.Color.web("#5FD3C0"));
        Anim.float_(logo);
        logo.getTransforms().add(new Rotate(-8, 19, 19)); // Unit 5: transforms
        Label title = new Label("CertChain");
        title.getStyleClass().add("brand-title");
        Label sub = new Label(role.getLabel() + " portal");
        sub.getStyleClass().add("brand-sub");
        HBox brand = new HBox(12, logo, new VBox(1, title, sub));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(24, 20, 26, 22));

        ToggleGroup group = new ToggleGroup();
        VBox nav = new VBox(2);
        String[][] items;
        if (role == Role.VERIFIER) {
            items = new String[][]{{"verify", "Verify documents", "verify"}, {"ledger", "Ledger", "ledger"}};
        } else if (role == Role.ISSUER) {
            items = new String[][]{{"issuer", "Issue and requests", "issue"}, {"ledger", "Ledger", "ledger"}};
        } else {
            items = new String[][]{{"authority", "Issuers", "issue"}, {"ledger", "Ledger", "ledger"}};
        }
        for (String[] item : items) {
            RadioButton rb = new RadioButton(item[1]);
            rb.setToggleGroup(group);
            rb.getStyleClass().add("nav-item");
            rb.setGraphic(Icons.view(item[2], 20, Icons.LIGHT));
            rb.setMaxWidth(Double.MAX_VALUE);
            rb.setId("nav-" + item[0]);
            rb.setOnAction(e -> showPage(item[0]));
            navButtons.put(item[0], rb);
            nav.getChildren().add(rb);
        }
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        // the signed-in user and the sign-out button
        String shown = account.getDisplayName().isEmpty() ? account.getUsername() : account.getDisplayName();
        Label avatar = new Label(shown.substring(0, 1).toUpperCase());
        avatar.getStyleClass().add("avatar");
        Label name = new Label(shown);
        name.getStyleClass().add("user-name");
        name.setMaxWidth(150);
        Label who = new Label(role.getLabel() + " - " + account.getUsername());
        who.getStyleClass().add("user-role");
        who.setMaxWidth(150);
        HBox userRow = new HBox(12, avatar, new VBox(2, name, who));
        userRow.setAlignment(Pos.CENTER_LEFT);
        Button signOut = Ui.button("Sign out", "btn-ghost-dark", Icons.view("exit", 16, Icons.LIGHT));
        signOut.setId("signOutBtn");
        signOut.setMaxWidth(Double.MAX_VALUE);
        signOut.setOnAction(e -> ctx.logout());
        Button settings = Ui.button("Account settings", "btn-ghost-dark", Icons.view("exit", 16, Icons.LIGHT));
        settings.setId("settingsBtn");
        settings.setMaxWidth(Double.MAX_VALUE);
        settings.setOnAction(e -> openSettings());
        VBox userCard = new VBox(12, userRow, settings, signOut);
        userCard.setPadding(new Insets(0, 16, 20, 18));

        VBox side = new VBox(brand, nav, grow, userCard);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(236);
        side.setMinWidth(236);
        return side;
    }

    // ------------------------------------------------------------------ menu bar
    private MenuBar buildMenuBar(Role role) {
        Menu file = new Menu("File");
        if (role == Role.VERIFIER) {
            file.getItems().add(item("Open document to verify...", "folder", KeyCode.O, () -> {
                showPage("verify");
                verifyPage.browse();
            }));
        }
        file.getItems().addAll(
                item("Export ledger...", "save", KeyCode.E, () -> ledgerPage.export()),
                new SeparatorMenuItem(),
                item("Account settings", "exit", KeyCode.K, () -> openSettings()),
                item("Sign out", "exit", KeyCode.L, () -> ctx.logout()),
                item("Exit", "exit", KeyCode.Q, () -> {
                    stop();
                    Platform.exit();
                }));

        Menu view = new Menu("View");
        if (role == Role.VERIFIER) {
            view.getItems().add(item("Verify documents", "verify", KeyCode.DIGIT1, () -> showPage("verify")));
        } else if (role == Role.ISSUER) {
            view.getItems().add(item("Issue and requests", "issue", KeyCode.DIGIT1, () -> showPage("issuer")));
        } else {
            view.getItems().add(item("Issuers", "issue", KeyCode.DIGIT1, () -> showPage("authority")));
        }
        view.getItems().add(item("Ledger", "ledger", KeyCode.DIGIT2, () -> showPage("ledger")));

        Menu tools = new Menu("Tools");
        MenuItem validate = item("Validate chain", "validate", null, () -> ledgerPage.validate(true));
        validate.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        tools.getItems().add(validate);
        if (role == Role.AUTHORITY) { // the tampering demo is only for the regulator
            tools.getItems().addAll(new SeparatorMenuItem(),
                    item("Simulate tampering (demo)", "tamper", null, this::tamper),
                    item("Restore tampered block", "restore", null, this::restore));
        }

        Menu help = new Menu("Help");
        help.getItems().addAll(
                item("Syllabus concepts used in this project", "info", null, this::showSyllabus),
                item("About CertChain", "info", null, () -> Ui.alert(Alert.AlertType.INFORMATION, "About CertChain",
                        "CertChain verifies certificates with a blockchain.\n\nAuthority approves issuers, issuers register "
                                + "certificates, companies verify them.\n\nBuilt with Java, SQLite (JDBC) and JavaFX.")));
        return new MenuBar(file, view, tools, help);
    }

    private MenuItem item(String text, String icon, KeyCode key, Runnable action) {
        MenuItem mi = new MenuItem(text);
        mi.setGraphic(Icons.view(icon, 16, Icons.GREY)); // Unit 5: images on menu items
        if (key != null) {
            mi.setAccelerator(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN));
        }
        mi.setOnAction(e -> action.run());
        return mi;
    }

    // ------------------------------------------------------------------ toolbar
    private ToolBar buildToolBar(Role role) {
        ToolBar bar = new ToolBar();
        if (role == Role.VERIFIER) {
            Button open = Ui.button("Open document", "btn-ghost", Icons.view("folder", 16, Icons.GREY));
            open.setOnAction(e -> {
                showPage("verify");
                verifyPage.browse();
            });
            bar.getItems().add(open);
        }
        Button validate = Ui.button("Validate chain", "btn-ghost", Icons.view("validate", 16, Icons.GREY));
        validate.setOnAction(e -> ledgerPage.validate(true));
        bar.getItems().add(validate);
        if (role == Role.AUTHORITY) {
            Button tamper = Ui.button("Tampering demo", "btn-ghost", Icons.view("tamper", 16, Icons.GREY));
            tamper.setOnAction(e -> tamper());
            Button restore = Ui.button("Restore", "btn-ghost", Icons.view("restore", 16, Icons.GREY));
            restore.setOnAction(e -> restore());
            bar.getItems().addAll(tamper, restore);
        }

        spinner.setPrefSize(22, 22);
        spinner.setMaxSize(22, 22);
        spinner.setVisible(false);
        busyLabel.getStyleClass().add("caption");
        busyLabel.setVisible(false);
        chainChip.getStyleClass().addAll("chip", "chip-idle");
        chainChip.setId("appChainChip");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getItems().addAll(spacer, spinner, busyLabel, chainChip);
        bar.setId("toolbar");
        return bar;
    }

    private HBox buildStatusBar() {
        Label db = new Label("Database: " + service.getDatabaseFile().getAbsolutePath());
        db.getStyleClass().add("status-text");
        Label pow = new Label("Proof of work: hash must start with 0000");
        pow.getStyleClass().add("status-text");
        statusBlocks.getStyleClass().add("status-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(24, db, spacer, pow, statusBlocks);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    // ------------------------------------------------------------------ demo actions and dialogs
    private void tamper() {
        Async.run(() -> service.tamperDemo(), message -> {
            Ui.alert(Alert.AlertType.WARNING, "Tampering demo", message);
            ledgerPage.validate(false); // shows the broken chain at once
            showPage("ledger");
        }, e -> Ui.error(e));
    }

    private void restore() {
        Async.run(() -> service.revertTamper(), message -> {
            Ui.alert(Alert.AlertType.INFORMATION, "Restored", message);
            ledgerPage.validate(false);
        }, e -> Ui.error(e));
    }

    private void showSyllabus() {
        String text = "UNIT 1 - Overview of Java and Inheritance\n"
                + "  Classes, objects, constructors, this, static, final  : model, util, Blockchain\n"
                + "  Inheritance, multilevel hierarchy, super             : BlockData > CertificateData > IssuerConfirmedCertificate\n"
                + "  Abstract class, interface, final with inheritance    : BlockData, Page, JdbcRepository / Hashable, Repository\n"
                + "  Packages and access specifiers                       : certchain.model / db / blockchain / service / ui\n\n"
                + "UNIT 2 - Polymorphism and Exception handling\n"
                + "  Overloading (methods, constructors)                  : Hasher.sha256(), verify(File|String), Block, CertificateData\n"
                + "  Overriding, dynamic method dispatch                  : describe(), getType(), Page.onShow()\n"
                + "  try/catch, multiple catch, nested try, throw, throws, finally, user defined exceptions\n"
                + "                                                       : certchain.exception, FileUtil, Hasher, VerifyPage.showImage\n\n"
                + "UNIT 3 - Multithreading and Event handling\n"
                + "  Thread class, Runnable, priorities                   : ChainWatchdog (Thread, MIN), Miner (Runnable, NORM+1)\n"
                + "  Synchronizing, inter-thread communication            : Blockchain (synchronized), MiningQueue (wait / notifyAll)\n"
                + "  Byte streams, FileInputStream, OutputStream          : Hasher, FileUtil, VerifyPage.showImage\n"
                + "  Delegation event model, KeyEvent, adapter class      : KeyAdapterFX, setOnAction, drag and drop events\n\n"
                + "UNIT 4 - JDBC and Collections\n"
                + "  JDBC with SQLite, create / insert / select / update  : Database, JdbcRepository, BlockRepository\n"
                + "  List, Set, Map, ArrayList, LinkedList                : Blockchain (ArrayList), MiningQueue (LinkedList)\n"
                + "  Vector, HashSet, TreeSet, HashMap, Hashtable, Iterator: ActivityLog, Blockchain, issuerNames(), validate()\n\n"
                + "UNIT 5 - Generics and JavaFX\n"
                + "  Generic class with two type parameters               : Pair<A, B>\n"
                + "  Generic method, wildcard, generic interface          : GenericUtil.ofType, ResultHandler<T>, Repository<T, ID>\n"
                + "  Generic class hierarchy                              : Repository > JdbcRepository > BlockRepository\n"
                + "  JavaFX application skeleton                          : CertChainApp\n"
                + "  RadioButton, ListView, ComboBox, TextField, TreeView : VerifyPage, IssuerPage, LedgerPage\n"
                + "  Effects and transforms                               : DropShadow, Glow, InnerShadow, Rotate, scale on hover\n"
                + "  MenuBar, Menu, MenuItem with images, ToolBar         : CertChainApp.buildMenuBar / buildToolBar\n";
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setPrefSize(900, 520);
        area.getStyleClass().add("mono");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Syllabus concepts used");
        dialog.setHeaderText("Where each topic of the course appears in the code");
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(Ui.css());
        dialog.initOwner(ctx.stage());
        dialog.showAndWait();
    }

    // accessors for the test harness
    public VerifyPage getVerifyPage() {
        return verifyPage;
    }

    public IssuerPage getIssuerPage() {
        return issuerPage;
    }

    public AuthorityPage getAuthorityPage() {
        return authorityPage;
    }

    public LedgerPage getLedgerPage() {
        return ledgerPage;
    }

    public CertChainService getService() {
        return service;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
