package de.vinzens.lumen.desktop;

import de.vinzens.lumen.LumenApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.desktop.AppReopenedListener;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Starts Lumen as a Mac app: a small window shows whether the server runs and opens the user interface in the
 * browser. Closing the window only hides it – the light keeps running; a click on the Dock icon brings it back.
 * Quitting asks first, because it stops the light output.
 */
public final class DesktopLauncher {

	private static final System.Logger LOG = System.getLogger(DesktopLauncher.class.getName());
	private static final Color OK = new Color(0x1f9d55);
	private static final Color ERROR = new Color(0xd0293f);

	private final DesktopSetup setup;
	private final JFrame frame = new JFrame("Lumen");
	private final JLabel status = new JLabel("● Starting…");
	private final JLabel address = new JLabel(" ");
	private final JButton openBrowser = new JButton("Open in browser");
	private volatile ConfigurableApplicationContext context;
	private volatile URI uri;

	private DesktopLauncher(DesktopSetup setup) {
		this.setup = setup;
	}

	/**
	 * @param args command line arguments; the data directories of {@link DesktopSetup} are added
	 */
	public static void launch(String[] args) {
		System.setProperty("apple.awt.application.name", "Lumen");
		var launcher = new DesktopLauncher(DesktopSetup.forUser(Path.of(System.getProperty("user.home"))));
		SwingUtilities.invokeLater(launcher::showWindow);
		Thread.ofPlatform().name("lumen-start").start(() -> launcher.start(args));
	}

	private void start(String[] args) {
		try {
			var bundled = Path.of(System.getProperty("lumen.bundled-fixture-library", "fixture-types"));
			var copied = setup.prepare(bundled);
			keepAwake();
			var arguments = new ArrayList<>(Arrays.asList(args));
			arguments.addAll(setup.arguments());
			var application = new SpringApplication(LumenApplication.class);
			application.setHeadless(false);
			context = application.run(arguments.toArray(String[]::new));
			if (!copied.isEmpty()) {
				LOG.log(Level.INFO, "Copied " + copied.size() + " fixture profiles to " + setup.fixtureLibrary());
			}
			var port = context.getEnvironment().getProperty("local.server.port", "8080");
			uri = URI.create("http://127.0.0.1:" + port + "/");
			SwingUtilities.invokeLater(this::running);
		} catch (Throwable e) {
			LOG.log(Level.ERROR, "Lumen could not start", e);
			SwingUtilities.invokeLater(() -> failed(rootMessage(e)));
		}
	}

	private void showWindow() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
			// the default look works too
		}
		var title = new JLabel("Lumen");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
		status.setFont(status.getFont().deriveFont(Font.BOLD, 14f));
		address.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		openBrowser.setEnabled(false);
		openBrowser.addActionListener(event -> browse());
		var openData = new JButton("Open data folder");
		openData.addActionListener(event -> open(setup.dataDir()));
		var showLog = new JButton("Show log");
		showLog.addActionListener(event -> open(setup.logFile()));
		var quit = new JButton("Quit");
		quit.addActionListener(event -> quit());

		var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.add(openBrowser);
		buttons.add(openData);
		buttons.add(showLog);
		buttons.add(Box.createHorizontalStrut(16));
		buttons.add(quit);

		var hint = new JLabel("<html>Closing this window keeps Lumen running – click the Dock icon to show it again."
				+ "<br>Quit with ⌘Q or the Quit button.</html>");
		hint.setFont(hint.getFont().deriveFont(11f));
		hint.setForeground(Color.GRAY);

		var panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
		for (Component component : new Component[] {title, Box.createVerticalStrut(8), status, address,
				Box.createVerticalStrut(16), buttons, Box.createVerticalStrut(16), hint}) {
			if (component instanceof JComponent swing) {
				swing.setAlignmentX(Component.LEFT_ALIGNMENT);
			}
			panel.add(component);
		}

		frame.setContentPane(panel);
		frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		frame.pack();
		frame.setResizable(false);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
				desktop.setQuitHandler((event, response) -> {
					if (confirmQuit()) {
						stop();
						response.performQuit();
					} else {
						response.cancelQuit();
					}
				});
			}
			if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
				desktop.addAppEventListener((AppReopenedListener) event -> frame.setVisible(true));
			}
		}
	}

	private void running() {
		status.setText("● Running");
		status.setForeground(OK);
		address.setText(uri.toString());
		openBrowser.setEnabled(true);
		frame.getRootPane().setDefaultButton(openBrowser);
		frame.pack();
	}

	private void failed(String message) {
		status.setText("● Lumen could not start");
		status.setForeground(ERROR);
		address.setText("<html>" + escape(message) + "<br>Details in " + escape(setup.logFile().toString())
				+ "</html>");
		address.setFont(address.getFont().deriveFont(Font.PLAIN));
		frame.pack();
		frame.setVisible(true);
	}

	private void browse() {
		try {
			Desktop.getDesktop().browse(uri);
		} catch (IOException | RuntimeException e) {
			JOptionPane.showMessageDialog(frame, "Cannot open the browser: " + e.getMessage() + "\nOpen " + uri,
					"Lumen", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void open(Path path) {
		try {
			Desktop.getDesktop().open(path.toFile());
		} catch (IOException | RuntimeException e) {
			JOptionPane.showMessageDialog(frame, "Cannot open " + path + ": " + e.getMessage(), "Lumen",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private void quit() {
		if (confirmQuit()) {
			stop();
			System.exit(0);
		}
	}

	private boolean confirmQuit() {
		if (context == null) {
			return true;
		}
		frame.setVisible(true);
		var answer = JOptionPane.showConfirmDialog(frame,
				"Quit Lumen?\nThe light output stops – devices keep or lose the last look, depending on the device.",
				"Quit Lumen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		return answer == JOptionPane.OK_OPTION;
	}

	private void stop() {
		var running = context;
		if (running != null) {
			SpringApplication.exit(running);
		}
	}

	/** Keeps the Mac awake while Lumen runs, like {@code run.sh} does. */
	private static void keepAwake() {
		if (!System.getProperty("os.name", "").startsWith("Mac")) {
			return;
		}
		try {
			new ProcessBuilder("caffeinate", "-i", "-w", String.valueOf(ProcessHandle.current().pid())).start();
		} catch (IOException e) {
			LOG.log(Level.WARNING, "Cannot keep the Mac awake: " + e.getMessage());
		}
	}

	private static String rootMessage(Throwable e) {
		var root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		var message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
		return message.contains("Address already in use") || message.contains("already in use")
				? "The port is already in use – is Lumen already running (e.g. via run.sh)?"
				: message;
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
