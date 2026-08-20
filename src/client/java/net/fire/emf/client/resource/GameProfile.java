package net.fire.emf.client.resource;

public enum GameProfile {
	SKYBLOCK("skyblock"),
	STONEBLOCK("stoneblock"),
	DEMON("demon"),
	OCEANBLOCK("oceanblock"),
	UNKNOWN("unknown");

	private final String id;

	GameProfile(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static GameProfile fromId(String id) {
		if (id == null || id.isBlank()) {
			return UNKNOWN;
		}
		for (GameProfile profile : values()) {
			if (profile.id.equalsIgnoreCase(id)) {
				return profile;
			}
		}
		return UNKNOWN;
	}
}
