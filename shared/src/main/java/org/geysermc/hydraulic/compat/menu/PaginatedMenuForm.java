package org.geysermc.hydraulic.compat.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Universal Paginated and Searchable Menu Form for large virtual inventory networks
 * (AE2, Refined Storage, Storage Drawers, etc.).
 * Generates valid Bedrock SimpleForm JSON payloads with client-side item searching and multi-page chunking.
 */
public final class PaginatedMenuForm {
    public static final int DEFAULT_PAGE_SIZE = 24;

    public record ItemEntry(
        @NotNull Identifier itemId,
        int count,
        @NotNull String displayName,
        @Nullable String nbtSummary
    ) {
        public ItemEntry {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    private final String title;
    private final List<ItemEntry> allItems;
    private final int pageSize;
    private String currentSearchFilter = "";
    private int currentPage = 0;

    public PaginatedMenuForm(@NotNull String title, @NotNull List<ItemEntry> items) {
        this(title, items, DEFAULT_PAGE_SIZE);
    }

    public PaginatedMenuForm(@NotNull String title, @NotNull List<ItemEntry> items, int pageSize) {
        this.title = Objects.requireNonNull(title, "title");
        this.allItems = List.copyOf(items);
        this.pageSize = Math.max(1, pageSize);
    }

    public void setSearchFilter(@Nullable String filter) {
        this.currentSearchFilter = filter == null ? "" : filter.trim().toLowerCase();
        this.currentPage = 0; // Reset to page 0 on search change
    }

    public void setCurrentPage(int page) {
        int maxPage = Math.max(0, totalPages() - 1);
        this.currentPage = Math.max(0, Math.min(page, maxPage));
    }

    public int currentPage() {
        return this.currentPage;
    }

    public int totalPages() {
        List<ItemEntry> filtered = filteredItems();
        if (filtered.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) filtered.size() / pageSize);
    }

    @NotNull
    public List<ItemEntry> filteredItems() {
        if (currentSearchFilter.isEmpty()) {
            return allItems;
        }
        List<ItemEntry> result = new ArrayList<>();
        for (ItemEntry item : allItems) {
            if (item.displayName().toLowerCase().contains(currentSearchFilter) ||
                item.itemId().toString().toLowerCase().contains(currentSearchFilter)) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NotNull
    public List<ItemEntry> currentPagedItems() {
        List<ItemEntry> filtered = filteredItems();
        if (filtered.isEmpty()) {
            return List.of();
        }
        int fromIndex = currentPage * pageSize;
        if (fromIndex >= filtered.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        return filtered.subList(fromIndex, toIndex);
    }

    /**
     * Serializes this paginated form into a standard Bedrock SimpleForm JSON structure.
     */
    @NotNull
    public JsonObject toBedrockFormJson() {
        JsonObject form = new JsonObject();
        form.addProperty("type", "form");
        form.addProperty("title", title);

        int total = totalPages();
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Page ").append(currentPage + 1).append(" of ").append(total);
        if (!currentSearchFilter.isEmpty()) {
            contentBuilder.append(" (Filter: '").append(currentSearchFilter).append("')");
        }
        form.addProperty("content", contentBuilder.toString());

        JsonArray buttons = new JsonArray();

        // Search Action Button
        JsonObject searchBtn = new JsonObject();
        searchBtn.addProperty("text", "[ Search Items ]");
        buttons.add(searchBtn);

        // Previous Page Button
        if (currentPage > 0) {
            JsonObject prevBtn = new JsonObject();
            prevBtn.addProperty("text", "< Previous Page");
            buttons.add(prevBtn);
        }

        // Item Buttons
        for (ItemEntry item : currentPagedItems()) {
            JsonObject itemBtn = new JsonObject();
            itemBtn.addProperty("text", item.displayName() + " (x" + item.count() + ")");
            buttons.add(itemBtn);
        }

        // Next Page Button
        if (currentPage < total - 1) {
            JsonObject nextBtn = new JsonObject();
            nextBtn.addProperty("text", "Next Page >");
            buttons.add(nextBtn);
        }

        form.add("buttons", buttons);
        return form;
    }
}
