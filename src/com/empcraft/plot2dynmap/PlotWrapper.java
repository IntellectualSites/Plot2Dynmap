package com.empcraft.plot2dynmap;

import com.intellectualcrafters.plot.object.PlotId;

public class PlotWrapper {
    public String owner;
    public String helpers;
    public String trusted;
    public PlotId id;
    public String alias;
    public String flags;
    
    public PlotWrapper(final String owner, final String helpers, final String trusted, final PlotId id, final String alias, final String flags) {
        this.owner = owner;
        this.helpers = helpers;
        this.trusted = trusted;
        this.id = id;
        this.alias = alias;
        this.flags = flags;
    }
}
