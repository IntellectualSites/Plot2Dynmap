package com.plotsquared.plot2dynmap;

import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotId;

record PlotWrapper(String owner, String helpers, String trusted, String denied,
                   PlotId plotId, String alias, String flags,
                   PlotArea area, String creationDate, String rating) {

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public String getOwner() {
        return this.owner;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public String getHelpers() {
        return this.helpers;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public String getTrusted() {
        return this.trusted;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public String getDenied() {
        return this.denied;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public PlotId getPlotId() {
        return this.plotId;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public String getAlias() {
        return this.alias;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public String getFlags() {
        return this.flags;
    }

    /**
     * @deprecated Scheduled for removal in favor of record accessors.
     */
    @Deprecated(forRemoval = true)
    public PlotArea getArea() {
        return this.area;
    }

}
