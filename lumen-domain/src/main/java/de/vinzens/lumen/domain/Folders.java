package de.vinzens.lumen.domain;

/** Folders sort lists of looks and compositions; they mean nothing else. */
public final class Folders {

	/** Where things go unless told otherwise. */
	public static final String MANUAL = "Manual";
	/** Where quick setup puts what it makes. */
	public static final String GENERATED = "Auto generated";
	public static final int MAX_LENGTH = 40;

	private Folders() {
	}

	/** The folder without surrounding blanks; none or a blank one is {@link #MANUAL}. */
	public static String normalize(String folder) {
		return folder == null || folder.isBlank() ? MANUAL : folder.strip();
	}
}
