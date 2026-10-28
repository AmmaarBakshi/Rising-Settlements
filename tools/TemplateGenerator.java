import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * Generates the structure templates (.nbt) that Rising Settlements builds from.
 *
 * <p>Run it from the project root with Java 25 (no compiling needed):
 *
 * <pre>
 *     java tools/TemplateGenerator.java
 * </pre>
 *
 * <p>It writes one template per (design, village style) pair into
 * {@code src/main/resources/data/rising_settlements/structure/}. The files use the same
 * format as vanilla structure templates, so they can also be opened and edited in game
 * with a structure block. Data packs can add more houses by putting templates into
 * {@code data/<namespace>/structure/house/<style>/}, see the README.
 *
 * <p>Template layout conventions (the mod relies on these):
 * <ul>
 *     <li>y = 0 is the foundation/floor layer. It is placed at ground level.</li>
 *     <li>The front door is on the z = 0 side, in the middle of the x axis.</li>
 *     <li>Air blocks are stored on purpose: they mark space that must be clear.</li>
 * </ul>
 */
public final class TemplateGenerator {
	/** World data version of Minecraft 26.3 (from version.json, "world_version"). */
	private static final int DATA_VERSION = 5023;

	private static final Path OUTPUT = Path.of("src/main/resources/data/rising_settlements/structure");

	/** The block palette for one village style. */
	record Style(String name, String foundation, String floor, String wall, String corner, String roofStairs,
				 String roofSlab, String door, String bed, String fence) {
	}

	static final List<Style> STYLES = List.of(
			new Style("plains", "cobblestone", "oak_planks", "oak_planks", "oak_log", "oak_stairs",
					"oak_slab", "oak_door", "red_bed", "oak_fence"),
			new Style("desert", "cut_sandstone", "smooth_sandstone", "sandstone", "chiseled_sandstone", "smooth_sandstone_stairs",
					"smooth_sandstone_slab", "birch_door", "yellow_bed", "birch_fence"),
			new Style("savanna", "cobblestone", "acacia_planks", "acacia_planks", "acacia_log", "acacia_stairs",
					"acacia_slab", "acacia_door", "orange_bed", "acacia_fence"),
			new Style("taiga", "cobblestone", "spruce_planks", "spruce_planks", "spruce_log", "spruce_stairs",
					"spruce_slab", "spruce_door", "green_bed", "spruce_fence"),
			new Style("snowy", "stone_bricks", "spruce_planks", "spruce_planks", "stripped_spruce_log", "spruce_stairs",
					"spruce_slab", "spruce_door", "light_blue_bed", "spruce_fence"));

	public static void main(String[] args) throws IOException {
		int count = 0;
		for (Style style : STYLES) {
			write(OUTPUT.resolve("house/" + style.name + "/small.nbt"), house(style, 7, 7, 1));
			write(OUTPUT.resolve("house/" + style.name + "/medium.nbt"), house(style, 9, 7, 2));
			write(OUTPUT.resolve("house/" + style.name + "/long.nbt"), house(style, 11, 7, 3));
			write(OUTPUT.resolve("well/" + style.name + ".nbt"), well(style));
			count += 4;
		}
		System.out.println("Wrote " + count + " templates to " + OUTPUT.toAbsolutePath());
	}

	// ------------------------------------------------------------------ designs

