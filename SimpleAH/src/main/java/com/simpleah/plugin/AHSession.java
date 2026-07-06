package com.simpleah.plugin;

public class AHSession {

    public enum SortMode {
        NEWEST("Newest First"),
        OLDEST("Oldest First"),
        PRICE_LOW("Price: Low to High"),
        PRICE_HIGH("Price: High to Low");

        private final String display;

        SortMode(String display) {
            this.display = display;
        }

        public String getDisplay() { return display; }

        public SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private int page;
    private SortMode sortMode;
    private String searchQuery;
    private String currencyFilter;

    public AHSession() {
        this.page = 0;
        this.sortMode = SortMode.NEWEST;
        this.searchQuery = null;
        this.currencyFilter = null;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public SortMode getSortMode() { return sortMode; }
    public void setSortMode(SortMode sortMode) { this.sortMode = sortMode; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public String getCurrencyFilter() { return currencyFilter; }
    public void setCurrencyFilter(String currencyFilter) { this.currencyFilter = currencyFilter; }

    public boolean hasSearch() { return searchQuery != null && !searchQuery.isEmpty(); }
    public boolean hasCurrencyFilter() { return currencyFilter != null; }

    public void clearSearch() { this.searchQuery = null; }
    public void clearCurrencyFilter() { this.currencyFilter = null; }
}
