package com.plotsquared.plot2dynmap;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.PlotFlag;
import com.plotsquared.core.plot.world.PlotAreaManager;
import com.plotsquared.core.util.query.PlotQuery;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * A lot of this code is reused from the examples provided by 'mikeprimm' - creator of dynmap
 */
@SuppressWarnings("unused")
public class Plot2DynmapPlugin extends JavaPlugin implements Listener, Runnable {

    private static final String DEF_INFO_ELEMENT = "%key% <span style=\"font-weight:bold;\">%values%</span><br>";
    private static final String DEF_INFO_WINDOW =
            "<div class=\"infowindow\">" + "<span style=\"font-size:120%;\">%id%</span><br>" + "%creationdate%" +
                    "%alias%" + "%owner%" + "%members%" + "%trusted%" + "%rating%" + "%denied%" + "%flags%" + "</div>";

    private static MarkerSet markerSet;
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

    private String formatInfoWindow(final PlotWrapper plot) {
        String v = "<div class=\"plotinfo\">" + this.infoWindow + "</div>";
        v = v.replace("%id%", plot.plotId().toCommaSeparatedString());
        v = v.replace(
                "%alias%",
                this.infoElement.replace("%values%", StringEscapeUtils.escapeHtml4(plot.alias())).replace("%key%", "Alias")
        );
        v = v.replace("%owner%", this.infoElement.replace("%values%", plot.owner()).replace("%key%", "Owner"));
        v = v.replace("%trusted%", this.infoElement.replace("%values%", plot.trusted()).replace("%key%", "Trusted"));
        v = v.replace("%members%", this.infoElement.replace("%values%", plot.helpers()).replace("%key%", "Members"));
        v = v.replace("%denied%", this.infoElement.replace("%values%", plot.denied()).replace("%key%", "Denied"));
        v = v.replace(
                "%flags%",
                this.infoElement.replace("%values%", StringEscapeUtils.escapeHtml4(plot.flags())).replace("%key%", "Flags")
        );
        v = v.replace("%owner%", plot.owner());
        v = v.replace(
                "%creationdate%",
                this.infoElement.replace("%values%", plot.creationDate()).replace("%key%", "Creation Date")
        );
        v = v.replace("%rating%", this.infoElement.replace("%values%", plot.rating()).replace("%key%", "Rating"));
        return v;
    }

    private void addStyle(final String plotId, final String worldId, final AreaMarker areaMarker, final PlotWrapper plot) {
        AreaStyle areaStyle = this.cusStyle.get(worldId + "/" + plotId);
        if (areaStyle == null) {
            areaStyle = this.cusStyle.get(plotId);
        }
        if (areaStyle == null) { /* Check for wildcard style matches */
            for (final String wc : this.cusWildStyle.keySet()) {
                final String[] tok = wc.split("\\|");
                if ((tok.length == 1) && plotId.startsWith(tok[0])) {
                    areaStyle = this.cusWildStyle.get(wc);
                } else if ((tok.length >= 2) && plotId.startsWith(tok[0]) && plotId.endsWith(tok[1])) {
                    areaStyle = this.cusWildStyle.get(wc);
                }
            }
        }
        if (areaStyle == null) { /* Check for owner style matches */
            if (!this.ownerStyle.isEmpty()) {
                String owner = plot.owner();
                if (owner == null) {
                    owner = "unknown";
                }
                areaStyle = this.ownerStyle.get(owner.toLowerCase());
            }
        }
        if (areaStyle == null) {
            areaStyle = this.defStyle;
        }

        int strokeColor = 0xFF0000;
        int fillColor = 0xFF0000;
        double strokeOpacity = areaStyle.strokeOpacity;
        double fillOpacity = areaStyle.fillOpacity;
        try {
            strokeColor = Integer.parseInt(areaStyle.strokeColor.substring(1), 16);
            fillColor = Integer.parseInt(areaStyle.fillColor.substring(1), 16);
        } catch (final NumberFormatException e) {
            e.printStackTrace();
        }
        // If 0-opacity or stroke weight, set the color to red with 0 percentage alpha channel to still be drawn and interactable
        if (strokeOpacity == 0) {
            strokeColor = 0xFF000000;
            strokeOpacity = 1;
        }
        if (fillOpacity == 0) {
            fillColor = 0xFF000000;
            fillOpacity = 1;
        }
        areaMarker.setLineStyle(areaStyle.strokeWeight, strokeOpacity, strokeColor);
        areaMarker.setFillStyle(fillOpacity, fillColor);
        if (areaStyle.label != null) {
            areaMarker.setLabel(areaStyle.label);
        }
    }

