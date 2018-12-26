package com.empcraft.plot2dynmap;

import com.github.intellectualsites.plotsquared.plot.PlotSquared;
import com.github.intellectualsites.plotsquared.plot.flag.Flag;
import com.github.intellectualsites.plotsquared.plot.flag.FlagManager;
import com.github.intellectualsites.plotsquared.plot.object.Plot;
import com.github.intellectualsites.plotsquared.plot.object.PlotPlayer;
import com.github.intellectualsites.plotsquared.plot.object.RegionWrapper;
import com.github.intellectualsites.plotsquared.plot.util.MainUtil;
import com.github.intellectualsites.plotsquared.plot.util.UUIDHandler;
import com.github.intellectualsites.plotsquared.plot.util.expiry.ExpireManager;
import org.apache.commons.lang.StringEscapeUtils;
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

import java.util.*;


/**
 * A lot of this code is reused from the examples provided by 'mikeprimm' - creator of dynmap
 *
 * @author Original: Empire92. Updated by Sauilitired
 */
@SuppressWarnings("unused")
public class Main extends JavaPlugin implements Listener {

    private static final String DEF_INFO_ELEMENT =
        "%key% <span style=\"font-weight:bold;\">%values%</span><br>";
    private static final String DEF_INFO_WINDOW =
        "<div class=\"infowindow\">" + "<span style=\"font-size:120%;\">%id%</span><br>" + "%alias%"
            + "%owner%" + "%members%" + "%trusted%" + "%flags%" + "</div>";

    private static MarkerSet set;
    private DynmapAPI dynAPI;
    private Plugin dynmap;
    private Plugin plot2;
    private long updatePeriod;
    private String infoWindow;
    private String infoElement;
    private AreaStyle defStyle;
    private Map<String, AreaStyle> cusStyle;
    private Map<String, AreaStyle> cusWildStyle;
    private Map<String, AreaStyle> ownerStyle;
    private boolean stop;
    private Map<String, AreaMarker> resAreas = new HashMap<>();

    private void severe(final String msg) {
        getLogger().severe("[Plot^2] " + msg);
    }

    private String formatInfoWindow(final PlotWrapper plot) {
        String v = "<div class=\"plotinfo\">" + this.infoWindow + "</div>";
        v = v.replace("%id%", plot.getPlotId().x + "," + plot.getPlotId().y);
        v = v.replace("%alias%",
            this.infoElement.replace("%values%", StringEscapeUtils.escapeHtml(plot.getAlias()))
                .replace("%key%", "Alias"));
        v = v.replace("%owner%",
            this.infoElement.replace("%values%", plot.getOwner()).replace("%key%", "Owner"));
        v = v.replace("%members%",
            this.infoElement.replace("%values%", plot.getHelpers()).replace("%key%", "Members"));
        v = v.replace("%trusted%",
            this.infoElement.replace("%values%", plot.getTrusted()).replace("%key%", "Trusted"));
        v = v.replace("%flags%",
            this.infoElement.replace("%values%", StringEscapeUtils.escapeHtml(plot.getFlags()))
                .replace("%key%", "Flags"));
        v = v.replace("%owner%", plot.getOwner());
        return v;
    }

    private void addStyle(final String plotId, final String worldId, final AreaMarker m,
        final PlotWrapper plot) {
        AreaStyle as = this.cusStyle.get(worldId + "/" + plotId);
        if (as == null) {
            as = this.cusStyle.get(plotId);
        }
        if (as == null) { /* Check for wildcard style matches */
            for (final String wc : this.cusWildStyle.keySet()) {
                final String[] tok = wc.split("\\|");
                if ((tok.length == 1) && plotId.startsWith(tok[0])) {
                    as = this.cusWildStyle.get(wc);
                } else if ((tok.length >= 2) && plotId.startsWith(tok[0]) && plotId
                    .endsWith(tok[1])) {
                    as = this.cusWildStyle.get(wc);
                }
            }
        }
        if (as == null) { /* Check for owner style matches */
            if (!this.ownerStyle.isEmpty()) {
                String owner = plot.getOwner();
                if (owner == null) {
                    owner = "unknown";
                }
                as = this.ownerStyle.get(owner.toLowerCase());
            }
        }
        if (as == null) {
            as = this.defStyle;
        }

        int sc = 0xFF0000;
        int fc = 0xFF0000;
        try {
            sc = Integer.parseInt(as.strokeColor.substring(1), 16);
            fc = Integer.parseInt(as.fillColor.substring(1), 16);
        } catch (final NumberFormatException nfx) {
            nfx.printStackTrace();
        }
        if (as.strokeWeight != 0) {
            m.setLineStyle(as.strokeWeight, as.strokeOpacity, sc);
        }
        if (as.fillOpacity != 0) {
            m.setFillStyle(as.fillOpacity, fc);
        }
        if (as.label != null) {
            m.setLabel(as.label);
        }
    }

