package com.empcraft.plot2dynmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.flag.Flag;
import com.intellectualcrafters.plot.flag.FlagManager;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.RegionWrapper;
import com.intellectualcrafters.plot.util.ExpireManager;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.UUIDHandler;

/**
 *
 * A lot of this code is reused from the examples provided by 'mikeprimm' - creator of dynmap
 *
 */
public class Main extends JavaPlugin implements Listener {
    private static final String DEF_INFOWINDOW = "<div class=\"infowindow\">" + "<span style=\"font-size:120%;\">%id%</span><br>" + "%alias%" + "%owner%" + "%members%" + "%trusted%" + "%flags%" + "</div>";
    public static final String DEF_INFOELEMENT = "%key% <span style=\"font-weight:bold;\">%values%</span><br>";
    
    public DynmapAPI dynAPI;
    public MarkerAPI markerapi;
    public Plugin dynmap;
    public Plugin plot2;
    
    private static MarkerSet set;
    private long updperiod;
    private String infowindow;
    private String infoelement;
    private AreaStyle defstyle;
    private Map<String, AreaStyle> cusstyle;
    private Map<String, AreaStyle> cuswildstyle;
    private Map<String, AreaStyle> ownerstyle;
    private boolean stop;
    
    private static class AreaStyle {
        String strokecolor;
        double strokeopacity;
        int strokeweight;
        String fillcolor;
        double fillopacity;
        String label;
        
        AreaStyle(final FileConfiguration cfg, final String path, final AreaStyle def) {
            this.strokecolor = cfg.getString(path + ".strokeColor", def.strokecolor);
            this.strokeopacity = cfg.getDouble(path + ".strokeOpacity", def.strokeopacity);
            this.strokeweight = cfg.getInt(path + ".strokeWeight", def.strokeweight);
            this.fillcolor = cfg.getString(path + ".fillColor", def.fillcolor);
            this.fillopacity = cfg.getDouble(path + ".fillOpacity", def.fillopacity);
            this.label = cfg.getString(path + ".label", null);
        }
        
        AreaStyle(final FileConfiguration cfg, final String path) {
            this.strokecolor = cfg.getString(path + ".strokeColor", "#6666CC");
            this.strokeopacity = cfg.getDouble(path + ".strokeOpacity", 0.8);
            this.strokeweight = cfg.getInt(path + ".strokeWeight", 8);
            this.fillcolor = cfg.getString(path + ".fillColor", "#FFFFFF");
            this.fillopacity = cfg.getDouble(path + ".fillOpacity", 0.01);
        }
    }
    
    public void info(final String msg) {
        getLogger().info("[Plot^2] " + msg);
    }
    
    public void severe(final String msg) {
        getLogger().severe("[Plot^2] " + msg);
    }
    
    private class Plot2Update implements Runnable {
        @Override
        public void run() {
            if (!Main.this.stop) {
                updatePlots();
            }
        }
    }
    
    private Map<String, AreaMarker> resareas = new HashMap<String, AreaMarker>();
    
    private String formatInfoWindow(final PlotWrapper plot) {
        String v = "<div class=\"plotinfo\">" + this.infowindow + "</div>";
        v = v.replace("%id%", plot.id.x + "," + plot.id.y);
        v = v.replace("%alias%", this.infoelement.replace("%values%", plot.alias).replace("%key%", "Alias"));
        v = v.replace("%owner%", this.infoelement.replace("%values%", plot.owner).replace("%key%", "Owner"));
        v = v.replace("%members%", this.infoelement.replace("%values%", plot.helpers).replace("%key%", "Members"));
        v = v.replace("%trusted%", this.infoelement.replace("%values%", plot.trusted).replace("%key%", "Trusted"));
        v = v.replace("%flags%", this.infoelement.replace("%values%", plot.flags).replace("%key%", "Flags"));
        v = v.replace("%owner%", plot.owner);
        return v;
    }
    
