
public import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

/**
 * Melon Client 1.1 — Custom Minecraft Launcher (Java)
 * ---------------------------------------------------
 *  • Fixes compilation on vanilla JDK ≥ 8 (removes explicit com.sun.* import)
 *  • Uses sane default window height (800 × 600 instead of 800 × 6000)
 *  • Dynamically chooses the class-path separator for Windows/Unix
 *  • Falls back to Runtime.maxMemory() when physical RAM isn’t available
 *  • Minor code-style & NPE clean-ups
 */
public class MelonClient {

    private static final Logger logger = Logger.getLogger(MelonClient.class.getName());

    // ── UI palette ────────────────────────────────────────────────────────────
    private static final Color BG = new Color(0x2e2e2e);
    private static final Color FG = new Color(0xffffff);
    private static final Color ACCENT = new Color(0x5fbf00);
    private static final Color ENTRY_BG = new Color(0x454545);

    // ── Swing fields ──────────────────────────────────────────────────────────
    private JFrame frame;
    private JLabel usernameLabel;
    private JTextField usernameField;
    private JButton msButton;
    private JRadioButton offlineRadio;
    private JRadioButton microsoftRadio;
    private JComboBox<String> versionComboBox;
    private JLabel ramLabel;
    private JSlider ramSlider;

    // ── State & config ────────────────────────────────────────────────────────
    private final Properties config = new Properties();
    private String loginType;           // "offline" | "microsoft"
    private String msProfileName;       // Placeholder; real OAuth user-name
    private String msProfileId;         // Placeholder; real OAuth UUID

    public MelonClient() {
        setupLogging();
        logger.info("Melon Launcher starting up.");

        int maxRam = detectMaxRam();
        loadConfig();

        String initialUsername = config.getProperty("offline_username", "");
        loginType = config.getProperty("login_type", "offline");
        int initialRam = Integer.parseInt(config.getProperty("ram", String.valueOf(Math.min(4, maxRam))));

        SwingUtilities.invokeLater(() -> buildUI(initialUsername, initialRam, maxRam));
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  UI
    // ──────────────────────────────────────────────────────────────────────────

    private void buildUI(String username, int initialRam, int maxRam) {
        frame = new JFrame("Melon Client 1.1");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(800, 600);               // sane default
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));

        applyDarkTheme();

