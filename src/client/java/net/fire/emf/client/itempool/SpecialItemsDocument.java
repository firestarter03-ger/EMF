package net.fire.emf.client.itempool;

import java.util.ArrayList;
import java.util.List;

/**
 * Dokument für Special-Items (eingebettet, lokal, Server) inkl. Revisionsnummer.
 */
public final class SpecialItemsDocument {
	public int revision;
	public List<SpecialItemDefinition> specialItems = new ArrayList<>();

	public SpecialItemsDocument() {
	}

	public SpecialItemsDocument(int revision, List<SpecialItemDefinition> specialItems) {
		this.revision = revision;
		this.specialItems = specialItems == null ? new ArrayList<>() : new ArrayList<>(specialItems);
	}
}
