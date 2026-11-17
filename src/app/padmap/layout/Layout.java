package app.padmap.layout;

import java.util.List;

/** A game's mapping sets; the first is active at the start. */
public record Layout(List<MappingSet> sets) {
    public static final Layout EMPTY = new Layout(List.of(new MappingSet(null, List.of())));

    public Layout {
        if (sets.isEmpty()) throw new IllegalArgumentException("a layout needs at least one mapping set");
        sets = List.copyOf(sets);
    }

    /** One set of controls; chord (null for none) is the buttons that switch to it. */
    public record MappingSet(String chord, List<Control> controls) {
        public MappingSet {
            controls = List.copyOf(controls);
        }
    }

    public boolean maps() {
        return sets.stream().anyMatch(set -> !set.controls().isEmpty());
    }
}
