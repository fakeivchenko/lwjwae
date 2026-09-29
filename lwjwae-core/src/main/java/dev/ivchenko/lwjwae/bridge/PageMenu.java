package dev.ivchenko.lwjwae.bridge;

import dev.ivchenko.lwjwae.menu.MenuItem;
import java.util.List;
import java.util.Map;

/**
 * A menu that a page asked for with {@code window.lwjwae.menu.popup}, and the ID that the page gave
 * each entry, which is what the page gets back for a pick.
 *
 * @param items The entries, submenus included.
 * @param pageIds The ID of every entry that the user can pick, by identity: two equal entries are
 *     two entries.
 */
public record PageMenu(List<MenuItem> items, Map<MenuItem, String> pageIds) {}