    private void addStyle(final String plotid, final String worldid, final AreaMarker m, final PlotWrapper plot) {
        AreaStyle as = this.cusstyle.get(worldid + "/" + plotid);
        if (as == null) {
            as = this.cusstyle.get(plotid);
        }
        if (as == null) { /* Check for wildcard style matches */
            for (final String wc : this.cuswildstyle.keySet()) {
                final String[] tok = wc.split("\\|");
                if ((tok.length == 1) && plotid.startsWith(tok[0])) {
                    as = this.cuswildstyle.get(wc);
                } else if ((tok.length >= 2) && plotid.startsWith(tok[0]) && plotid.endsWith(tok[1])) {
                    as = this.cuswildstyle.get(wc);
                }
            }
        }
        if (as == null) { /* Check for owner style matches */
            if (this.ownerstyle.isEmpty() != true) {
                String owner = plot.owner;
                if (owner == null) {
                    owner = "unknown";
                }
                if (as == null) {
                    as = this.ownerstyle.get(owner.toLowerCase());
                }
            }
        }
        if (as == null) {
            as = this.defstyle;
        }
        
        int sc = 0xFF0000;
        int fc = 0xFF0000;
        try {
            sc = Integer.parseInt(as.strokecolor.substring(1), 16);
            fc = Integer.parseInt(as.fillcolor.substring(1), 16);
        } catch (final NumberFormatException nfx) {
            nfx.printStackTrace();
        }
        if (as.strokeweight != 0) {
            m.setLineStyle(as.strokeweight, as.strokeopacity, sc);
        }
        if (as.fillopacity != 0) {
            m.setFillStyle(as.fillopacity, fc);
        }
        if (as.label != null) {
            m.setLabel(as.label);
        }
    }
    
    private void handlePlot(final World world, final PlotWrapper plot, final Map<String, AreaMarker> newmap) {
        final String name = plot.id.x + "," + plot.id.y;
        double[] x = null;
        double[] z = null;
        
        final String id = name;
        
        for (RegionWrapper region : MainUtil.getRegions(MainUtil.getPlot(world.getName(), plot.id))) {

            x = new double[4];
            z = new double[4];
            x[0] = region.minX;
            z[0] = region.minZ;
            x[1] = region.minX;
            z[1] = region.maxZ;
            x[2] = region.maxX;
            z[2] = region.maxZ;
            x[3] = region.maxX;
            z[3] = region.minZ;
            
            final String markerid = world.getName() + "_" + id;
            AreaMarker m = this.resareas.remove(markerid); /* Existing area? */
            if (m == null) {
                m = set.createAreaMarker(markerid, name, false, world.getName(), x, z, false);
                if (m == null) {
                    return;
                }
            } else {
                m.setCornerLocations(x, z); /* Replace corner locations */
                m.setLabel(name); /* Update label */
            }
            
            addStyle(id, world.getName(), m, plot);
            
            final String desc = formatInfoWindow(plot);
            
            m.setDescription(desc); /* Set popup */
            
            newmap.put(markerid, m);
        }
    }
    
