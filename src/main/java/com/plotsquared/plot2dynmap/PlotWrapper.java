package com.plotsquared.plot2dynmap;

import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter @RequiredArgsConstructor class PlotWrapper {

    private final String owner;
    private final String helpers;
    private final String trusted;
    private final String denied;
    private final PlotId plotId;
    private final String alias;
    private final String flags;
    private final PlotArea area;

}
