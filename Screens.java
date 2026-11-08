package certchain.ui;

import certchain.core.Core.*;
import certchain.engine.Engine.*;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/** All classes of the "ui" part of CertChain, kept in one file. */
public final class Screens {
    private Screens() {
    }

    // ===================== Anim =====================
    /**
     * Small helper class with static animation methods (Unit 5: transitions, timelines, effects).
     * All animations are short so the application always feels quick.
     */
    public static final class Anim {
        private Anim() {
        }

        /** Stops the animation that is still running on this node, so two animations never fight over it. */
        private static void play(Node node, Animation next) {
            Object old = node.getProperties().get("anim");
            if (old instanceof Animation) {
                ((Animation) old).stop();
            }
            node.getProperties().put("anim", next);
            next.setOnFinished(e -> {
                node.setOpacity(1);
                node.setTranslateY(0);
                node.setScaleX(1);
                node.setScaleY(1);
            });
            next.play();
        }

        /** A screen fades in while sliding up a little. */
        public static void fadeSlideIn(Node node) {
            node.setOpacity(0);
            node.setTranslateY(18);
            FadeTransition fade = new FadeTransition(Duration.millis(320), node);
            fade.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(320), node);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            fade.setFromValue(0);
            play(node, new ParallelTransition(fade, slide));
        }

        /** Several nodes appear one after the other (staggered entrance). */
        public static void cascade(List<? extends Node> nodes) {
            int i = 0;
            for (Node n : nodes) {
                n.setOpacity(0);
                n.setTranslateY(22);
                FadeTransition fade = new FadeTransition(Duration.millis(350), n);
                fade.setToValue(1);
                TranslateTransition slide = new TranslateTransition(Duration.millis(350), n);
                slide.setToY(0);
                slide.setInterpolator(Interpolator.EASE_OUT);
                ParallelTransition both = new ParallelTransition(fade, slide);
                both.setDelay(Duration.millis(70L * i));
                both.play();
                i++;
            }
        }

        /** Numbers count up from the old value to the new one. */
        public static void countUp(Label label, int from, int to) {
            if (from == to) {
                label.setText(String.valueOf(to));
                return;
            }
            int[] current = {from};
            Timeline t = new Timeline();
            int steps = Math.min(24, Math.abs(to - from) * 4);
            for (int s = 1; s <= steps; s++) {
                final int value = from + (int) Math.round((to - from) * (s / (double) steps));
                t.getKeyFrames().add(new KeyFrame(Duration.millis(30L * s), e -> {
                    current[0] = value;
                    label.setText(String.valueOf(value));
                }));
            }
            t.setOnFinished(e -> label.setText(String.valueOf(to)));
            t.play();
        }

        /** The node pops in with a small overshoot (grows past its size, then settles back). */
        public static void pop(Node node) {
            node.setScaleX(0.6);
            node.setScaleY(0.6);
            node.setOpacity(0);
            ScaleTransition grow = new ScaleTransition(Duration.millis(240), node);
            grow.setToX(1.12);
            grow.setToY(1.12);
            grow.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition settle = new ScaleTransition(Duration.millis(140), node);
            settle.setToX(1);
            settle.setToY(1);
            FadeTransition fade = new FadeTransition(Duration.millis(200), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            play(node, new ParallelTransition(new SequentialTransition(grow, settle), fade));
        }

        /** A rubber stamp is slammed onto the paper: it starts big and transparent and hits the page. */
        public static void slam(Node stamp) {
            stamp.setScaleX(2.6);
            stamp.setScaleY(2.6);
            stamp.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(160), stamp);
            fade.setToValue(1);
            ScaleTransition down = new ScaleTransition(Duration.millis(260), stamp);
            down.setToX(1);
            down.setToY(1);
            down.setInterpolator(Interpolator.EASE_IN);
            ScaleTransition bounce = new ScaleTransition(Duration.millis(110), stamp);
            bounce.setToX(1.08);
            bounce.setToY(1.08);
            bounce.setAutoReverse(true);
            bounce.setCycleCount(2);
            fade.setFromValue(0);
            play(stamp, new SequentialTransition(new ParallelTransition(fade, down), bounce));
        }

