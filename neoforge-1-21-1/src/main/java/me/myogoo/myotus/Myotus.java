package me.myogoo.myotus;

import com.mojang.logging.LogUtils;

import me.myogoo.myotus.api.MyotusAPI;
import me.myogoo.myotus.data.MyotusDataGenerators;
import me.myogoo.myotus.impl.MyotusAPIImpl;
import me.myogoo.myotus.init.MyoBlocks;
import me.myogoo.myotus.init.MyoCondition;
import me.myogoo.myotus.init.MyoConfig;
import me.myogoo.myotus.init.MyoCreativeModeTabs;
import me.myogoo.myotus.init.MyoItems;
import me.myogoo.myotus.platform.AnnotationScanData;
import me.myogoo.myotus.platform.mod.NeoForgeModList;
import me.myogoo.myotus.util.mod.ModIntegrationManager;
import me.myogoo.myotus.util.mod.MyoModVersionMismatchException;
import me.myogoo.myotus.util.reflect.annotation.AnnotationScanner;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;

@Mod(Myotus.MODID)
public class Myotus {
    public static final String MODID = "myotus";
    private static final String VERSION_MISMATCH_LOADING_ERROR =
            "The {0} must satisfy version range {1}. Current version: {2}";
    public static final boolean DEFAULT_DEV_MODE = !FMLLoader.isProduction();
    public static boolean DEV_MODE = DEFAULT_DEV_MODE;
    public static final Logger LOGGER = LogUtils.getLogger();

    public Myotus(IEventBus modEventBus, ModContainer modContainer) {
        MyotusAPI._setInstance(MyotusAPIImpl.INSTANCE);
        AnnotationScanner.setAnnotationProvider(AnnotationScanData::getAnnotations);
        try {
            ModIntegrationManager.setModList(NeoForgeModList.INSTANCE);
        } catch (MyoModVersionMismatchException e) {
            throw new ModLoadingException(
                    ModLoadingIssue.error(
                            VERSION_MISMATCH_LOADING_ERROR,
                            e.getDisplayModName(),
                            e.getVersionRange(),
                            e.getModVersion())
                            .withCause(e)
                            .withAffectedMod(modContainer.getModInfo()));
        }
        MyoCondition.REGISTER.register(modEventBus);
        MyoCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        MyoBlocks.BLOCKS.register(modEventBus);
        MyoItems.ITEMS.register(modEventBus);
        modEventBus.addListener(MyotusDataGenerators::onGatherData);
        MyoConfig.initialize(modContainer);
    }

    public static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
