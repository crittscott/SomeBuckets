package com.github.crittscott.somebuckets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Item-model quads for content layers: the fluid layer retextures the content mask model's
 * generated quads, and the Junk Bucket cover builds its rectangles by hand because they are derived
 * from the opening mask when the renderer bakes, not from a model.
 */
@Environment(EnvType.CLIENT)
public final class FluidMaskGeometry {
    // Pushes the fluid's front and back faces just outside the vessel's so they draw ahead of it.
    private static final float FACE_OFFSET = 0.01F / 16.0F;

    private FluidMaskGeometry() {}

    /** One outward-facing quad in normalized item-model coordinates. */
    public record Face(Direction direction, Vector3fc first, Vector3fc second, Vector3fc third,
                       Vector3fc fourth) {}

    /**
     * Bakes {@code face} with texture coordinates that map x and y linearly onto the sprite in
     * {@code material}.
     */
    static BakedQuad quad(Face face, BakedQuad.MaterialInfo material) {
        TextureAtlasSprite sprite = material.sprite();
        return new BakedQuad(face.first(), face.second(), face.third(), face.fourth(),
                uv(sprite, face.first()), uv(sprite, face.second()),
                uv(sprite, face.third()), uv(sprite, face.fourth()),
                face.direction(), material);
    }

    /**
     * Copies one quad of the generated mask model onto the sprite in {@code material}: each texture
     * coordinate keeps its relative place within the sprite, and front and back faces move just
     * outside the vessel's.
     */
    static BakedQuad retexture(BakedQuad maskQuad, BakedQuad.MaterialInfo material) {
        TextureAtlasSprite mask = maskQuad.materialInfo().sprite();
        TextureAtlasSprite sprite = material.sprite();
        Direction direction = maskQuad.direction();
        float zOffset = direction == Direction.SOUTH ? FACE_OFFSET
                : direction == Direction.NORTH ? -FACE_OFFSET : 0.0F;
        return new BakedQuad(
                offset(maskQuad.position0(), zOffset), offset(maskQuad.position1(), zOffset),
                offset(maskQuad.position2(), zOffset), offset(maskQuad.position3(), zOffset),
                retexture(maskQuad.packedUV0(), mask, sprite), retexture(maskQuad.packedUV1(), mask, sprite),
                retexture(maskQuad.packedUV2(), mask, sprite), retexture(maskQuad.packedUV3(), mask, sprite),
                direction, material);
    }

    private static Vector3fc offset(Vector3fc position, float zOffset) {
        return zOffset == 0.0F ? position : new Vector3f(position.x(), position.y(), position.z() + zOffset);
    }

    private static long retexture(long packedUv, TextureAtlasSprite from, TextureAtlasSprite to) {
        float uFraction = (UVPair.unpackU(packedUv) - from.getU0()) / (from.getU1() - from.getU0());
        float vFraction = (UVPair.unpackV(packedUv) - from.getV0()) / (from.getV1() - from.getV0());
        return UVPair.pack(Mth.lerp(uFraction, to.getU0(), to.getU1()), Mth.lerp(vFraction, to.getV0(), to.getV1()));
    }

    private static long uv(TextureAtlasSprite sprite, Vector3fc position) {
        return UVPair.pack(Mth.lerp(position.x(), sprite.getU0(), sprite.getU1()),
                Mth.lerp(position.y(), sprite.getV1(), sprite.getV0()));
    }
}
