package com.bloxelemental.elemental;

import org.bukkit.plugin.java.JavaPlugin;

public final class ElementalSMP extends JavaPlugin {

    private static ElementalSMP instance;

    private MasteryManager masteryManager;
    private ChamberManager chamberManager;
    private PassiveManager passiveManager;
    private TradeListener tradeListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        Tier.applyConfig(getConfig().getConfigurationSection("cooldowns"));

        masteryManager = new MasteryManager(this);
        masteryManager.loadData();

        chamberManager = new ChamberManager(this);
        chamberManager.start();

        passiveManager = new PassiveManager(this);
        passiveManager.start();

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        AbilityListener abilityListener = new AbilityListener(this);
        getServer().getPluginManager().registerEvents(abilityListener, this);
        getServer().getPluginManager().registerEvents(new ChamberListener(this), this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new MeleePassiveListener(this, abilityListener), this);
        getServer().getPluginManager().registerEvents(new ElementalItemProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingProtectionListener(this), this);

        tradeListener = new TradeListener(this);
        getServer().getPluginManager().registerEvents(tradeListener, this);

        SleepListener sleepListener = new SleepListener(this);
        getServer().getPluginManager().registerEvents(sleepListener, this);
        sleepListener.applyToAllWorlds();

        getCommand("element").setExecutor(new ElementCommand(this));
        AdminCommandHandler adminHandler = new AdminCommandHandler(this);
        getCommand("elemental").setExecutor(adminHandler);
        getCommand("elemental").setTabCompleter(adminHandler);

        getLogger().info("ElementalSMP has been enabled. " + masteryManager.getClass().getSimpleName() + " ready.");
    }

    @Override
    public void onDisable() {
        if (masteryManager != null) {
            masteryManager.saveData();
        }
        if (chamberManager != null) {
            chamberManager.stop();
        }
        getLogger().info("ElementalSMP has been disabled.");
    }

    public static ElementalSMP getInstance() {
        return instance;
    }

    public MasteryManager getMasteryManager() {
        return masteryManager;
    }

    public ChamberManager getChamberManager() {
        return chamberManager;
    }

    public PassiveManager getPassiveManager() {
        return passiveManager;
    }

    public TradeListener getTradeListener() {
        return tradeListener;
    }
}
