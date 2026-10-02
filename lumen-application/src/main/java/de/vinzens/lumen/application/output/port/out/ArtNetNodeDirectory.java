package de.vinzens.lumen.application.output.port.out;

import de.vinzens.lumen.domain.project.ArtNetNode;

import java.util.List;

/**
 * Art-Net nodes known from discovery (ArtPoll), including nodes that went offline recently.
 */
public interface ArtNetNodeDirectory {

	ArtNetNodeDirectory NONE = List::of;

	List<ArtNetNode> nodes();
}
