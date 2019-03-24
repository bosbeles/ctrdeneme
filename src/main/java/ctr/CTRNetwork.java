package ctr;

import lombok.Data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
@Data
public class CTRNetwork {

    private Set<CTRManager> managers = ConcurrentHashMap.newKeySet();

    public void add(CTRManager manager) {
        managers.add(manager);
    }

    public void remove(CTRManager manager) {
        managers.remove(manager);
    }

    public void send(CTRManager manager, CTR ctr) {
        managers.stream().filter(m -> !m.equals(manager)).forEach(m -> m.onReceive(ctr));
    }
}
