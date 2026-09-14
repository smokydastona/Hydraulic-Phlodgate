package org.geysermc.hydraulic.compat.menu;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PaginatedMenuFormTest {

    @Test
    public void testPaginationAndFiltering() {
        List<PaginatedMenuForm.ItemEntry> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(new PaginatedMenuForm.ItemEntry(
                Identifier.parse("ae2:certus_quartz_crystal_" + i),
                i + 1,
                "Certus Quartz Crystal #" + i,
                null
            ));
        }

        PaginatedMenuForm form = new PaginatedMenuForm("AE2 Storage Terminal", items, 10);
        assertEquals(5, form.totalPages());
        assertEquals(0, form.currentPage());
        assertEquals(10, form.currentPagedItems().size());

        // Test next page
        form.setCurrentPage(1);
        assertEquals(1, form.currentPage());
        assertEquals("Certus Quartz Crystal #10", form.currentPagedItems().get(0).displayName());

        // Test search filter
        form.setSearchFilter("crystal #4");
        assertEquals(0, form.currentPage()); // Reset to page 0
        assertEquals(11, form.filteredItems().size()); // #4, #40..#49

        JsonObject json = form.toBedrockFormJson();
        assertTrue(json.has("type"));
        assertEquals("form", json.get("type").getAsString());
        assertTrue(json.has("buttons"));
    }
}
