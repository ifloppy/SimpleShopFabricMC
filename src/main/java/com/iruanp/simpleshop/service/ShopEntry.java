package com.iruanp.simpleshop.service;

public class ShopEntry {
    public String name;
    public String description;
    public boolean isAdminShop;
    public String item;

    public ShopEntry(String name, String description, boolean isAdminShop, String item) {
        this.name = name;
        this.description = description;
        this.isAdminShop = isAdminShop;
        this.item = item;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAdminShop() {
        return isAdminShop;
    }

    public String getItem() {
        return item;
    }
}