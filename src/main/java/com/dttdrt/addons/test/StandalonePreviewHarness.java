package com.dttdrt.addons.test;

import com.dttdrt.addons.api.CoflnetAhApi;
import com.dttdrt.addons.api.HypixelBazaarApi;
import com.dttdrt.addons.api.ItemDatabase;
import com.dttdrt.addons.features.market.model.AhItemListing;
import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.dttdrt.addons.util.NumberFormatUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * DttdrtAddons Standalone Preview & Quick Test Harness (100% English).
 */
public class StandalonePreviewHarness {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("DttdrtAddons Quick Test & Preview Harness");
        System.out.println("=================================================");

        ItemDatabase.syncFullCatalogAsync().thenRun(() ->
                System.out.println("Catalog synced: " + ItemDatabase.getCatalogSize() + " items."));

        testFuzzySearch("hyp");
        testFuzzySearch("aote");
        testLiveApis();

        SwingUtilities.invokeLater(StandalonePreviewHarness::launchInteractivePreview);
    }

    private static void testFuzzySearch(String query) {
        System.out.println("\n[Fuzzy Search] Query: '" + query + "'");
        List<SkyblockItem> results = ItemDatabase.fuzzySearch(query, false);
        for (int i = 0; i < results.size(); i++) {
            SkyblockItem item = results.get(i);
            System.out.println("  #" + (i + 1) + " [" + item.getTier() + "] " + item.getName()
                    + " (ID: " + item.getId() + ", Material: " + item.getVanillaMaterial() + ")");
        }
    }

    private static void testLiveApis() {
        CoflnetAhApi.getLowestBinsAsync("HYPERION").thenAccept(bins -> {
            System.out.println("\n[Coflnet AH] Lowest " + bins.size() + " BIN listings for Hyperion:");
            for (int i = 0; i < bins.size(); i++) {
                AhItemListing l = bins.get(i);
                System.out.println("  #" + (i + 1) + " " + NumberFormatUtils.formatCompact(l.getPrice())
                        + " | Seller: " + l.getSellerName() + " | " + l.getRelativeAgeString());
            }
        });

        HypixelBazaarApi.getBazaarSummaryAsync("ENCHANTED_CARROT").thenAccept(bz -> {
            if (bz != null) {
                System.out.println("\n[Hypixel BZ] ENCHANTED_CARROT:");
                System.out.println("  Buy: " + bz.getInstantBuyPrice() + " | Sell: " + bz.getInstantSellPrice()
                        + " | Orders: " + bz.getBuyOrderBook().size() + " / " + bz.getSellOrderBook().size());
            }
        });
    }

    private static void launchInteractivePreview() {
        JFrame frame = new JFrame("DttdrtAddons - Standalone Preview");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 480);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x14, 0x16, 0x1B));

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(0x10, 0x12, 0x16));
        sidebar.setPreferredSize(new Dimension(110, 480));
        sidebar.setBorder(new EmptyBorder(15, 10, 15, 10));

        JButton ahBzBtn = new JButton("ah/bz");
        ahBzBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        ahBzBtn.setMaximumSize(new Dimension(90, 28));
        ahBzBtn.setBackground(new Color(0x38, 0x98, 0xEC));
        ahBzBtn.setForeground(Color.WHITE);
        ahBzBtn.setFocusPainted(false);
        sidebar.add(ahBzBtn);
        sidebar.add(Box.createVerticalStrut(10));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(90, 2));
        sidebar.add(sep);

        root.add(sidebar, BorderLayout.WEST);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(0x14, 0x16, 0x1B));
        content.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modeRow.setOpaque(false);
        JButton ahBtn = new JButton("Auction House (AH)");
        JButton bzBtn = new JButton("Bazaar (BZ)");
        ahBtn.setBackground(new Color(0x38, 0x98, 0xEC));
        ahBtn.setForeground(Color.WHITE);
        bzBtn.setBackground(new Color(0x1C, 0x1F, 0x26));
        bzBtn.setForeground(new Color(0xE6, 0xE8, 0xEB));
        modeRow.add(ahBtn);
        modeRow.add(bzBtn);
        content.add(modeRow);

        content.add(Box.createVerticalStrut(12));

        JTextField searchField = new JTextField("Hyperion");
        searchField.setMaximumSize(new Dimension(580, 28));
        searchField.setBackground(new Color(0x1C, 0x1F, 0x26));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.CYAN);
        content.add(searchField);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> suggestionList = new JList<>(listModel);
        suggestionList.setBackground(new Color(0x16, 0x19, 0x20));
        suggestionList.setForeground(new Color(0xFF, 0xAA, 0x00));
        suggestionList.setMaximumSize(new Dimension(580, 110));
        content.add(suggestionList);

        Runnable updateSuggestions = () -> {
            listModel.clear();
            List<SkyblockItem> list = ItemDatabase.fuzzySearch(searchField.getText(), false);
            for (SkyblockItem item : list) {
                listModel.addElement(" [" + item.getTier() + "] " + item.getName() + " (" + item.getId() + ")");
            }
        };
        updateSuggestions.run();

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateSuggestions.run(); }
            public void removeUpdate(DocumentEvent e) { updateSuggestions.run(); }
            public void changedUpdate(DocumentEvent e) { updateSuggestions.run(); }
        });

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (suggestionList.getSelectedValue() != null) {
                    String selected = suggestionList.getSelectedValue();
                    String name = selected.substring(selected.indexOf(']') + 2, selected.lastIndexOf('(')).trim();
                    searchField.setText(name);
                }
            }
        });

        content.add(Box.createVerticalStrut(15));

        JPanel alertPanel = new JPanel();
        alertPanel.setLayout(new BoxLayout(alertPanel, BoxLayout.Y_AXIS));
        alertPanel.setOpaque(false);

        JPanel checkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        checkRow.setOpaque(false);
        JCheckBox enableAlert = new JCheckBox("Enable Alert", true);
        enableAlert.setForeground(Color.WHITE);
        enableAlert.setOpaque(false);
        JCheckBox chatCb = new JCheckBox("Chat", true);
        chatCb.setForeground(Color.LIGHT_GRAY);
        chatCb.setOpaque(false);
        JCheckBox soundCb = new JCheckBox("Sound", true);
        soundCb.setForeground(Color.LIGHT_GRAY);
        soundCb.setOpaque(false);
        JCheckBox titleCb = new JCheckBox("Overlay", true);
        titleCb.setForeground(Color.LIGHT_GRAY);
        titleCb.setOpaque(false);

        checkRow.add(enableAlert);
        checkRow.add(chatCb);
        checkRow.add(soundCb);
        checkRow.add(titleCb);
        alertPanel.add(checkRow);

        alertPanel.add(Box.createVerticalStrut(6));

        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        line1.setOpaque(false);
        JLabel lbl1 = new JLabel("Below: ");
        lbl1.setForeground(Color.WHITE);
        JTextField minInput = new JTextField("1.1b", 12);
        minInput.setBackground(new Color(0x1C, 0x1F, 0x26));
        minInput.setForeground(Color.WHITE);
        line1.add(lbl1);
        line1.add(minInput);
        alertPanel.add(line1);

        alertPanel.add(Box.createVerticalStrut(6));

        JPanel line2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        line2.setOpaque(false);
        JLabel lbl2 = new JLabel("Above: ");
        lbl2.setForeground(Color.WHITE);
        JTextField maxInput = new JTextField("1.3b", 12);
        maxInput.setBackground(new Color(0x1C, 0x1F, 0x26));
        maxInput.setForeground(Color.WHITE);
        line2.add(lbl2);
        line2.add(maxInput);
        alertPanel.add(line2);

        content.add(alertPanel);
        root.add(content, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);
    }
}