        JLabel title = new JLabel("Melon Client", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(FG);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        frame.add(title);

        // Login mode radio buttons
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginPanel.setBackground(BG);
        offlineRadio = radio("Offline", "offline");
        microsoftRadio = radio("Microsoft", "microsoft");
        ButtonGroup group = new ButtonGroup();
        group.add(offlineRadio);
        group.add(microsoftRadio);
        loginPanel.add(offlineRadio);
        loginPanel.add(microsoftRadio);
        frame.add(loginPanel);
        ("microsoft".equals(loginType) ? microsoftRadio : offlineRadio).setSelected(true);

        // Username field
        usernameLabel = label("Username:");
        usernameField = new JTextField(username, 16);
        styleTextField(usernameField);
        frame.add(usernameLabel);
        frame.add(usernameField);

        // MS OAuth placeholder button
        msButton = new JButton("Login with Microsoft");
        styleButton(msButton, ENTRY_BG, FG);
        msButton.addActionListener(e -> loginWithMs());
        frame.add(msButton);

        // Game type selector
        frame.add(label("Game Type:"));
        versionComboBox = new JComboBox<>(new String[]{"Vanilla", "Forge", "Fabric"});
        versionComboBox.setMaximumSize(new Dimension(250, 30));
        frame.add(versionComboBox);

        // RAM slider
        ramLabel = label(String.format("RAM Allocation (GB): %d", initialRam));
        ramSlider = new JSlider(1, maxRam, initialRam);
        ramSlider.addChangeListener(e -> ramLabel.setText(String.format("RAM Allocation (GB): %d", ramSlider.getValue())));
        ramSlider.setMaximumSize(new Dimension(300, 50));
        ramSlider.setBackground(BG);
        frame.add(ramLabel);
        frame.add(ramSlider);

        // Launch button
        JButton launch = new JButton("Launch");
        styleButton(launch, ACCENT, Color.BLACK);
        launch.setFont(launch.getFont().deriveFont(Font.BOLD));
        launch.addActionListener(e -> launch());
        launch.setAlignmentX(Component.CENTER_ALIGNMENT);
        frame.add(Box.createVerticalStrut(20));
        frame.add(launch);
        frame.add(Box.createVerticalGlue());

        updateLoginUI();
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onClose(); }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        logger.info("UI initialised.");
    }

    private void updateLoginUI() {
        boolean offline = "offline".equals(loginType);
        usernameLabel.setVisible(offline);
        usernameField.setVisible(offline);
        msButton.setVisible(!offline);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Event handlers
    // ──────────────────────────────────────────────────────────────────────────

    private void loginWithMs() {
        logger.info("Microsoft login placeholder invoked.");
        JOptionPane.showMessageDialog(frame, "Microsoft login is not implemented in this build.", "Microsoft Login", JOptionPane.INFORMATION_MESSAGE);
        msProfileName = "Player";
        msProfileId = UUID.randomUUID().toString();
    }

    private void launch() {
        logger.info("Launch clicked.");

        String username = usernameField.getText().trim();
        String gameType = (String) versionComboBox.getSelectedItem();
        int ramGb = ramSlider.getValue();

        // username validation
        if ("offline".equals(loginType) && !isValidUsername(username)) {
            JOptionPane.showMessageDialog(frame, "Username must be 3–16 characters long and contain only letters, numbers, and underscores.", "Invalid Username", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // ensure MS login happened
        if ("microsoft".equals(loginType) && (msProfileId == null)) {
            JOptionPane.showMessageDialog(frame, "Please log in with Microsoft before launching.", "Not logged in", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // persist cfg
        if ("offline".equals(loginType)) config.setProperty("offline_username", username);
        config.setProperty("login_type", loginType);
        config.setProperty("ram", String.valueOf(ramGb));
        saveConfig();

        // session info
        String sessionUser = "offline".equals(loginType) ? username : msProfileName;
        String sessionUuid = "offline".equals(loginType) ? offlineUuid(username) : msProfileId;
        String token       = "offline".equals(loginType) ? "null"          : "placeholder_token";

        // build command
        try {
            String mcDir = getMinecraftDirectory();
            String versionId = findBestVersionId(gameType);
            if (versionId == null) {
                JOptionPane.showMessageDialog(frame, "Could not find a suitable " + gameType + " version.", "Version missing", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // installMinecraftVersion(versionId, mcDir); // TODO real implementation
            java.util.List<String> cmd = buildCommand(versionId, mcDir, sessionUser, sessionUuid, token, ramGb);
            new ProcessBuilder(cmd).directory(new File(mcDir)).start();
            JOptionPane.showMessageDialog(frame, "Launching Minecraft (" + gameType + ")…", "Launching", JOptionPane.INFORMATION_MESSAGE);
            logger.info("Minecraft launched → " + String.join(" ", cmd));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Launch failed", ex);
            JOptionPane.showMessageDialog(frame, "Launch failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Placeholder logic (replace with real launcher lib)
    // ──────────────────────────────────────────────────────────────────────────

    private String findBestVersionId(String gameType) {
        switch (gameType) {
            case "Forge":   return "1.20.1-forge-47.2.20";
            case "Fabric":  return "fabric-loader-0.15.7-1.20.4";
            default:         return "1.20.4"; // Vanilla
        }
    }

    private java.util.List<String> buildCommand(String versionId, String mcDir, String user, String uuid, String token, int ramGb) {
        String sep = System.getProperty("path.separator");
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("java");
        cmd.add("-Xmx" + ramGb + "G");
        cmd.add("-Xms" + Math.max(1, ramGb / 2) + "G");
        cmd.add("-Djava.library.path=" + mcDir + File.separator + "natives");
        cmd.add("-cp");
        cmd.add(mcDir + sep + "libraries" + sep + mcDir + sep + "versions" + sep + versionId + ".jar");
        cmd.add("net.minecraft.client.main.Main");
        cmd.add("--username");
        cmd.add(user);
        cmd.add("--uuid");
        cmd.add(uuid);
        cmd.add("--accessToken");
        cmd.add(token);
        cmd.add("--version");
        cmd.add(versionId);
        cmd.add("--gameDir");
        cmd.add(mcDir);
        return cmd;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Utility
    // ──────────────────────────────────────────────────────────────────────────

    private static String getMinecraftDirectory() {
        String os = System.getProperty("os.name").toUpperCase();
        if (os.contains("WIN"))  return System.getenv("APPDATA") + File.separator + ".minecraft";
        if (os.contains("MAC"))  return System.getProperty("user.home") + "/Library/Application Support/minecraft";
        return System.getProperty("user.home") + "/.minecraft";
    }

    private static boolean isValidUsername(String name) {
        return name != null && name.length() >= 3 && name.length() <= 16 && Pattern.matches("^[A-Za-z0-9_]+$", name);
    }

    private static String offlineUuid(String user) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8));
            digest[6] &= 0x0f; digest[6] |= 0x30; // version 3
            digest[8] &= 0x3f; digest[8] |= 0x80; // variant
            long msb = 0, lsb = 0;
            for (int i = 0; i < 8;  i++) msb = (msb << 8) | (digest[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (digest[i] & 0xff);
            return new UUID(msb, lsb).toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    private int detectMaxRam() {
        // Prefer physical RAM when accessible
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                long bytes = ((com.sun.management.OperatingSystemMXBean) os).getTotalPhysicalMemorySize();
                return Math.max(1, (int) (bytes / 1_073_741_824L)); // GiB
            }
        } catch (Throwable t) {
            logger.fine("Physical RAM detection unavailable: " + t);
        }
        // Fallback: heap limit ≈ ¼ physical on default JVM settings
        long heap = Runtime.getRuntime().maxMemory();
        return Math.max(1, (int) (heap / 1_073_741_824L));
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Config & logging helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void loadConfig() {
        try (InputStream in = new FileInputStream("melonclient.properties")) {
            config.load(in);
        } catch (FileNotFoundException ignored) {
            logger.info("No existing config; defaults loaded.");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Config load failed", e);
        }
    }

    private void saveConfig() {
        try (OutputStream out = new FileOutputStream("melonclient.properties")) {
            config.store(out, "Melon Client configuration");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Config save failed", e);
        }
    }

    private void setupLogging() {
        try {
            FileHandler fh = new FileHandler("melon_client.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onClose() {
        saveConfig();
        logger.info("Exiting Melon Client.");
        frame.dispose();
        System.exit(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Styling helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void applyDarkTheme() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", FG);
        UIManager.put("Button.background", ENTRY_BG);
        UIManager.put("Button.foreground", FG);
        UIManager.put("RadioButton.background", BG);
        UIManager.put("RadioButton.foreground", FG);
        UIManager.put("ComboBox.background", ENTRY_BG);
        UIManager.put("ComboBox.foreground", FG);
        UIManager.put("TextField.background", ENTRY_BG);
        UIManager.put("TextField.foreground", FG);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        return l;
    }

    private JRadioButton radio(String text, String cmd) {
        JRadioButton r = new JRadioButton(text);
        r.setBackground(BG);
        r.setForeground(FG);
        r.setActionCommand(cmd);
        r.addActionListener(e -> { loginType = cmd; updateLoginUI(); });
        return r;
    }

    private void styleButton(AbstractButton b, Color bg, Color fg) {
        b.setBackground(bg);
        b.setForeground(fg);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(ENTRY_BG);
        tf.setForeground(FG);
        tf.setCaretColor(FG);
        tf.setHorizontalAlignment(JTextField.CENTER);
        tf.setMaximumSize(new Dimension(250, 30));
        tf.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    public static void main(String[] args) {
        new MelonClient();
    }
} {
    
}
