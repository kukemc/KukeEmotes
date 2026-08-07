package kuke.emotes.bobj;

import java.util.HashMap;
import java.util.Map;

/**
 * A named animation ({@code an} block) — a map of bone name to its animated channels.
 *
 * <p>Ported from McHorse's Emoticons (GPL-3.0).
 */
public class BOBJAction {

    public String name;
    public Map<String, BOBJGroup> groups = new HashMap<String, BOBJGroup>();

    public BOBJAction(String name) {
        this.name = name;
    }

    public int getDuration() {
        int max = 0;

        for (BOBJGroup group : this.groups.values()) {
            max = Math.max(max, group.getDuration());
        }

        return max;
    }
}
