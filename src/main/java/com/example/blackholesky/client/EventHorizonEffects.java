package com.example.blackholesky.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/** Cielo con un agujero negro como sol y un tsunami gigante del lado opuesto. */
public class EventHorizonEffects extends DimensionSpecialEffects {
    private static final float R = 100f;
    // 0 = noche, 1 = dia (se actualiza en cada frame)
    private static float DAY = 0f;
    // Direccion del agujero negro (fija en el cielo)
    private static final Vector3f BH = new Vector3f(0.75f, 0.42f, 0.2f).normalize();
    private static final float[][] STARS = new float[600][4];

    static {
        Random rnd = new Random(1337);
        for (float[] s : STARS) {
            double az = rnd.nextDouble() * Math.PI * 2;
            double y = -0.1 + rnd.nextDouble() * 1.1;
            double h = Math.sqrt(Math.max(0, 1 - y * y));
            s[0] = (float) (h * Math.cos(az));
            s[1] = (float) y;
            s[2] = (float) (h * Math.sin(az));
            s[3] = 0.25f + rnd.nextFloat() * 0.5f;
        }
    }

    public EventHorizonEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        float b = brightness * 0.94f + 0.06f;
        return new Vec3(color.x * b, color.y * b, color.z * b);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (!SkyToggle.isEnabled()) return false;   // cielo vanilla si el shader esta apagado
        draw(level, ticks, partialTick, poseStack);
        return true;
    }

    /** Dibuja todo el cielo (usado por la dimension y por el Overworld). */
    public static void draw(ClientLevel level, int ticks, float partialTick, PoseStack poseStack) {
        float time = (ticks + partialTick) / 20f;
        float sun = (float) Math.cos(level.getTimeOfDay(partialTick) * Math.PI * 2);
        DAY = Math.max(0f, Math.min(1f, sun * 2f + 0.5f));
        float gl = 1f - 0.6f * DAY;
        Matrix4f m = poseStack.last().pose();

        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        drawDome(m);
        drawStars(m);

        // Brillo aditivo del agujero negro
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        fan(m, BH, 70f, new float[]{1f, 0.45f, 0.15f, 0.35f * gl}, new float[]{1f, 0.2f, 0.05f, 0f});
        fan(m, BH, 28f, new float[]{1f, 0.8f, 0.5f, 0.6f * gl}, new float[]{1f, 0.4f, 0.1f, 0f});
        ring(m, 14f, 46f, 0.22f, 0.35f, 0, Math.PI, time, false);       // mitad de atras del disco

        // Horizonte de sucesos (negro opaco)
        RenderSystem.defaultBlendFunc();
        fan(m, BH, 11f, new float[]{0f, 0f, 0f, 1f}, new float[]{0f, 0f, 0f, 1f});

        // Mitad de adelante del disco + anillo de lente gravitacional
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        ring(m, 14f, 46f, 0.22f, 0.35f, Math.PI, Math.PI * 2, time, false);
        ring(m, 11f, 15f, 1f, 0f, 0, Math.PI * 2, time, true);

        // Tsunami
        RenderSystem.defaultBlendFunc();
        drawTsunami(m, time, 0);
        drawTsunami(m, time, 1);

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    // ---------- Utilidades ----------

    private static BufferBuilder begin(VertexFormat.Mode mode) {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float bl, float a) {
        b.vertex(m, x, y, z).color(r, g, bl, a).endVertex();
    }

    private static void flush(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
    }

    private static Vector3f[] basis(Vector3f d) {
        Vector3f up = Math.abs(d.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(d).cross(up).normalize();
        Vector3f u = new Vector3f(right).cross(d).normalize();
        return new Vector3f[]{right, u};
    }

    private static float[] pt(Vector3f d, Vector3f[] bs, float x, float y) {
        return new float[]{
                d.x * R + bs[0].x * x + bs[1].x * y,
                d.y * R + bs[0].y * x + bs[1].y * y,
                d.z * R + bs[0].z * x + bs[1].z * y};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---------- Fondo ----------

    private static void domeColor(BufferBuilder b, Matrix4f m, double az, double el) {
        float x = (float) (Math.cos(el) * Math.cos(az));
        float y = (float) Math.sin(el);
        float z = (float) (Math.cos(el) * Math.sin(az));
        float up = (float) Math.pow(Math.max(0, Math.min(1, y)), 0.5);
        float r = lerp(lerp(0.12f, 0.005f, up), lerp(0.60f, 0.12f, up), DAY);
        float g = lerp(lerp(0.04f, 0.005f, up), lerp(0.45f, 0.22f, up), DAY);
        float bl = lerp(lerp(0.14f, 0.025f, up), lerp(0.72f, 0.60f, up), DAY);
        float glow = (float) Math.pow(Math.max(0, x * BH.x + y * BH.y + z * BH.z), 4) * 0.5f * (1f - 0.5f * DAY);
        v(b, m, x * R * 1.2f, y * R * 1.2f, z * R * 1.2f, r + glow * 0.9f, g + glow * 0.35f, bl + glow * 0.1f, 1f);
    }

    private static void drawDome(Matrix4f m) {
        int stacks = 10, slices = 32;
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        for (int s = 0; s < stacks; s++) {
            double e0 = lerp(-0.35f, (float) (Math.PI / 2), s / (float) stacks);
            double e1 = lerp(-0.35f, (float) (Math.PI / 2), (s + 1) / (float) stacks);
            for (int k = 0; k < slices; k++) {
                double a0 = k * Math.PI * 2 / slices, a1 = (k + 1) * Math.PI * 2 / slices;
                domeColor(b, m, a0, e0);
                domeColor(b, m, a1, e0);
                domeColor(b, m, a1, e1);
                domeColor(b, m, a0, e1);
            }
        }
        flush(b);
    }

    private static void drawStars(Matrix4f m) {
        float sa = 0.9f * (1f - DAY);
        if (sa <= 0.02f) return;
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        for (float[] s : STARS) {
            Vector3f d = new Vector3f(s[0], s[1], s[2]);
            Vector3f[] bs = basis(d);
            float h = s[3];
            float[] p1 = pt(d, bs, -h, -h), p2 = pt(d, bs, h, -h), p3 = pt(d, bs, h, h), p4 = pt(d, bs, -h, h);
            v(b, m, p1[0], p1[1], p1[2], 1, 1, 1, sa);
            v(b, m, p2[0], p2[1], p2[2], 1, 1, 1, sa);
            v(b, m, p3[0], p3[1], p3[2], 1, 1, 1, sa);
            v(b, m, p4[0], p4[1], p4[2], 1, 1, 1, sa);
        }
        flush(b);
    }

    // ---------- Agujero negro ----------

    private static void fan(Matrix4f m, Vector3f d, float rad, float[] c, float[] e) {
        Vector3f[] bs = basis(d);
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLE_FAN);
        v(b, m, d.x * R, d.y * R, d.z * R, c[0], c[1], c[2], c[3]);
        int n = 48;
        for (int i = 0; i <= n; i++) {
            double t = i * Math.PI * 2 / n;
            float[] p = pt(d, bs, (float) Math.cos(t) * rad, (float) Math.sin(t) * rad);
            v(b, m, p[0], p[1], p[2], e[0], e[1], e[2], e[3]);
        }
        flush(b);
    }

    /** Disco de acrecion (elipse inclinada) con brillo que "gira" con el tiempo. */
    private static void ring(Matrix4f m, float inner, float outer, float squash, float tilt,
                             double t0, double t1, float time, boolean lens) {
        Vector3f[] bs = basis(BH);
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLE_STRIP);
        int n = 64;
        for (int i = 0; i <= n; i++) {
            double t = t0 + (t1 - t0) * i / n;
            float bright = 0.55f + 0.45f * (float) Math.cos(t - time * 1.5);
            float ex = (float) Math.cos(t), ey = (float) Math.sin(t) * squash;
            float x = ex * ct - ey * st, y = ex * st + ey * ct;
            float[] pi = pt(BH, bs, x * inner, y * inner);
            float[] po = pt(BH, bs, x * outer, y * outer);
            if (lens) {
                v(b, m, pi[0], pi[1], pi[2], 1f, 0.9f, 0.7f, 0.9f);
                v(b, m, po[0], po[1], po[2], 1f, 0.4f, 0.1f, 0f);
            } else {
                v(b, m, pi[0], pi[1], pi[2], 1f, 0.95f, 0.75f, bright);
                v(b, m, po[0], po[1], po[2], 1f, 0.25f, 0.03f, 0.05f);
            }
        }
        flush(b);
    }

    // ---------- Tsunami ----------

    private static void drawTsunami(Matrix4f m, float time, int layer) {
        double center = Math.atan2(-BH.z, -BH.x);   // lado opuesto al agujero negro
        double span = Math.toRadians(150);
        int n = 64;
        float rad = layer == 0 ? 100f : 93f;
        float scale = layer == 0 ? 1.15f : 0.8f;
        float rise = 0.9f + 0.1f * (float) Math.sin(time * 0.5 + layer);

        float[][] bot = new float[n + 1][3], mid = new float[n + 1][3], top = new float[n + 1][3];
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double a = center + (f - 0.5) * span;
            float env = (float) Math.pow(Math.max(0, Math.cos((f - 0.5) * Math.PI)), 0.6);
            float h = ((0.10f + 0.55f * env) * rise
                    + 0.04f * (float) Math.sin(a * 9 + time * 1.3 + layer * 2)
                    + 0.03f * (float) Math.sin(a * 17 - time * 2.1)) * scale;
            float topR = rad - 14f * env;            // la cresta se curva hacia el jugador
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            float yTop = rad * (float) Math.tan(Math.max(0.02f, h));
            bot[i] = new float[]{cx * rad, -rad * 0.3f, cz * rad};
            mid[i] = new float[]{cx * rad, yTop * 0.82f, cz * rad};
            top[i] = new float[]{cx * topR, yTop, cz * topR};
        }
        float k = (layer == 0 ? 0.6f : 1f) * (0.55f + 0.6f * DAY);
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        for (int i = 0; i < n; i++) {
            // cuerpo de agua
            quad(b, m, bot[i], bot[i + 1], mid[i + 1], mid[i],
                    0.02f * k, 0.10f * k, 0.25f * k, 0.96f, 0.08f * k, 0.45f * k, 0.65f * k, 0.96f);
            // espuma de la cresta
            quad(b, m, mid[i], mid[i + 1], top[i + 1], top[i],
                    0.08f * k, 0.45f * k, 0.65f * k, 0.96f, 0.95f * k, 1f * k, 1f * k, 1f);
        }
        flush(b);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float[] a, float[] bb, float[] c, float[] d,
                             float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
        v(b, m, a[0], a[1], a[2], r0, g0, b0, a0);
        v(b, m, bb[0], bb[1], bb[2], r0, g0, b0, a0);
        v(b, m, c[0], c[1], c[2], r1, g1, b1, a1);
        v(b, m, d[0], d[1], d[2], r1, g1, b1, a1);
    }
}
