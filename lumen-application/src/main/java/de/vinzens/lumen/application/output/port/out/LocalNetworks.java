package de.vinzens.lumen.application.output.port.out;

import de.vinzens.lumen.domain.project.LocalNetwork;

import java.util.List;

/**
 * The IPv4 networks of the network interfaces of this computer that are up, without loopback.
 */
public interface LocalNetworks {

	LocalNetworks NONE = List::of;

	List<LocalNetwork> networks();
}
