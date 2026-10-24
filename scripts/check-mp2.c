/* AU-1 local native check, synthetic fixture only; no Android app/device access. */
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define REQUIRE(condition) do { if (!(condition)) { fprintf(stderr, "check failed at line %d\n", __LINE__); exit(1); } } while (0)

int main(int argc, char **argv) {
    REQUIRE(argc == 2);
    const AVCodec *codec = avcodec_find_decoder_by_name("mp3");
    REQUIRE(codec != NULL);
    REQUIRE(avcodec_find_decoder_by_name("aac") == NULL); /* narrow build */
    AVCodecContext *context = avcodec_alloc_context3(codec);
    REQUIRE(context != NULL && avcodec_open2(context, codec, NULL) == 0);
    AVPacket *packet = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    REQUIRE(packet && frame);
    FILE *input = fopen(argv[1], "rb");
    REQUIRE(input != NULL);
    /* Fixture: MPEG-1 Layer II, 192 kbit/s, 48 kHz, no padding -> 576 bytes/frame. */
    uint8_t compressed[576];
    int frames = 0;
    int64_t count = 0;
    long double squares = 0;
    int peak = 0;
    SwrContext *resampler = NULL;
    while (fread(compressed, 1, sizeof(compressed), input) == sizeof(compressed)) {
        REQUIRE(compressed[0] == 0xff && (compressed[1] & 0xfe) == 0xfc);
        REQUIRE(av_new_packet(packet, sizeof(compressed)) == 0);
        memcpy(packet->data, compressed, sizeof(compressed));
        REQUIRE(avcodec_send_packet(context, packet) == 0);
        av_packet_unref(packet);
        int result;
        while ((result = avcodec_receive_frame(context, frame)) == 0) {
            REQUIRE(context->sample_rate == 48000 && context->ch_layout.nb_channels == 2);
            if (!resampler) {
                AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
                REQUIRE(swr_alloc_set_opts2(&resampler, &stereo, AV_SAMPLE_FMT_S16, 48000,
                    &context->ch_layout, context->sample_fmt, 48000, 0, NULL) == 0);
                REQUIRE(resampler && swr_init(resampler) == 0);
            }
            int16_t pcm[4096];
            uint8_t *output = (uint8_t *)pcm;
            int samples = swr_convert(resampler, &output, 2048,
                (const uint8_t **)frame->extended_data, frame->nb_samples);
            REQUIRE(samples > 0 && samples <= 2048);
            for (int i = 0; i < samples * 2; i++) {
                int magnitude = abs((int)pcm[i]);
                if (magnitude > peak) peak = magnitude;
                squares += (long double)pcm[i] * pcm[i];
            }
            count += samples * 2;
            frames++;
            av_frame_unref(frame);
        }
        REQUIRE(result == AVERROR(EAGAIN) || result == AVERROR_EOF);
    }
    REQUIRE(feof(input));
    fclose(input);
    double rms = sqrt((double)(squares / (count ? count : 1)));
    REQUIRE(frames == 84 && count == 193536 && rms > 1000 && rms < 4000 && peak > 2000);
    printf("MP2 PASS: %d frames, 48000 Hz stereo, %lld PCM samples, RMS %.1f, peak %d; AAC absent\n",
        frames, (long long)count, rms, peak);
    swr_free(&resampler);
    av_frame_free(&frame);
    av_packet_free(&packet);
    avcodec_free_context(&context);
    return 0;
}
