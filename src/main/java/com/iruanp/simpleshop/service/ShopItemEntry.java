package com.iruanp.simpleshop.service;

import net.minecraft.item.ItemStack;
import java.math.BigDecimal;

public class ShopItemEntry {
    public final Integer id;
    public final ItemStack itemStack;
    public final BigDecimal price;
    public final int quantity;
    public final boolean isSelling;
    public final String creator;

    public ShopItemEntry(Integer id, ItemStack itemStack, BigDecimal price, int quantity, boolean isSelling, String creator) {
        this.id = id;
        this.itemStack = itemStack;
        this.price = price;
        this.quantity = quantity;
        this.isSelling = isSelling;
        this.creator = creator;
    }
}