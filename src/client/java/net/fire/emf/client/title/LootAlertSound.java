package net.fire.emf.client.title;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

public enum LootAlertSound {
	HARP("Harp", () -> SoundEvents.NOTE_BLOCK_HARP.value(), true),
	BASS("Bass", () -> SoundEvents.NOTE_BLOCK_BASS.value(), true),
	BASEDRUM("Bass Drum", () -> SoundEvents.NOTE_BLOCK_BASEDRUM.value(), true),
	SNARE("Snare", () -> SoundEvents.NOTE_BLOCK_SNARE.value(), true),
	HAT("Hat", () -> SoundEvents.NOTE_BLOCK_HAT.value(), true),
	GUITAR("Guitar", () -> SoundEvents.NOTE_BLOCK_GUITAR.value(), true),
	FLUTE("Flute", () -> SoundEvents.NOTE_BLOCK_FLUTE.value(), true),
	BELL("Bell", () -> SoundEvents.NOTE_BLOCK_BELL.value(), true),
	CHIME("Chime", () -> SoundEvents.NOTE_BLOCK_CHIME.value(), true),
	XYLOPHONE("Xylophone", () -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), true),
	IRON_XYLOPHONE("Iron Xylophone", () -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), true),
	COW_BELL("Cow Bell", () -> SoundEvents.NOTE_BLOCK_COW_BELL.value(), true),
	DIDGERIDOO("Didgeridoo", () -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), true),
	BIT("Bit", () -> SoundEvents.NOTE_BLOCK_BIT.value(), true),
	BANJO("Banjo", () -> SoundEvents.NOTE_BLOCK_BANJO.value(), true),
	PLING("Pling", () -> SoundEvents.NOTE_BLOCK_PLING.value(), true),
	CHALLENGE_COMPLETE("Challenge Complete", () -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, false);

	private final String displayName;
	private final Supplier<SoundEvent> sound;
	private final boolean usesPitch;

	LootAlertSound(String displayName, Supplier<SoundEvent> sound, boolean usesPitch) {
		this.displayName = displayName;
		this.sound = sound;
		this.usesPitch = usesPitch;
	}

	public String displayName() {
		return displayName;
	}

	public boolean usesPitch() {
		return usesPitch;
	}

	public int playCount() {
		return usesPitch ? 3 : 1;
	}

	public SoundEvent soundEvent() {
		SoundEvent event = sound.get();
		return event != null ? event : SoundEvents.NOTE_BLOCK_BELL.value();
	}
}
