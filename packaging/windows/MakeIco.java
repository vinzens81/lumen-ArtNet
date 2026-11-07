import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the app icon drawn by {@code packaging/macos/MakeIcon.java} into a Windows {@code .ico}: the sizes Windows
 * wants, each stored as a PNG entry (supported since Vista). Run with
 * {@code java packaging/windows/MakeIco.java <icon.png> <Lumen.ico>}.
 */
public class MakeIco {

	private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage: java packaging/windows/MakeIco.java <icon.png> <out.ico>");
			System.exit(2);
		}
		var icon = ImageIO.read(new File(args[0]));
		if (icon == null) {
			throw new IOException("Cannot read " + args[0]);
		}
		var images = new ArrayList<byte[]>();
		for (int size : SIZES) {
			images.add(png(scale(icon, size)));
		}
		write(Path.of(args[1]), images);
		System.out.println("✔ " + args[1] + " (" + SIZES.length + " sizes: 16–256)");
	}

	private static BufferedImage scale(BufferedImage icon, int size) {
		var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(icon, 0, 0, size, size, null);
		g.dispose();
		return image;
	}

	private static byte[] png(BufferedImage image) throws IOException {
		var out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out)) {
			throw new IOException("PNG writer missing");
		}
		return out.toByteArray();
	}


	/** ICO file: 6-byte header, one 16-byte entry per image, then the images. */
	private static void write(Path target, List<byte[]> images) throws IOException {
		var buffer = ByteBuffer.allocate(6 + 16 * images.size() + images.stream().mapToInt(b -> b.length).sum())
				.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort((short) 0);            // reserved
		buffer.putShort((short) 1);            // type: icon
		buffer.putShort((short) images.size());
		var offset = 6 + 16 * images.size();
		for (int i = 0; i < images.size(); i++) {
			var data = images.get(i);
			var size = SIZES[i];
			buffer.put((byte) (size == 256 ? 0 : size)); // width, 0 = 256
			buffer.put((byte) (size == 256 ? 0 : size)); // height
			buffer.put((byte) 0);            // colors in the palette
			buffer.put((byte) 0);            // reserved
			buffer.putShort((short) 1);      // color planes
			buffer.putShort((short) 32);     // bits per pixel
			buffer.putInt(data.length);
			buffer.putInt(offset);
			offset += data.length;
		}
		images.forEach(buffer::put);
		try (OutputStream out = Files.newOutputStream(target)) {
			out.write(buffer.array(), 0, buffer.position());
		}
	}
}
