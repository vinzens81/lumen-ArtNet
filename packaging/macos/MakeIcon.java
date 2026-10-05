import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the app icon (1024 × 1024 PNG): a glowing point of light on a dark rounded square, like the logo in the top
 * bar. Run with {@code java packaging/macos/MakeIcon.java <out.png>}; package-mac.sh turns it into Lumen.icns.
 */
public class MakeIcon {

	public static void main(String[] args) throws IOException {
		var size = 1024;
		var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		var g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		// macOS icon grid: the square covers about 80 % of the canvas
		var inset = 100f;
		var square = new RoundRectangle2D.Float(inset, inset, size - 2 * inset, size - 2 * inset, 185, 185);
		g.setPaint(new RadialGradientPaint(size / 2f, size * 0.42f, size * 0.55f, new float[] {0f, 1f},
				new Color[] {new Color(0x1a2030), new Color(0x07080b)}));
		g.fill(square);
		g.setClip(square);

		// soft light cone from the top
		g.setComposite(AlphaComposite.SrcOver);
		g.setPaint(new RadialGradientPaint(size / 2f, size * 0.47f, size * 0.36f, new float[] {0f, 0.45f, 1f},
				new Color[] {new Color(255, 181, 71, 150), new Color(255, 181, 71, 40), new Color(255, 181, 71, 0)}));
		g.fill(new Ellipse2D.Float(size * 0.14f, size * 0.11f, size * 0.72f, size * 0.72f));

		// the point of light
		var r = size * 0.14f;
		g.setPaint(new RadialGradientPaint(size / 2f, size * 0.47f, r, new float[] {0f, 0.55f, 1f},
				new Color[] {new Color(0xfff6e0), new Color(0xffc56b), new Color(255, 181, 71, 0)}));
		g.fill(new Ellipse2D.Float(size / 2f - r, size * 0.47f - r, 2 * r, 2 * r));
		g.dispose();

		ImageIO.write(image, "png", new File(args.length > 0 ? args[0] : "icon.png"));
	}
}