        /** Quick left-right shake, used for bad results. */
        public static void shake(Node node) {
            Timeline t = new Timeline(
                    new KeyFrame(Duration.millis(0), new KeyValue(node.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(60), new KeyValue(node.translateXProperty(), -10)),
                    new KeyFrame(Duration.millis(120), new KeyValue(node.translateXProperty(), 10)),
                    new KeyFrame(Duration.millis(180), new KeyValue(node.translateXProperty(), -7)),
                    new KeyFrame(Duration.millis(240), new KeyValue(node.translateXProperty(), 7)),
                    new KeyFrame(Duration.millis(300), new KeyValue(node.translateXProperty(), 0)));
            t.play();
        }

        /** Endless slow glow, used for the logo and for the drop zone. Returns the animation so callers can stop it. */
        public static Animation breathe(Node node, Color color) {
            DropShadow shadow = new DropShadow(8, color);
            node.setEffect(shadow);
            Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(shadow.radiusProperty(), 6)),
                    new KeyFrame(Duration.seconds(1.6), new KeyValue(shadow.radiusProperty(), 22, Interpolator.EASE_BOTH)));
            t.setAutoReverse(true);
            t.setCycleCount(Animation.INDEFINITE);
            t.play();
            return t;
        }

        /** Endless gentle floating up and down. */
        public static Animation float_(Node node) {
            TranslateTransition t = new TranslateTransition(Duration.seconds(2.2), node);
            t.setByY(-4);
            t.setAutoReverse(true);
            t.setCycleCount(Animation.INDEFINITE);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.play();
            return t;
        }

        /** Hover lift: the node rises a little and glows while the mouse is over it. */
        public static void hoverLift(Node node) {
            Glow glow = new Glow(0);
            node.setOnMouseEntered(e -> lift(node, glow, -3, 0.25));
            node.setOnMouseExited(e -> lift(node, glow, 0, 0));
        }

        private static void lift(Node node, Glow glow, double y, double level) {
            TranslateTransition t = new TranslateTransition(Duration.millis(140), node);
            t.setToY(y);
            t.play();
        }

        /** Runs the action after a short pause (used to delay sequences). */
        public static void later(int millis, Runnable action) {
            PauseTransition p = new PauseTransition(Duration.millis(millis));
            p.setOnFinished(e -> action.run());
            p.play();
        }
    }

    // ===================== AppContext =====================
    /** Things every screen needs: the backend service, the window, the log and a few callbacks into the main window. */
    public static class AppContext {
        private final CertChainService service;
        private final Stage stage;
        private final ObservableList<String> logItems = FXCollections.observableArrayList();

        private Consumer<String> pageSwitcher = name -> { };
        private Runnable refresher = () -> { };
        private Consumer<Boolean> busyListener = busy -> { };
        private Consumer<ValidationReport> chainListener = report -> { };
        private int busyCount;
        private Account account;
        private Runnable logoutAction = () -> { };

        public AppContext(CertChainService service, Stage stage) {
            this.service = service;
            this.stage = stage;
        }

        /** The user who is signed in right now (null on the login screen). */
        public Account account() {
            return account;
        }

        public void setAccount(Account account) {
            this.account = account;
        }

        public void setLogoutAction(Runnable logoutAction) {
            this.logoutAction = logoutAction;
        }

        public void logout() {
            logoutAction.run();
        }

        public CertChainService service() {
            return service;
        }

        public Stage stage() {
            return stage;
        }

        public ObservableList<String> logItems() {
            return logItems;
        }

        public void setPageSwitcher(Consumer<String> pageSwitcher) {
            this.pageSwitcher = pageSwitcher;
        }

        public void setRefresher(Runnable refresher) {
            this.refresher = refresher;
        }

        public void setBusyListener(Consumer<Boolean> busyListener) {
            this.busyListener = busyListener;
        }

        public void setChainListener(Consumer<ValidationReport> chainListener) {
            this.chainListener = chainListener;
        }

        public void showPage(String name) {
            pageSwitcher.accept(name);
        }

        /** Re-reads statistics and the ledger after something changed. */
        public void refresh() {
            refresher.run();
        }

        public void reportChain(ValidationReport report) {
            chainListener.accept(report);
        }

        /** Shows the spinner while background work (mining) is running. Call only from the JavaFX thread. */
        public void beginBusy() {
            busyCount++;
            busyListener.accept(true);
        }

        public void endBusy() {
            if (busyCount > 0) {
                busyCount--;
            }
            busyListener.accept(busyCount > 0);
        }
    }

    // ===================== Async =====================
    /**
     * Runs slow work (hashing files, database access) on a separate thread so the window never freezes,
     * then hands the result back to the JavaFX thread with Platform.runLater (Unit 3: multithreading).
     */
    public static final class Async {
        private Async() {
        }

        public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Exception> onError) {
            Thread worker = new Thread(() -> {
                try {
                    T result = work.call();
                    Platform.runLater(() -> onSuccess.accept(result));
                } catch (Exception e) {
                    Platform.runLater(() -> onError.accept(e));
                }
            }, "ui-worker");
            worker.setDaemon(true);
            worker.start();
        }
    }

    // ===================== AuthorityPage =====================
    /** Authority portal: the regulator creates an account (institution + username + password) for every genuine issuer. */
    public static class AuthorityPage extends Page {
        private final TextField institutionField = Ui.field("institutionField", "e.g. Board of Secondary Education");
        private final TextField usernameField = Ui.field("issuerUserField", "Login username, e.g. board.ssc");
        private final TextField passwordField = Ui.field("issuerPassField", "At least 6 characters");
        private final TextField emailField = Ui.field("issuerEmailField", "Official e-mail, e.g. exams@board.gov.in");
        private final TextField phoneField = Ui.field("issuerPhoneField", "Mobile number (optional)");
        private final Label result = new Label();
        private final ObservableList<String> rows = FXCollections.observableArrayList();
        private final ListView<String> issuerList = new ListView<>(rows);

        public AuthorityPage(AppContext ctx) {
            super(ctx);
            Label title = Ui.title("Authority portal");
            Label subtitle = Ui.caption("Create an account for each genuine issuing institution. Give the username and password to the institution.");

            Button suggest = Ui.button("Suggest", "btn-ghost", null);
            suggest.setOnAction(e -> passwordField.setText(Ids.newKey().substring(0, 10)));
            Button create = Ui.button("Create account", "btn-primary", Icons.view("issue", 16, Icons.WHITE));
            create.setId("createIssuerBtn");
            create.setOnAction(e -> create());
            result.setWrapText(true);
            result.setId("authResult");
            HBox buttons = new HBox(10, create, suggest);
            buttons.setAlignment(Pos.CENTER_LEFT);

            VBox form = Ui.card();
            form.setSpacing(12);
            form.setPrefWidth(430);
            form.setMinWidth(380);
            form.getChildren().addAll(Ui.heading("Approve an issuing institution"),
                    Ui.labeled("Institution name", institutionField),
                    Ui.labeled("Username for the institution", usernameField),
                    Ui.labeled("Password", passwordField),
                    Ui.labeled("Official e-mail (used for OTP / forgotten password)", emailField),
                    Ui.labeled("Mobile number", phoneField),
                    buttons, result);

            issuerList.setId("issuerList");
            issuerList.setPlaceholder(new Label("No issuers yet"));
            issuerList.setPrefHeight(190);
            VBox listCard = Ui.card();
            listCard.getChildren().addAll(Ui.heading("Approved institutions"), issuerList);

            VBox how = Ui.card();
            how.setSpacing(10);
            how.getChildren().addAll(Ui.heading("How trust works"),
                    step("1", "Authority", "The government or education regulator creates an account for each genuine institution."),
                    step("2", "Issuer", "The institution signs in to the Issuer portal, registers its certificates and answers verification requests."),
                    step("3", "Verifier", "A company signs in to the Verifier portal and checks a candidate's document. Unknown documents go to the real issuer."),
                    step("4", "Blockchain", "Every decision is stored permanently. Nobody can quietly edit an old block."));

            VBox left = new VBox(16, form, listCard);
            left.setPrefWidth(430);
            left.setMinWidth(380);
            HBox.setHgrow(how, Priority.ALWAYS);
            HBox columns = new HBox(18, left, how);
            VBox content = new VBox(16, new VBox(2, title, subtitle), columns);
            content.setPadding(new Insets(22, 26, 26, 26));
            setContent(content);
            refreshList();
        }

        @Override
        public String getTitle() {
            return "Authority Portal";
        }

        @Override
        public void onShow() {
            refreshList();
        }

        private void refreshList() {
            rows.clear();
            for (Account a : ctx.service().listAccounts(Role.ISSUER)) {
                rows.add(a.getDisplayName() + "  -  " + a.getUsername());
            }
        }

        private void create() {
            String name = institutionField.getText();
            String user = usernameField.getText();
            String pass = passwordField.getText();
            String mail = emailField.getText();
            String mobile = phoneField.getText();
            result.getStyleClass().removeAll("result-ok", "login-error");
            Async.run(() -> ctx.service().createIssuerAccount(ctx.account(), name, user, pass, mail, mobile), pair -> {
                result.setText("Created " + pair.getFirst().getName() + " (" + pair.getFirst().getId() + ").\nUsername: "
                        + pair.getSecond().getUsername() + "    Password: " + pass
                        + "\nGive these to the institution; the password is not stored in readable form.");
                result.getStyleClass().add("result-ok");
                Anim.pop(result);
                institutionField.clear();
                usernameField.clear();
                passwordField.clear();
                emailField.clear();
                phoneField.clear();
                refreshList();
                ctx.refresh();
            }, e -> {
                result.setText(Ui.message(e));
                result.getStyleClass().add("login-error");
                Anim.shake(result);
            });
        }

        private HBox step(String number, String title, String text) {
            Label n = new Label(number);
            n.getStyleClass().addAll("badge", "badge-ok");
            n.setMinWidth(34);
            n.setAlignment(Pos.CENTER);
            n.setStyle("-fx-background-color: #0B6B5F; -fx-padding: 4 0 4 0;");
            Label t = new Label(title);
            t.getStyleClass().add("cell-title");
            Label body = new Label(text);
            body.getStyleClass().add("caption");
            body.setWrapText(true);
            HBox box = new HBox(12, n, new VBox(2, t, body));
            box.setAlignment(Pos.TOP_LEFT);
            return box;
        }
    }

    // ===================== Icons =====================
    /**
     * Menu and button icons. They are drawn with AWT (Graphics2D) and converted to JavaFX Images,
     * so the project needs no image files (Unit 5: adding images to menu items).
     */
    public static final class Icons {
        public static final java.awt.Color WHITE = java.awt.Color.WHITE;
        public static final java.awt.Color NAVY = new java.awt.Color(0x0F2A4A);
        public static final java.awt.Color BLUE = new java.awt.Color(0x0B6B5F);
        public static final java.awt.Color GREY = new java.awt.Color(0x4A5A6E);
        public static final java.awt.Color LIGHT = new java.awt.Color(0xC9D6E6);

        private Icons() {
        }

        public static ImageView view(String name, int size, java.awt.Color color) {
            ImageView v = new ImageView(image(name, size * 2, color));
            v.setFitWidth(size);
            v.setFitHeight(size);
            v.setSmooth(true);
            return v;
        }

        public static Image image(String name, int px, java.awt.Color color) {
            BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.scale(px / 24.0, px / 24.0); // draw in a 24 x 24 grid
                g.setColor(color);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(g, name);
            } finally {
                g.dispose();
            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(img, "png", out);
                return new Image(new ByteArrayInputStream(out.toByteArray()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void draw(Graphics2D g, String name) {
            switch (name) {
                case "verify": {
                    Path2D shield = new Path2D.Double();
                    shield.moveTo(12, 2);
                    shield.lineTo(20, 5);
                    shield.lineTo(20, 12);
                    shield.curveTo(20, 17, 16, 20.5, 12, 22);
                    shield.curveTo(8, 20.5, 4, 17, 4, 12);
                    shield.lineTo(4, 5);
                    shield.closePath();
                    g.draw(shield);
                    g.draw(check());
                    break;
                }
                case "issue": {
                    g.draw(new RoundRectangle2D.Double(5, 3, 14, 18, 3, 3));
                    g.draw(new Line2D.Double(12, 9, 12, 15));
                    g.draw(new Line2D.Double(9, 12, 15, 12));
                    break;
                }
                case "ledger": {
                    g.draw(new RoundRectangle2D.Double(2.5, 8, 11, 8, 8, 8));
                    g.draw(new RoundRectangle2D.Double(10.5, 8, 11, 8, 8, 8));
                    break;
                }
                case "validate": {
                    g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                    g.draw(check());
                    break;
                }
                case "folder": {
                    Path2D p = new Path2D.Double();
                    p.moveTo(3, 6);
                    p.lineTo(10, 6);
                    p.lineTo(12, 8.5);
                    p.lineTo(21, 8.5);
                    p.lineTo(21, 19);
                    p.lineTo(3, 19);
                    p.closePath();
                    g.draw(p);
                    break;
                }
                case "save": {
                    g.draw(new RoundRectangle2D.Double(4, 4, 16, 16, 3, 3));
                    g.draw(new Line2D.Double(8, 4, 8, 9));
                    g.draw(new Line2D.Double(8, 9, 16, 9));
                    g.draw(new Line2D.Double(8, 20, 8, 14));
                    g.draw(new Line2D.Double(8, 14, 16, 14));
                    g.draw(new Line2D.Double(16, 14, 16, 20));
                    break;
                }
                case "exit": {
                    g.draw(new Line2D.Double(6, 6, 18, 18));
                    g.draw(new Line2D.Double(18, 6, 6, 18));
                    break;
                }
                case "tamper": {
                    Path2D t = new Path2D.Double();
                    t.moveTo(12, 3);
                    t.lineTo(22, 20);
                    t.lineTo(2, 20);
                    t.closePath();
                    g.draw(t);
                    g.draw(new Line2D.Double(12, 9, 12, 14));
                    g.fill(new Ellipse2D.Double(11, 16, 2, 2));
                    break;
                }
                case "restore": {
                    g.draw(new Arc2D.Double(4, 4, 16, 16, 60, 270, Arc2D.OPEN));
                    g.draw(new Line2D.Double(7.5, 4.5, 7.5, 9.5));
                    g.draw(new Line2D.Double(7.5, 9.5, 12.5, 9.5));
                    break;
                }
                case "info": {
                    g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                    g.draw(new Line2D.Double(12, 11, 12, 17));
                    g.fill(new Ellipse2D.Double(11, 6.5, 2, 2));
                    break;
                }
                case "key": {
                    g.draw(new Ellipse2D.Double(3, 8, 8, 8));
                    g.draw(new Line2D.Double(11, 12, 21, 12));
                    g.draw(new Line2D.Double(17, 12, 17, 15));
                    g.draw(new Line2D.Double(20, 12, 20, 15));
                    break;
                }
                case "refresh": {
                    g.draw(new Arc2D.Double(4, 4, 16, 16, 30, 280, Arc2D.OPEN));
                    g.draw(new Line2D.Double(19, 4, 19, 9));
                    g.draw(new Line2D.Double(19, 9, 14, 9));
                    break;
                }
                case "stamp": {
                    g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                    g.draw(new Ellipse2D.Double(7, 7, 10, 10));
                    break;
                }
                default:
                    g.draw(new Ellipse2D.Double(4, 4, 16, 16));
            }
        }

        private static Path2D check() {
            Path2D c = new Path2D.Double();
            c.moveTo(8, 12.5);
            c.lineTo(11, 15.5);
            c.lineTo(16, 9);
            return c;
        }
    }

    // ===================== IssuerPage =====================
    /** Issuer portal: issue certificates and answer verification requests for the signed-in institution. */
    public static class IssuerPage extends Page {
        private final Label whoLabel = new Label("Not signed in");
        private Issuer issuer;

        // issue tab
        private final TextField holderField = Ui.field("holderField", "Full name as it should appear");
        private final ComboBox<String> courseBox = new ComboBox<>();
        private final TextField gradeField = Ui.field("gradeField", "e.g. 9.5 CGPA or 87%");
        private final TextField yearField = Ui.field("yearField", "e.g. 2022");
        { yearField.setText("2022"); } // default year
        private final TextField photoField = Ui.field("photoField", "Photo link (https://...) or choose a file");
        private final ImageView issuedImage = new ImageView();
        private final Label issuedHint = new Label("The generated certificate appears here");
        private final Label issuedId = new Label(" ");
        private final Button saveBtn = Ui.button("Save PNG", "btn-primary", Icons.view("save", 16, Icons.WHITE));
        private final Button copyBtn = Ui.button("Copy ID", "btn-secondary", Icons.view("key", 16, Icons.WHITE));
        private final VBox issueForm = Ui.card();
        private File issuedFile;
        private String issuedCertId = "";

        // requests tab
        private final ListView<VerificationRequest> requestList = new ListView<>();
        private final ImageView requestImage = new ImageView();
        private final Label requestHint = new Label("Select a request to see the document");
        private final VBox requestInfo = new VBox(4);
        private final TextField remarksField = Ui.field("remarksField", "Optional note, shown to the company");
        private final Button approveBtn = Ui.button("Confirm genuine", "btn-success", Icons.view("validate", 16, Icons.WHITE));
        private final Button rejectBtn = Ui.button("Mark as fake", "btn-danger", Icons.view("tamper", 16, Icons.WHITE));
        private final Tab requestsTab = new Tab("Verification requests");
        private final VBox requestCard = Ui.card();

        public IssuerPage(AppContext ctx) {
            super(ctx);
            Label title = Ui.title("Issuer portal");
            Label subtitle = Ui.caption("Register the certificates your institution issues and answer companies' verification requests.");

            whoLabel.setId("whoLabel");
            whoLabel.getStyleClass().add("chip");
            whoLabel.getStyleClass().add("chip-idle");
            HBox bar = new HBox(12, Icons.view("key", 20, Icons.GREY), whoLabel);
            bar.setAlignment(Pos.CENTER_LEFT);
            VBox barCard = Ui.card();
            barCard.setPadding(new Insets(12, 18, 12, 18));
            barCard.getChildren().add(bar);

            TabPane tabs = new TabPane();
            tabs.setId("tabs");
            Tab issueTab = new Tab("Issue certificates", buildIssueTab());
            requestsTab.setContent(buildRequestsTab());
            issueTab.setClosable(false);
            requestsTab.setClosable(false);
            tabs.getTabs().addAll(issueTab, requestsTab);

            VBox content = new VBox(16, new VBox(2, title, subtitle), barCard, tabs);
            content.setPadding(new Insets(22, 26, 26, 26));
            setContent(content);
            Issuer mine = null;
            try {
                mine = ctx.service().issuerFor(ctx.account()); // the issuer account decides which institution this is
            } catch (Exception e) {
                // not an issuer account: the page stays locked
            }
            setSignedIn(mine);
        }

        @Override
        public String getTitle() {
            return "Issuer Portal";
        }

        @Override
        public void onShow() {
            if (issuer != null) {
                loadRequests();
            }
        }

        // ------------------------------------------------------------------ sign in
        private void setSignedIn(Issuer who) {
            issuer = who;
            whoLabel.getStyleClass().removeAll("chip-idle", "chip-ok", "chip-bad");
            if (who == null) {
                whoLabel.setText("Not signed in");
                whoLabel.getStyleClass().add("chip-idle");
            } else {
                whoLabel.setText("Signed in: " + who.getName() + " (" + who.getId() + ")  -  user " + ctx.account().getUsername());
                whoLabel.getStyleClass().add("chip-ok");
            }
            issueForm.setDisable(who == null);
            requestCard.setDisable(who == null);
            requestList.setDisable(who == null);
            if (who == null) {
                requestList.getItems().clear();
                requestsTab.setText("Verification requests");
            }
        }

        private boolean needSignIn() {
            if (issuer == null) {
                Ui.alert(Alert.AlertType.INFORMATION, "Sign in first", "Sign in with an issuer account first.");
                return true;
            }
            return false;
        }

        // ------------------------------------------------------------------ issue tab
        private HBox buildIssueTab() {
            courseBox.setEditable(true);
            courseBox.setId("courseBox");
            courseBox.setPromptText("Course / examination");
            courseBox.setMaxWidth(Double.MAX_VALUE);
            courseBox.setItems(FXCollections.observableArrayList(
                    "Secondary School Certificate (SSC)", "Intermediate (12th)", "Diploma", "B.Tech", "M.Tech",
                    "B.Sc", "B.Com", "MBA", "Training course completion"));

            Button generate = Ui.button("Generate and register", "btn-primary", Icons.view("issue", 16, Icons.WHITE));
            generate.setId("generateBtn");
            generate.setOnAction(e -> generate());
            Button register = Ui.button("Register file...", "btn-ghost", Icons.view("folder", 16, Icons.GREY));
            register.setOnAction(e -> registerExisting());
            HBox buttons = new HBox(10, generate, register);

            issueForm.setPrefWidth(430);
            issueForm.setMinWidth(380);
            issueForm.setSpacing(12);
            issueForm.getChildren().addAll(
                    Ui.heading("Issue a new certificate"),
                    Ui.labeled("Holder name *", holderField),
                    Ui.labeled("Course / examination *", courseBox),
                    new HBox(12, grow(Ui.labeled("Grade / marks", gradeField)), grow(Ui.labeled("Year", yearField))),
                    Ui.labeled("Holder photo (optional, printed on the certificate)", photoRow()),
                    buttons,
                    Ui.caption("\"Register a file\" stores the fingerprint of a document you already have, "
                            + "for example a scanned marks memo, using the details typed above."));

            VBox preview = Ui.card();
            issuedImage.setFitWidth(520);
            issuedImage.setFitHeight(380);
            issuedImage.setPreserveRatio(true);
            issuedImage.setSmooth(true);
            issuedImage.setEffect(new javafx.scene.effect.DropShadow(12, 0, 3, javafx.scene.paint.Color.rgb(0, 0, 0, 0.25)));
            issuedHint.getStyleClass().add("preview-hint");
            StackPane box = new StackPane(issuedHint, issuedImage);
            box.getStyleClass().add("preview-box");
            box.setMinHeight(400);
            box.setPadding(new Insets(14));
            issuedId.getStyleClass().add("h2");
            issuedId.setId("issuedId");
            saveBtn.setDisable(true);
            copyBtn.setDisable(true);
            saveBtn.setOnAction(e -> savePng());
            copyBtn.setOnAction(e -> copyId());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox bottom = new HBox(10, issuedId, spacer, copyBtn, saveBtn);
            bottom.setAlignment(Pos.CENTER_LEFT);
            preview.getChildren().addAll(Ui.heading("Certificate preview"), box, bottom);
            HBox.setHgrow(preview, Priority.ALWAYS);

            HBox row = new HBox(18, issueForm, preview);
            row.setPadding(new Insets(6, 0, 0, 0));
            return row;
        }

        private HBox photoRow() {
            Button browse = Ui.button("Choose...", "btn-ghost", Icons.view("folder", 16, Icons.GREY));
            browse.setId("photoBrowse");
            browse.setOnAction(e -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Choose the holder's photo");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.jpg", "*.jpeg", "*.png"));
                File f = chooser.showOpenDialog(ctx.stage());
                if (f != null) {
                    photoField.setText(f.getAbsolutePath());
                }
            });
            HBox.setHgrow(photoField, Priority.ALWAYS);
            return new HBox(8, photoField, browse);
        }

        private static VBox grow(VBox box) {
            HBox.setHgrow(box, Priority.ALWAYS);
            box.setMaxWidth(Double.MAX_VALUE);
            return box;
        }

        private void generate() {
            if (needSignIn()) {
                return;
            }
            String holder = holderField.getText();
            String course = courseBox.getEditor().getText();
            String grade = gradeField.getText();
            String year = yearField.getText();
            String photo = photoField.getText();
            Issuer who = issuer;
            ctx.beginBusy();
            // issueCertificate returns at once; the miner thread calls the handler when the block is mined.
            Async.run(() -> {
                ctx.service().issueCertificate(who, holder, course, grade, year, photo,
                        new ResultHandler<Pair<CertificateData, File>>() {
                            @Override
                            public void onSuccess(Pair<CertificateData, File> result) {
                                Platform.runLater(() -> {
                                    ctx.endBusy();
                                    showIssued(result.getFirst(), result.getSecond());
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                Platform.runLater(() -> {
                                    ctx.endBusy();
                                    Ui.error(error);
                                });
                            }
                        });
                return Boolean.TRUE;
            }, ok -> { }, e -> {
                ctx.endBusy();
                Ui.error(e);
            });
        }

        private void showIssued(CertificateData data, File picture) {
            photoField.clear();
            issuedFile = picture;
            issuedCertId = data.getCertId();
            showPicture(issuedImage, issuedHint, picture);
            issuedId.setText("Certificate ID: " + data.getCertId());
            saveBtn.setDisable(false);
            copyBtn.setDisable(false);
            ctx.refresh();
        }

        private void registerExisting() {
            if (needSignIn()) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose the document to register");
            File file = chooser.showOpenDialog(ctx.stage());
            if (file == null) {
                return;
            }
            String holder = holderField.getText();
            String course = courseBox.getEditor().getText();
            String grade = gradeField.getText();
            String year = yearField.getText();
            Issuer who = issuer;
            ctx.beginBusy();
            Async.run(() -> {
                ctx.service().registerExisting(who, file, holder, course, grade, year, "",
                        new ResultHandler<CertificateData>() {
                            @Override
                            public void onSuccess(CertificateData data) {
                                Platform.runLater(() -> {
                                    ctx.endBusy();
                                    issuedCertId = data.getCertId();
                                    issuedFile = null;
                                    issuedId.setText("Registered. ID: " + data.getCertId());
                                    copyBtn.setDisable(false);
                                    saveBtn.setDisable(true);
                                    if (FileUtil.isImage(file)) {
                                        showPicture(issuedImage, issuedHint, file);
                                    } else {
                                        issuedImage.setImage(null);
                                        issuedHint.setText(file.getName() + " registered");
                                        issuedHint.setVisible(true);
                                    }
                                    ctx.refresh();
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                Platform.runLater(() -> {
                                    ctx.endBusy();
                                    Ui.error(error);
                                });
                            }
                        });
                return Boolean.TRUE;
            }, ok -> { }, e -> {
                ctx.endBusy();
                Ui.error(e);
            });
        }

        private void savePng() {
            if (issuedFile == null) {
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName(issuedCertId + ".png");
            File target = chooser.showSaveDialog(ctx.stage());
            if (target == null) {
                return;
            }
            File source = issuedFile;
            Async.run(() -> {
                FileUtil.copy(source, target);
                return target;
            }, saved -> Ui.alert(Alert.AlertType.INFORMATION, "Saved", "Certificate saved to\n" + saved.getAbsolutePath()),
                    e -> Ui.error(e));
        }

        private void copyId() {
            ClipboardContent content = new ClipboardContent();
            content.putString(issuedCertId);
            Clipboard.getSystemClipboard().setContent(content);
            issuedId.setText("Copied " + issuedCertId + " to the clipboard");
        }

        /** Reads a picture with a FileInputStream and shows it in an ImageView. */
        private void showPicture(ImageView view, Label hint, File file) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                view.setImage(new Image(in));
                hint.setVisible(false);
            } catch (IOException e) {
                view.setImage(null);
                hint.setText("The picture could not be read");
                hint.setVisible(true);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // nothing to do
                    }
                }
            }
        }

        // ------------------------------------------------------------------ requests tab
        private HBox buildRequestsTab() {
            VBox listCard = Ui.card();
            Button refresh = Ui.button("Refresh", "btn-ghost", Icons.view("refresh", 16, Icons.GREY));
            refresh.setOnAction(e -> {
                if (!needSignIn()) {
                    loadRequests();
                }
            });
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox head = new HBox(10, Ui.heading("Requests from companies"), spacer, refresh);
            head.setAlignment(Pos.CENTER_LEFT);

            requestList.setId("requestList");
            requestList.setPlaceholder(new Label("No verification requests yet"));
            requestList.setMinHeight(420);
            requestList.setCellFactory(lv -> new ListCell<VerificationRequest>() {
                @Override
                protected void updateItem(VerificationRequest r, boolean empty) {
                    super.updateItem(r, empty);
                    if (empty || r == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label name = new Label(r.getCandidate());
                    name.getStyleClass().add("cell-title");
                    String no = r.getCertNumber().isEmpty() ? "" : "   |   No. " + r.getCertNumber();
                    Label sub = new Label(r.getCompany() + no + "   |   " + r.getCreated());
                    sub.getStyleClass().add("caption");
                    Label pill = new Label(r.getStatus().getLabel());
                    pill.getStyleClass().add("pill");
                    pill.setMinWidth(Region.USE_PREF_SIZE);
                    pill.getStyleClass().add(r.getStatus() == RequestStatus.PENDING ? "pill-warn"
                            : r.getStatus() == RequestStatus.APPROVED ? "pill-ok" : "pill-bad");
                    Region gap = new Region();
                    HBox.setHgrow(gap, Priority.ALWAYS);
                    VBox texts = new VBox(2, name, sub);
                    texts.setMinWidth(0);
                    HBox box = new HBox(10, texts, gap, pill);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.prefWidthProperty().bind(requestList.widthProperty().subtract(46));
                    box.maxWidthProperty().bind(requestList.widthProperty().subtract(46));
                    setGraphic(box);
                    setText(null);
                }
            });
            requestList.getSelectionModel().selectedItemProperty()
                    .addListener((obs, oldValue, newValue) -> onRequestSelected(newValue));
            VBox.setVgrow(requestList, Priority.ALWAYS);
            listCard.getChildren().addAll(head, requestList);
            listCard.setPrefWidth(470);
            listCard.setMinWidth(400);

            requestImage.setFitWidth(460);
            requestImage.setFitHeight(300);
            requestImage.setPreserveRatio(true);
            requestImage.setSmooth(true);
            requestHint.getStyleClass().add("preview-hint");
            StackPane box = new StackPane(requestHint, requestImage);
            box.getStyleClass().add("preview-box");
            box.setMinHeight(320);
            box.setPadding(new Insets(12));
            approveBtn.setId("approveBtn");
            rejectBtn.setId("rejectBtn");
            approveBtn.setOnAction(e -> decide(true));
            rejectBtn.setOnAction(e -> decide(false));
            requestCard.getChildren().addAll(Ui.heading("Submitted document"), box, requestInfo,
                    Ui.labeled("Remarks", remarksField), new HBox(10, approveBtn, rejectBtn));
            HBox.setHgrow(requestCard, Priority.ALWAYS);

            HBox row = new HBox(18, listCard, requestCard);
            row.setPadding(new Insets(6, 0, 0, 0));
            return row;
        }

        private void loadRequests() {
            if (issuer == null) {
                return;
            }
            Issuer who = issuer;
            Async.run(() -> ctx.service().requestsFor(who), list -> {
                requestList.setItems(FXCollections.observableArrayList(list));
                long pending = list.stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
                requestsTab.setText(pending > 0 ? "Verification requests (" + pending + ")" : "Verification requests");
                requestImage.setImage(null);
                requestHint.setText("Select a request to see the document");
                requestHint.setVisible(true);
                requestInfo.getChildren().clear();
            }, e -> Ui.error(e));
        }

        private void onRequestSelected(VerificationRequest r) {
            requestInfo.getChildren().clear();
            if (r == null) {
                return;
            }
            info("Candidate", r.getCandidate());
            info("Course / year", r.getCourse() + (r.getYear().isEmpty() ? "" : "  |  " + r.getYear()));
            info("Certificate no.", r.getCertNumber());
            info("Requested by", r.getCompany());
            info("File", r.getFileName());
            info("SHA-256", Hasher.shorten(r.getDocHash(), 36));
            File file = ctx.service().requestFile(r);
            if (file != null && FileUtil.isImage(file)) {
                showPicture(requestImage, requestHint, file);
            } else {
                requestImage.setImage(null);
                requestHint.setText(file == null ? "File not found" : "No picture preview for ." + FileUtil.extension(file.getName())
                        + " files. Compare the details with your records.");
                requestHint.setVisible(true);
            }
            boolean pending = r.getStatus() == RequestStatus.PENDING;
            approveBtn.setDisable(!pending);
            rejectBtn.setDisable(!pending);
            remarksField.setDisable(!pending);
        }

        private void info(String key, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            Label k = new Label(key);
            k.getStyleClass().add("caption");
            k.setMinWidth(110);
            Label v = new Label(value);
            v.getStyleClass().add("value");
            v.setWrapText(true);
            requestInfo.getChildren().add(new HBox(8, k, v));
        }

        private void decide(boolean approve) {
            if (needSignIn()) {
                return;
            }
            VerificationRequest r = requestList.getSelectionModel().getSelectedItem();
            if (r == null) {
                Ui.alert(Alert.AlertType.INFORMATION, "No request selected", "Select a request in the list first.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm decision");
            confirm.setHeaderText(null);
            confirm.setContentText(approve
                    ? "Confirm that this document is a genuine certificate issued by your institution?"
                    : "Mark this document as FAKE? Every company that verifies it will see it flagged as forged.");
            confirm.getDialogPane().getStylesheets().add(Ui.css());
            Optional<ButtonType> answer = confirm.showAndWait();
            if (!answer.isPresent() || answer.get() != ButtonType.OK) {
                return;
            }
            String remarks = remarksField.getText();
            Issuer who = issuer;
            ctx.beginBusy();
            Async.run(() -> {
                ctx.service().decide(who, r.getId(), approve, remarks, new ResultHandler<Block>() {
                    @Override
                    public void onSuccess(Block block) {
                        Platform.runLater(() -> {
                            ctx.endBusy();
                            remarksField.clear();
                            loadRequests();
                            ctx.refresh();
                            Ui.alert(Alert.AlertType.INFORMATION, "Decision recorded",
                                    "Saved permanently on the blockchain in block #" + block.getIndex() + ".");
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        Platform.runLater(() -> {
                            ctx.endBusy();
                            Ui.error(error);
                        });
                    }
                });
                return Boolean.TRUE;
            }, ok -> { }, e -> {
                ctx.endBusy();
                Ui.error(e);
            });
        }

        /** Lets the test harness and the menu list the signed-in issuer. */
        public Issuer getIssuer() {
            return issuer;
        }

        public List<VerificationRequest> shownRequests() {
            return requestList.getItems();
        }
    }

    // ===================== KeyAdapterFX =====================
    /**
     * An adapter class (Unit 3): handle() receives every key event and calls one of three empty methods.
     * A screen overrides only the method it needs, e.g. keyPressed(), exactly like java.awt.event.KeyAdapter.
     */
    public static abstract class KeyAdapterFX implements EventHandler<KeyEvent> {
        @Override
        public final void handle(KeyEvent event) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                keyPressed(event);
            } else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
                keyReleased(event);
            } else if (event.getEventType() == KeyEvent.KEY_TYPED) {
                keyTyped(event);
            }
        }

        public void keyPressed(KeyEvent event) {
        }

        public void keyReleased(KeyEvent event) {
        }

        public void keyTyped(KeyEvent event) {
        }
    }

    // ===================== LedgerPage =====================
    /** Shows the whole blockchain as a TreeView, with details of the selected block and the activity log. */
    public static class LedgerPage extends Page {
        private final TreeView<String> tree = new TreeView<>();
        private final Map<TreeItem<String>, Block> blockOf = new HashMap<>();
        private final TextField searchField = Ui.field("searchField", "Search holder, certificate ID or issuer...");
        private final GridPane detail = new GridPane();
        private final Label detailHint = new Label("Select a block in the tree to see its details.");
        private final Label chainChip = new Label("Chain not checked yet");
        private final ListView<String> logView = new ListView<>();
        private int badIndex = -1;
        private int detailRow;

        public LedgerPage(AppContext ctx) {
            super(ctx);
            Label title = Ui.title("Blockchain ledger");
            Label subtitle = Ui.caption("Every block stores the hash of the block before it. Changing any old record breaks the chain and is detected.");

            // left: tree
            VBox treeCard = Ui.card();
            chainChip.getStyleClass().addAll("chip", "chip-idle");
            chainChip.setId("chainChip");
            Button validate = Ui.button("Validate chain", "btn-primary", Icons.view("validate", 16, Icons.WHITE));
            validate.setId("validateBtn");
            validate.setOnAction(e -> validate(true));
            Button export = Ui.button("Export", "btn-ghost", Icons.view("save", 16, Icons.GREY));
            export.setOnAction(e -> export());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label blocksHeading = Ui.heading("Blocks");
            blocksHeading.setMinWidth(Region.USE_PREF_SIZE);
            chainChip.setMinWidth(Region.USE_PREF_SIZE);
            HBox head = new HBox(10, blocksHeading, spacer, chainChip, export, validate);
            head.setAlignment(Pos.CENTER_LEFT);

            searchField.textProperty().addListener((obs, oldText, newText) -> rebuild());
            tree.setId("ledgerTree");
            tree.setShowRoot(true);
            tree.setMinHeight(430);
            VBox.setVgrow(tree, Priority.ALWAYS);
            tree.setCellFactory(tv -> new TreeCell<String>() {
                @Override
                protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty);
                    getStyleClass().removeAll("tree-block", "tree-genesis", "tree-cert", "tree-confirmed",
                            "tree-rejection", "tree-field");
                    if (empty || text == null) {
                        setText(null);
                        return;
                    }
                    setText(text);
                    Block b = blockOf.get(getTreeItem());
                    if (b != null) {
                        getStyleClass().add("tree-block");
                        String type = b.getData().getType();
                        if (b.getIndex() == badIndex || type.equals("REJECTION")) {
                            getStyleClass().add("tree-rejection");
                        } else if (type.equals("CONFIRMED")) {
                            getStyleClass().add("tree-confirmed");
                        } else if (type.equals("GENESIS")) {
                            getStyleClass().add("tree-genesis");
                        } else {
                            getStyleClass().add("tree-cert");
                        }
                    } else if (getTreeItem() != null && blockOf.containsKey(getTreeItem().getParent())) {
                        getStyleClass().add("tree-field");
                    }
                }
            });
            tree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> showDetail(newItem));
            treeCard.getChildren().addAll(head, searchField, tree);
            HBox.setHgrow(treeCard, Priority.ALWAYS);

            // right: details + log
            VBox detailCard = Ui.card();
            detailHint.getStyleClass().add("caption");
            ColumnConstraints keyCol = new ColumnConstraints(96);
            ColumnConstraints valueCol = new ColumnConstraints();
            valueCol.setHgrow(Priority.ALWAYS);
            detail.getColumnConstraints().addAll(keyCol, valueCol);
            detail.setVgap(6);
            detail.add(detailHint, 0, 0, 2, 1);
            detailCard.getChildren().addAll(Ui.heading("Block details"), detail);

            VBox logCard = Ui.card();
            logView.setItems(ctx.logItems());
            logView.setId("logView");
            logView.setPlaceholder(new Label("Nothing has happened yet"));
            logView.setMinHeight(190);
            logView.setCellFactory(lv -> new ListCell<String>() {
                @Override
                protected void updateItem(String line, boolean empty) {
                    super.updateItem(line, empty);
                    setText(empty ? null : line);
                    getStyleClass().remove("log-cell");
                    getStyleClass().add("log-cell");
                }
            });
            logCard.getChildren().addAll(Ui.heading("Activity log (live)"), logView);
            VBox.setVgrow(logView, Priority.ALWAYS);

            VBox right = new VBox(16, detailCard, logCard);
            right.setPrefWidth(430);
            right.setMinWidth(380);
            VBox.setVgrow(logCard, Priority.ALWAYS);

            HBox columns = new HBox(18, treeCard, right);
            VBox content = new VBox(16, new VBox(2, title, subtitle), columns);
            content.setPadding(new Insets(22, 26, 26, 26));
            setContent(content);
            rebuild();
        }

        @Override
        public String getTitle() {
            return "Ledger";
        }

        @Override
        public void onShow() {
            rebuild();
        }

        // ------------------------------------------------------------------ tree
        public void rebuild() {
            String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            List<Block> blocks = ctx.service().ledger();
            blockOf.clear();
            TreeItem<String> root = new TreeItem<>("Blockchain  (" + blocks.size() + " blocks)");
            root.setExpanded(true);
            for (Block b : blocks) {
                BlockData d = b.getData();
                String haystack = (d.getTitle() + " " + d.getIssuerName() + " " + d.getType() + " " + d.getDocHash()).toLowerCase();
                if (!q.isEmpty() && !haystack.contains(q)) {
                    continue;
                }
                String flag = b.getIndex() == badIndex ? "     <-- TAMPERED" : "";
                TreeItem<String> item = new TreeItem<>("#" + b.getIndex() + "    " + d.getTitle() + flag);
                blockOf.put(item, b);
                item.getChildren().add(new TreeItem<>("Type:  " + d.getType()));
                item.getChildren().add(new TreeItem<>("Issuer:  " + d.getIssuerName()));
                item.getChildren().add(new TreeItem<>("Hash:  " + b.getHash()));
                item.getChildren().add(new TreeItem<>("Previous:  " + b.getPreviousHash()));
                root.getChildren().add(item);
            }
            tree.setRoot(root);
        }

        private void showDetail(TreeItem<String> selected) {
            detail.getChildren().clear();
            detailRow = 0;
            Block b = null;
            if (selected != null) {
                b = blockOf.get(selected);
                if (b == null && selected.getParent() != null) {
                    b = blockOf.get(selected.getParent());
                }
            }
            if (b == null) {
                detail.add(detailHint, 0, 0, 2, 1);
                return;
            }
            BlockData d = b.getData();
            row("Block", "#" + b.getIndex());
            row("Type", d.getType());
            row("Summary", d.describe());
            row("Issuer", d.getIssuerName());
            row("Date", d.getDate());
            row("Mined at", new SimpleDateFormat("dd MMM yyyy HH:mm:ss").format(new Date(b.getTimestamp())));
            row("Nonce", String.valueOf(b.getNonce()));
            row("Hash", b.getHash());
            row("Previous", b.getPreviousHash());
            row("Document", d.getDocHash().isEmpty() ? "-" : d.getDocHash());
            row("Integrity", b.hasValidHash() ? "Hash matches the content" : "HASH MISMATCH - block was edited");
        }

        private void row(String key, String value) {
            Label k = new Label(key);
            k.getStyleClass().add("key");
            Label v = new Label(value);
            v.getStyleClass().add("value");
            if (key.equals("Hash") || key.equals("Previous") || key.equals("Document")) {
                v.getStyleClass().add("mono");
            }
            v.setWrapText(true);
            v.setMaxWidth(300);
            detail.add(k, 0, detailRow);
            detail.add(v, 1, detailRow);
            detailRow++;
        }

        // ------------------------------------------------------------------ validate and export
        public void validate(boolean showDialog) {
            Async.run(() -> ctx.service().validateChain(), report -> {
                ctx.reportChain(report);
                if (showDialog) {
                    Ui.alert(report.isValid() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                            report.isValid() ? "Chain is valid" : "Chain is BROKEN", report.getMessage());
                }
            }, e -> Ui.error(e));
        }

        /** Called by the main window (background watchdog, menu, toolbar) with the latest chain report. */
        public void showChainReport(ValidationReport report) {
            chainChip.getStyleClass().removeAll("chip-idle", "chip-ok", "chip-bad");
            if (report.isValid()) {
                chainChip.setText("Chain valid");
                chainChip.getStyleClass().add("chip-ok");
            } else {
                chainChip.setText("Chain BROKEN at block #" + report.getBadBlockIndex());
                chainChip.getStyleClass().add("chip-bad");
            }
            if (badIndex != report.getBadBlockIndex()) {
                badIndex = report.getBadBlockIndex();
                rebuild();
            }
        }

        public void export() {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export the ledger");
            chooser.setInitialFileName("certchain-ledger.txt");
            File target = chooser.showSaveDialog(ctx.stage());
            if (target == null) {
                return;
            }
            Async.run(() -> {
                ctx.service().exportLedger(target);
                return target;
            }, saved -> Ui.alert(Alert.AlertType.INFORMATION, "Ledger exported", "Saved to\n" + saved.getAbsolutePath()),
                    e -> Ui.error(e));
        }
    }

    // ===================== LoginView =====================
    /**
     * The first screen. The user chooses a portal (Verifier, Issuer or Authority) and signs in with a
     * username and password. Companies can create their own verifier account here.
     */
    public static class LoginView extends HBox {
        private final CertChainService service;
        private final Consumer<Account> onSuccess;

        private final ToggleGroup roles = new ToggleGroup();
        private final TextField companyField = Ui.field("companyField", "e.g. Infosys Ltd");
        private final TextField userField = Ui.field("userField", "Username");
        private final PasswordField passField = new PasswordField();
        private final PasswordField confirmField = new PasswordField();
        private final TextField emailField = Ui.field("emailField", "Company e-mail, e.g. hr@company.com");
        private final TextField phoneField = Ui.field("phoneField", "Mobile number (optional)");
        private final TextField otpField = Ui.field("otpField", "6-digit code");
        private final Button sendOtpBtn = Ui.button("Send OTP", "btn-secondary", null);
        private final Hyperlink forgotLink = new Hyperlink("Forgot password?");
        private final Label formTitle = Ui.title("Sign in");
        private final Label roleHint = Ui.caption("");
        private final Label error = new Label();
        private final Hyperlink switchLink = new Hyperlink();
        private final Button submit = Ui.button("Sign in", "btn-primary", null);
        private final VBox companyRow;
        private final VBox confirmRow;
        private final VBox emailRow;
        private final VBox phoneRow;
        private final VBox otpRow;
        private final VBox card = new VBox(14);
        private boolean registering;

        public LoginView(CertChainService service, Consumer<Account> onSuccess) {
            this.service = service;
            this.onSuccess = onSuccess;
            passField.setId("passField");
            passField.setPromptText("Password");
            confirmField.setId("confirmField");
            confirmField.setPromptText("Type the password again");

            getChildren().addAll(buildBrandPanel(), buildFormPanel());
            companyRow = (VBox) card.lookup("#companyRow");
            confirmRow = (VBox) card.lookup("#confirmRow");
            emailRow = (VBox) card.lookup("#emailRow");
            phoneRow = (VBox) card.lookup("#phoneRow");
            otpRow = (VBox) card.lookup("#otpRow");
            updateMode();
            Anim.fadeSlideIn(card);
        }

        // ------------------------------------------------------------------ left: brand panel
        private VBox buildBrandPanel() {
            ImageView logo = Icons.view("verify", 64, Icons.WHITE);
            Anim.breathe(logo, Color.web("#5FD3C0"));
            Anim.float_(logo);
            Label title = new Label("CertChain");
            title.getStyleClass().add("login-brand-title");
            Label tagline = new Label("Fake certificates stop here.\nEvery certificate is checked against\nthe institution that issued it.");
            tagline.getStyleClass().add("login-tagline");

            List<VBox> points = new ArrayList<>();
            points.add(point("Authority", "Approves genuine institutions and creates their accounts."));
            points.add(point("Issuer", "Boards and universities register certificates and answer companies."));
            points.add(point("Verifier", "Companies check any document against the blockchain in seconds."));

            VBox box = new VBox(18, logo, title, tagline);
            box.getChildren().add(new Region());
            box.getChildren().addAll(points);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(40, 50, 40, 56));
            box.getStyleClass().add("login-brand");
            box.setPrefWidth(480);
            box.setMinWidth(420);
            Anim.cascade(points);
            return box;
        }

        private VBox point(String head, String text) {
            Label h = new Label(head);
            h.getStyleClass().add("login-point-title");
            Label t = new Label(text);
            t.getStyleClass().add("login-point");
            t.setWrapText(true);
            return new VBox(2, h, t);
        }

        // ------------------------------------------------------------------ right: form
        private StackPane buildFormPanel() {
            HBox tabs = new HBox(8);
            for (Role role : new Role[]{Role.VERIFIER, Role.ISSUER, Role.AUTHORITY}) {
                ToggleButton tab = new ToggleButton(role.getLabel());
                tab.setUserData(role);
                tab.setToggleGroup(roles);
                tab.getStyleClass().add("role-tab");
                tab.setId("tab-" + role.name().toLowerCase());
                tabs.getChildren().add(tab);
            }
            roles.getToggles().get(0).setSelected(true);
            // one tab must always stay selected
            roles.selectedToggleProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab == null) {
                    oldTab.setSelected(true);
                } else {
                    registering = false;
                    updateMode();
                }
            });

            VBox companyBox = Ui.labeled("Company name", companyField);
            companyBox.setId("companyRow");
            VBox confirmBox = Ui.labeled("Confirm password", confirmField);
            confirmBox.setId("confirmRow");
            VBox emailBox = Ui.labeled("Company e-mail (we send an OTP here)", emailField);
            emailBox.setId("emailRow");
            VBox phoneBox = Ui.labeled("Mobile number", phoneField);
            phoneBox.setId("phoneRow");
            sendOtpBtn.setId("sendOtpBtn");
            sendOtpBtn.setOnAction(e -> sendSignupOtp());
            HBox otpLine = new HBox(10, otpField, sendOtpBtn);
            HBox.setHgrow(otpField, Priority.ALWAYS);
            VBox otpBox = Ui.labeled("Verification code (OTP)", otpLine);
            otpBox.setId("otpRow");
            forgotLink.setId("forgotLink");
            forgotLink.setOnAction(e -> forgotPassword());

            error.getStyleClass().add("login-error");
            error.setWrapText(true);
            submit.setId("loginBtn");
            submit.setMaxWidth(Double.MAX_VALUE);
            submit.setOnAction(e -> submit());
            userField.setOnAction(e -> submit());
            passField.setOnAction(e -> submit());
            confirmField.setOnAction(e -> submit());
            switchLink.setId("switchLink");
            switchLink.setOnAction(e -> {
                registering = !registering;
                updateMode();
            });

            card.getStyleClass().add("login-card");
            card.setMaxWidth(440);
            card.setPrefWidth(440);
            card.setMaxHeight(Region.USE_PREF_SIZE);
            card.getChildren().addAll(formTitle, tabs, roleHint, companyBox, Ui.labeled("Username", userField),
                    Ui.labeled("Password", passField), confirmBox, emailBox, phoneBox, otpBox, error, submit,
                    forgotLink, switchLink);

            StackPane holder = new StackPane(card);
            holder.getStyleClass().add("login-bg");
            holder.setPadding(new Insets(30));
            HBox.setHgrow(holder, Priority.ALWAYS);
            return holder;
        }

        private Role selectedRole() {
            Toggle t = roles.getSelectedToggle();
            return (Role) t.getUserData();
        }

        /** Re-labels the form for the chosen portal and for sign-in or registration. */
        private void updateMode() {
            Role role = selectedRole();
            boolean canRegister = role == Role.VERIFIER;
            if (!canRegister) {
                registering = false;
            }
            formTitle.setText(registering ? "Create a verifier account" : role.getLabel() + " portal");
            submit.setText(registering ? "Create account" : "Sign in");
            switchLink.setText(registering ? "I already have an account - sign in" : "New company? Create a verifier account");
            switchLink.setVisible(canRegister);
            switchLink.setManaged(canRegister);
            show(companyRow, registering);
            show(confirmRow, registering);
            show(emailRow, registering);
            show(phoneRow, registering);
            show(otpRow, registering);
            forgotLink.setVisible(!registering);
            forgotLink.setManaged(!registering);
            switch (role) {
                case VERIFIER:
                    roleHint.setText("For companies and HR teams that check candidates' certificates.");
                    break;
                case ISSUER:
                    roleHint.setText("For boards, universities and institutes. Your account is created by the Authority.");
                    break;
                default:
                    roleHint.setText("The regulator. Username: " + CertChainService.AUTHORITY_USER
                            + ". The password is the authority key (admin123 unless CERT_ADMIN_KEY is set).");
            }
            error.setText("");
        }

        private static void show(VBox row, boolean visible) {
            if (row != null) {
                row.setVisible(visible);
                row.setManaged(visible);
            }
        }

        // ------------------------------------------------------------------ actions
        private void submit() {
            error.setText("");
            String user = userField.getText();
            String pass = passField.getText();
            if (registering) {
                if (!pass.equals(confirmField.getText())) {
                    fail("The two passwords do not match");
                    return;
                }
                String company = companyField.getText();
                String mail = emailField.getText();
                String mobile = phoneField.getText();
                String otp = otpField.getText();
                Async.run(() -> service.registerVerifier(company, user, pass, mail, mobile, otp), this::success,
                        e -> fail(Ui.message(e)));
            } else {
                Role role = selectedRole();
                Async.run(() -> service.login(user, pass, role), this::success, e -> fail(Ui.message(e)));
            }
        }

        private void success(Account account) {
            passField.clear();
            confirmField.clear();
            onSuccess.accept(account);
        }

        private void notice(String message) {
            error.setText(message);
            error.setStyle("-fx-text-fill: #0E7C6B;");
        }

        private void sendSignupOtp() {
            error.setText("");
            String mail = emailField.getText();
            String mobile = phoneField.getText();
            sendOtpBtn.setDisable(true);
            Async.run(() -> service.requestSignupOtp(mail, mobile), demo -> {
                sendOtpBtn.setDisable(false);
                notice(demo.isEmpty() ? "OTP sent to " + mail.trim() + ". It is valid for 5 minutes."
                        : "DEMO MODE (no mail server set up): your OTP is " + demo);
            }, e -> {
                sendOtpBtn.setDisable(false);
                fail(Ui.message(e));
            });
        }

        /** Forgotten password: the user gets an OTP by e-mail and chooses a new password. */
        private void forgotPassword() {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Forgot password");
            dialog.setHeaderText("We will e-mail a one-time code to the address registered for your account.");
            dialog.getDialogPane().getStylesheets().add(Ui.css());
            TextField user = Ui.field("resetUserField", "Your username");
            user.setText(userField.getText());
            TextField otp = Ui.field("resetOtpField", "6-digit code");
            Button send = Ui.button("Send OTP", "btn-secondary", null);
            send.setId("resetSendBtn");
            PasswordField np = new PasswordField();
            np.setId("resetNewPass");
            np.setPromptText("New password (at least 6 characters)");
            PasswordField cp = new PasswordField();
            cp.setId("resetConfirmPass");
            cp.setPromptText("Repeat new password");
            Label msg = new Label();
            msg.setWrapText(true);
            msg.setId("resetMessage");
            send.setOnAction(ev -> {
                send.setDisable(true);
                Async.run(() -> service.requestResetOtp(user.getText()), r -> {
                    send.setDisable(false);
                    msg.setStyle("-fx-text-fill: #0E7C6B;");
                    msg.setText(r[1].isEmpty() ? "OTP sent to " + r[0] : "DEMO MODE (no mail server set up): your OTP is " + r[1]);
                }, ex -> {
                    send.setDisable(false);
                    msg.setStyle("-fx-text-fill: #B3261E;");
                    msg.setText(Ui.message(ex));
                });
            });
            HBox otpLine = new HBox(10, otp, send);
            HBox.setHgrow(otp, Priority.ALWAYS);
            VBox box = new VBox(10, Ui.labeled("Username", user), Ui.labeled("Verification code", otpLine),
                    Ui.labeled("New password", np), cp, msg);
            box.setPadding(new Insets(10));
            box.setPrefWidth(400);
            dialog.getDialogPane().setContent(box);
            ButtonType save = new ButtonType("Set new password", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
            dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                ev.consume();
                try {
                    if (!np.getText().equals(cp.getText())) {
                        throw new ValidationException("The two passwords are not the same");
                    }
                    Account a = service.resetPassword(user.getText(), otp.getText(), np.getText());
                    dialog.close();
                    userField.setText(a.getUsername());
                    passField.clear();
                    notice("Password changed. Sign in with your new password.");
                } catch (Exception ex) {
                    msg.setStyle("-fx-text-fill: #B3261E;");
                    msg.setText(Ui.message(ex));
                }
            });
            dialog.showAndWait();
        }

        private void fail(String message) {
            error.setStyle("");
            error.setText(message);
            Anim.shake(card);
        }

        /** Used by the test harness. */
        public void fillAndSubmit(Role role, String user, String pass) {
            for (Toggle t : roles.getToggles()) {
                if (t.getUserData() == role) {
                    t.setSelected(true);
                }
            }
            userField.setText(user);
            passField.setText(pass);
            submit();
        }
    }

    // ===================== Page =====================
    /**
     * Abstract class (Unit 1): every screen of the application extends Page and overrides getTitle().
     * The main window calls onShow() through the Page type, whichever screen it is (Unit 2: runtime polymorphism).
     */
    public static abstract class Page extends StackPane {
        protected final AppContext ctx;

        protected Page(AppContext ctx) {
            this.ctx = ctx;
            getStyleClass().add("page");
        }

        public abstract String getTitle();

        /** Called every time the screen becomes visible. */
        public void onShow() {
        }

        /** Puts the screen content into a scroll pane, so small windows still work. */
        protected void setContent(Node content) {
            ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("page-scroll");
            getChildren().setAll(scroll);
        }
    }

    // ===================== Ui =====================
    /** Small factory methods so every screen looks the same. */
    public static final class Ui {
        private static String css = "";

        private Ui() {
        }

        public static void setCss(String url) {
            css = url == null ? "" : url;
        }

        public static String css() {
            return css;
        }

        /** White rounded card with a soft shadow (Unit 5: effects). */
        public static VBox card() {
            VBox box = new VBox(10);
            box.getStyleClass().add("card");
            box.setPadding(new Insets(18));
            box.setEffect(new DropShadow(14, 0, 3, Color.rgb(20, 38, 61, 0.13)));
            return box;
        }

        public static Label title(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("h1");
            return l;
        }

        public static Label heading(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("h2");
            return l;
        }

        public static Label caption(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("caption");
            l.setWrapText(true);
            return l;
        }

        public static TextField field(String id, String prompt) {
            TextField f = new TextField();
            f.setId(id);
            f.setPromptText(prompt);
            return f;
        }

        /** A caption above an input control. */
        public static VBox labeled(String label, javafx.scene.Node control) {
            VBox box = new VBox(4, caption(label), control);
            return box;
        }

        /** Button with a colour style (btn-primary, btn-secondary, btn-success, btn-danger, btn-warning). */
        public static Button button(String text, String style, ImageView icon) {
            Button b = new Button(text);
            if (icon != null) {
                b.setGraphic(icon);
            }
            b.getStyleClass().addAll("btn", style);
            b.setCursor(Cursor.HAND);
            b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            // Unit 5: transforms. The button grows 3 % while the mouse is over it.
            b.setOnMouseEntered(e -> {
                b.setScaleX(1.03);
                b.setScaleY(1.03);
            });
            b.setOnMouseExited(e -> {
                b.setScaleX(1.0);
                b.setScaleY(1.0);
            });
            return b;
        }

        public static void alert(Alert.AlertType type, String title, String message) {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(message);
            if (!css.isEmpty()) {
                a.getDialogPane().getStylesheets().add(css);
            }
            a.showAndWait();
        }

        public static void error(Exception e) {
            alert(Alert.AlertType.ERROR, "Something went wrong", message(e));
        }

        public static String message(Exception e) {
            if (e instanceof CertChainException) {
                return e.getMessage();
            }
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ===================== VerifyPage =====================
    /** The screen companies use: verify a document or a certificate ID, and ask an issuer when nothing is found. */
    public static class VerifyPage extends Page {
        private final RadioButton rbId = new RadioButton("Certificate / Request ID");
        private final RadioButton rbFile = new RadioButton("Upload a document");
        private final TextField idField = Ui.field("idField", "e.g. CV-1A2B3C4D5E or REQ-1A2B3C4D");
        private final StackPane dropZone = new StackPane();
        private HBox tilesRow;
        private boolean firstShow = true;
        private final Label fileInfo = new Label("No file chosen");
        private final ImageView previewImage = new ImageView();
        private final Label previewHint = new Label("The document you check appears here");
        private final VBox previewInfo = new VBox(4);
        private final Label badge = new Label("AWAITING CHECK");
        private final Label stamp = new Label();
        private final GridPane details = new GridPane();
        private final Button requestBtn = Ui.button("Request verification from issuer", "btn-warning", Icons.view("issue", 16, Icons.WHITE));
        private final Label blocksValue = new Label("0");
        private final Label certsValue = new Label("0");
        private final Label issuersValue = new Label("0");
        private final Label pendingValue = new Label("0");
        private File chosenFile;
        private int detailRow;

        public VerifyPage(AppContext ctx) {
            super(ctx);

            Label title = Ui.title("Verify a certificate");
            Label subtitle = Ui.caption("Check a candidate's document against the blockchain. Real certificates are registered by their issuing institution.");

            tilesRow = new HBox(14,
                    tile("Blocks on chain", blocksValue, "stat-accent-blue"),
                    tile("Certificates registered", certsValue, "stat-accent-green"),
                    tile("Approved issuers", issuersValue, "stat-accent-gold"),
                    tile("Pending requests", pendingValue, "stat-accent-amber"));

            VBox left = new VBox(16, buildInputCard(), buildResultCard());
            left.setPrefWidth(500);
            left.setMinWidth(440);
            VBox right = buildPreviewCard();
            HBox.setHgrow(right, Priority.ALWAYS);
            HBox columns = new HBox(18, left, right);

            VBox content = new VBox(16, new VBox(2, title, subtitle), tilesRow, columns);
            content.setPadding(new Insets(22, 26, 26, 26));
            setContent(content);
            updateMode();
            resetResult();
        }

        @Override
        public String getTitle() {
            return "Verify";
        }

        @Override
        public void onShow() {
            refreshStats();
            if (firstShow) {
                firstShow = false;
                Anim.cascade(tilesRow.getChildren());
            }
        }

        // ------------------------------------------------------------------ building blocks
        private VBox tile(String caption, Label value, String accent) {
            value.getStyleClass().add("stat-value");
            Label cap = new Label(caption);
            cap.getStyleClass().add("stat-caption");
            VBox box = new VBox(2, value, cap);
            box.getStyleClass().addAll("stat-tile", accent);
            box.setEffect(new DropShadow(10, 0, 2, Color.rgb(20, 38, 61, 0.10)));
            HBox.setHgrow(box, Priority.ALWAYS);
            box.setMaxWidth(Double.MAX_VALUE);
            return box;
        }

        private VBox buildInputCard() {
            VBox card = Ui.card();
            card.getChildren().add(Ui.heading("What do you want to check?"));

            ToggleGroup mode = new ToggleGroup();
            rbId.setToggleGroup(mode);
            rbFile.setToggleGroup(mode);
            rbId.setSelected(true);
            rbId.setId("rbId");
            rbFile.setId("rbFile");
            mode.selectedToggleProperty().addListener((obs, oldValue, newValue) -> updateMode());
            card.getChildren().add(new HBox(22, rbId, rbFile));

            // Typing: pressing Enter verifies. KeyAdapterFX is our adapter class (Unit 3).
            idField.addEventHandler(KeyEvent.ANY, new KeyAdapterFX() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getCode() == KeyCode.ENTER) {
                        verify();
                    }
                }
            });
            card.getChildren().add(Ui.labeled("Certificate ID or Request ID", idField));

            // Drop zone: drag a file from the desktop onto it, or click to browse.
            Label dropTitle = new Label("Drop a document here, or click to browse");
            dropTitle.getStyleClass().add("drop-title");
            fileInfo.getStyleClass().add("caption");
            VBox dropContent = new VBox(4, Icons.view("folder", 30, Icons.BLUE), dropTitle, fileInfo);
            dropContent.setAlignment(Pos.CENTER);
            dropZone.getChildren().add(dropContent);
            Anim.breathe(dropZone, javafx.scene.paint.Color.web("#0B6B5F55"));
            dropZone.getStyleClass().add("drop-zone");
            dropZone.setId("dropZone");
            dropZone.setPrefHeight(118);
            dropZone.setOnMouseClicked((MouseEvent e) -> browse());
            dropZone.setOnDragOver(e -> {
                if (e.getDragboard().hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
                e.consume();
            });
            dropZone.setOnDragEntered(e -> {
                dropZone.getStyleClass().add("drop-active");
                dropZone.setScaleX(1.02);
                dropZone.setScaleY(1.02);
            });
            dropZone.setOnDragExited(e -> {
                dropZone.getStyleClass().remove("drop-active");
                dropZone.setScaleX(1.0);
                dropZone.setScaleY(1.0);
            });
            dropZone.setOnDragDropped(e -> {
                List<File> files = e.getDragboard().getFiles();
                if (!files.isEmpty()) {
                    chooseFile(files.get(0));
                }
                e.setDropCompleted(!files.isEmpty());
                e.consume();
            });
            card.getChildren().add(dropZone);

            Button verifyBtn = Ui.button("Verify now", "btn-primary", Icons.view("verify", 16, Icons.WHITE));
            verifyBtn.setId("verifyBtn");
            verifyBtn.setDefaultButton(true);
            verifyBtn.setOnAction(e -> verify());
            card.getChildren().add(verifyBtn);
            return card;
        }

        private VBox buildResultCard() {
            VBox card = Ui.card();
            badge.getStyleClass().addAll("badge", "badge-idle");
            badge.setId("badge");
            HBox head = new HBox(12, Ui.heading("Result"), badge);
            head.setAlignment(Pos.CENTER_LEFT);

            ColumnConstraints keyCol = new ColumnConstraints(128);
            ColumnConstraints valueCol = new ColumnConstraints();
            valueCol.setHgrow(Priority.ALWAYS);
            details.getColumnConstraints().addAll(keyCol, valueCol);
            details.setVgap(7);
            details.setId("details");

            stamp.getStyleClass().add("stamp");
            stamp.setRotate(-12); // Unit 5: transforms - the stamp is rotated like a rubber stamp
            stamp.setMouseTransparent(true);
            stamp.setVisible(false);
            stamp.setId("stamp");

            requestBtn.setId("requestBtn");
            requestBtn.setOnAction(e -> openRequestDialog());
            requestBtn.setVisible(false);
            requestBtn.setManaged(false);

            stamp.managedProperty().bind(stamp.visibleProperty());
            HBox stampRow = new HBox(stamp);
            stampRow.setAlignment(Pos.CENTER_RIGHT);
            stampRow.setPadding(new Insets(6, 10, 6, 0));
            VBox body = new VBox(12, head, details, stampRow, requestBtn);
            card.getChildren().add(body);
            return card;
        }

        private VBox buildPreviewCard() {
            VBox card = Ui.card();
            previewImage.setFitWidth(470);
            previewImage.setFitHeight(400);
            previewImage.setPreserveRatio(true);
            previewImage.setSmooth(true);
            previewImage.setEffect(new DropShadow(12, 0, 3, Color.rgb(0, 0, 0, 0.25)));
            previewHint.getStyleClass().add("preview-hint");
            StackPane box = new StackPane(previewHint, previewImage);
            box.getStyleClass().add("preview-box");
            box.setMinHeight(430);
            box.setPadding(new Insets(14));
            box.setEffect(new InnerShadow(6, 0, 1, Color.rgb(0, 0, 0, 0.10)));
            previewInfo.setPadding(new Insets(4, 2, 0, 2));
            card.getChildren().addAll(Ui.heading("Document preview"), box, previewInfo);
            return card;
        }

        // ------------------------------------------------------------------ statistics
        public void refreshStats() {
            count(blocksValue, ctx.service().blockCount());
            count(certsValue, ctx.service().certificateCount());
            count(issuersValue, ctx.service().issuerCount());
            count(pendingValue, ctx.service().pendingCount());
        }

        /** Animates a statistic from its old number to the new one. */
        private void count(Label label, int target) {
            int old = 0;
            try {
                old = Integer.parseInt(label.getText().trim());
            } catch (NumberFormatException e) {
                old = 0; // first time: count up from zero
            }
            Anim.countUp(label, old, target);
        }

        // ------------------------------------------------------------------ choosing a file
        private void updateMode() {
            boolean byId = rbId.isSelected();
            idField.setPromptText(byId ? "e.g. CV-1A2B3C4D5E or REQ-1A2B3C4D"
                    : "Optional: ID of the original certificate (also read from the file name)");
            dropZone.setOpacity(byId ? 0.55 : 1.0);
        }

        /** Also called from the File menu. */
        public void browse() {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose the document to verify");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Documents and images", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File file = chooser.showOpenDialog(ctx.stage());
            if (file != null) {
                chooseFile(file);
            }
        }

        public void chooseFile(File file) {
            chosenFile = file;
            rbFile.setSelected(true);
            fileInfo.setText(file.getName() + "   (" + FileUtil.humanSize(file.length()) + ")");
            showFilePreview(file);
        }

        private void showFilePreview(File file) {
            previewInfo.getChildren().clear();
            if (FileUtil.isImage(file)) {
                showImage(file);
            } else {
                previewImage.setImage(null);
                previewHint.setText("No picture preview for ." + FileUtil.extension(file.getName()) + " files");
                previewHint.setVisible(true);
            }
            addInfoLine("File", file.getName());
            addInfoLine("Size", FileUtil.humanSize(file.length()));
            Label hashLine = addInfoLine("SHA-256", "calculating...");
            Async.run(() -> Hasher.sha256(file), hash -> hashLine.setText(Hasher.shorten(hash, 40)),
                    e -> hashLine.setText("could not read the file"));
        }

        private Label addInfoLine(String key, String value) {
            Label k = new Label(key);
            k.getStyleClass().addAll("caption");
            k.setMinWidth(60);
            Label v = new Label(value);
            v.getStyleClass().addAll("mono");
            v.setWrapText(true);
            previewInfo.getChildren().add(new HBox(10, k, v));
            return v;
        }

        /** Reads the image with a byte stream (FileInputStream) and shows it. */
        private void showImage(File file) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                previewImage.setImage(new Image(in));
                previewHint.setVisible(false);
            } catch (IOException e) {
                previewImage.setImage(null);
                previewHint.setText("The picture could not be read");
                previewHint.setVisible(true);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // nothing to do
                    }
                }
            }
        }

        // ------------------------------------------------------------------ verifying
        public void verify() {
            if (rbId.isSelected()) {
                String id = idField.getText().trim();
                if (id.isEmpty()) {
                    showMessage("Type a certificate ID or a request ID first.");
                    return;
                }
                if (id.toUpperCase().startsWith("REQ-")) {
                    Async.run(() -> ctx.service().getRequest(id), this::showRequest, this::showError);
                } else {
                    Async.run(() -> ctx.service().verify(id), this::showResult, this::showError);
                }
            } else {
                if (chosenFile == null) {
                    showMessage("Choose or drop a document first.");
                    return;
                }
                File file = chosenFile;
                String claimed = idField.getText().trim();
                Async.run(() -> ctx.service().verify(file, claimed), this::showResult, this::showError);
            }
        }

        private void resetResult() {
            setBadge("AWAITING CHECK", "badge-idle", null);
            details.getChildren().clear();
            detailRow = 0;
            requestBtn.setVisible(false);
            requestBtn.setManaged(false);
            addRow("Tip", "Press Enter in the ID box, or drop a document on the dashed area.");
        }

        private void showMessage(String message) {
            resetResult();
            details.getChildren().clear();
            detailRow = 0;
            addRow("Note", message);
        }

        private void setBadge(String text, String style, String stampStyle) {
            badge.setText(text);
            badge.getStyleClass().removeAll("badge-idle", "badge-ok", "badge-bad", "badge-warn");
            badge.getStyleClass().add(style);
            Color glow = style.equals("badge-ok") ? Color.web("#0B6B5F") : style.equals("badge-bad")
                    ? Color.web("#dc2626") : style.equals("badge-warn") ? Color.web("#d97706") : Color.web("#9ca3af");
            badge.setEffect(new DropShadow(12, 0, 3, glow.deriveColor(0, 1, 1, 0.55)));
            stamp.getStyleClass().removeAll("stamp-ok", "stamp-bad", "stamp-warn");
            if (stampStyle == null) {
                stamp.setVisible(false);
            } else {
                stamp.setText(text);
                stamp.getStyleClass().add(stampStyle);
                stamp.setVisible(true);
                Anim.pop(badge);
                Anim.slam(stamp);
                Anim.fadeSlideIn(details);
                if (style.equals("badge-bad")) {
                    Anim.shake(badge);
                }
            }
        }

        private void addRow(String key, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            Label k = new Label(key);
            k.getStyleClass().add("key");
            Label v = new Label(value);
            v.getStyleClass().add("value");
            v.setWrapText(true);
            v.setMaxWidth(300);
            details.add(k, 0, detailRow);
            details.add(v, 1, detailRow);
            detailRow++;
        }

        private void startRows() {
            details.getChildren().clear();
            detailRow = 0;
            requestBtn.setVisible(false);
            requestBtn.setManaged(false);
        }

        private void showResult(VerificationResult r) {
            startRows();
            BlockData data = r.getData();
            Verdict verdict = r.getVerdict();
            if (verdict == Verdict.AUTHENTIC) {
                setBadge("AUTHENTIC", "badge-ok", "stamp-ok");
                if (data instanceof CertificateData) {
                    CertificateData c = (CertificateData) data;
                    addRow("Certificate ID", c.getCertId());
                    addRow("Holder", c.getHolder());
                    addRow("Course", c.getCourse());
                    addRow("Issued by", c.getIssuerName());
                    addRow("Certificate no.", c.getCertNumber());
                    addRow("Grade / marks", c.getGrade());
                    addRow("Year", c.getYear());
                    addRow("Issued on", c.getDate());
                    if (c instanceof IssuerConfirmedCertificate) {
                        addRow("Confirmed", "By the issuer, after a company asked");
                        addRow("Issuer remarks", ((IssuerConfirmedCertificate) c).getRemarks());
                    }
                    File picture = ctx.service().certificateFile(c.getCertId());
                    if (picture != null && !rbFile.isSelected()) {
                        previewInfo.getChildren().clear();
                        showImage(picture);
                        addInfoLine("File", picture.getName());
                    }
                }
                addRow("Block number", String.valueOf(r.getBlockIndex()));
                addRow("Block hash", Hasher.shorten(r.getBlockHash(), 26));
                if (!r.getSignatureState().isEmpty()) {
                    String st = r.getSignatureState();
                    addRow("Digital signature", ("VALID".equals(st) ? "VALID - " : "UNSIGNED".equals(st) ? "Not signed - " : "INVALID - ")
                            + r.getSignatureInfo());
                }
                if (r.getFileIntact() != null) {
                    addRow("Stored file", r.getFileIntact() ? "Unmodified" : "MODIFIED - does not match the record");
                }
            } else if (verdict == Verdict.FORGED) {
                setBadge("FORGED", "badge-bad", "stamp-bad");
                addRow("Verdict", r.getMessage());
                if (data instanceof RejectionData) {
                    RejectionData d = (RejectionData) data;
                    addRow("Issuer", d.getIssuerName());
                    addRow("Issuer remarks", d.getRemarks());
                    addRow("Decided on", d.getDate());
                }
            } else if (verdict == Verdict.PENDING) {
                setBadge("PENDING REVIEW", "badge-warn", "stamp-warn");
                addRow("Status", r.getMessage());
                addRow("Request ID", r.getRequestId());
            } else if (verdict == Verdict.MODIFIED) {
                setBadge("MODIFIED", "badge-bad", "stamp-bad");
                addRow("Verdict", r.getMessage());
                addRow("Certificate", r.getCertId());
                if (r.getData() instanceof CertificateData) {
                    addRow("Holder", ((CertificateData) r.getData()).getHolder());
                }
                addRow("Uploaded file hash", r.getDocHash());
                addRow("Modified from hash", r.getOriginalHash());
                addRow("Conclusion", "The uploaded file (hash " + Hasher.shorten(r.getDocHash(), 16)
                        + ") was modified from the registered original (hash " + Hasher.shorten(r.getOriginalHash(), 16) + ").");
                int n = 1;
                for (String note : r.getChangeNotes()) {
                    addRow(n == 1 ? "What changed" : "", note);
                    n++;
                }
                addRow("Block number", String.valueOf(r.getBlockIndex()));
                if (r.getHighlightFile() != null) {
                    previewInfo.getChildren().clear();
                    showImage(r.getHighlightFile());
                    addInfoLine("Preview", "uploaded file, changed area boxed in red");
                }
            } else if (verdict == Verdict.NOT_VERIFIED) {
                setBadge("NOT VERIFIED", "badge-bad", "stamp-bad");
                addRow("Reason", r.getMessage());
                addRow("Document hash", Hasher.shorten(r.getDocHash(), 26));
                if (rbFile.isSelected() && chosenFile != null) {
                    requestBtn.setVisible(true);
                    requestBtn.setManaged(true);
                }
            } else {
                setBadge("ERROR", "badge-warn", "stamp-warn");
                addRow("Details", r.getMessage());
            }
            ctx.refresh();
        }

        private void showRequest(VerificationRequest r) {
            startRows();
            RequestStatus s = r.getStatus();
            if (s == RequestStatus.PENDING) {
                setBadge("PENDING REVIEW", "badge-warn", "stamp-warn");
            } else if (s == RequestStatus.APPROVED) {
                setBadge("CONFIRMED GENUINE", "badge-ok", "stamp-ok");
            } else {
                setBadge("REJECTED - FAKE", "badge-bad", "stamp-bad");
            }
            addRow("Request ID", r.getId());
            addRow("Issuer", ctx.service().issuerNameOf(r.getIssuerId()));
            addRow("Candidate", r.getCandidate());
            addRow("Certificate no.", r.getCertNumber());
            addRow("Requested by", r.getCompany());
            addRow("Submitted", r.getCreated());
            addRow("Decided on", r.getDecidedOn());
            addRow("Issuer remarks", r.getRemarks());
            addRow("Certificate ID", r.getCertId());
            if (s == RequestStatus.PENDING) {
                addRow("Status", "Waiting for the issuing institution to review the document.");
            }
        }

        private void showError(Exception e) {
            startRows();
            setBadge(e instanceof CertChainException && e.getMessage() != null && e.getMessage().startsWith("No request")
                    ? "NOT FOUND" : "ERROR", "badge-warn", null);
            addRow("Details", Ui.message(e));
        }

        // ------------------------------------------------------------------ ask the issuer
        private void openRequestDialog() {
            if (chosenFile == null) {
                return;
            }
            List<Issuer> issuers = ctx.service().listIssuers();
            if (issuers.isEmpty()) {
                Ui.alert(Alert.AlertType.INFORMATION, "No issuers yet",
                        "No issuing institution is registered yet.\nThe authority must create issuer accounts first (Issuer Portal > Authority).");
                return;
            }
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Request verification");
            dialog.setHeaderText("The issuing institution will see this document and confirm or reject it.");
            dialog.initOwner(ctx.stage());
            dialog.getDialogPane().getStylesheets().add(Ui.css());

            ComboBox<Issuer> issuerBox = new ComboBox<>(FXCollections.observableArrayList(issuers));
            issuerBox.getSelectionModel().selectFirst();
            issuerBox.setMaxWidth(Double.MAX_VALUE);
            TextField company = new TextField();
            if (ctx.account() != null && ctx.account().getRole() == Role.VERIFIER) {
                company.setText(ctx.account().getDisplayName()); // the verifier's own company, taken from the account
                company.setEditable(false);
            }
            TextField candidate = new TextField();
            TextField certNo = new TextField();
            TextField course = new TextField();
            TextField year = new TextField();

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(10);
            grid.setPadding(new Insets(14, 6, 6, 6));
            ColumnConstraints c0 = new ColumnConstraints(150);
            ColumnConstraints c1 = new ColumnConstraints(300);
            grid.getColumnConstraints().addAll(c0, c1);
            String[] labels = {"Issuing institution", "Your company *", "Candidate name *", "Certificate number", "Course / examination", "Year"};
            javafx.scene.Node[] inputs = {issuerBox, company, candidate, certNo, course, year};
            for (int i = 0; i < labels.length; i++) {
                Label l = new Label(labels[i]);
                l.getStyleClass().add("key");
                grid.add(l, 0, i);
                grid.add(inputs[i], 1, i);
            }
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> answer = dialog.showAndWait();
            if (!answer.isPresent() || answer.get() != ButtonType.OK) {
                return;
            }
            Issuer issuer = issuerBox.getValue();
            File file = chosenFile;
            String[] values = {company.getText(), candidate.getText(), certNo.getText(), course.getText(), year.getText()};
            Async.run(() -> ctx.service().submitRequest(issuer.getId(), values[0], values[1], values[2], values[3], values[4], file),
                    request -> {
                        idField.setText(request.getId());
                        rbId.setSelected(true);
                        Ui.alert(Alert.AlertType.INFORMATION, "Request sent",
                                "Your request was sent to " + issuer.getName() + ".\n\nRequest ID: " + request.getId()
                                        + "\nType this ID in the box and press Verify to see the answer later.");
                        ctx.refresh();
                        verify();
                    }, e -> Ui.error(e));
        }
    }

}
