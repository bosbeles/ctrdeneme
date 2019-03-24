package ctr.gui;

import ctr.CTRManager;
import ctr.CTRNetwork;

import javax.swing.*;
import java.awt.*;

public class CTRTest extends JPanel {

    private final CTRNetwork network;
    private JTextField field;
    private JButton add;
    private int counter = 1;
    GridBagConstraints gc;


    public CTRTest() {
        network = new CTRNetwork();
        add = new JButton("Add");
        add.addActionListener(e-> addManager());

        setLayout(new GridBagLayout());
        gc = new GridBagConstraints();
        gc.gridy = 0;
        System.out.println(gc.gridy);
        add(add, gc);

    }

    private void addManager() {
        CTRManager manager = new CTRManager(counter++);
        CTRManagerPanel panel = new CTRManagerPanel(manager, network);
        gc.gridy = gc.gridy + 1;
        System.out.println(gc.gridy);
        add(panel, gc);
        JFrame frame = (JFrame) SwingUtilities.getRoot(this);
        frame.pack();
    }

    public static void main(String[] args) {
        Tester.test(new CTRTest());




    }
}
