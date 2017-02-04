package com.raoulvdberge.refinedstorage.tile;

import com.raoulvdberge.refinedstorage.RS;

public class TileCable extends TileMultipartNode {
    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.cableUsage;
    }

    @Override
    protected boolean wantsUpdateNode(){
        return false;
    }
    @Override
    public void updateNode() {
        // NO OP
    }
}
