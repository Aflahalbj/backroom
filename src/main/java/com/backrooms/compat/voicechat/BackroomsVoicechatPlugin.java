package com.backrooms.compat.voicechat;

import com.backrooms.component.PlayerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;

/**
 * Port dari {@code com.sp.sounds.voicechat.BackroomsVoicechatPlugin}:
 * mencatat berapa lama tiap pemain bicara (korban Skinwalker = yang paling
 * jarang bicara), merekam potongan suaranya untuk ditirukan, membisukan
 * korban, dan merendahkan nada suara Skinwalker wujud asli di klien.
 *
 * <p>{@code Utils.dbToPerc}/{@code Utils.getHighestAudioLevel} voice chat
 * dipindah ke kelas lain di versi baru voice chat, jadi rumusnya disalin
 * apa adanya di bawah ({@link #dbToPerc}, {@link #getHighestAudioLevel}).
 */
@ForgeVoicechatPlugin
public class BackroomsVoicechatPlugin implements VoicechatPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static VoicechatServerApi voicechatApi;
    private ConcurrentHashMap<UUID, OpusDecoder> decoders;
    public static ConcurrentHashMap<UUID, Float> speakingTime = new ConcurrentHashMap<>();
    public static Map<UUID, Vector<short[]>> randomSpeakingList = new HashMap<>();
    private static Map<UUID, short[]> totalSoundData = new HashMap<>();
    private static Map<UUID, Integer> ticks = new HashMap<>();

    @Override
    public String getPluginId() {
        return "spb-revamped";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.decoders = new ConcurrentHashMap<>();
        speakingTime = new ConcurrentHashMap<>();
        randomSpeakingList = new HashMap<>();
        ticks = new HashMap<>();
        totalSoundData = new HashMap<>();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.class, this::SkinWalkerVoicesPitchDown);
        registration.registerEvent(MicrophonePacketEvent.class, this::recordPlayersTalking);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStop);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::playerDisconnect);
        registration.registerEvent(PlayerConnectedEvent.class, this::playerConnect);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStart);
    }

    private void SkinWalkerVoicesPitchDown(OpenALSoundEvent openALSoundEvent) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            if (client.player.level() != null) {
                UUID channelID = openALSoundEvent.getChannelId();
                if (channelID != null) {
                    try {
                        List<SkinWalkerEntity> skinWalkerList = client.player.level().getEntitiesOfClass(SkinWalkerEntity.class,
                                client.player.getBoundingBox().inflate(50.0D), EntitySelector.LIVING_ENTITY_STILL_ALIVE);
                        if (skinWalkerList.isEmpty()) {
                            return;
                        }

                        for (SkinWalkerEntity skinWalker : skinWalkerList) {
                            if (skinWalker.getUUID().equals(channelID) && skinWalker.component.isInTrueForm()) {
                                AL10.alSourcei(openALSoundEvent.getSource(), 53248, 53251);
                                AL10.alSourcef(openALSoundEvent.getSource(), 4099, 0.8F);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error pitching down the Skinwalker's Voice: {}", String.valueOf(e));
                    }
                }
            }
        }
    }

    private void recordPlayersTalking(MicrophonePacketEvent microphonePacketEvent) {
        VoicechatConnection senderConnection = microphonePacketEvent.getSenderConnection();
        if (senderConnection != null) {
            Object sender = senderConnection.getPlayer().getPlayer();
            if (sender instanceof Player player) {
                if (ticks.containsKey(player.getUUID())) {
                    ticks.put(player.getUUID(), ticks.get(player.getUUID()) + 1);
                } else {
                    ticks.put(player.getUUID(), 0);
                }

                byte[] encodedData = microphonePacketEvent.getPacket().getOpusEncodedData();
                byte[] copyData = encodedData.clone();
                if (copyData.length == 0) {
                    this.decoders.get(player.getUUID()).resetState();
                    if (ticks.get(player.getUUID()) > 40 && ticks.get(player.getUUID()) < 200) {
                        RandomSource random = RandomSource.create();
                        Vector<short[]> soundList = new Vector<>();
                        if (randomSpeakingList.containsKey(player.getUUID())) {
                            soundList = randomSpeakingList.getOrDefault(player.getUUID(), new Vector<>());
                        }

                        if (soundList.size() < 20) {
                            soundList.add(totalSoundData.get(player.getUUID()));
                        } else {
                            soundList.set(random.nextIntBetweenInclusive(0, randomSpeakingList.size() - 1), totalSoundData.get(player.getUUID()));
                        }

                        randomSpeakingList.put(player.getUUID(), soundList);
                    }

                    ticks.put(player.getUUID(), 0);
                    totalSoundData.remove(player.getUUID());
                } else {
                    if (!this.decoders.containsKey(player.getUUID())) {
                        this.decoders.put(player.getUUID(), microphonePacketEvent.getVoicechat().createDecoder());
                    }

                    OpusDecoder decoder = this.decoders.get(player.getUUID());
                    short[] data = decoder.decode(encodedData);
                    PlayerComponent component = PlayerComponent.get(player);
                    if (component.shouldBeMuted()) {
                        microphonePacketEvent.cancel();
                    } else {
                        component.setSpeaking(true);
                        if (!player.isSpectator() && !player.isCreative()) {
                            if (!speakingTime.containsKey(player.getUUID())) {
                                speakingTime.put(player.getUUID(), 0.0F);
                            }

                            speakingTime.put(player.getUUID(), speakingTime.get(player.getUUID()) + 1.0E-4F);
                            if (!component.isVisibleToEntity()) {
                                double volume = dbToPerc(getHighestAudioLevel(data));
                                if (volume >= 0.8D) {
                                    component.setTalkingTooLoud(true);
                                    component.resetTalkingTooLoudTimer();
                                }
                            }
                        } else if (!component.isBeingCaptured() && !component.hasBeenCaptured()) {
                            speakingTime.remove(player.getUUID());
                        }

                        short[] totalData;
                        if (totalSoundData.containsKey(player.getUUID())) {
                            totalData = totalSoundData.get(player.getUUID());
                        } else {
                            totalData = new short[0];
                        }

                        short[] result = new short[totalData.length + data.length];
                        System.arraycopy(totalData, 0, result, 0, totalData.length);
                        System.arraycopy(data, 0, result, totalData.length, data.length);
                        totalSoundData.put(player.getUUID(), result);
                    }
                }
            }
        }
    }

    private void onServerStart(VoicechatServerStartedEvent voicechatServerStartedEvent) {
        voicechatApi = voicechatServerStartedEvent.getVoicechat();
    }

    private void playerConnect(PlayerConnectedEvent playerConnectedEvent) {
        Player player = (Player) playerConnectedEvent.getConnection().getPlayer().getPlayer();
        if (!player.isSpectator() && !player.isCreative()) {
            speakingTime.put(playerConnectedEvent.getConnection().getPlayer().getUuid(), 0.0F);
        }
    }

    private void playerDisconnect(PlayerDisconnectedEvent playerDisconnectedEvent) {
        UUID playerUUID = playerDisconnectedEvent.getPlayerUuid();
        this.removePlayerDecoder(playerUUID, this.decoders.get(playerUUID));
        speakingTime.remove(playerUUID);
    }

    private void onServerStop(VoicechatServerStoppedEvent voicechatServerStoppedEvent) {
        this.decoders.forEach(this::removePlayerDecoder);
        speakingTime.clear();
    }

    private void removePlayerDecoder(UUID uuid, OpusDecoder decoder) {
        if (decoder != null) {
            decoder.close();
        }
        this.decoders.remove(uuid);
    }

    // ------------------------------------------- rumus dari voice chat

    /** {@code de.maxhenkel.voicechat.voice.common.Utils.dbToPerc}. */
    private static double dbToPerc(double db) {
        db = Math.min(Math.max(db, -127.0D), 0.0D);
        return (db + Math.abs(-127.0D)) / Math.abs(-127.0D);
    }

    /** {@code Utils.getHighestAudioLevel}: level dB sampel paling keras. */
    private static double getHighestAudioLevel(short[] samples) {
        short highest = 0;
        for (short sample : samples) {
            if (Math.abs(sample) > highest) {
                highest = (short) Math.abs(sample);
            }
        }
        return sampleDb(highest);
    }

    /** {@code Utils.sampleDb}. */
    private static double sampleDb(short sample) {
        if (sample == 0) {
            return -127.0D;
        }
        double db = 20.0D * Math.log10((double) Math.abs(sample) / 32768.0D);
        if (!Double.isFinite(db)) {
            return -127.0D;
        }
        return db > 0.0D ? 0.0D : db;
    }
}
