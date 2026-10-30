package transpiler.cobol2java.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The follow-up decisions raised by a candidate with slots. Each slot decision records its
 * choice here; when the last one is resolved, the group generates the statement from all the
 * choices and returns the decisions its nested statements raise.
 */
public final class SlotGroup {
    private final List<String> names;
    private final Map<String, String> chosen = new LinkedHashMap<>();
    private final Map<String, String> notes = new LinkedHashMap<>();
    private final BiFunction<Map<String, String>, String, List<Decision>> generate;

    /**
     * @param generate called once with {slot name: candidate id} in slot order and the slots' notes
     *     (who decided each, joined; null when none has one)
     */
    public SlotGroup(List<String> names, BiFunction<Map<String, String>, String, List<Decision>> generate) {
        this.names = List.copyOf(names);
        this.generate = generate;
    }

    /** Records a slot's choice; returns the nested decisions once every slot is chosen. */
    List<Decision> fill(String name, String candidateId, String note) {
        if (!names.contains(name) || chosen.containsKey(name)) {
            throw new IllegalStateException("slot " + name + " is not open");
        }
        chosen.put(name, candidateId);
        if (note != null) {
            notes.put(name, note);
        }
        if (!complete()) {
            return List.of();
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        names.forEach(n -> ordered.put(n, chosen.get(n)));
        List<String> said = names.stream().filter(notes::containsKey).map(n -> n + ": " + notes.get(n)).toList();
        return generate.apply(ordered, said.isEmpty() ? null : String.join("; ", said));
    }

    public boolean complete() {
        return chosen.size() == names.size();
    }
}
