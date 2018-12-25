package com.empcraft.plot2dynmap;

import com.github.intellectualsites.plotsquared.plot.object.PlotArea;
import com.github.intellectualsites.plotsquared.plot.object.PlotId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter @RequiredArgsConstructor class PlotWrapper {

    private final String owner;
    private final String helpers;
    private final String trusted;
    private final PlotId plotId;
    private final String alias;
    private final String flags;
    private final PlotArea area;

}
