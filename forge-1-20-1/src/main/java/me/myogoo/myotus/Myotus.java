package me.myogoo.myotus;

import com.mojang.logging.LogUtils;

import me.myogoo.myotus.impl.MyotusAPIImpl;
import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.init.MyoCondition;
import me.myogoo.myotus.init.MyoBlocks;
import me.myogoo.myotus.init.MyoConfig;
import me.myogoo.myotus.init.MyoCreativeModeTabs;
import me.myogoo.myotus.init.MyoItems;
import me.myogoo.myotus.platform.AnnotationScanData;
import me.myogoo.myotus.platform.mod.ForgeModList;
import me.myogoo.myotus.util.mod.ModIntegrationManager;
import me.myogoo.myotus.util.mod.MyoModVersionMismatchException;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Myotus.MODID)
public class Myotus {
    public static final String MODID = "myotus";
    private static final String VERSION_MISMATCH_LOADING_ERROR =
            "The {3} must satisfy version range {4}. Current version: {5}";
    public static final boolean DEFAULT_DEV_MODE = !FMLEnvironment.production;
    public static boolean DEV_MODE = DEFAULT_DEV_MODE;
    public static final Logger LOGGER = LogUtils.getLogger();

    private final IEventBus modEventBus;

    public Myotus(FMLJavaModLoadingContext context) {
        this.modEventBus = context.getModEventBus();
        MyotusAPI._setInstance(MyotusAPIImpl.INSTANCE);
        AnnotationScanner.setAnnotationProvider(AnnotationScanData::getAnnotations);
        try {
            ModIntegrationManager.setModList(ForgeModList.INSTANCE);
        } catch (MyoModVersionMismatchException e) {
            throw new ModLoadingException(
                    context.getContainer().getModInfo(),
                    ModLoadingStage.CONSTRUCT,
                    VERSION_MISMATCH_LOADING_ERROR,
                    e,
                    e.getDisplayModName(),
                    e.getVersionRange(),
                    e.getModVersion());
        }
        MyoCreativeModeTabs.CREATIVE_MODE_TABS.register(this.modEventBus);
        this.modEventBus.addListener(MyoCreativeModeTabs::addAE2WTLibTerminals);
        MyoBlocks.BLOCKS.register(this.modEventBus);
        MyoItems.ITEMS.register(this.modEventBus);
        MyoCondition.register();
        MyoConfig.initialize(context);
    }

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
