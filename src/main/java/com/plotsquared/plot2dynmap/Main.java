package com.plotsquared.plot2dynmap;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.PlotFlag;
import com.plotsquared.core.plot.world.PlotAreaManager;
import com.plotsquared.core.util.query.PlotQuery;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A lot of this code is reused from the examples provided by 'mikeprimm' - creator of dynmap
 *
 * @author Original: Empire92. Updated by Sauilitired
 */
@SuppressWarnings("unused") public class Main extends JavaPlugin implements Listener, Runnable {

    private static final String DEF_INFO_ELEMENT = "%key% <span style=\"font-weight:bold;\">%values%</span><br>";
    private static final String DEF_INFO_WINDOW =
        "<div class=\"infowindow\">" + "<span style=\"font-size:120%;\">%id%</span><br>" + "%alias%" + "%owner%"
            + "%members%" + "%trusted%" + "%denied%" + "%flags%" + "</div>";

    private static MarkerSet set;
    private long updatePeriod;
    private String infoWindow;
    private String infoElement;
    private AreaStyle defStyle;
    private Map<String, AreaStyle> cusStyle;
    private Map<String, AreaStyle> cusWildStyle;
    private Map<String, AreaStyle> ownerStyle;
    private boolean stop;
    private Map<String, AreaMarker> resAreas = new HashMap<>();
    private PlotAreaManager plotAreaManager;

    private void severe(final String msg) {
        getLogger().severe("[PlotSquared] " + msg);
    }

    private String formatInfoWindow(final PlotWrapper plot) {
        String v = "<div class=\"plotinfo\">" + this.infoWindow + "</div>";
        v = v.replace("%id%", plot.getPlotId().toCommaSeparatedString());
        v = v.replace("%alias%",
            this.infoElement.replace("%values%", StringEscapeUtils.escapeHtml(plot.getAlias())).replace("%key%", "Alias"));
        v = v.replace("%owner%", this.infoElement.replace("%values%", plot.getOwner()).replace("%key%", "Owner"));
        v = v.replace("%trusted%", this.infoElement.replace("%values%", plot.getTrusted()).replace("%key%", "Trusted"));
        v = v.replace("%members%", this.infoElement.replace("%values%", plot.getHelpers()).replace("%key%", "Members"));
        v = v.replace("%denied%", this.infoElement.replace("%values%", plot.getDenied()).replace("%key%", "Denied"));
        v = v.replace("%flags%",
            this.infoElement.replace("%values%", StringEscapeUtils.escapeHtml(plot.getFlags())).replace("%key%", "Flags:"));
        v = v.replace("%owner%", plot.getOwner());
        return v;
    }

