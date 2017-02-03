package com.raoulvdberge.refinedstorage.tile.data;

import com.raoulvdberge.refinedstorage.container.ContainerBase;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ServerTickEventListener {

    public ServerTickEventListener(IOnServerTickHandler handler) {
        this.handler = handler;
    }

    public void setEnabled(boolean enabled)
    {
        if(this.enabled == enabled)
            return;
        this.enabled = enabled;

        if(this.enabled)
        {
            MinecraftForge.EVENT_BUS.register(this);
        }
        else
        {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    public boolean getEnabled()
    {
        return enabled;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        handler.onServerTick(e);
    }

    private boolean enabled;
    private IOnServerTickHandler handler;
}