    private void updatePlots() {
        final Map<String, AreaMarker> newmap = new HashMap<String, AreaMarker>(); /* Build new map */
        try {
            for (final World w : getServer().getWorlds()) {
                if (PS.get().isPlotWorld(w.getName())) {
                    List<Plot> plots = new ArrayList<>(PS.get().getPlotsInWorld(w.getName()));
                    if (plots.size() > 4096) {
                        Collections.sort(plots, new Comparator<Plot>() {
                            @Override
                            public int compare(Plot a, Plot b) {
                                PlotPlayer p1 = UUIDHandler.getPlayer(a.owner);
                                PlotPlayer p2 = UUIDHandler.getPlayer(b.owner);
                                if (p1 == p2) {
                                    Long l1 = ExpireManager.dates.get(a.owner);
                                    Long l2 = ExpireManager.dates.get(b.owner);
                                    if (l1 == l2) {
                                        return Math.abs(a.hashCode()) - Math.abs(b.hashCode());
                                    }
                                    if (l2 == null) {
                                        return -1;
                                    }
                                    if (l1 == null) {
                                        return 1;
                                    }
                                    if (l1 > l2) {
                                        return -1;
                                    }
                                    return 1;
                                }
                                if (p2 == null) {
                                    return -1;
                                }
                                return 1;
                            }
                        });
                        plots = plots.subList(0, 4096);
                    }
                    for (final Plot plot : plots) {
                        String owner = MainUtil.getName(plot.owner);
                        if (owner == null) {
                            owner = "unknown";
                        }
                        final String[] helpers_list = new String[plot.getMembers().size()];
                        int i = 0;
                        for (UUID member : plot.getMembers()) {
                            helpers_list[i] = MainUtil.getName(member);
                            i++;
                        }
                        String helpers = "";
                        if (helpers_list.length > 0) {
                            helpers = StringUtils.join(helpers_list, ",");
                        }
                        final String[] trusted_list = new String[plot.getTrusted().size()];
                        i = 0;
                        for (UUID trusted : plot.getTrusted()) {
                            trusted_list[i] = MainUtil.getName(trusted);
                            i++;
                        }
                        String trusted = "";
                        if (trusted_list.length > 0) {
                            trusted = StringUtils.join(trusted_list, ",");
                        }
                        
                        String alias = plot.toString();
                        String flags = "";
                        final Collection<Flag> plotflags = FlagManager.getPlotFlags(plot).values();
                        if (plotflags.size() > 0) {
                            flags = StringUtils.join(plotflags, ",");
                        }
                        final PlotWrapper plotWrapper = new PlotWrapper(owner, helpers, trusted, plot.id, alias, flags);
                        handlePlot(w, plotWrapper, newmap);
                    }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        
        /* Now, review old map - anything left is gone */
        for (final AreaMarker oldm : this.resareas.values()) {
            oldm.deleteMarker();
        }
        
        /* And replace with new map */
        this.resareas = newmap;
        
        getServer().getScheduler().scheduleAsyncDelayedTask(this, new Plot2Update(), this.updperiod);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(final PluginEnableEvent event) {
        final Plugin p = event.getPlugin();
        final String name = p.getDescription().getName();
        if (name.equals("dynmap")) {
            if (this.dynmap.isEnabled() && this.plot2.isEnabled()) {
                initialize();
            }
        }
    }
    
    @Override
    public void onEnable() {
        final PluginManager pm = getServer().getPluginManager();
        this.dynmap = pm.getPlugin("dynmap");
        if (this.dynmap == null) {
            severe("Dynmap not found, disabling Plot2Dynmap");
            return;
        }
        this.dynAPI = (DynmapAPI) this.dynmap;
        this.plot2 = pm.getPlugin("PlotSquared");
        getServer().getPluginManager().registerEvents(this, this);
        
        if (this.dynmap.isEnabled() && this.plot2.isEnabled()) {
            initialize();
        }
    }
    
    public void initialize() {
        
        this.markerapi = this.dynAPI.getMarkerAPI();
        if (this.markerapi == null) {
            severe("Error loading dynmap-API");
            return;
        }
        final FileConfiguration config = getConfig();
        config.options().copyDefaults(true);
        saveConfig();
        
        /* Now, add marker set for mobs (make it transient) */
        set = this.markerapi.getMarkerSet("plot2.markerset");
        if (set == null) {
            set = this.markerapi.createMarkerSet("plot2.markerset", config.getString("layer.name", "PlotSquared"), null, false);
        } else {
            set.setMarkerSetLabel(config.getString("layer.name", "PlotSquared"));
        }
        if (set == null) {
            severe("Error creating marker set");
            return;
        }
        
        final int minzoom = config.getInt("layer.minzoom", 0);
        if (minzoom > 0) {
            set.setMinZoom(minzoom);
        }
        set.setLayerPriority(config.getInt("layer.layerprio", 10));
        set.setHideByDefault(config.getBoolean("layer.hidebydefault", false));
        this.infowindow = config.getString("infowindow", DEF_INFOWINDOW);
        this.infoelement = config.getString("infoelement", DEF_INFOELEMENT);
        
        /* Get style information */
        this.defstyle = new AreaStyle(config, "plotstyle");
        this.cusstyle = new HashMap<String, AreaStyle>();
        this.ownerstyle = new HashMap<String, AreaStyle>();
        this.cuswildstyle = new HashMap<String, AreaStyle>();
        ConfigurationSection sect = config.getConfigurationSection("custstyle");
        if (sect != null) {
            final Set<String> ids = sect.getKeys(false);
            
            for (final String id : ids) {
                if (id.indexOf('|') >= 0) {
                    this.cuswildstyle.put(id, new AreaStyle(config, "custstyle." + id, this.defstyle));
                } else {
                    this.cusstyle.put(id, new AreaStyle(config, "custstyle." + id, this.defstyle));
                }
            }
        }
        sect = config.getConfigurationSection("ownerstyle");
        if (sect != null) {
            final Set<String> ids = sect.getKeys(false);
            
            for (final String id : ids) {
                this.ownerstyle.put(id.toLowerCase(), new AreaStyle(config, "ownerstyle." + id, this.defstyle));
            }
        }
        
        /* Set up update job - based on period */
        int per = config.getInt("update.period", 60);
        if (per < 15) {
            per = 15;
        }
        this.updperiod = per * 20;
        this.stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new Plot2Update(), 420);
    }
    
    public void Disable() {
        if (set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        this.resareas.clear();
        this.stop = true;
    }
}
