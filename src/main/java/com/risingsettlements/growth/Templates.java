package com.risingsettlements.growth;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.village.VillageStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finds and reads the structure templates that villages build from.
 *
 * <p>Templates are looked up through Minecraft's own structure template manager, so data
 * packs can add or replace them: any template at {@code structure/house/<style>/...} in any
 * namespace becomes a house for villages of that style.
 */
public final class Templates {
	/** Parsed templates, dropped automatically when a data pack reload replaces them. */
	private static final Map<StructureTemplate, TemplateShape> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	private Templates() {
	}

	/** Every house template for a style, in a stable order. */
	public static List<TemplateShape> houses(ServerLevel level, VillageStyle style) {
		String folder = "house/" + style.id() + "/";
		List<TemplateShape> shapes = new ArrayList<>();
		level.getStructureTemplateManager().listTemplates()
				.filter(id -> id.getPath().startsWith(folder))
				.sorted(Comparator.comparing(Identifier::toString))
				.forEach(id -> {
					TemplateShape shape = get(level, id);
					if (shape != null) {
						shapes.add(shape);
					}
				});
		return shapes;
	}

	public static @Nullable TemplateShape well(ServerLevel level, VillageStyle style) {
		return get(level, RisingSettlements.id("well/" + style.id()));
	}

	public static @Nullable TemplateShape get(ServerLevel level, Identifier id) {
		StructureTemplate template = level.getStructureTemplateManager().get(id).orElse(null);
		if (template == null) {
			return null;
		}
		return CACHE.computeIfAbsent(template, t -> read(id, t));
	}

	/**
	 * Reads the blocks of a template. The block list is private inside
	 * {@link StructureTemplate}, but its public {@code save} method writes the same data in
	 * the documented structure file format, which is stable across versions.
	 */
	private static @Nullable TemplateShape read(Identifier id, StructureTemplate template) {
		CompoundTag tag = template.save(new CompoundTag());
		ListTag paletteTag = tag.getList("palette")
				.orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));

		List<BlockState> palette = new ArrayList<>();
		for (Tag entry : paletteTag) {
			if (entry instanceof CompoundTag compound) {
				palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, compound));
			}
		}

		List<TemplateShape.Block> blocks = new ArrayList<>();
		for (Tag entry : tag.getListOrEmpty("blocks")) {
			if (!(entry instanceof CompoundTag compound)) {
				continue;
			}
			ListTag pos = compound.getListOrEmpty("pos");
			int index = compound.getIntOr("state", -1);
			if (pos.size() != 3 || index < 0 || index >= palette.size()) {
				continue;
			}
			BlockState state = palette.get(index);
			// Structure voids mean "leave whatever is there"; jigsaws are for world generation.
			if (state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW)) {
				continue;
			}
			blocks.add(new TemplateShape.Block(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), state));
		}

		if (blocks.isEmpty()) {
			RisingSettlements.LOGGER.warn("Template {} has no blocks and will not be used", id);
			return null;
		}
		return new TemplateShape(id, template.getSize(), List.copyOf(blocks));
	}
}
