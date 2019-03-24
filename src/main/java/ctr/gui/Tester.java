package ctr.gui;

import javax.swing.*;
import java.awt.*;

public class Tester {

    public static void test(JPanel panel) {
        EventQueue.invokeLater(()->createAndShowGUI(panel));
    }

    public static void createAndShowGUI(JPanel panel) {
        JFrame frame = new JFrame();
        frame.getContentPane().add(panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