    private void handlePlot(final World world, final PlotWrapper plotWrapper, final Map<String, AreaMarker> newMap) {
        final String name = plotWrapper.plotId().toCommaSeparatedString();

        double[] x;
        double[] z;

        int i = 0;

        final Plot plotObject = plotWrapper.area().getPlot(plotWrapper.plotId());

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

            final String markerId = world.getName() + "_" + name + (i == 0 ? "" : "-" + i);
            AreaMarker areaMarker = this.resAreas.remove(markerId); /* Existing area? */
            if (areaMarker == null) {
                areaMarker = markerSet.createAreaMarker(markerId, name, false, world.getName(), x, z, false);
                if (areaMarker == null) {
                    return;
                }
            } else {
                areaMarker.setCornerLocations(x, z); /* Replace corner locations */
                areaMarker.setLabel(name); /* Update label */
            }

            addStyle(name, world.getName(), areaMarker, plotWrapper);

            final String desc = formatInfoWindow(plotWrapper);

            areaMarker.setDescription(desc); /* Set popup */

            newMap.put(markerId, areaMarker);
            i++;
        }
    }

    @Override
    public void run() {
        if (stop) {
            return;
        }

        final Map<String, AreaMarker> newMap = new HashMap<>(); /* Build new map */
        try {
            for (final World world : getServer().getWorlds()) {
                if (plotAreaManager.hasPlotArea(world.getName())) {
                    for (final Plot plot : PlotQuery.newQuery().inWorld(world.getName())) {
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
                        helpers = helpers.isEmpty() ? "None" : helpers;
                        trusted = trusted.isEmpty() ? "None" : trusted;
                        denied = denied.isEmpty() ? "None" : denied;

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

                        final long creationDate = Long.parseLong(String.valueOf(plot.getTimestamp()));
                        SimpleDateFormat sdf = new SimpleDateFormat(Settings.Timeformat.DATE_FORMAT);
                        sdf.setTimeZone(TimeZone.getTimeZone(Settings.Timeformat.TIME_ZONE));
                        String newDate = sdf.format(creationDate);

                        final String rating;
                        if (Double.isNaN(plot.getAverageRating())) {
                            rating = "NaN";
                        } else if (!Settings.General.SCIENTIFIC) {
                            BigDecimal roundRating = BigDecimal.valueOf(plot.getAverageRating()).setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );
                            rating = String.valueOf(roundRating);
                        } else {
                            rating = Double.toString(plot.getAverageRating());
                        }

                        final PlotWrapper plotWrapper =
                                new PlotWrapper(owner, helpers, trusted, denied, plot.getId(), alias, flags, plot.getArea(),
                                        newDate, rating
                                );
                        handlePlot(world, plotWrapper, newMap);
                    }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        /* Now, review old map - anything left is gone */
        for (final AreaMarker oldMap : this.resAreas.values()) {
            oldMap.deleteMarker();
        }

        /* And replace with new map */
        this.resAreas = newMap;

        getServer().getScheduler().runTaskLaterAsynchronously(this, this, updatePeriod);
    }

    @Override
    public void onEnable() {
        // Just to be safe on our side. Prior versions should not load anyway...
        if (!Bukkit.getPluginManager().getPlugin("PlotSquared").getDescription().getVersion().startsWith("7")) {
            getLogger().severe("PlotSquared 7.x is required for Plot2Dynmap to work!");
            getLogger().severe("Please update PlotSquared: https://www.spigotmc.org/resources/77506/");
            getLogger().severe("Disabling Plot2Dynmap...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        final FileConfiguration config = getConfig();
        final PluginManager pluginManager = getServer().getPluginManager();
        DynmapAPI dynAPI = (DynmapAPI) pluginManager.getPlugin("dynmap");
        this.plotAreaManager = PlotSquared.get().getPlotAreaManager();

        getServer().getPluginManager().registerEvents(this, this);
        Metrics metrics = new Metrics(this, 6400);
        MarkerAPI markerApi = dynAPI.getMarkerAPI();
        if (markerApi == null) {
            getLogger().severe("Error loading dynmap-API");
            return;
        }

        config.options().copyDefaults(true);
        saveConfig();

        /* Now, add marker set for mobs (make it transient) */
        markerSet = markerApi.getMarkerSet("plot2.markerset");
        if (markerSet == null) {
            markerSet = markerApi.createMarkerSet("plot2.markerset", config.getString("layer.name", "PlotSquared"), null, false);
        } else {
            markerSet.setMarkerSetLabel(config.getString("layer.name", "PlotSquared"));
        }
        if (markerSet == null) {
            getLogger().severe("Error creating marker set");
            return;
        }

        final int minZoom = config.getInt("layer.minimumpoint.zoom", 0);
        if (minZoom > 0) {
            markerSet.setMinZoom(minZoom);
        }
        markerSet.setLayerPriority(config.getInt("layer.layerprio", 10));
        markerSet.setHideByDefault(config.getBoolean("layer.hidebydefault", false));
        this.infoWindow = config.getString("infowindow", DEF_INFO_WINDOW);
        this.infoElement = config.getString("infoelement", DEF_INFO_ELEMENT);

        /* Get style information */
        this.defStyle = new AreaStyle(config, "plotstyle");
        this.cusStyle = new HashMap<>();
        this.ownerStyle = new HashMap<>();
        this.cusWildStyle = new HashMap<>();
        ConfigurationSection configurationSection = config.getConfigurationSection("custstyle");
        if (configurationSection != null) {
            final Set<String> ids = configurationSection.getKeys(false);

            for (final String id : ids) {
                if (id.indexOf('|') >= 0) {
                    this.cusWildStyle.put(id, new AreaStyle(config, "custstyle." + id, this.defStyle));
                } else {
                    this.cusStyle.put(id, new AreaStyle(config, "custstyle." + id, this.defStyle));
                }
            }
        }
        configurationSection = config.getConfigurationSection("ownerstyle");
        if (configurationSection != null) {
            final Set<String> ids = configurationSection.getKeys(false);

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

        AreaStyle(final FileConfiguration config, final String path, final AreaStyle def) {
            this.strokeColor = config.getString(path + ".strokeColor", def.strokeColor);
            this.strokeOpacity = config.getDouble(path + ".strokeOpacity", def.strokeOpacity);
            this.strokeWeight = config.getInt(path + ".strokeWeight", def.strokeWeight);
            this.fillColor = config.getString(path + ".fillColor", def.fillColor);
            this.fillOpacity = config.getDouble(path + ".fillOpacity", def.fillOpacity);
            this.label = config.getString(path + ".label", null);
        }

        AreaStyle(final FileConfiguration config, final String path) {
            this.strokeColor = config.getString(path + ".strokeColor", "#6666CC");
            this.strokeOpacity = config.getDouble(path + ".strokeOpacity", 0.8);
            this.strokeWeight = config.getInt(path + ".strokeWeight", 8);
            this.fillColor = config.getString(path + ".fillColor", "#FFFFFF");
            this.fillOpacity = config.getDouble(path + ".fillOpacity", 0.01);
        }

    }

}
