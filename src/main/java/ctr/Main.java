package ctr;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Main {


    public static void main(String[] args) {
        CTRNetwork network = new CTRNetwork();
        CTRManager manager1 = new CTRManager(1);
        CTRManager manager2 = new CTRManager(2);
        CTRManager manager3 = new CTRManager(3);
        CTRManager manager4 = new CTRManager(4);

        manager1.join(network);
        manager2.join(network);
        manager3.join(network);
        manager4.join(network);

        manager1.start("A", Arrays.asList("A", "B", "C"));
        manager2.start("B", Arrays.asList("A", "B", "C"));
        manager3.start("D", Arrays.asList("A", "C", "D"));
        sleep(50);
        manager4.start("A", Arrays.asList("A", "D"));
    }

    private static void sleep(int i) {
        try {
            TimeUnit.SECONDS.sleep(i);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
