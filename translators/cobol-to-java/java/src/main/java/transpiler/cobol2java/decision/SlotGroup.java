package transpiler.cobol2java.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The follow-up decisions raised by a candidate with slots. Each slot decision records its
 * choice here; when the last one is resolved, the group generates the statement from all the
 * choices and returns the decisions its nested statements raise.
 */
public final class SlotGroup {
    private final List<String> names;
    private final Map<String, String> chosen = new LinkedHashMap<>();
    private final Function<Map<String, String>, List<Decision>> generate;

    /** @param generate called once with {slot name: candidate id} in slot order */
    public SlotGroup(List<String> names, Function<Map<String, String>, List<Decision>> generate) {
        this.names = List.copyOf(names);
        this.generate = generate;
    }

    /** Records a slot's choice; returns the nested decisions once every slot is chosen. */
    List<Decision> fill(String name, String candidateId) {
        if (!names.contains(name) || chosen.containsKey(name)) {
            throw new IllegalStateException("slot " + name + " is not open");
        }
        chosen.put(name, candidateId);
        if (!complete()) {
            return List.of();
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        names.forEach(n -> ordered.put(n, chosen.get(n)));
        return generate.apply(ordered);
    }

    public boolean complete() {
        return chosen.size() == names.size();
    }
}
