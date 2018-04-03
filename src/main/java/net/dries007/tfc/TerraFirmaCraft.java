package net.dries007.tfc;

import net.dries007.tfc.objects.CreativeTab;
import net.dries007.tfc.world.classic.WorldTypeTFC;
import net.dries007.tfc.world.classic.capabilities.ChunkCapabilityHandler;
import net.dries007.tfc.world.classic.capabilities.ChunkDataMessage;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

import static net.dries007.tfc.Constants.*;

@SuppressWarnings("DefaultAnnotationParam")
@Mod(modid = MOD_ID, name = MOD_NAME, useMetadata = true, guiFactory = GUI_FACTORY, canBeDeactivated = false)
@Mod.EventBusSubscriber()
public class TerraFirmaCraft
{
    @Mod.Instance()
    private static TerraFirmaCraft instance = null;

    @Mod.Metadata()
    private static ModMetadata metadata = null;

    private Logger log;
    private WorldTypeTFC worldTypeTFC;
    private SimpleNetworkWrapper network;

    @Mod.EventHandler
    public void construction(FMLConstructionEvent event)
    {
        FluidRegistry.enableUniversalBucket();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        log = event.getModLog();
        // No need to sync config here, forge magic

        network = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
        int id = 0;
        network.registerMessage(ChunkDataMessage.Handler.class, ChunkDataMessage.class, ++id, Side.CLIENT);

        ChunkCapabilityHandler.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        CreativeTab.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        worldTypeTFC = new WorldTypeTFC();
    }

    public static Logger getLog()
    {
        return instance.log;
    }

    public static String getVersion()
    {
        return metadata.version;
    }

    public static WorldTypeTFC getWorldTypeTFC()
    {
        return instance.worldTypeTFC;
    }

    public static SimpleNetworkWrapper getNetwork()
    {
        return instance.network;
    }
}
