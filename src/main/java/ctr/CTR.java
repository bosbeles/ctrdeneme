package ctr;

import lombok.Data;
import lombok.ToString;

import java.util.Set;

@Data
public class CTR {

    private int id;

    public enum Type {
        QUERY, INFO, COMMAND, REJECT
    }

    private Type type = Type.QUERY;
    private String p;
    private Set<String> c;

}
