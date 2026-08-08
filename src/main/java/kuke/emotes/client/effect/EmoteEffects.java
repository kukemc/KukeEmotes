package kuke.emotes.client.effect;

import kuke.emotes.client.EmoteSession;
import kuke.emotes.client.model.EmoteModel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The eight emotes that do more than animate.
 *
 * <p>Ported from upstream's {@code common.emotes.*} subclasses (GPL-3.0), keeping their tick
 * timings and bone anchors exactly.
 *
 * <p><b>Particle substitution.</b> Upstream drew popcorn kernels and salt grains as bespoke 3D
 * cube particles with their own texture sheet — that whole path (immediate-mode {@code
 * ModelRenderer} inside {@code Particle.renderParticle}) no longer exists, and rebuilding it means
 * a custom particle type, a sprite set and an atlas entry for two cosmetic bursts. Vanilla item
 * particles of a white item read almost identically at the scale these are seen, so that is what
 * is used; swapping in real 3D particles later only touches this file.
 */
public final class EmoteEffects {

    private static final Map<String, EmoteEffect> EFFECTS = new HashMap<>();

    /** Right hand bone tip — upstream anchors hand-held bursts here. */
    private static final String HAND = "low_right_arm.end";
    private static final String HEAD = "head";

    static {
        EFFECTS.put("popcorn", new PopcornEffect());
        EFFECTS.put("pure_salt", new PureSaltEffect());
        EFFECTS.put("crying", new CryingEffect());
        EFFECTS.put("star_power", new StarPowerEffect());
        EFFECTS.put("rock_paper_scissors", new RockPaperScissorsEffect());
        EFFECTS.put("sneeze", new SneezeEffect());
        EFFECTS.put("disgusted", new DisgustedEffect());
    }

    private EmoteEffects() {
    }

    @Nullable
    public static EmoteEffect get(String emoteKey) {
        return EFFECTS.get(emoteKey);
    }

    /** Upstream's 30 fps → tick conversion, needed for the 2020-era emotes' timings. */
    private static int toTicks(int frames30) {
        return (int) Math.floor(frames30 / 30F * 20F);
    }

    /** A bucket of popcorn appears in hand and kernels fly out on four beats. */
    private static final class PopcornEffect implements EmoteEffect {

