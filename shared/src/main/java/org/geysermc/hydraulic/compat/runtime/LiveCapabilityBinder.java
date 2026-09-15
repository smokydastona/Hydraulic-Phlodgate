package org.geysermc.hydraulic.compat.runtime;

import net.minecraft.resources.Identifier;
import org.geysermc.hydraulic.compat.discovery.DynamicMachineLifecycleManager;
import org.geysermc.hydraulic.compat.ir.CompiledCompatibilityPlan;
import org.geysermc.hydraulic.compat.model.Confidence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns ephemeral bindings between live runtime objects and plans whose bridge contracts
 * were verified by dynamic capability discovery. Authoritative object state remains on
 * the Java object; this registry only owns lifecycle-scoped execution metadata.
 */
public final class LiveCapabilityBinder {
    private static final String CONTRACT_VERSION = "dynamic-semantic-v1";
    private static final AtomicLong NEXT_BINDING_ID = new AtomicLong();

    private final DynamicMachineLifecycleManager lifecycleManager;
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final Map<IdentityWeakReference, LiveBinding> bindings = new HashMap<>();
    private final Map<String, IdentityWeakReference> positionToBindingRef = new HashMap<>();

    public LiveCapabilityBinder(@NotNull DynamicMachineLifecycleManager lifecycleManager) {
        this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "lifecycleManager");
    }

    @NotNull
    public LiveBinding bind(
        @NotNull Identifier identifier,
        @NotNull Object runtimeObject,
        @NotNull Map<String, String> initialFacts
    ) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(runtimeObject, "runtimeObject");
        Objects.requireNonNull(initialFacts, "initialFacts");
        synchronized (bindings) {
            purgeCollectedBindings();
            LiveBinding existing = bindings.get(new IdentityWeakReference(runtimeObject));
            if (existing != null && existing.identifier().equals(identifier)) {
                return existing;
            }
            CompiledCompatibilityPlan plan = lifecycleManager.registerAndCompile(identifier, runtimeObject, initialFacts);
            Set<String> capabilities = new LinkedHashSet<>();
            for (Map.Entry<String, String> fact : plan.inventoryFacts().entrySet()) {
                if ("true".equalsIgnoreCase(fact.getValue())) {
                    capabilities.add(fact.getKey());
                }
            }
            Set<String> adapters = new LinkedHashSet<>();
            plan.adapterBindings().forEach(adapter -> adapters.add(adapter.adapterId()));
            LiveBinding binding = new LiveBinding(
                identifier,
                runtimeObject.getClass().getName() + "#" + NEXT_BINDING_ID.incrementAndGet(),
                runtimeObject.getClass().getName(),
                Set.copyOf(capabilities),
                Set.copyOf(adapters),
                Set.copyOf(new LinkedHashSet<>(plan.runtimeBridgeKinds())),
                CONTRACT_VERSION,
                plan.confidence(),
                plan
            );
            bindings.put(new IdentityWeakReference(runtimeObject, referenceQueue), binding);
            return binding;
        }
    }

    @NotNull
    public LiveBinding bindPosition(
        @NotNull Identifier identifier,
        @NotNull String worldKey,
        @NotNull String positionKey,
        @NotNull Object runtimeObject,
        @NotNull Map<String, String> initialFacts
    ) {
        LiveBinding binding = bind(identifier, runtimeObject, initialFacts);
        synchronized (bindings) {
            String key = worldKey + "@" + positionKey;
            positionToBindingRef.put(key, new IdentityWeakReference(runtimeObject));
        }
        return binding;
    }

    @Nullable
    public LiveBinding resolvePosition(@NotNull String worldKey, @NotNull String positionKey) {
        synchronized (bindings) {
            purgeCollectedBindings();
            String key = worldKey + "@" + positionKey;
            IdentityWeakReference ref = positionToBindingRef.get(key);
            if (ref == null) return null;
            Object referent = ref.get();
            if (referent == null) {
                positionToBindingRef.remove(key);
                return null;
            }
            return bindings.get(ref);
        }
    }

    public void invalidatePosition(@NotNull String worldKey, @NotNull String positionKey) {
        synchronized (bindings) {
            String key = worldKey + "@" + positionKey;
            IdentityWeakReference ref = positionToBindingRef.remove(key);
            if (ref != null) {
                Object referent = ref.get();
                if (referent != null) {
                    bindings.remove(ref);
                }
            }
        }
    }

    public void invalidateDimension(@NotNull String worldKey) {
        synchronized (bindings) {
            String prefix = worldKey + "@";
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, IdentityWeakReference> entry : positionToBindingRef.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    toRemove.add(entry.getKey());
                    IdentityWeakReference ref = entry.getValue();
                    if (ref != null) {
                        bindings.remove(ref);
                    }
                }
            }
            toRemove.forEach(positionToBindingRef::remove);
        }
    }

    @Nullable
    public LiveBinding resolve(@NotNull Object runtimeObject) {
        Objects.requireNonNull(runtimeObject, "runtimeObject");
        synchronized (bindings) {
            purgeCollectedBindings();
            return bindings.get(new IdentityWeakReference(runtimeObject));
        }
    }

    @NotNull
    public LiveBinding refresh(
        @NotNull Identifier identifier,
        @NotNull Object runtimeObject,
        @NotNull Map<String, String> initialFacts
    ) {
        unbind(runtimeObject);
        return bind(identifier, runtimeObject, initialFacts);
    }

    public void unbind(@NotNull Object runtimeObject) {
        Objects.requireNonNull(runtimeObject, "runtimeObject");
        synchronized (bindings) {
            purgeCollectedBindings();
            bindings.remove(new IdentityWeakReference(runtimeObject));
        }
    }

    public void clear() {
        synchronized (bindings) {
            bindings.clear();
        }
    }

    public int bindingCount() {
        synchronized (bindings) {
            purgeCollectedBindings();
            return bindings.size();
        }
    }

    private void purgeCollectedBindings() {
        IdentityWeakReference reference;
        while ((reference = (IdentityWeakReference) referenceQueue.poll()) != null) {
            bindings.remove(reference);
        }
    }

    private static final class IdentityWeakReference extends WeakReference<Object> {
        private final int identityHash;

        private IdentityWeakReference(@NotNull Object referent) {
            super(referent);
            this.identityHash = System.identityHashCode(referent);
        }

        private IdentityWeakReference(@NotNull Object referent, @NotNull ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityWeakReference reference)) {
                return false;
            }
            Object referent = get();
            return referent != null && referent == reference.get();
        }
    }

    public record LiveBinding(
        @NotNull Identifier identifier,
        @NotNull String objectIdentity,
        @NotNull String runtimeType,
        @NotNull Set<String> capabilities,
        @NotNull Set<String> adapters,
        @NotNull Set<RuntimeBridgeKind> bridgeKinds,
        @NotNull String contractVersion,
        @NotNull Confidence confidence,
        @NotNull CompiledCompatibilityPlan plan
    ) {
        public LiveBinding {
            capabilities = Set.copyOf(capabilities);
            adapters = Set.copyOf(adapters);
            bridgeKinds = Set.copyOf(bridgeKinds);
        }
    }
}
