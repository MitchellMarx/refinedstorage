package com.raoulvdberge.refinedstorage.tile.data;

import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Created by felix on 2/3/2017.
 */
public interface IOnServerTickHandler {
    void onServerTick(TickEvent.ServerTickEvent e);
}