	/**
	 * A rectangular house with a gable roof running along the x axis.
	 *
	 * @param width x size including walls (odd, so the door sits in the middle)
	 * @param depth z size including walls (odd, so the roof has a ridge row)
	 * @param beds  number of beds along the back wall
	 */
	static Template house(Style s, int width, int depth, int beds) {
		int wallHeight = 3;
		int roofLayers = depth / 2 + 1;
		Template t = new Template(width, 1 + wallHeight + roofLayers, depth);
		int doorX = width / 2;

		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				boolean edgeX = x == 0 || x == width - 1;
				boolean edgeZ = z == 0 || z == depth - 1;
				boolean edge = edgeX || edgeZ;
				boolean corner = edgeX && edgeZ;

				// Floor layer: foundation ring with a plank floor inside.
				t.set(x, 0, z, edge ? s.foundation : s.floor);

				// Walls with log corners; the inside is kept clear.
				for (int y = 1; y <= wallHeight; y++) {
					if (corner) {
						t.set(x, y, z, s.corner, "axis", "y");
					} else if (edge) {
						t.set(x, y, z, s.wall);
					} else {
						t.set(x, y, z, "air");
					}
				}
			}
		}

		// Windows: every other block along the long walls, and one in each side wall.
		for (int x = 2; x < width - 2; x += 2) {
			if (x != doorX) {
				t.set(x, 2, 0, "glass_pane");
			}
			t.set(x, 2, depth - 1, "glass_pane");
		}
		t.set(0, 2, depth / 2, "glass_pane");
		t.set(width - 1, 2, depth / 2, "glass_pane");

		// Front door (two halves).
		t.set(doorX, 1, 0, s.door, "facing", "south", "half", "lower", "hinge", "left", "open", "false", "powered", "false");
		t.set(doorX, 2, 0, s.door, "facing", "south", "half", "upper", "hinge", "left", "open", "false", "powered", "false");

		// Beds against the back wall, heads touching the wall, spaced one apart.
		int bedZFoot = depth - 3;
		int firstBedX = width / 2 - (beds - 1);
		for (int i = 0; i < beds; i++) {
			int bx = firstBedX + i * 2;
			t.set(bx, 1, bedZFoot, s.bed, "facing", "south", "part", "foot", "occupied", "false");
			t.set(bx, 1, bedZFoot + 1, s.bed, "facing", "south", "part", "head", "occupied", "false");
		}

		// Gable roof: stairs climb from both long sides towards the ridge.
		for (int k = 0; k < roofLayers; k++) {
			int y = wallHeight + 1 + k;
			int north = k;
			int south = depth - 1 - k;
			for (int x = 0; x < width; x++) {
				if (north < south) {
					t.set(x, y, north, s.roofStairs, "facing", "south", "half", "bottom", "shape", "straight", "waterlogged", "false");
					t.set(x, y, south, s.roofStairs, "facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false");
				} else {
					t.set(x, y, north, s.roofSlab, "type", "bottom", "waterlogged", "false");
				}
				// Space under the roof is air, except the gable walls at both ends.
				for (int z = north + 1; z < south; z++) {
					boolean gable = x == 0 || x == width - 1;
					t.set(x, y, z, gable ? s.wall : "air");
				}
			}
		}

		// A lantern hanging from the roof in the middle of the room.
		int ridgeY = wallHeight + roofLayers;
		t.set(width / 2, ridgeY - 1, depth / 2, "lantern", "hanging", "true", "waterlogged", "false");
		return t;
	}

	/** A small covered well: a ring of stone around 3x3 water, with fence posts and a slab roof. */
	static Template well(Style s) {
		Template t = new Template(5, 4, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				t.set(x, 0, z, edge ? s.foundation : "water", edge ? new String[0] : new String[] {"level", "0"});
				t.set(x, 1, z, corner ? s.fence : edge ? s.foundation : "air");
				t.set(x, 2, z, corner ? s.fence : "air");
				t.set(x, 3, z, s.roofSlab, "type", "bottom", "waterlogged", "false");
			}
		}
		return t;
	}

	// ------------------------------------------------------------------ template model

	/** One block in the template palette: a block ID plus its state properties. */
	record State(String id, Map<String, String> properties) {
	}

	/** A simple 3D grid of block states; unset positions are not stored at all. */
	static final class Template {
		final int sizeX;
		final int sizeY;
		final int sizeZ;
		final Map<Long, State> blocks = new TreeMap<>();

		Template(int sizeX, int sizeY, int sizeZ) {
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.sizeZ = sizeZ;
		}

		void set(int x, int y, int z, String block, String... properties) {
			if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
				throw new IllegalArgumentException("Outside template: " + x + "," + y + "," + z);
			}
			Map<String, String> props = new LinkedHashMap<>();
			for (int i = 0; i + 1 < properties.length; i += 2) {
				props.put(properties[i], properties[i + 1]);
			}
			blocks.put(key(x, y, z), new State("minecraft:" + block, props));
		}

		// Sort order: y first, so the file lists blocks bottom-up like vanilla templates.
		long key(int x, int y, int z) {
			return ((long) y << 32) | ((long) z << 16) | x;
		}
	}

	// ------------------------------------------------------------------ NBT writing

	private static final byte TAG_END = 0;
	private static final byte TAG_INT = 3;
	private static final byte TAG_STRING = 8;
	private static final byte TAG_LIST = 9;
	private static final byte TAG_COMPOUND = 10;

	static void write(Path file, Template t) throws IOException {
		Files.createDirectories(file.getParent());

		List<State> palette = new ArrayList<>();
		Map<State, Integer> paletteIndex = new LinkedHashMap<>();
		for (State state : t.blocks.values()) {
			paletteIndex.computeIfAbsent(state, st -> {
				palette.add(st);
				return palette.size() - 1;
			});
		}

		try (OutputStream raw = Files.newOutputStream(file);
			 DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
			out.writeByte(TAG_COMPOUND);
			out.writeUTF("");

			// size: [x, y, z]
			out.writeByte(TAG_LIST);
			out.writeUTF("size");
			out.writeByte(TAG_INT);
			out.writeInt(3);
			out.writeInt(t.sizeX);
			out.writeInt(t.sizeY);
			out.writeInt(t.sizeZ);

			// blocks: [{pos: [x, y, z], state: paletteIndex}]
			out.writeByte(TAG_LIST);
			out.writeUTF("blocks");
			out.writeByte(TAG_COMPOUND);
			out.writeInt(t.blocks.size());
			for (Map.Entry<Long, State> entry : t.blocks.entrySet()) {
				long key = entry.getKey();
				out.writeByte(TAG_LIST);
				out.writeUTF("pos");
				out.writeByte(TAG_INT);
				out.writeInt(3);
				out.writeInt((int) (key & 0xFFFF));
				out.writeInt((int) (key >>> 32));
				out.writeInt((int) ((key >>> 16) & 0xFFFF));
				out.writeByte(TAG_INT);
				out.writeUTF("state");
				out.writeInt(paletteIndex.get(entry.getValue()));
				out.writeByte(TAG_END);
			}

			// palette: [{id: "minecraft:...", properties: {...}}]
			out.writeByte(TAG_LIST);
			out.writeUTF("palette");
			out.writeByte(TAG_COMPOUND);
			out.writeInt(palette.size());
			for (State state : palette) {
				out.writeByte(TAG_STRING);
				out.writeUTF("id");
				out.writeUTF(state.id);
				if (!state.properties.isEmpty()) {
					out.writeByte(TAG_COMPOUND);
					out.writeUTF("properties");
					for (Map.Entry<String, String> p : state.properties.entrySet()) {
						out.writeByte(TAG_STRING);
						out.writeUTF(p.getKey());
						out.writeUTF(p.getValue());
					}
					out.writeByte(TAG_END);
				}
				out.writeByte(TAG_END);
			}

			// entities: [] (empty list of compounds)
			out.writeByte(TAG_LIST);
			out.writeUTF("entities");
			out.writeByte(TAG_END);
			out.writeInt(0);

			out.writeByte(TAG_INT);
			out.writeUTF("DataVersion");
			out.writeInt(DATA_VERSION);

			out.writeByte(TAG_END);
		}
	}
}
