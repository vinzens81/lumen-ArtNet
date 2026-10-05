package de.vinzens.lumen;

import de.vinzens.lumen.desktop.DesktopLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LumenApplication {

	/** With {@code -Dlumen.desktop=true} (set by the Mac app) Lumen starts with its own window, see DesktopLauncher. */
	public static void main(String[] args) {
		if (Boolean.getBoolean("lumen.desktop")) {
			DesktopLauncher.launch(args);
			return;
		}
		SpringApplication.run(LumenApplication.class, args);
	}

}