    private void handlePlot(final World world, final PlotWrapper plot,
        final Map<String, AreaMarker> newMap) {
        final String name = plot.getPlotId().x + "," + plot.getPlotId().y;

        double[] x;
        double[] z;

        int i = 0;

        final Plot plotObject = plot.getArea().getPlot(plot.getPlotId());

        for (RegionWrapper region : plotObject.getRegions()) {

            x = new double[4];
            z = new double[4];
            x[0] = region.minX;
            z[0] = region.minZ;

            x[1] = region.minX;
            z[1] = region.maxZ + 1;

            x[2] = region.maxX + 1;
            z[2] = region.maxZ + 1;

            x[3] = region.maxX + 1;
            z[3] = region.minZ;

            final String markerid = world.getName() + "_" + name + (i == 0 ? "" : "-" + i);
            AreaMarker m = this.resAreas.remove(markerid); /* Existing area? */
            if (m == null) {
                m = set.createAreaMarker(markerid, name, false, world.getName(), x, z, false);
                if (m == null) {
                    return;
                }
            } else {
                m.setCornerLocations(x, z); /* Replace corner locations */
                m.setLabel(name); /* Update label */
            }

            addStyle(name, world.getName(), m, plot);

            final String desc = formatInfoWindow(plot);

            m.setDescription(desc); /* Set popup */

            newMap.put(markerid, m);
            i++;
        }
    }

