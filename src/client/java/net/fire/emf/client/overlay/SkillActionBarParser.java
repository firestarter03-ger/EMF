package net.fire.emf.client.overlay;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillActionBarParser {
	private static final Set<String> IGNORED_SKILLS = Set.of("magic", "crafting");
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final Pattern SKILL_PATTERN = Pattern.compile(
			"(?i)([A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß' \\-]{0,24}):\\s*([\\d.,]+)\\s*/\\s*([\\d.,]+)\\s*XP\\s*\\(\\s*(?:RB:\\s*)?([\\d.,]+)");

	private SkillActionBarParser() {
	}

	public static SkillReading parse(String raw) {
		return parseDetailed(raw).reading();
	}

	public static ParseOutcome parseDetailed(String raw) {
		if (raw == null || raw.isBlank()) {
			return ParseOutcome.fail("leer");
		}

		String cleaned = COLOR_CODES.matcher(raw).replaceAll("").trim();
		Matcher matcher = SKILL_PATTERN.matcher(cleaned);
		if (!matcher.find()) {
			return ParseOutcome.fail("kein Match, cleaned='" + cleaned + "'");
		}

		String skill = matcher.group(1).trim();
		if (IGNORED_SKILLS.contains(skill.toLowerCase(Locale.ROOT))) {
			return ParseOutcome.fail("Skill ignoriert: " + skill);
		}

		long currentXp = parseNumber(matcher.group(2));
		long targetXp = parseNumber(matcher.group(3));
		long resources = parseNumber(matcher.group(4));
		if (currentXp < 0 || targetXp <= 0 || resources < 0) {
			return ParseOutcome.fail("ungültige Zahlen current=" + matcher.group(2)
					+ " target=" + matcher.group(3) + " res=" + matcher.group(4));
		}

		SkillReading reading = new SkillReading(skill, currentXp, targetXp, resources);
		return new ParseOutcome(reading, SkillXpDebug.describeReading(reading));
	}

	private static long parseNumber(String value) {
		try {
			return Long.parseLong(value.replace(".", "").replace(",", "").trim());
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	public record SkillReading(String skill, long currentXp, long targetXp, long resources) {
	}

	public record ParseOutcome(SkillReading reading, String description) {
		private static ParseOutcome fail(String description) {
			return new ParseOutcome(null, description);
		}
	}
}
