package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves a representative RGB color from a Fabric fluid variant's sprite and tint. */
final class FabricClientFluidColors {
    private static final int NO_COLOR = -1;
    private static final Map<ResourceLocation, Integer> BASE_COLORS = new ConcurrentHashMap<>();

    private FabricClientFluidColors() {}

    static int color(StoredFluid stored, int fallback) {
        FluidVariant variant = FluidVariant.of(stored.fluid(), stored.variantTag());
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        int base = sprite == null ? fallback
                : BASE_COLORS.computeIfAbsent(sprite.contents().name(), FabricClientFluidColors::readAverage);
        if (base == NO_COLOR) base = fallback;
        return multiply(base, FluidVariantRendering.getColor(variant));
    }

    private static int readAverage(ResourceLocation texture) {
        ResourceLocation file = new ResourceLocation(texture.getNamespace(),
                "textures/" + texture.getPath() + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return NO_COLOR;
        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            long red = 0, green = 0, blue = 0, weight = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int alpha = abgr >>> 24;
                    if (alpha == 0) continue;
                    red += (long) (abgr & 0xFF) * alpha;
                    green += (long) ((abgr >>> 8) & 0xFF) * alpha;
                    blue += (long) ((abgr >>> 16) & 0xFF) * alpha;
                    weight += alpha;
                }
            }
            return weight == 0 ? NO_COLOR : ((int) (red / weight) << 16)
                    | ((int) (green / weight) << 8) | (int) (blue / weight);
        } catch (IOException exception) {
            return NO_COLOR;
        }
    }

    private static int multiply(int rgb, int argbTint) {
        int red = ((rgb >>> 16) & 0xFF) * ((argbTint >>> 16) & 0xFF) / 255;
        int green = ((rgb >>> 8) & 0xFF) * ((argbTint >>> 8) & 0xFF) / 255;
        int blue = (rgb & 0xFF) * (argbTint & 0xFF) / 255;
        return (red << 16) | (green << 8) | blue;
    }
}
