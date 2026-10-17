package dev.kro.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kro.core.module.Module;
import dev.kro.core.module.ModuleRegistry;
import dev.kro.core.setting.Setting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** Reads and writes {@code config/kro.json}. Unknown or malformed entries are ignored. */
public final class ConfigIO {
	private static final int VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Modules saved under an earlier name, so a rename keeps your settings. */
	private static final Map<String, String> FORMER_NAMES = Map.of("Velocity", "Anti Knockback");

	private ConfigIO() {
	}

	public static void load(Path file, ModuleRegistry modules) throws IOException {
		if (!Files.isRegularFile(file)) {
			return;
		}
		JsonObject root;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement e = JsonParser.parseReader(r);
			if (!e.isJsonObject()) {
				return;
			}
			root = e.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Malformed config " + file, e);
		}
		JsonObject mods = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
		for (Module m : modules.all()) {
			JsonElement me = mods.get(m.name());
			if (me == null && FORMER_NAMES.containsKey(m.name())) {
				me = mods.get(FORMER_NAMES.get(m.name()));
			}
			if (me == null || !me.isJsonObject()) {
				continue;
			}
			JsonObject mo = me.getAsJsonObject();
			if (mo.has("bind")) {
				m.bind().fromJson(mo.get("bind"));
			}
			JsonObject settings = mo.has("settings") && mo.get("settings").isJsonObject() ? mo.getAsJsonObject("settings") : new JsonObject();
			for (Setting<?> s : m.settings()) {
				if (settings.has(s.name())) {
					try {
						s.fromJson(settings.get(s.name()));
					} catch (RuntimeException ignored) {
						// keep defaults for anything we cannot read
					}
				}
			}
			if (mo.has("enabled") && mo.get("enabled").isJsonPrimitive()) {
				m.setEnabled(mo.get("enabled").getAsBoolean());
			}
		}
	}

	public static void save(Path file, ModuleRegistry modules) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject mods = new JsonObject();
		for (Module m : modules.all()) {
			JsonObject mo = new JsonObject();
			mo.addProperty("enabled", m.enabled());
			mo.add("bind", m.bind().toJson());
			JsonObject settings = new JsonObject();
			for (Setting<?> s : m.settings()) {
				settings.add(s.name(), s.toJson());
			}
			mo.add("settings", settings);
			mods.add(m.name(), mo);
		}
		root.add("modules", mods);

		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, w);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException atomicUnsupported) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
