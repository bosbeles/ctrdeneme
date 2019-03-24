package ctr.gui;

import ctr.CTRManager;
import ctr.CTRNetwork;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CTRManagerPanel extends JPanel implements ActionListener, CTRListener {

    private final CTRManager manager;
    private final CTRNetwork network;

    private JButton state;
    private JLabel tRef;
    private JLabel myP;

    private JCheckBox[] checkBoxes;
    private JComboBox<String> prefered;

    JButton start;
    JButton join;
    JButton leave;
    private List<String> selecteds;

    public CTRManagerPanel(CTRManager manager, CTRNetwork network) {
        this.manager = manager;
        this.manager.addListener(this);
        this.network = network;
        initializeGUI();

    }

    private void initializeGUI() {
        checkBoxes = new JCheckBox[CTRManager.ALL_CTR.length];
        for (int i = 0; i < checkBoxes.length; i++) {
            checkBoxes[i] = new JCheckBox(CTRManager.ALL_CTR[i]);
            checkBoxes[i].addActionListener(this);
        }
        prefered = new JComboBox<>();

        start = new JButton("Start");
        leave = new JButton("Leave");
        join = new JButton("Join");

        start.addActionListener(e -> manager.start(prefered.getSelectedItem().toString(), new HashSet<>(selecteds)));
        join.addActionListener(e -> manager.join(network));
        leave.addActionListener(e -> manager.leave(network));

        state = new JButton(manager.getState().toString());
        state.setPreferredSize(new Dimension(200, state.getPreferredSize().height));
        tRef = createLabel("TRef: " + manager.getTRef());
        myP = createLabel("MyP: " + manager.getMyP());


        setLayout(new GridBagLayout());
        JPanel panel = new JPanel();
        for (JCheckBox checkBox : checkBoxes) {
            panel.add(checkBox);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        add(panel, gbc);
        gbc.gridy++;
        add(prefered, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        panel = new JPanel();
        panel.add(join);
        panel.add(leave);
        panel.add(start);
        add(panel, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        panel = new JPanel();
        panel.add(state);
        panel.add(tRef);
        panel.add(myP);
        add(panel, gbc);

    }

    JLabel createLabel(Object value) {
        JLabel label = new JLabel();
        if (value != null) {
            label.setText(value.toString());
        }
        return label;
    }

    void updateStateLabel() {
        CTRManager.CTRState state = manager.getState();
        Color c = null;
        switch (state) {
            case LISTENING:
                c = Color.YELLOW;
                break;
            case ESTABLISHED:
                c = Color.ORANGE;
                break;
            case FINAL:
                c = Color.GREEN;
                break;
            default:
                c = Color.RED;
                break;
        }
        this.state.setBackground(c);
        this.state.setText("State: " + state.toString());
    }

    void updateTRef() {
        this.tRef.setText("TRef: " + manager.getTRef());
    }

    void updateMyP() {
        this.myP.setText("MyP: " + manager.getMyP());
    }


    public void onChange() {
        EventQueue.invokeLater(() -> {
            updateTRef();
            updateMyP();
            updateStateLabel();
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source instanceof JCheckBox) {
            updateCapables();
        }
    }

    private void updateCapables() {
        selecteds = new ArrayList<>();
        prefered.removeAllItems();
        for (int i = 0; i < checkBoxes.length; i++) {
            if (checkBoxes[i].isSelected()) {
                String item = CTRManager.ALL_CTR[i];
                selecteds.add(item);
                prefered.addItem(item);
            }
        }


    }

    public static void main(String[] args) {
        CTRNetwork network = new CTRNetwork();
        CTRManager manager = new CTRManager(1);
        Tester.test(new CTRManagerPanel(manager, network));
    }
}