        @Override
        public void start(EmoteSession session) {
            session.showProp(EmoteModel.POPCORN);
        }

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            if (tick == 8 || tick == 32 || tick == 56 || tick == 86) {
                Vec3 hand = context.bonePosition(HAND, 0F, 0.15F, 0F);

                for (int i = 0; i < 15; i++) {
                    context.particle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE_MEAL)),
                        hand, 0D, 0.1D, 0D);
                }
            }
        }
    }

    /** Salt is poured from the hand, then a final fistful is thrown. */
    private static final class PureSaltEffect implements EmoteEffect {

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            if (tick > 18 && tick <= 78 && tick % 2 == 0) {
                Vec3 hand = context.bonePosition(HAND, 0F, 0.15F, 0F);
                int count = tick == 78 ? 12 : 1;

                for (int i = 0; i < count; i++) {
                    context.particle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SUGAR)),
                        hand, 0D, 0D, 0D);
                }
            }
        }
    }

    /**
     * Tears, from both eyes.
     *
     * <p>The anchor is worked out from the head bone rather than eyeballed: the bone's origin sits
     * at y=1.5 with no rotation, a player's eyes are at y≈1.62 and the face is the +Z plane at
     * z≈0.25 — so eye level is 0.12 above the bone and a hair in front of the face, with the eyes
     * themselves ±0.075 either side of centre. Upstream's single 0.5/0.35 anchor put the drop above
     * the crown, which reads as a leak rather than crying once the head starts moving.
     */
    private static final class CryingEffect implements EmoteEffect {

        private static final float EYE_UP = 0.12F;
        private static final float EYE_OUT = 0.27F;
        private static final float EYE_SIDE = 0.075F;

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            if (tick % 4 != 0) {
                return;
            }

            /* Alternate eyes so the drips stagger instead of falling in lockstep. */
            float side = (tick / 4) % 2 == 0 ? -EYE_SIDE : EYE_SIDE;

            /* Vanilla has no tear: SPLASH throws itself upward, DRIPPING_WATER needs a block
             * overhead to hang from and vanishes in mid-air, so FALLING_WATER it is — thinned to
             * one drop per four ticks so it reads as tears rather than a running tap. A real
             * short-lived tear would need a custom particle type. */
            context.particle(ParticleTypes.FALLING_WATER,
                context.bonePosition(HEAD, side, EYE_UP, EYE_OUT), 0D, 0D, 0D);
        }
    }

    /**
     * A burst of end-rod sparks, then a rainbow the hand paints in the air.
     *
     * <p>Upstream drew the rainbow with {@code SPELL_MOB} particles, abusing the velocity fields to
     * smuggle an RGB colour through (a 1.12 trick). Modern coloured dust does the same thing
     * honestly, and keeps upstream's exact colour ramp.
     */
    private static final class StarPowerEffect implements EmoteEffect {

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            if (tick == 30) {
                Vec3 hand = context.bonePosition(HAND, 0F, 0.15F, 0F);

                for (int i = 0; i < 15; i++) {
                    context.particle(ParticleTypes.END_ROD, hand,
                        context.level.random.nextDouble() * 0.05D - 0.025D,
                        context.level.random.nextDouble() * 0.05D - 0.025D,
                        context.level.random.nextDouble() * 0.05D - 0.025D);
                }
            }

            if (tick >= 33 && tick < 43) {
                Vec3 hand = context.bonePosition(HAND, 0F, 0.15F, 0F);
                float p = (tick - 33) / 10F;
                int color = rainbow(p);

                for (int i = 0; i < 7; i++) {
                    context.particle(new DustParticleOptions(color, 0.7F),
                        hand.add(random(context, 0.05F), random(context, 0.05F), random(context, 0.05F)),
                        0D, 0D, 0D);
                }
            }
        }

        /** Upstream's colour ramp, as a packed RGB. */
        private static int rainbow(float p) {
            if (p < 0.2F) {
                return 0xFF0000;
            } else if (p < 0.35F) {
                return 0xFF8000;
            } else if (p < 0.45F) {
                return 0xFFFF00;
            } else if (p < 0.65F) {
                return 0x40FF00;
            } else if (p < 0.85F) {
                return 0x00BFFF;
            }

            return 0x0000FF;
        }
    }

    /**
     * Rock, paper or scissors — the thrown sign appears in the hand mid-animation and scales in
     * and out. Which one it is comes from the emote key's variant suffix so that every client
     * shows the same sign (upstream rolled it locally, so two players watching disagreed).
     */
    private static final class RockPaperScissorsEffect implements EmoteEffect {

        @Override
        public void start(EmoteSession session) {
            session.setProp(switch (session.variant()) {
                case "paper" -> new ItemStack(Items.PAPER);
                case "scissors" -> new ItemStack(Items.SHEARS);
                default -> new ItemStack(Items.STONE);
            });
            session.setPropScale(0F);
        }

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            EmoteSession session = context.session;

            if (tick > 25 && tick < 55) {
                if (tick < 30) {
                    session.setPropScale((tick - 25 + context.partialTick) / 5F);
                } else if (tick >= 50) {
                    session.setPropScale(1F - (tick - 50 + context.partialTick) / 5F);
                } else {
                    session.setPropScale(1F);
                }
            } else {
                session.setPropScale(0F);
            }
        }
    }

    /** One cloud burst on the sneeze. */
    private static final class SneezeEffect implements EmoteEffect {

        private static final int SNEEZE_TICK = toTicks(121) - 1;

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            if (tick == SNEEZE_TICK) {
                Vec3 face = context.bonePosition(HEAD, 0F, 0.125F, 0.25F);

                for (int i = 0; i < 10; i++) {
                    context.particle(ParticleTypes.CLOUD, face,
                        random(context, 0.05F), -0.025D, random(context, 0.05F));
                }
            }
        }
    }

    /** Retching: green item crumbs for about a second. */
    private static final class DisgustedEffect implements EmoteEffect {

        private static final int FROM = toTicks(117);
        private static final int TO = toTicks(140);

        @Override
        public void progress(EmoteEffectContext context, int tick) {
            if (tick >= FROM && tick < TO) {
                Vec3 face = context.bonePosition(HEAD, 0F, 0.125F, 0.25F);

                for (int i = 0; i < 10; i++) {
                    context.particle(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GREEN_DYE)),
                        face.add(random(context, 0.1F), 0D, random(context, 0.1F)),
                        random(context, 0.05F), -0.125D, random(context, 0.05F));
                }
            }
        }
    }

    /** Upstream's {@code Emote.rand(factor)}: a value in [-factor/2, factor/2). */
    private static double random(EmoteEffectContext context, float factor) {
        return context.level.random.nextFloat() * factor - factor / 2F;
    }
}