    private void addStyle(final String plotId, final String worldId, final AreaMarker m, final PlotWrapper plot) {
        AreaStyle as = this.cusStyle.get(worldId + "/" + plotId);
        if (as == null) {
            as = this.cusStyle.get(plotId);
        }
        if (as == null) { /* Check for wildcard style matches */
            for (final String wc : this.cusWildStyle.keySet()) {
                final String[] tok = wc.split("\\|");
                if ((tok.length == 1) && plotId.startsWith(tok[0])) {
                    as = this.cusWildStyle.get(wc);
                } else if ((tok.length >= 2) && plotId.startsWith(tok[0]) && plotId.endsWith(tok[1])) {
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

    private void handlePlot(final World world, final PlotWrapper plot, final Map<String, AreaMarker> newMap) {
        final String name = plot.getPlotId().toCommaSeparatedString();

        double[] x;
        double[] z;

        int i = 0;

        final Plot plotObject = plot.getArea().getPlot(plot.getPlotId());

        if (plotObject == null) {
            return;
        }

        for (CuboidRegion region : plotObject.getRegions()) {

            x = new double[4];
            z = new double[4];
            x[0] = region.getMinimumPoint().getX();
            z[0] = region.getMinimumPoint().getZ();

            x[1] = region.getMinimumPoint().getX();
            z[1] = region.getMaximumPoint().getZ() + 1;

            x[2] = region.getMaximumPoint().getX() + 1;
            z[2] = region.getMaximumPoint().getZ() + 1;

            x[3] = region.getMaximumPoint().getX() + 1;
            z[3] = region.getMinimumPoint().getZ();

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

    @Override public void run() {
        if (stop) {
            return;
        }

        final Map<String, AreaMarker> newMap = new HashMap<>(); /* Build new map */
        try {
            for (final World w : getServer().getWorlds()) {
                if (plotAreaManager.hasPlotArea(w.getName())) {
                    for (final Plot plot : PlotQuery.newQuery().inWorld(w.getName())) {
                        if (!plot.hasOwner()) {
                            continue;
                        }
                        String owner = PlotSquared.get().getImpromptuUUIDPipeline().getSingle(plot.getOwnerAbs(), 50);
                        final String[] helpers_list = new String[plot.getMembers().size()];
                        int i = 0;
                        for (UUID member : plot.getMembers()) {
                            helpers_list[i] = PlotSquared.get().getImpromptuUUIDPipeline().getSingle(member, 20);
                            i++;
                        }
                        String helpers = "";
                        if (helpers_list.length > 0) {
                            helpers = StringUtils.join(helpers_list, ",");
                        }
                        final String[] trusted_list = new String[plot.getTrusted().size()];
                        i = 0;
                        for (final UUID trusted : plot.getTrusted()) {
                            trusted_list[i] = PlotSquared.get().getImpromptuUUIDPipeline().getSingle(trusted, 20);
                            i++;
                        }
                        String trusted = "";
                        if (trusted_list.length > 0) {
                            trusted = StringUtils.join(trusted_list, ",");
                        }
                        String denied = "";
                        final String[] denied_list = new String[plot.getDenied().size()];
                        if (denied_list.length > 0) {
                            denied = StringUtils.join(denied_list, ",");
                        }
                        helpers = helpers.isEmpty() ? helpers : "None";
                        trusted = trusted.isEmpty() ? trusted : "None";
                        denied = denied.isEmpty() ? denied : "None";

                        final String alias = plot.getAlias().isEmpty() ? "None" : plot.getAlias();
                        /*final Collection<Flag<?>> plotFlags =
                            FlagManager.getPlotFlags(plot).keySet();
                        if (plotFlags.size() > 0) {
                            flags = StringUtils.join(plotFlags, ",");
                        }*/
                        final StringBuilder flagBuilder = new StringBuilder();
                        final Iterator<PlotFlag<?, ?>> iterator = plot.getFlags().iterator();
                        while (iterator.hasNext()) {
                            final PlotFlag<?, ?> entry = iterator.next();
                            flagBuilder.append(String.format("%s = %s", entry.getName(), entry.getValue()));
                            if (iterator.hasNext()) {
                                flagBuilder.append(", ");
                            }
                        }

                        String flags = flagBuilder.toString();
                        flags = flags.isEmpty() ? "None" : flags;

                        final PlotWrapper plotWrapper =
                            new PlotWrapper(owner, helpers, trusted, denied, plot.getId(), alias, flags, plot.getArea());
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

        getServer().getScheduler().runTaskLaterAsynchronously(this, this, updatePeriod);
    }

    @Override public void onEnable() {
        final FileConfiguration config = getConfig();
        final PluginManager pm = getServer().getPluginManager();
        DynmapAPI dynAPI = (DynmapAPI) pm.getPlugin("dynmap");
        this.plotAreaManager = PlotSquared.get().getPlotAreaManager();

        getServer().getPluginManager().registerEvents(this, this);
        Metrics metrics = new Metrics(this, 6400);
        MarkerAPI markerApi = dynAPI.getMarkerAPI();
        if (markerApi == null) {
            severe("Error loading dynmap-API");
            return;
        }

        config.options().copyDefaults(true);
        saveConfig();

        /* Now, add marker set for mobs (make it transient) */
        set = markerApi.getMarkerSet("plot2.markerset");
        if (set == null) {
            set = markerApi.createMarkerSet("plot2.markerset", config.getString("layer.name", "PlotSquared"), null, false);
        } else {
            set.setMarkerSetLabel(config.getString("layer.name", "PlotSquared"));
        }
        if (set == null) {
            severe("Error creating marker set");
            return;
        }

        final int minZoom = config.getInt("layer.minimumpoint.zoom", 0);
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
                    this.cusWildStyle.put(id, new AreaStyle(config, "custstyle." + id, this.defStyle));
                } else {
                    this.cusStyle.put(id, new AreaStyle(config, "custstyle." + id, this.defStyle));
                }
            }
        }
        sect = config.getConfigurationSection("ownerstyle");
        if (sect != null) {
            final Set<String> ids = sect.getKeys(false);

            for (final String id : ids) {
                this.ownerStyle.put(id.toLowerCase(), new AreaStyle(config, "ownerstyle." + id, this.defStyle));
            }
        }

        /* Set up update job - based on period */
        int per = config.getInt("update.period", 60);
        if (per < 15) {
            per = 15;
        }
        this.updatePeriod = per * 20L;
        this.stop = false;
        getServer().getScheduler().runTaskLaterAsynchronously(this, this, 420L);
    }

    private static final class AreaStyle {

        public String strokeColor;
        public double strokeOpacity;
        public int strokeWeight;
        public String fillColor;
        public double fillOpacity;
        public String label;

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
}
