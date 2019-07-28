package com.massivecraft.factions.zcore.persist;

import com.massivecraft.factions.*;
import com.massivecraft.factions.event.FPlayerLeaveEvent;
import com.massivecraft.factions.event.FactionDisbandEvent;
import com.massivecraft.factions.event.FactionDisbandEvent.PlayerDisbandReason;
import com.massivecraft.factions.iface.EconomyParticipator;
import com.massivecraft.factions.iface.RelationParticipator;
import com.massivecraft.factions.integration.Econ;
import com.massivecraft.factions.scoreboards.FTeamWrapper;
import com.massivecraft.factions.shop.Pair;
import com.massivecraft.factions.struct.BanInfo;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.LazyLocation;
import com.massivecraft.factions.util.MiscUtil;
import com.massivecraft.factions.util.RelationUtil;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.Permissable;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import com.massivecraft.factions.zcore.fupgrades.UpgradeType;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class MemoryFaction implements Faction, EconomyParticipator {
    public HashMap<Integer, String> rules = new HashMap<Integer, String>();
    public int tnt;
    public Location checkpoint;
    public LazyLocation vault;
    public HashMap<String, Integer> upgrades = new HashMap<>();
    protected String id = null;
    protected boolean peacefulExplosionsEnabled;
    protected boolean permanent;
    protected String tag;
    protected String description;
    protected boolean open;
    protected boolean peaceful;
    protected Integer permanentPower;
    protected LazyLocation home;
    protected long foundedDate;
    protected transient long lastPlayerLoggedOffTime;
    protected double money;
    protected double powerBoost;
    protected String paypal;
    protected Map<String, Relation> relationWish = new HashMap<>();
    protected Map<FLocation, Set<String>> claimOwnership = new ConcurrentHashMap<>();
    protected transient Set<FPlayer> fplayers = new HashSet<>();
    protected transient Set<FPlayer> alts = new HashSet<>();
    protected Set<String> invites = new HashSet<>();
    protected Set<String> altinvites = new HashSet<>();
    protected HashMap<String, List<String>> announcements = new HashMap<>();
    protected ConcurrentHashMap<String, LazyLocation> warps = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, String> warpPasswords = new ConcurrentHashMap<>();
    protected int maxVaults;
    protected Role defaultRole;
    protected Map<Permissable, Map<PermissableAction, Access>> permissions = new HashMap<>();
    protected Set<BanInfo> bans = new HashSet<>();
    protected String player;
    Inventory chest;
    Map<String, Object> bannerSerialized;
    private long lastDeath;
    private int strikes = 0;
    private int points = 0;
    private Map<String, Pair<Integer, Long>> potionEffects;
    private Map<String, Long> boosters;

    // -------------------------------------------- //
    // Construct
    // -------------------------------------------- //
    public MemoryFaction() {
    }

    public MemoryFaction(String id) {
        this.id = id;
        this.open = Conf.newFactionsDefaultOpen;
        this.tag = "???";
        this.description = TL.GENERIC_DEFAULTDESCRIPTION.toString();
        this.lastPlayerLoggedOffTime = 0;
        this.peaceful = false;
        this.peacefulExplosionsEnabled = false;
        this.permanent = false;
        this.money = 0.0;
        this.powerBoost = 0.0;
        this.foundedDate = System.currentTimeMillis();
        this.maxVaults = Conf.defaultMaxVaults;
        this.defaultRole = Role.RECRUIT;
        potionEffects = new ConcurrentHashMap<>();
        boosters = new ConcurrentHashMap<>();
        resetPerms(); // Reset on new Faction so it has default values.
    }

    public MemoryFaction(MemoryFaction old) {
        id = old.id;
        peacefulExplosionsEnabled = old.peacefulExplosionsEnabled;
        permanent = old.permanent;
        tag = old.tag;
        description = old.description;
        open = old.open;
        foundedDate = old.foundedDate;
        peaceful = old.peaceful;
        permanentPower = old.permanentPower;
        home = old.home;
        lastPlayerLoggedOffTime = old.lastPlayerLoggedOffTime;
        money = old.money;
        powerBoost = old.powerBoost;
        relationWish = old.relationWish;
        claimOwnership = old.claimOwnership;
        fplayers = new HashSet<>();
        potionEffects = new ConcurrentHashMap<>();
        boosters = new ConcurrentHashMap<>();
        alts = new HashSet<>();
        invites = old.invites;
        announcements = old.announcements;
        this.defaultRole = Role.NORMAL;

        resetPerms(); // Reset on new Faction so it has default values.
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getStrikes() {
        return strikes;
    }

    public void setStrikes(int strikes) {
        this.strikes = strikes;
    }

    public HashMap<String, List<String>> getAnnouncements() {
        return this.announcements;
    }

    public void addAnnouncement(FPlayer fPlayer, String msg) {
        List<String> list = announcements.containsKey(fPlayer.getId()) ? announcements.get(fPlayer.getId()) : new ArrayList<String>();
        list.add(msg);
        announcements.put(fPlayer.getId(), list);
    }

    public void sendUnreadAnnouncements(FPlayer fPlayer) {
        if (!announcements.containsKey(fPlayer.getId())) {
            return;
        }
        fPlayer.msg(TL.FACTIONS_ANNOUNCEMENT_TOP);
        for (String s : announcements.get(fPlayer.getPlayer().getUniqueId().toString())) {
            fPlayer.sendMessage(s);
        }
        fPlayer.msg(TL.FACTIONS_ANNOUNCEMENT_BOTTOM);
        announcements.remove(fPlayer.getId());
    }

    public void removeAnnouncements(FPlayer fPlayer) {
        announcements.remove(fPlayer.getId());
    }

    public ConcurrentHashMap<String, LazyLocation> getWarps() {
        return this.warps;
    }

    public LazyLocation getWarp(String name) {
        return this.warps.get(name);
    }

    public void setWarp(String name, LazyLocation loc) {
        this.warps.put(name, loc);
    }

    public boolean isWarp(String name) {
        return this.warps.containsKey(name);
    }

    public boolean removeWarp(String name) {
        warpPasswords.remove(name); // remove password no matter what.
        return warps.remove(name) != null;
    }

    public boolean isWarpPassword(String warp, String password) {
        return hasWarpPassword(warp) && warpPasswords.get(warp.toLowerCase()).equals(password);
    }

    public String getPaypal() {
        return this.paypal;
    }

    public void paypalSet(String paypal) {
        this.paypal = paypal;
    }

    public boolean hasWarpPassword(String warp) {
        return warpPasswords.containsKey(warp.toLowerCase());
    }

    public void setWarpPassword(String warp, String password) {
        warpPasswords.put(warp.toLowerCase(), password);
    }

    public void clearWarps() {
        warps.clear();
    }

    public int getMaxVaults() {
        return this.maxVaults;
    }

    public void setMaxVaults(int value) {
        this.maxVaults = value;
    }

    public String getFocused() {
        return this.player;
    }

    public void setFocused(String fp) {
        this.player = fp;
    }

    public Set<String> getInvites() {
        return invites;
    }

    public Set<String> getAltInvites() {
        return altinvites;
    }

    public void deinviteAlt(FPlayer fplayer) {
        altinvites.remove(fplayer.getId());
    }

    public void deinviteAllAlts() {
        altinvites.clear();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void invite(FPlayer fplayer) {
        this.invites.add(fplayer.getId());
    }

    public void altInvite(FPlayer fplayer) {
        this.altinvites.add(fplayer.getId());
    }

    public void deinvite(FPlayer fplayer) {
        this.invites.remove(fplayer.getId());
        this.altinvites.remove(fplayer.getId());
    }

    public boolean altInvited(FPlayer fplayer) {
        if (this.altinvites.contains(fplayer.getId()))
            return true;
        return false;
    }

    public boolean isInvited(FPlayer fplayer) {
        if (this.invites.contains(fplayer.getId()) || this.altinvites.contains(fplayer.getId())) {
            return true;
        }
        return false;
    }

    public void ban(FPlayer target, FPlayer banner) {
        BanInfo info = new BanInfo(banner.getId(), target.getId(), System.currentTimeMillis());
        this.bans.add(info);
    }

    public void unban(FPlayer player) {
        bans.removeIf(banInfo -> banInfo.getBanned().equalsIgnoreCase(player.getId()));
    }

    @Override
    public void disband(Player disbander) {
        disband(disbander, PlayerDisbandReason.PLUGIN);
    }

    @Override
    public void disband(Player disbander, PlayerDisbandReason reason) {

        boolean disbanderIsConsole = disbander == null;
        FPlayer fdisbander = FPlayers.getInstance().getByOfflinePlayer(disbander);

        FactionDisbandEvent disbandEvent = new FactionDisbandEvent(disbander, this.getId(), reason);
        Bukkit.getServer().getPluginManager().callEvent(disbandEvent);
        if (disbandEvent.isCancelled()) {
            return;
        }

        // Send FPlayerLeaveEvent for each player in the faction
        for (FPlayer fplayer : this.getFPlayers()) {
            Bukkit.getServer().getPluginManager().callEvent(new FPlayerLeaveEvent(fplayer, this, FPlayerLeaveEvent.PlayerLeaveReason.DISBAND));
        }

        if (Conf.logFactionDisband) {
            //TODO: Format this correctly and translate.
            SaberFactions.plugin.log("The faction " + this.getTag() + " (" + this.getId() + ") was disbanded by " + (disbanderIsConsole ? "console command" : fdisbander.getName()) + ".");
        }

        if (Econ.shouldBeUsed() && !disbanderIsConsole) {
            // Should we prevent to withdraw money if the faction was just created
            if (Conf.econFactionStartingBalance != 0 && (System.currentTimeMillis() - this.foundedDate) <= (Conf.econDenyWithdrawWhenMinutesAgeLessThan * 6000)) {
                msg("Your faction is too young to withdraw money like this");
            } else {
                //Give all the faction's money to the disbander
                double amount = Econ.getBalance(this.getAccountId());
                Econ.transferMoney(fdisbander, this, fdisbander, amount, false);

                if (amount > 0.0) {
                    String amountString = Econ.moneyString(amount);
                    msg(TL.COMMAND_DISBAND_HOLDINGS, amountString);
                    //TODO: Format this correctly and translate
                    SaberFactions.plugin.log(fdisbander.getName() + " has been given bank holdings of " + amountString + " from disbanding " + this.getTag() + ".");
                }
            }
        }

        Factions.getInstance().removeFaction(this.getId());
        FTeamWrapper.applyUpdates(this);
    }

    public boolean isBanned(FPlayer player) {
        for (BanInfo info : bans) {
            if (info.getBanned().equalsIgnoreCase(player.getId())) {
                return true;
            }
        }

        return false;
    }

    public Set<BanInfo> getBannedPlayers() {
        return this.bans;
    }

    public String getRule(int index) {
        if (rules.size() == 0) return null;
        return rules.get(index);
    }

    public HashMap<Integer, String> getRulesMap() {
        return rules;
    }

    public void setRule(int index, String rule) {
        rules.put(index, rule);
    }

    public void removeRule(int index) {
        HashMap<Integer, String> newRule = rules;
        newRule.remove(index);
        rules = newRule;
    }

    public void addTnt(int amt) {
        tnt += amt;
    }

    public void takeTnt(int amt) {
        tnt -= amt;
    }

    public int getTnt() {
        return tnt;
    }


    public Location getVault() {
        if (vault == null) {
            return null;
        }
        return vault.getLocation();
    }

    public void setVault(Location vaultLocation) {
        if (vaultLocation == null) {
            vault = null;
            return;
        }
        LazyLocation newlocation = new LazyLocation(vaultLocation);
        vault = newlocation;
    }

    public int getUpgrade(UpgradeType upgrade) {
        if (upgrades.keySet().contains(upgrade.toString())) return upgrades.get(upgrade.toString());
        return 0;
    }

    @Override
    public Inventory getChestInventory() {
        if (chest != null) return chest;

        int size = 9;
        switch (getUpgrade(UpgradeType.CHEST)) {
            case 1:
                size = SaberFactions.plugin.getConfig().getInt("fupgrades.MainMenu.Chest.Chest-Size.level-1") * 9;
                break;
            case 2:
                size = SaberFactions.plugin.getConfig().getInt("fupgrades.MainMenu.Chest.Chest-Size.level-2") * 9;
                break;
            case 3:
                size = SaberFactions.plugin.getConfig().getInt("fupgrades.MainMenu.Chest.Chest-Size.level-3") * 9;
                break;
        }
        chest = Bukkit.createInventory(null, size, SaberFactions.plugin.color(SaberFactions.plugin.getConfig().getString("fchest.Inventory-Title")));
        return chest;
    }


    @Override
    public void setChestSize(int chestSize) {
        ItemStack[] contents = this.getChestInventory().getContents();
        chest = Bukkit.createInventory(null, chestSize, SaberFactions.plugin.color(SaberFactions.plugin.getConfig().getString("fchest.Inventory-Title")));
        chest.setContents(contents);
    }

    @Override
    public Map<String, Long> getBoosters() {
        return this.boosters;
    }


    @Override
    public void setBannerPattern(ItemStack banner) {
        bannerSerialized = banner.serialize();
    }

    @Override
    public ItemStack getBanner() {
        if (bannerSerialized == null) {
            return null;
        }
        return ItemStack.deserialize(bannerSerialized);
    }

    public void setUpgrade(UpgradeType upgrade, int level) {
        upgrades.put(upgrade.toString(), level);
    }

    public Location getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Location location) {
        checkpoint = location;
    }

    public void clearRules() {
        rules.clear();
    }

    public void addRule(String rule) {
        rules.put(rules.size(), rule);
    }

    public boolean getOpen() {
        return open;
    }

    public void setOpen(boolean isOpen) {
        open = isOpen;
    }

    public boolean isPeaceful() {
        return this.peaceful;
    }

    public void setPeaceful(boolean isPeaceful) {
        this.peaceful = isPeaceful;
    }

    public boolean getPeacefulExplosionsEnabled() {
        return this.peacefulExplosionsEnabled;
    }

    public void setPeacefulExplosionsEnabled(boolean val) {
        peacefulExplosionsEnabled = val;
    }

    public boolean noExplosionsInTerritory() {
        return this.peaceful && !peacefulExplosionsEnabled;
    }

    public boolean isPermanent() {
        return permanent || !this.isNormal();
    }

    public void setPermanent(boolean isPermanent) {
        permanent = isPermanent;
    }

    public String getTag() {
        return this.tag;
    }

    public void setTag(String str) {
        if (Conf.factionTagForceUpperCase) {
            str = str.toUpperCase();
        }
        this.tag = str;
    }

    public String getTag(String prefix) {
        return prefix + this.tag;
    }

    public String getTag(Faction otherFaction) {
        if (otherFaction == null) {
            return getTag();
        }
        return this.getTag(this.getColorTo(otherFaction).toString());
    }

    public String getTag(FPlayer otherFplayer) {
        if (otherFplayer == null) {
            return getTag();
        }
        return this.getTag(this.getColorTo(otherFplayer).toString());
    }

    public String getComparisonTag() {
        return MiscUtil.getComparisonString(this.tag);
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String value) {
        this.description = value;
    }

    public boolean hasHome() {
        return this.getHome() != null;
    }

    public Location getHome() {
        confirmValidHome();
        return (this.home != null) ? this.home.getLocation() : null;
    }

    public void setHome(Location home) {
        this.home = new LazyLocation(home);
    }

    public long getFoundedDate() {
        if (this.foundedDate == 0) {
            setFoundedDate(System.currentTimeMillis());
        }
        return this.foundedDate;
    }

    public void setFoundedDate(long newDate) {
        this.foundedDate = newDate;
    }

    public void confirmValidHome() {
        if (!Conf.homesMustBeInClaimedTerritory || this.home == null || (this.home.getLocation() != null && Board.getInstance().getFactionAt(new FLocation(this.home.getLocation())) == this)) {
            return;
        }

        msg("<b>Your faction home has been un-set since it is no longer in your territory.");
        this.home = null;
    }

    public String getAccountId() {
        String aid = "faction-" + this.getId();

        // We need to override the default money given to players.
        if (!Econ.hasAccount(aid)) {
            Econ.setBalance(aid, 0);
        }

        return aid;
    }

    public Integer getPermanentPower() {
        return this.permanentPower;
    }

    public void setPermanentPower(Integer permanentPower) {
        this.permanentPower = permanentPower;
    }

    public boolean hasPermanentPower() {
        return this.permanentPower != null;
    }

    public double getPowerBoost() {
        return this.powerBoost;
    }

    public void setPowerBoost(double powerBoost) {
        this.powerBoost = powerBoost;
    }

    public boolean isPowerFrozen() {
        int freezeSeconds = SaberFactions.plugin.getConfig().getInt("hcf.powerfreeze", 0);
        return freezeSeconds != 0 && System.currentTimeMillis() - lastDeath < freezeSeconds * 1000;

    }

    public long getLastDeath() {
        return this.lastDeath;
    }

    // -------------------------------------------- //
    // F Permissions stuff
    // -------------------------------------------- //

    public void setLastDeath(long time) {
        this.lastDeath = time;
    }

    public int getKills() {
        int kills = 0;
        for (FPlayer fp : getFPlayers()) {
            kills += fp.getKills();
        }

        return kills;
    }

    public int getDeaths() {
        int deaths = 0;
        for (FPlayer fp : getFPlayers()) {
            deaths += fp.getDeaths();
        }

        return deaths;
    }

    public Access getAccess(Permissable permissable, PermissableAction permissableAction) {
        if (permissable == null || permissableAction == null) {
            return Access.UNDEFINED;
        }

        Map<PermissableAction, Access> accessMap = permissions.get(permissable);
        if (accessMap != null && accessMap.containsKey(permissableAction)) {
            return accessMap.get(permissableAction);
        }

        return Access.UNDEFINED;
    }

    /**
     * Get the Access of a player. Will use player's Role if they are a faction member. Otherwise, uses their Relation.
     *
     * @param player
     * @param permissableAction
     * @return
     */
    public Access getAccess(FPlayer player, PermissableAction permissableAction) {
        if (player == null || permissableAction == null) {
            return Access.UNDEFINED;
        }

        Permissable perm;

        if (player.getFaction() == this) {
            perm = player.getRole();
        } else {
            perm = player.getFaction().getRelationTo(this);
        }

        Map<PermissableAction, Access> accessMap = permissions.get(perm);
        if (accessMap != null && accessMap.containsKey(permissableAction)) {
            return accessMap.get(permissableAction);
        }

        return Access.UNDEFINED;
    }

    public void setPermission(Permissable permissable, PermissableAction permissableAction, Access access) {
        Map<PermissableAction, Access> accessMap = permissions.get(permissable);
        if (accessMap == null) {
            accessMap = new HashMap<>();
        }

        accessMap.put(permissableAction, access);
    }

    public void resetPerms() {
        SaberFactions.plugin.log(Level.WARNING, "Resetting permissions for Faction: " + tag);

        permissions.clear();

        // First populate a map with undefined as the permission for each action.
        Map<PermissableAction, Access> freshMap = new HashMap<>();
        for (PermissableAction permissableAction : PermissableAction.values()) {
            freshMap.put(permissableAction, Access.UNDEFINED);
        }

        // Put the map in there for each relation.
        for (Relation relation : Relation.values()) {
            if (relation != Relation.MEMBER) {
                permissions.put(relation, new HashMap<>(freshMap));
            }
        }

        // And each role.
        for (Role role : Role.values()) {
            if (role != Role.LEADER) {
                permissions.put(role, new HashMap<>(freshMap));
            }
        }
    }

    public void setDefaultPerms() {
        if (!Conf.useCustomDefaultPermissions) return;
        Map<PermissableAction, Access> defaultMap = new HashMap<>();
        for (PermissableAction permissableAction : PermissableAction.values()) {
            defaultMap.put(permissableAction, Access.UNDEFINED);
        }
        // Put the map in there for each relation.
        for (Relation relation : Relation.values()) {
            if (relation != Relation.MEMBER) {
                if (!Conf.defaultFactionPermissions.containsKey(relation.nicename.toUpperCase()))
                    permissions.put(relation, new HashMap<>(defaultMap));
                else
                    permissions.put(relation, PermissableAction.fromDefaults(Conf.defaultFactionPermissions.get(relation.nicename.toUpperCase())));
            }
        }

        // And each role.
        for (Role role : Role.values()) {
            if (role != Role.LEADER) {
                if (!Conf.defaultFactionPermissions.containsKey(role.nicename.toUpperCase()))
                    permissions.put(role, new HashMap<>(defaultMap));
                else
                    permissions.put(role, PermissableAction.fromDefaults(Conf.defaultFactionPermissions.get(role.nicename.toUpperCase())));
            }
        }
    }

    /**
     * Read only map of Permissions.
     *
     * @return
     */
    public Map<Permissable, Map<PermissableAction, Access>> getPermissions() {
        return Collections.unmodifiableMap(permissions);
    }

    public Role getDefaultRole() {
        return this.defaultRole;
    }

    public void setDefaultRole(Role role) {
        this.defaultRole = role;
    }

    // -------------------------------------------- //
    // Extra Getters And Setters
    // -------------------------------------------- //
    public boolean noPvPInTerritory() {
        return isSafeZone() || (peaceful && Conf.peacefulTerritoryDisablePVP);
    }

    public boolean noMonstersInTerritory() {
        return isSafeZone() || (peaceful && Conf.peacefulTerritoryDisableMonsters);
    }

    // -------------------------------
    // Understand the types
    // -------------------------------

    public boolean isNormal() {
        return !(this.isWilderness() || this.isSafeZone() || this.isWarZone());
    }

    public boolean isNone() {
        return this.getId().equals("0");
    }

    public boolean isWilderness() {
        return this.getId().equals("0");
    }

    public boolean isSafeZone() {
        return this.getId().equals("-1");
    }

    public boolean isWarZone() {
        return this.getId().equals("-2");
    }

    public boolean isSystemFaction() {
        return this.isSafeZone() || this.isWarZone() || this.isWilderness();
    }

    public boolean isPlayerFreeType() {
        return this.isSafeZone() || this.isWarZone();
    }

    // -------------------------------
    // Relation and relation colors
    // -------------------------------

    @Override
    public String describeTo(RelationParticipator that, boolean ucfirst) {
        return RelationUtil.describeThatToMe(this, that, ucfirst);
    }

    @Override
    public String describeTo(RelationParticipator that) {
        return RelationUtil.describeThatToMe(this, that);
    }

    @Override
    public Relation getRelationTo(RelationParticipator rp) {
        return RelationUtil.getRelationTo(this, rp);
    }

    @Override
    public Relation getRelationTo(RelationParticipator rp, boolean ignorePeaceful) {
        return RelationUtil.getRelationTo(this, rp, ignorePeaceful);
    }

    @Override
    public ChatColor getColorTo(RelationParticipator rp) {
        return RelationUtil.getColorOfThatToMe(this, rp);
    }

    public Relation getRelationWish(Faction otherFaction) {
        if (this.relationWish.containsKey(otherFaction.getId())) {
            return this.relationWish.get(otherFaction.getId());
        }
        return Relation.fromString(SaberFactions.plugin.getConfig().getString("default-relation", "neutral")); // Always default to old behavior.
    }

    public void setRelationWish(Faction otherFaction, Relation relation) {
        if (this.relationWish.containsKey(otherFaction.getId()) && relation.equals(Relation.NEUTRAL)) {
            this.relationWish.remove(otherFaction.getId());
        } else {
            this.relationWish.put(otherFaction.getId(), relation);
        }
    }

    public int getRelationCount(Relation relation) {
        int count = 0;
        for (Faction faction : Factions.getInstance().getAllFactions()) {
            if (faction.getRelationTo(this) == relation) {
                count++;
            }
        }
        return count;
    }

    // ----------------------------------------------//
    // Power
    // ----------------------------------------------//
    public double getPower() {
        if (this.hasPermanentPower()) return this.getPermanentPower();


        double ret = 0;
        for (FPlayer fplayer : fplayers) ret += fplayer.getPower();
        for (FPlayer fplayer : alts) ret += fplayer.getPower();
        if (Conf.powerFactionMax > 0 && ret > Conf.powerFactionMax) {
            ret = Conf.powerFactionMax;
        }
        return ret + this.powerBoost;
    }

    public double getPowerMax() {
        if (this.hasPermanentPower()) return this.getPermanentPower();


        double ret = 0;
        for (FPlayer fplayer : fplayers) ret += fplayer.getPowerMax();

        for (FPlayer fplayer : alts) ret += fplayer.getPowerMax();

        if (Conf.powerFactionMax > 0 && ret > Conf.powerFactionMax) {
            ret = Conf.powerFactionMax;
        }
        return ret + this.powerBoost;
    }

    public int getPowerRounded() {
        return (int) Math.round(this.getPower());
    }

    public int getPowerMaxRounded() {
        return (int) Math.round(this.getPowerMax());
    }

    public int getLandRounded() {
        return Board.getInstance().getFactionCoordCount(this);
    }

    public int getLandRoundedInWorld(String worldName) {
        return Board.getInstance().getFactionCoordCountInWorld(this, worldName);
    }

    public boolean hasLandInflation() {
        return this.getLandRounded() > this.getPowerRounded();
    }

    // -------------------------------
    // FPlayers
    // -------------------------------

    // maintain the reference list of FPlayers in this faction
    public void refreshFPlayers() {
        fplayers.clear();
        alts.clear();
        if (this.isPlayerFreeType()) {
            return;
        }

        for (FPlayer fplayer : FPlayers.getInstance().getAllFPlayers()) {
            if (fplayer.getFactionId().equalsIgnoreCase(id)) {
                if (fplayer.isAlt()) {
                    alts.add(fplayer);
                } else {
                    fplayers.add(fplayer);
                }
            }
        }
    }

    public boolean addFPlayer(FPlayer fplayer) {
        return !this.isPlayerFreeType() && fplayers.add(fplayer);
    }

    public boolean removeFPlayer(FPlayer fplayer) {
        return !this.isPlayerFreeType() && fplayers.remove(fplayer);
    }


    public boolean addAltPlayer(FPlayer fplayer) {
        return !this.isPlayerFreeType() && alts.add(fplayer);
    }

    public boolean removeAltPlayer(FPlayer fplayer) {
        return !this.isPlayerFreeType() && alts.remove(fplayer);
    }

    public int getSize() {
        return fplayers.size() + alts.size();
    }

    public Set<FPlayer> getFPlayers() {
        // return a shallow copy of the FPlayer list, to prevent tampering and
        // concurrency issues
        return new HashSet<>(fplayers);
    }

    public Set<FPlayer> getAltPlayers() {
        // return a shallow copy of the FPlayer list, to prevent tampering and
        // concurrency issues
        return new HashSet<>(alts);
    }

    public Set<FPlayer> getFPlayersWhereOnline(boolean online) {
        Set<FPlayer> ret = new HashSet<>();

        for (FPlayer fplayer : fplayers) {
            if (fplayer.isOnline() == online) {
                ret.add(fplayer);
            }
        }

        return ret;
    }

    public Set<FPlayer> getFPlayersWhereOnline(boolean online, FPlayer viewer) {
        Set<FPlayer> ret = new HashSet<>();
        if (!this.isNormal()) {
            return ret;
        }

        for (FPlayer viewed : fplayers) {
            // Add if their online status is what we want
            if (viewed.isOnline() == online) {
                // If we want online, check to see if we are able to see this player
                // This checks if they are in vanish.
                if (online
                        && viewed.getPlayer() != null
                        && viewer.getPlayer() != null
                        && viewer.getPlayer().canSee(viewed.getPlayer())) {
                    ret.add(viewed);
                    // If we want offline, just add them.
                    // Prob a better way to do this but idk.
                } else if (!online) {
                    ret.add(viewed);
                }
            }
        }

        return ret;
    }


    public FPlayer getFPlayerAdmin() {
        if (!this.isNormal()) return null;


        for (FPlayer fplayer : fplayers) {
            if (fplayer.getRole() == Role.LEADER) {
                return fplayer;
            }
        }
        return null;
    }

    public FPlayer getFPlayerLeader() {
        return getFPlayerAdmin();
    }

    public ArrayList<FPlayer> getFPlayersWhereRole(Role role) {
        ArrayList<FPlayer> ret = new ArrayList<>();
        if (!this.isNormal()) {
            return ret;
        }

        for (FPlayer fplayer : fplayers) {
            if (fplayer.getRole() == role) {
                ret.add(fplayer);
            }
        }

        return ret;
    }


    public ArrayList<Player> getOnlinePlayers() {
        ArrayList<Player> ret = new ArrayList<>();
        if (this.isPlayerFreeType()) {
            return ret;
        }

        for (Player player : SaberFactions.plugin.getServer().getOnlinePlayers()) {
            FPlayer fplayer = FPlayers.getInstance().getByPlayer(player);
            if (fplayer.getFaction() == this && !fplayer.isAlt()) {
                ret.add(player);
            }
        }

        return ret;
    }

    // slightly faster check than getOnlinePlayers() if you just want to see if
    // there are any players online
    public boolean hasPlayersOnline() {
        // only real factions can have players online, not safe zone / war zone
        if (this.isPlayerFreeType()) {
            return false;
        }

        for (Player player : SaberFactions.plugin.getServer().getOnlinePlayers()) {
            FPlayer fplayer = FPlayers.getInstance().getByPlayer(player);
            if (fplayer != null && fplayer.getFaction() == this) {
                return true;
            }
        }

        // even if all players are technically logged off, maybe someone was on
        // recently enough to not consider them officially offline yet
        return Conf.considerFactionsReallyOfflineAfterXMinutes > 0 && System.currentTimeMillis() < lastPlayerLoggedOffTime + (Conf.considerFactionsReallyOfflineAfterXMinutes * 60000);
    }

    public void memberLoggedOff() {
        if (this.isNormal()) {
            lastPlayerLoggedOffTime = System.currentTimeMillis();
        }
    }

    // used when current leader is about to be removed from the faction;
    // promotes new leader, or disbands faction if no other members left
    @Override
    public void promoteNewLeader() {
        promoteNewLeader(false);
    }

    @Override
    public void promoteNewLeader(boolean autoLeave) {
        if (!this.isNormal()) {
            return;
        }
        if (this.isPermanent() && Conf.permanentFactionsDisableLeaderPromotion) {
            return;
        }

        FPlayer oldLeader = this.getFPlayerAdmin();

        // get list of moderators, or list of normal members if there are no moderators
        ArrayList<FPlayer> replacements = this.getFPlayersWhereRole(Role.MODERATOR);
        if (replacements == null || replacements.isEmpty()) {
            replacements = this.getFPlayersWhereRole(Role.NORMAL);
        }

        if (replacements == null || replacements.isEmpty()) { // faction admin  is the only  member; one-man  faction
            if (this.isPermanent()) {
                if (oldLeader != null) {
                    oldLeader.setRole(Role.NORMAL);
                }
                return;
            }

            // no members left and faction isn't permanent, so disband it
            if (Conf.logFactionDisband) {
                SaberFactions.plugin.log("The faction " + this.getTag() + " (" + this.getId() + ") has been disbanded since it has no members left" + (autoLeave ? " and by inactivity" : "") + ".");
            }

            for (FPlayer fplayer : FPlayers.getInstance().getOnlinePlayers()) {
                fplayer.msg("The faction %s<i> was disbanded.", this.getTag(fplayer));
            }

            FactionDisbandEvent disbandEvent = new FactionDisbandEvent(null, getId(), autoLeave ? PlayerDisbandReason.INACTIVITY : PlayerDisbandReason.LEAVE);
            Bukkit.getPluginManager().callEvent(disbandEvent);

            Factions.getInstance().removeFaction(getId());
        } else { // promote new faction admin
            if (oldLeader != null) {
                oldLeader.setRole(Role.NORMAL);
            }
            replacements.get(0).setRole(Role.LEADER);
            //TODO:TL
            this.msg("<i>Faction admin <h>%s<i> has been removed. %s<i> has been promoted as the new faction admin.", oldLeader == null ? "" : oldLeader.getName(), replacements.get(0).getName());
            SaberFactions.plugin.log("Faction " + this.getTag() + " (" + this.getId() + ") admin was removed. Replacement admin: " + replacements.get(0).getName());
        }
    }

    // ----------------------------------------------//
    // Messages
    // ----------------------------------------------//
    public void msg(String message, Object... args) {
        message = SaberFactions.plugin.txt.parse(message, args);

        for (FPlayer fplayer : this.getFPlayersWhereOnline(true)) {
            fplayer.sendMessage(message);
        }
    }

    public void msg(TL translation, Object... args) {
        msg(translation.toString(), args);
    }

    public void sendMessage(String message) {
        for (FPlayer fplayer : this.getFPlayersWhereOnline(true)) {
            fplayer.sendMessage(message);
        }
    }

    public void sendMessage(List<String> messages) {
        for (FPlayer fplayer : this.getFPlayersWhereOnline(true)) {
            fplayer.sendMessage(messages);
        }
    }

    // ----------------------------------------------//
    // Ownership of specific claims
    // ----------------------------------------------//

    public Map<FLocation, Set<String>> getClaimOwnership() {
        return claimOwnership;
    }

    public void clearAllClaimOwnership() {
        claimOwnership.clear();
    }

    public void clearClaimOwnership(FLocation loc) {
        claimOwnership.remove(loc);
    }

    public void clearClaimOwnership(FPlayer player) {
        if (id == null || id.isEmpty()) {
            return;
        }

        Set<String> ownerData;

        for (Entry<FLocation, Set<String>> entry : claimOwnership.entrySet()) {
            ownerData = entry.getValue();

            if (ownerData == null) {
                continue;
            }

            ownerData.removeIf(s -> s.equals(player.getId()));

            if (ownerData.isEmpty()) {
                claimOwnership.remove(entry.getKey());
            }
        }
    }

    public int getCountOfClaimsWithOwners() {
        return claimOwnership.isEmpty() ? 0 : claimOwnership.size();
    }

    public boolean doesLocationHaveOwnersSet(FLocation loc) {
        if (claimOwnership.isEmpty() || !claimOwnership.containsKey(loc)) {
            return false;
        }

        Set<String> ownerData = claimOwnership.get(loc);
        return ownerData != null && !ownerData.isEmpty();
    }

    public boolean isPlayerInOwnerList(FPlayer player, FLocation loc) {
        if (claimOwnership.isEmpty()) {
            return false;
        }
        Set<String> ownerData = claimOwnership.get(loc);
        return ownerData != null && ownerData.contains(player.getId());
    }

    public void setPlayerAsOwner(FPlayer player, FLocation loc) {
        Set<String> ownerData = claimOwnership.get(loc);
        if (ownerData == null) {
            ownerData = new HashSet<>();
        }
        ownerData.add(player.getId());
        claimOwnership.put(loc, ownerData);
    }

    public void removePlayerAsOwner(FPlayer player, FLocation loc) {
        Set<String> ownerData = claimOwnership.get(loc);
        if (ownerData == null) return;

        ownerData.remove(player.getId());
        claimOwnership.put(loc, ownerData);
    }

    public Set<String> getOwnerList(FLocation loc) {
        return claimOwnership.get(loc);
    }

    @Override
    public Map<String, Pair<Integer, Long>> getActivePotions() {
        return this.potionEffects;
    }

    @Override
    public Map<String, Pair<Integer, Long>> getPotionEffects() {
        return this.potionEffects;
    }

    public String getOwnerListString(FLocation loc) {
        Set<String> ownerData = claimOwnership.get(loc);
        if (ownerData == null || ownerData.isEmpty()) {
            return "";
        }

        StringBuilder ownerList = new StringBuilder();

        for (String anOwnerData : ownerData) {
            if (ownerList.length() > 0) {
                ownerList.append(", ");
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(anOwnerData));
            //TODO:TL
            ownerList.append(offlinePlayer != null ? offlinePlayer.getName() : "null player");
        }
        return ownerList.toString();
    }

    public boolean playerHasOwnershipRights(FPlayer fplayer, FLocation loc) {
        // in own faction, with sufficient role or permission to bypass
        // ownership?
        if (fplayer.getFaction() == this && (fplayer.getRole().isAtLeast(Conf.ownedAreaModeratorsBypass ? Role.MODERATOR : Role.LEADER) || Permission.OWNERSHIP_BYPASS.has(fplayer.getPlayer()))) return true;

        // make sure claimOwnership is initialized
        if (claimOwnership.isEmpty()) return true;

        // need to check the ownership list, then
        Set<String> ownerData = claimOwnership.get(loc);

        // if no owner list, owner list is empty, or player is in owner list,
        // they're allowed
        return ownerData == null || ownerData.isEmpty() || ownerData.contains(fplayer.getId());
    }

    // ----------------------------------------------//
    // Persistance and entity management
    // ----------------------------------------------//
    public void remove() {
        if (Econ.shouldBeUsed()) {
            Econ.setBalance(getAccountId(), 0);
        }

        // Clean the board
        ((MemoryBoard) Board.getInstance()).clean(id);

        for (FPlayer fPlayer : fplayers) {
            fPlayer.resetFactionData(false);
        }

        for (FPlayer fPlayer : alts) {
            fPlayer.resetFactionData(false);
        }
    }

    public Set<FLocation> getAllClaims() { return Board.getInstance().getAllClaims(this); }
}
