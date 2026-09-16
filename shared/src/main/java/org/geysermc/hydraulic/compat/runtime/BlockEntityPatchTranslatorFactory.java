package org.geysermc.hydraulic.compat.runtime;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtType;
import net.minecraft.resources.Identifier;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.hydraulic.compat.CompatibilityRegistry;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BlockEntityPatchTranslatorFactory {
    private BlockEntityPatchTranslatorFactory() {
    }

    @Nullable
    public static BlockEntityTranslator create(
        @NotNull GeyserSession session,
        @NotNull Vector3i position,
        @NotNull CompatibilityRegistry compatibilityRegistry
    ) {
        String javaIdentifier = CompatibilityRuntimeDiagnostics.resolveJavaBlockEntityIdentifier(session, position);
        if (javaIdentifier != null) {
            CompiledCompatibilityPlan plan = compatibilityRegistry.dispatchTable().blockEntity(Identifier.parse(javaIdentifier));
            if (BridgeAdapterSupport.supportsBlockEntityPatch(plan)) {
                return create(plan);
            }
        }

        // Automatic chest lid / dynamic block entity fallback:
        // If the block is a chest/barrel or has chest semantics, provide Bedrock Chest block entity tags
        // so that lid open/close animations and audio render smoothly
        if (javaIdentifier != null && isChestBlockEntity(javaIdentifier)) {
            return new ChestAnimationBlockEntityTranslator();
        }
        return null;
    }

    private static boolean isChestBlockEntity(@NotNull String javaIdentifier) {
        String lower = javaIdentifier.toLowerCase();
        return lower.contains("chest") || lower.contains("barrel") || lower.contains("lootr");
    }

    static boolean supports(@Nullable CompiledCompatibilityPlan plan) {
        return BridgeAdapterSupport.supportsBlockEntityPatch(plan);
    }

    @Nullable
    static BlockEntityTranslator create(@Nullable CompiledCompatibilityPlan plan) {
        if (!supports(plan)) {
            return null;
        }
        return new MetadataBackedBlockEntityTranslator(plan.blockEntityPatchTemplate());
    }

    private static final class ChestAnimationBlockEntityTranslator extends BlockEntityTranslator {
        @Override
        public void translateTag(@NotNull GeyserSession session, @NotNull NbtMapBuilder bedrockTag, @Nullable NbtMap javaTag, @Nullable BlockState blockState) {
            bedrockTag.putString("id", "Chest");
            if (javaTag != null && javaTag.containsKey("CustomName")) {
                bedrockTag.put("CustomName", javaTag.get("CustomName"));
            }
            if (javaTag != null && javaTag.containsKey("Lock")) {
                bedrockTag.put("Lock", javaTag.get("Lock"));
            }
            // Populate pair lead & chest visual components so Bedrock lid animation plays
            bedrockTag.putInt("pairlead", 0);
            bedrockTag.putByte("isMovable", (byte) 1);
        }
    }

    private static final class MetadataBackedBlockEntityTranslator extends BlockEntityTranslator {
        private final BlockEntityPatchTemplate template;

        private MetadataBackedBlockEntityTranslator(@NotNull BlockEntityPatchTemplate template) {
            this.template = template;
        }

        @Override
        public void translateTag(@NotNull GeyserSession session, @NotNull NbtMapBuilder bedrockTag, @Nullable NbtMap javaTag, @Nullable BlockState blockState) {
            String bedrockIdentifier = this.template.bedrockIdentifier();
            if (bedrockIdentifier != null) {
                bedrockTag.putString("id", bedrockIdentifier);
            }

            for (BlockEntityPatchTemplate.TagMutation mutation : this.template.mutations()) {
                applyMutation(bedrockTag, mutation, javaTag, 0);
            }
        }

        private void applyMutation(@NotNull NbtMapBuilder target, @NotNull BlockEntityPatchTemplate.TagMutation mutation, @Nullable NbtMap javaTag, int index) {
            String key = mutation.path().get(index);
            if (index == mutation.path().size() - 1) {
                putValue(target, key, mutation.value(), javaTag);
                return;
            }

            String nextSegment = mutation.path().get(index + 1);
            if (isListIndex(nextSegment)) {
                List<Object> list = mutableList(target.get(key));
                if (!applyMutation(list, mutation, javaTag, index + 1)) {
                    return;
                }

                NbtList<?> builtList = buildList(list);
                if (builtList == null || builtList.isEmpty()) {
                    target.remove(key);
                    return;
                }

                target.put(key, builtList);
                return;
            }

            Object existing = target.get(key);
            NbtMapBuilder compound = existing instanceof NbtMap map ? NbtMapBuilder.from(map) : NbtMap.builder();
            applyMutation(compound, mutation, javaTag, index + 1);
            target.putCompound(key, compound.build());
        }

        private boolean applyMutation(@NotNull List<Object> target, @NotNull BlockEntityPatchTemplate.TagMutation mutation, @Nullable NbtMap javaTag, int index) {
            Integer listIndex = parseListIndex(mutation.path().get(index));
            if (listIndex == null || listIndex > target.size()) {
                return false;
            }

            if (index == mutation.path().size() - 1) {
                return setListValue(target, listIndex, mutation.value(), javaTag);
            }

            Object existing = listIndex < target.size() ? target.get(listIndex) : null;
            String nextSegment = mutation.path().get(index + 1);
            Object updated;
            if (isListIndex(nextSegment)) {
                List<Object> nestedList = mutableList(existing);
                if (!applyMutation(nestedList, mutation, javaTag, index + 1)) {
                    return false;
                }

                updated = buildList(nestedList);
                if (updated == null) {
                    return false;
                }
            } else {
                NbtMapBuilder compound = existing instanceof NbtMap map ? NbtMapBuilder.from(map) : NbtMap.builder();
                applyMutation(compound, mutation, javaTag, index + 1);
                updated = compound.build();
            }

            if (listIndex == target.size()) {
                target.add(updated);
            } else {
                target.set(listIndex, updated);
            }
            return true;
        }

        private void putValue(@NotNull NbtMapBuilder target, @NotNull String key, @NotNull BlockEntityPatchTemplate.TagValue value, @Nullable NbtMap javaTag) {
            switch (value.kind()) {
                case NULL -> target.remove(key);
                case BOOLEAN -> target.putBoolean(key, (Boolean) value.value());
                case INTEGER -> target.putInt(key, (Integer) value.value());
                case LONG -> target.putLong(key, (Long) value.value());
                case DOUBLE -> target.putDouble(key, (Double) value.value());
                case STRING -> target.putString(key, (String) value.value());
                case COPY_FROM_JAVA -> copyJavaValue(target, key, value, javaTag);
            }
        }

        private void copyJavaValue(@NotNull NbtMapBuilder target, @NotNull String key, @NotNull BlockEntityPatchTemplate.TagValue value, @Nullable NbtMap javaTag) {
            if (javaTag == null) {
                return;
            }

            Object sourceValue = resolveJavaValue(javaTag, value.javaSourcePath(), 0);
            if (sourceValue == null) {
                return;
            }

            if (sourceValue instanceof NbtMap map) {
                target.putCompound(key, map);
                return;
            }

            target.put(key, sourceValue);
        }

        private boolean setListValue(@NotNull List<Object> target, int listIndex, @NotNull BlockEntityPatchTemplate.TagValue value, @Nullable NbtMap javaTag) {
            if (value.kind() == BlockEntityPatchTemplate.TagValue.Kind.NULL) {
                if (listIndex >= target.size()) {
                    return false;
                }
                target.remove(listIndex);
                return true;
            }

            Object resolvedValue = switch (value.kind()) {
                case BOOLEAN -> (byte) ((Boolean) value.value() ? 1 : 0);
                case INTEGER, LONG, DOUBLE, STRING -> value.value();
                case COPY_FROM_JAVA -> resolveListCopyValue(value, javaTag);
                case NULL -> null;
            };
            if (resolvedValue == null) {
                return false;
            }

            if (listIndex == target.size()) {
                target.add(resolvedValue);
            } else {
                target.set(listIndex, resolvedValue);
            }
            return true;
        }

        @Nullable
        private Object resolveListCopyValue(@NotNull BlockEntityPatchTemplate.TagValue value, @Nullable NbtMap javaTag) {
            if (javaTag == null) {
                return null;
            }

            Object sourceValue = resolveJavaValue(javaTag, value.javaSourcePath(), 0);
            if (sourceValue instanceof Boolean booleanValue) {
                return (byte) (booleanValue ? 1 : 0);
            }
            return sourceValue;
        }

        @NotNull
        private List<Object> mutableList(@Nullable Object existing) {
            if (existing instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return new ArrayList<>();
        }

        @Nullable
        private NbtList<?> buildList(@NotNull List<Object> values) {
            if (values.isEmpty()) {
                return NbtList.EMPTY;
            }

            NbtType<?> type = listType(values.getFirst());
            if (type == null) {
                return null;
            }
            for (Object value : values) {
                if (!matchesListType(type, value)) {
                    return null;
                }
            }
            return buildTypedList(type, values);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private NbtList<?> buildTypedList(@NotNull NbtType<?> type, @NotNull List<Object> values) {
            return new NbtList((NbtType<Object>) type, values);
        }

        private boolean matchesListType(@NotNull NbtType<?> expected, @Nullable Object value) {
            return value != null && expected.equals(listType(value));
        }

        @Nullable
        private NbtType<?> listType(@Nullable Object value) {
            if (value instanceof Byte) {
                return NbtType.BYTE;
            }
            if (value instanceof Short) {
                return NbtType.SHORT;
            }
            if (value instanceof Integer) {
                return NbtType.INT;
            }
            if (value instanceof Long) {
                return NbtType.LONG;
            }
            if (value instanceof Float) {
                return NbtType.FLOAT;
            }
            if (value instanceof Double) {
                return NbtType.DOUBLE;
            }
            if (value instanceof byte[]) {
                return NbtType.BYTE_ARRAY;
            }
            if (value instanceof String) {
                return NbtType.STRING;
            }
            if (value instanceof NbtList<?>) {
                return NbtType.LIST;
            }
            if (value instanceof NbtMap) {
                return NbtType.COMPOUND;
            }
            if (value instanceof int[]) {
                return NbtType.INT_ARRAY;
            }
            if (value instanceof long[]) {
                return NbtType.LONG_ARRAY;
            }
            return null;
        }

        private boolean isListIndex(@NotNull String segment) {
            return parseListIndex(segment) != null;
        }

        @Nullable
        private Object resolveJavaValue(@Nullable Object current, @Nullable java.util.List<String> path, int index) {
            if (current == null || path == null) {
                return null;
            }
            if (index == path.size()) {
                return current;
            }
            String segment = path.get(index);
            if (current instanceof NbtMap map) {
                return resolveJavaValue(map.get(segment), path, index + 1);
            }
            if (current instanceof java.util.List<?> list) {
                Integer listIndex = parseListIndex(segment, list.size());
                if (listIndex == null) {
                    return null;
                }
                return resolveJavaValue(list.get(listIndex), path, index + 1);
            }
            return null;
        }

        @Nullable
        private Integer parseListIndex(@NotNull String rawIndex, int size) {
            Integer parsed = parseListIndex(rawIndex);
            return parsed != null && parsed < size ? parsed : null;
        }

        @Nullable
        private Integer parseListIndex(@NotNull String rawIndex) {
            try {
                int parsed = Integer.parseInt(rawIndex);
                return parsed >= 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}