    private void updatePlots() {
        final Map<String, AreaMarker> newMap = new HashMap<>(); /* Build new map */
        try {
            for (final World w : getServer().getWorlds()) {
                if (PlotSquared.get().hasPlotArea(w.getName())) {
                    List<Plot> plots = new ArrayList<>(PlotSquared.get().getPlots(w.getName()));
                    if (plots.size() > 4096) {
                        plots.sort((a, b) -> {
                            PlotPlayer p1 = UUIDHandler.getPlayer(a.guessOwner());
                            PlotPlayer p2 = UUIDHandler.getPlayer(b.guessOwner());
                            if (p1 == p2) {
                                long l1 = ExpireManager.IMP.getAge(a.guessOwner());
                                long l2 = ExpireManager.IMP.getAge(b.guessOwner());
                                if (l1 == l2) {
                                    return Math.abs(a.hashCode()) - Math.abs(b.hashCode());
                                }
                                if (l2 == 0L) {
                                    return -1;
                                }
                                if (l1 == 0L) {
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
                        });
                        plots = plots.subList(0, 4096);
                    }
                    for (final Plot plot : plots) {
                        String owner = MainUtil.getName(plot.guessOwner());
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
                        for (final UUID trusted : plot.getTrusted()) {
                            trusted_list[i] = MainUtil.getName(trusted);
                            i++;
                        }
                        String trusted = "";
                        if (trusted_list.length > 0) {
                            trusted = StringUtils.join(trusted_list, ",");
                        }

                        final String alias = plot.toString();
                        /*final Collection<Flag<?>> plotFlags =
                            FlagManager.getPlotFlags(plot).keySet();
                        if (plotFlags.size() > 0) {
                            flags = StringUtils.join(plotFlags, ",");
                        }*/
                        final StringBuilder flagBuilder = new StringBuilder();
                        final Iterator<Map.Entry<Flag<?>, Object>> iterator =
                            FlagManager.getPlotFlags(plot).entrySet().iterator();
                        while (iterator.hasNext()) {
                            final Map.Entry<Flag<?>, Object> entry = iterator.next();
                            flagBuilder.append(String.format("%s = %s", entry.getKey().getName(),
                                entry.getValue().toString()));
                            if (iterator.hasNext()) {
                                flagBuilder.append(", ");
                            }
                        }

                        final PlotWrapper plotWrapper =
                            new PlotWrapper(owner, helpers, trusted, plot.getId(), alias, flagBuilder.toString(),
                                plot.getArea());
                        handlePlot(w, plotWrapper, newMap);
                    }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        /* Now, review old map - anything left is gone */
        for (final AreaMarker oldm : this.resAreas.values()) {
            oldm.deleteMarker();
        }

        /* And replace with new map */
        this.resAreas = newMap;

        getServer().getScheduler()
            .runTaskLaterAsynchronously(this, new Plot2Update(), this.updatePeriod);
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

    @Override public void onEnable() {
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

    private void initialize() {

        MarkerAPI markerApi = this.dynAPI.getMarkerAPI();
        if (markerApi == null) {
            severe("Error loading dynmap-API");
            return;
        }
        final FileConfiguration config = getConfig();
        config.options().copyDefaults(true);
        saveConfig();

        /* Now, add marker set for mobs (make it transient) */
        set = markerApi.getMarkerSet("plot2.markerset");
        if (set == null) {
            set = markerApi
                .createMarkerSet("plot2.markerset", config.getString("layer.name", "PlotSquared"),
                    null, false);
        } else {
            set.setMarkerSetLabel(config.getString("layer.name", "PlotSquared"));
        }
        if (set == null) {
            severe("Error creating marker set");
            return;
        }

        final int minZoom = config.getInt("layer.minzoom", 0);
        if (minZoom > 0) {
            set.setMinZoom(minZoom);
        }
        set.setLayerPriority(config.getInt("layer.layerprio", 10));
        set.setHideByDefault(config.getBoolean("layer.hidebydefault", false));
        this.infoWindow = config.getString("infowindow", DEF_INFO_WINDOW);
        this.infoElement = config.getString("infoelement", DEF_INFO_ELEMENT);

        /* Get style information */
        this.defStyle = new AreaStyle(config, "plotstyle");
        this.cusStyle = new HashMap<>();
        this.ownerStyle = new HashMap<>();
        this.cusWildStyle = new HashMap<>();
        ConfigurationSection sect = config.getConfigurationSection("custstyle");
        if (sect != null) {
            final Set<String> ids = sect.getKeys(false);

            for (final String id : ids) {
                if (id.indexOf('|') >= 0) {
                    this.cusWildStyle
                        .put(id, new AreaStyle(config, "custstyle." + id, this.defStyle));
                } else {
                    this.cusStyle.put(id, new AreaStyle(config, "custstyle." + id, this.defStyle));
                }
            }
        }
        sect = config.getConfigurationSection("ownerstyle");
        if (sect != null) {
            final Set<String> ids = sect.getKeys(false);

            for (final String id : ids) {
                this.ownerStyle.put(id.toLowerCase(),
                    new AreaStyle(config, "ownerstyle." + id, this.defStyle));
            }
        }

        /* Set up update job - based on period */
        int per = config.getInt("update.period", 60);
        if (per < 15) {
            per = 15;
        }
        this.updatePeriod = per * 20;
        this.stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new Plot2Update(), 420);
    }

    private static final class AreaStyle {

        String strokeColor;
        double strokeOpacity;
        int strokeWeight;
        String fillColor;
        double fillOpacity;
        String label;

        AreaStyle(final FileConfiguration cfg, final String path, final AreaStyle def) {
            this.strokeColor = cfg.getString(path + ".strokeColor", def.strokeColor);
            this.strokeOpacity = cfg.getDouble(path + ".strokeOpacity", def.strokeOpacity);
            this.strokeWeight = cfg.getInt(path + ".strokeWeight", def.strokeWeight);
            this.fillColor = cfg.getString(path + ".fillColor", def.fillColor);
            this.fillOpacity = cfg.getDouble(path + ".fillOpacity", def.fillOpacity);
            this.label = cfg.getString(path + ".label", null);
        }

        AreaStyle(final FileConfiguration cfg, final String path) {
            this.strokeColor = cfg.getString(path + ".strokeColor", "#6666CC");
            this.strokeOpacity = cfg.getDouble(path + ".strokeOpacity", 0.8);
            this.strokeWeight = cfg.getInt(path + ".strokeWeight", 8);
            this.fillColor = cfg.getString(path + ".fillColor", "#FFFFFF");
            this.fillOpacity = cfg.getDouble(path + ".fillOpacity", 0.01);
        }
    }


    private class Plot2Update implements Runnable {
        @Override public void run() {
            if (!Main.this.stop) {
                updatePlots();
            }
        }
    }
}
